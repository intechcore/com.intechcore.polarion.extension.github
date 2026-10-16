package com.intechcore.polarion.extension.github.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intechcore.polarion.extension.github.properties.GithubExtensionConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the open issues, discussions and pull requests of a repository, and the checks of a commit,
 * from the GitHub REST API. With a token GitHub allows 5000 requests per hour, without one 60 for the
 * whole server. The token of the user ({@link RequestToken}) goes before the token of the server. A
 * token goes only to the configured API host.
 *
 * <p>The cache serves every user, so it keeps only what everyone may see: the lists of a public
 * repository. A private repository is read again on every request.
 */
public class GithubClient {

    public static final String DEFAULT_API_URL = "https://api.github.com";

    // The full representation carries the body twice: as Markdown and as the HTML GitHub renders.
    private static final String ACCEPT = "application/vnd.github.full+json";
    private static final int PAGE_SIZE = 100;
    // A repository with more open items than this is not what the import is for. The limit also
    // ends a loop over "next" links that never stop.
    private static final int MAX_PAGES = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_REDIRECTS = 3;
    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"next\"");
    private static final Pattern NAME = Pattern.compile("(?!\\.+$)[A-Za-z0-9._-]+");
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");
    private static final TypeReference<List<GithubItem>> ITEMS = new TypeReference<>() {
    };
    private static final TypeReference<List<GithubAdvisory>> ADVISORIES = new TypeReference<>() {
    };

    /**
     * How long a list read from GitHub serves again, unless the property {@code cache.minutes} says
     * otherwise. Without a token GitHub allows 60 requests per hour for the whole server, and a
     * conditional request costs one as well, so only a cache saves them. With a token the limit is
     * 5000 per hour; the cache keeps the pages fast all the same.
     */
    public static final Duration DEFAULT_CACHE_TIME = Duration.ofMinutes(5);

    /**
     * How old a list must be before {@link #forget} drops it. A second click, or a second user, within
     * that time gets the list just read instead of another request.
     */
    public static final Duration REFRESH_PAUSE = Duration.ofMinutes(1);

    private static final GithubClient SHARED = new GithubClient();

    private final String apiUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock;
    private final Supplier<String> token;
    private final Supplier<Duration> cacheTime;
    private final Map<String, CachedList> cache = new ConcurrentHashMap<>();
    private final Map<String, CachedChecks> checksCache = new ConcurrentHashMap<>();
    private final Map<String, CachedVisibility> visibility = new ConcurrentHashMap<>();

    private record CachedList(Instant readAt, List<?> items) {
    }

    private record CachedChecks(Instant readAt, List<String> failed) {
    }

    private record CachedVisibility(Instant readAt, boolean isPrivate) {
    }

    /** The token of a request, and whether it is the token of the user rather than of the server. */
    private record Bearer(@Nullable String token, boolean own) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RepositoryVisibility(@JsonProperty("private") Boolean flag) {
        /** Only an answer that says "not private" makes a repository public. */
        boolean isPrivate() {
            return !Boolean.FALSE.equals(flag);
        }
    }

    // The conclusions of a check run that a person has to look at. A cancelled run is left out: a
    // newer run of the same check usually cancelled it.
    private static final Set<String> FAILED_CONCLUSIONS = Set.of("failure", "timed_out", "action_required", "startup_failure");

    /**
     * The client of the server: GitHub, with the token of the configured Polarion secret, if any, and
     * the configured cache time.
     */
    public GithubClient() {
        this(DEFAULT_API_URL, Clock.systemUTC(), new GithubToken(), () -> GithubExtensionConfiguration.getInstance().getCacheTime());
    }

    /** A client without a token. */
    public GithubClient(@NotNull String apiUrl) {
        this(apiUrl, Clock.systemUTC(), () -> null);
    }

    GithubClient(@NotNull String apiUrl, @NotNull Clock clock) {
        this(apiUrl, clock, () -> null);
    }

    /**
     * @param token supplies the token for each request, or null to read GitHub anonymously
     */
    public GithubClient(@NotNull String apiUrl, @NotNull Clock clock, @NotNull Supplier<String> token) {
        this(apiUrl, clock, token, () -> DEFAULT_CACHE_TIME);
    }

    /**
     * @param token     supplies the token for each request, or null to read GitHub anonymously
     * @param cacheTime supplies how long a list read from GitHub serves again
     */
    public GithubClient(@NotNull String apiUrl, @NotNull Clock clock, @NotNull Supplier<String> token, @NotNull Supplier<Duration> cacheTime) {
        this.clock = clock;
        this.token = token;
        this.cacheTime = cacheTime;
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .proxy(ProxySelector.getDefault())
                // Redirects are followed by hand: only those on the API host, so the token stays there.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * The client of the extension. Its cache serves every page and every user.
     */
    public static @NotNull GithubClient shared() {
        return SHARED;
    }

    /** How long a list read from GitHub serves again. */
    public @NotNull Duration cacheTime() {
        return cacheTime.get();
    }

    /**
     * Drops the lists of a repository from the cache, so the next read asks GitHub again. A list read
     * within {@link #REFRESH_PAUSE} stays.
     */
    public void forget(@NotNull String owner, @NotNull String repository) {
        String prefix = repositoryUrl(owner, repository) + "/";
        Instant latest = clock.instant().minus(REFRESH_PAUSE);
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix) && !entry.getValue().readAt().isAfter(latest));
        checksCache.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix) && !entry.getValue().readAt().isAfter(latest));
    }

    /**
     * When the oldest list of a repository in the cache was read from GitHub, or null when none is.
     */
    public @Nullable Instant readAt(@NotNull String owner, @NotNull String repository) {
        String prefix = repositoryUrl(owner, repository) + "/";
        return cache.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(entry -> entry.getValue().readAt())
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * The open issues of a repository, without its pull requests.
     */
    public @NotNull List<GithubItem> getOpenIssues(@NotNull String owner, @NotNull String repository) {
        return readAll(owner, repository, "/issues?state=open&per_page=" + PAGE_SIZE, ITEMS, false).stream()
                .filter(item -> !item.isPullRequest())
                .toList();
    }

    /**
     * The open discussions of a repository.
     */
    public @NotNull List<GithubItem> getOpenDiscussions(@NotNull String owner, @NotNull String repository) {
        List<GithubItem> discussions;
        try {
            discussions = readAll(owner, repository, "/discussions?per_page=" + PAGE_SIZE, ITEMS, false);
        } catch (GithubStatusException e) {
            // GitHub answers "410 Gone" for a repository that has its discussions turned off.
            if (e.getStatus() == 410) {
                throw new GithubClientException("Discussions are turned off in the repository %s/%s".formatted(owner, repository), e);
            }
            throw e;
        }
        return discussions.stream().filter(GithubItem::isOpen).toList();
    }

    /**
     * The open pull requests of a repository, with the commit each points to.
     */
    public @NotNull List<GithubItem> getOpenPullRequests(@NotNull String owner, @NotNull String repository) {
        return readAll(owner, repository, "/pulls?state=open&per_page=" + PAGE_SIZE, ITEMS, false);
    }

    /**
     * The names of the check runs of a commit that failed, each once. A commit whose checks all
     * passed, or still run, has none.
     */
    public @NotNull List<String> getFailedChecks(@NotNull String owner, @NotNull String repository, @NotNull String sha) {
        if (!SHA.matcher(sha).matches()) {
            throw new IllegalArgumentException("Not a commit SHA: " + sha);
        }
        String url = repositoryUrl(owner, repository) + "/commits/" + sha + "/check-runs?per_page=" + PAGE_SIZE;
        Instant now = clock.instant();
        Duration keep = cacheTime();
        checksCache.values().removeIf(cached -> isExpired(cached.readAt(), now, keep));
        CachedChecks cached = checksCache.get(url);
        if (cached != null) {
            return cached.failed();
        }
        Bearer bearer = bearer();
        HttpResponse<String> response = send(url, bearer);
        CheckRuns runs;
        try {
            runs = objectMapper.readValue(response.body(), CheckRuns.class);
        } catch (IOException e) {
            throw new GithubClientException("GitHub answered " + url + " with an unreadable body", e);
        }
        List<String> failed = runs.checkRuns() == null ? List.of() : runs.checkRuns().stream()
                // A run that still runs has no conclusion yet.
                .filter(run -> run != null && run.name() != null && run.conclusion() != null && FAILED_CONCLUSIONS.contains(run.conclusion()))
                .map(CheckRun::name)
                .distinct()
                .toList();
        if (!keep.isZero() && isPublic(owner, repository, bearer, now, keep)) {
            checksCache.put(url, new CachedChecks(now, failed));
        }
        return failed;
    }

    // A list read exactly the cache time ago still serves, as one read right now does without a cache.
    private static boolean isExpired(Instant readAt, Instant now, Duration keep) {
        return keep.isZero() || readAt.plus(keep).isBefore(now);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CheckRuns(@JsonProperty("check_runs") List<CheckRun> checkRuns) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CheckRun(String name, String conclusion) {
    }

    /**
     * Who the token of the user belongs to, and what is left of its hourly limit. Never cached: it is
     * what tells a user that GitHub takes their token.
     *
     * @throws IllegalArgumentException when the request carries no token of the user
     */
    public @NotNull GithubTokenInfo checkUserToken() {
        Bearer bearer = bearer();
        if (!bearer.own()) {
            throw new IllegalArgumentException("Send the GitHub token to check in the header " + RequestToken.HEADER);
        }
        String url = apiUrl + "/user";
        HttpResponse<String> response = send(url, bearer);
        GithubUser user;
        try {
            user = objectMapper.readValue(response.body(), GithubUser.class);
        } catch (IOException e) {
            throw new GithubClientException("GitHub answered " + url + " with an unreadable body", e);
        }
        return new GithubTokenInfo(user.login(), header(response, "x-ratelimit-remaining"), header(response, "x-ratelimit-limit"));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubUser(String login) {
    }

    private static @Nullable Integer header(HttpResponse<String> response, String name) {
        try {
            return response.headers().firstValue(name).map(Integer::valueOf).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The security advisories of a repository in triage, drafted or published. Without a token, or with
     * one that may not see them, GitHub lists the published ones only. What a user sees with their own
     * token depends on their permission, so it never goes to the cache.
     */
    public @NotNull List<GithubAdvisory> getSecurityAdvisories(@NotNull String owner, @NotNull String repository) {
        return readAll(owner, repository, "/security-advisories?per_page=" + PAGE_SIZE, ADVISORIES, true).stream()
                .filter(GithubAdvisory::isShown)
                .toList();
    }

    private String repositoryUrl(String owner, String repository) {
        return apiUrl + "/repos/" + pathSegment(owner) + "/" + pathSegment(repository);
    }

    // The pattern leaves no character that needs encoding, and no "." or ".." segment.
    private static String pathSegment(String name) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Not a GitHub owner or repository name: " + name);
        }
        return name;
    }

    /**
     * @param byPermission true for a list whose content depends on the permission of the token: read
     *                     with the token of the user, it neither comes from the cache nor goes to it
     */
    // A cache entry belongs to its URL, and a URL always reads the same type.
    @SuppressWarnings("unchecked")
    private <T> List<T> readAll(String owner, String repository, String path, TypeReference<List<T>> type, boolean byPermission) {
        String firstPageUrl = repositoryUrl(owner, repository) + path;
        Instant now = clock.instant();
        Duration keep = cacheTime();
        cache.values().removeIf(cached -> isExpired(cached.readAt(), now, keep));
        Bearer bearer = bearer();
        boolean shared = !(byPermission && bearer.own());
        CachedList cached = shared ? cache.get(firstPageUrl) : null;
        if (cached != null) {
            return (List<T>) cached.items();
        }
        List<T> items = readAllPages(firstPageUrl, type, bearer);
        if (shared && !keep.isZero() && isPublic(owner, repository, bearer, now, keep)) {
            cache.put(firstPageUrl, new CachedList(now, List.copyOf(items)));
        }
        return items;
    }

    /**
     * Whether everyone may see the repository. Without a token GitHub shows public repositories only;
     * with one the client asks GitHub, once per cache time. A repository whose visibility GitHub does
     * not tell counts as private: its list serves the request, and nothing goes to the cache.
     */
    private boolean isPublic(String owner, String repository, Bearer bearer, Instant now, Duration keep) {
        if (bearer.token() == null) {
            return true;
        }
        String url = repositoryUrl(owner, repository);
        visibility.values().removeIf(known -> isExpired(known.readAt(), now, keep));
        CachedVisibility known = visibility.get(url);
        if (known == null) {
            try {
                known = new CachedVisibility(now, objectMapper.readValue(send(url, bearer).body(), RepositoryVisibility.class).isPrivate());
            } catch (IOException | GithubClientException e) {
                return false;
            }
            visibility.put(url, known);
        }
        return !known.isPrivate();
    }

    private Bearer bearer() {
        String own = RequestToken.current();
        return own != null ? new Bearer(own, true) : new Bearer(token.get(), false);
    }

    private <T> List<T> readAllPages(String firstPageUrl, TypeReference<List<T>> type, Bearer bearer) {
        List<T> items = new ArrayList<>();
        String url = firstPageUrl;
        for (int page = 0; url != null && page < MAX_PAGES; page++) {
            HttpResponse<String> response = send(url, bearer);
            try {
                items.addAll(objectMapper.readValue(response.body(), type));
            } catch (IOException e) {
                throw new GithubClientException("GitHub answered " + url + " with an unreadable body", e);
            }
            url = nextPageUrl(response);
        }
        return items;
    }

    private HttpResponse<String> send(String url, Bearer bearer) {
        HttpResponse<String> response = request(url, bearer.token());
        // GitHub answers a renamed or moved repository with a redirect to its new address.
        for (int redirects = 0; isRedirect(response) && redirects < MAX_REDIRECTS; redirects++) {
            String location = response.headers().firstValue("location").orElse("");
            if (!location.startsWith(apiUrl + "/")) {
                throw new GithubClientException("GitHub redirected " + url + " away from " + apiUrl);
            }
            response = request(location, bearer.token());
        }
        if (isRateLimited(response)) {
            throw new GithubRateLimitException(resetTime(response), bearer.token() != null);
        }
        if (response.statusCode() == 401 && bearer.own()) {
            // A refused token is of no use for a later PDF export either.
            UserTokens.shared().forgetToken(bearer.token());
            throw new GithubClientException("GitHub refused your GitHub token (401 Unauthorized). It may be wrong, expired"
                    + " or revoked: change or remove it with the button GitHub token.");
        }
        if (response.statusCode() == 401 && bearer.token() != null) {
            throw new GithubClientException("GitHub refused the token of the Polarion secret (401 Unauthorized)."
                    + " It may be wrong, expired or revoked.");
        }
        if (response.statusCode() != 200) {
            throw new GithubStatusException(url, response.statusCode());
        }
        return response;
    }

    private HttpResponse<String> request(String url, @Nullable String bearer) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", ACCEPT)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        try {
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new GithubClientException("GitHub did not answer " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GithubClientException("Interrupted while waiting for " + url, e);
        }
    }

    private static boolean isRedirect(HttpResponse<String> response) {
        int status = response.statusCode();
        return status == 301 || status == 302 || status == 307 || status == 308;
    }

    private static boolean isRateLimited(HttpResponse<String> response) {
        int status = response.statusCode();
        return status == 429
                || (status == 403 && response.headers().firstValue("x-ratelimit-remaining").filter("0"::equals).isPresent());
    }

    private static @Nullable Instant resetTime(HttpResponse<String> response) {
        try {
            return response.headers().firstValue("x-ratelimit-reset")
                    .map(seconds -> Instant.ofEpochSecond(Long.parseLong(seconds)))
                    .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private @Nullable String nextPageUrl(HttpResponse<String> response) {
        for (String link : response.headers().allValues("link")) {
            Matcher matcher = NEXT_LINK.matcher(link);
            // The next page must stay on the configured API host, whatever the answer says.
            if (matcher.find() && matcher.group(1).startsWith(apiUrl + "/")) {
                return matcher.group(1);
            }
        }
        return null;
    }
}
