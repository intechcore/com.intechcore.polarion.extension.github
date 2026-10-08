package com.intechcore.polarion.extension.github.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Reads the open issues, discussions and pull requests of a public repository, and the checks of a
 * commit, from the GitHub REST API. With a token GitHub allows 5000 requests per hour, without one 60
 * for the whole server. The token goes only to the configured API host.
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
     * How long a list read from GitHub serves again. Without a token GitHub allows 60 requests per
     * hour for the whole server, and a conditional request costs one as well, so only a cache saves
     * them. With a token the limit is 5000 per hour; the cache keeps the pages fast all the same.
     */
    public static final Duration CACHE_TIME = Duration.ofMinutes(5);

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
    private final Map<String, CachedList> cache = new ConcurrentHashMap<>();
    private final Map<String, CachedChecks> checksCache = new ConcurrentHashMap<>();

    private record CachedList(Instant readAt, List<?> items) {
    }

    private record CachedChecks(Instant readAt, List<String> failed) {
    }

    // The conclusions of a check run that a person has to look at. A cancelled run is left out: a
    // newer run of the same check usually cancelled it.
    private static final Set<String> FAILED_CONCLUSIONS = Set.of("failure", "timed_out", "action_required", "startup_failure");

    /** The client of the server: GitHub, with the token of the configured Polarion secret, if any. */
    public GithubClient() {
        this(DEFAULT_API_URL, Clock.systemUTC(), new GithubToken());
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
        this.clock = clock;
        this.token = token;
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
        return readAll(repositoryUrl(owner, repository) + "/issues?state=open&per_page=" + PAGE_SIZE).stream()
                .filter(item -> !item.isPullRequest())
                .toList();
    }

    /**
     * The open discussions of a repository.
     */
    public @NotNull List<GithubItem> getOpenDiscussions(@NotNull String owner, @NotNull String repository) {
        List<GithubItem> discussions;
        try {
            discussions = readAll(repositoryUrl(owner, repository) + "/discussions?per_page=" + PAGE_SIZE);
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
        return readAll(repositoryUrl(owner, repository) + "/pulls?state=open&per_page=" + PAGE_SIZE);
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
        checksCache.values().removeIf(cached -> cached.readAt().plus(CACHE_TIME).isBefore(now));
        CachedChecks cached = checksCache.get(url);
        if (cached != null) {
            return cached.failed();
        }
        HttpResponse<String> response = send(url);
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
        checksCache.put(url, new CachedChecks(now, failed));
        return failed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CheckRuns(@JsonProperty("check_runs") List<CheckRun> checkRuns) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CheckRun(String name, String conclusion) {
    }

    /**
     * The security advisories of a repository in triage, drafted or published. Without a token, or with
     * one that may not see them, GitHub lists the published ones only.
     */
    public @NotNull List<GithubAdvisory> getSecurityAdvisories(@NotNull String owner, @NotNull String repository) {
        return readAll(repositoryUrl(owner, repository) + "/security-advisories?per_page=" + PAGE_SIZE, ADVISORIES).stream()
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

    private List<GithubItem> readAll(String firstPageUrl) {
        return readAll(firstPageUrl, ITEMS);
    }

    // A cache entry belongs to its URL, and a URL always reads the same type.
    @SuppressWarnings("unchecked")
    private <T> List<T> readAll(String firstPageUrl, TypeReference<List<T>> type) {
        Instant now = clock.instant();
        cache.values().removeIf(cached -> cached.readAt().plus(CACHE_TIME).isBefore(now));
        CachedList cached = cache.get(firstPageUrl);
        if (cached != null) {
            return (List<T>) cached.items();
        }
        List<T> items = readAllPages(firstPageUrl, type);
        cache.put(firstPageUrl, new CachedList(now, List.copyOf(items)));
        return items;
    }

    private <T> List<T> readAllPages(String firstPageUrl, TypeReference<List<T>> type) {
        List<T> items = new ArrayList<>();
        String url = firstPageUrl;
        for (int page = 0; url != null && page < MAX_PAGES; page++) {
            HttpResponse<String> response = send(url);
            try {
                items.addAll(objectMapper.readValue(response.body(), type));
            } catch (IOException e) {
                throw new GithubClientException("GitHub answered " + url + " with an unreadable body", e);
            }
            url = nextPageUrl(response);
        }
        return items;
    }

    private HttpResponse<String> send(String url) {
        String bearer = token.get();
        HttpResponse<String> response = request(url, bearer);
        // GitHub answers a renamed or moved repository with a redirect to its new address.
        for (int redirects = 0; isRedirect(response) && redirects < MAX_REDIRECTS; redirects++) {
            String location = response.headers().firstValue("location").orElse("");
            if (!location.startsWith(apiUrl + "/")) {
                throw new GithubClientException("GitHub redirected " + url + " away from " + apiUrl);
            }
            response = request(location, bearer);
        }
        if (isRateLimited(response)) {
            throw new GithubRateLimitException(resetTime(response), bearer != null);
        }
        if (response.statusCode() == 401 && bearer != null) {
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
