package com.intechcore.polarion.extension.github.client;

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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the open issues and discussions of a public repository from the GitHub REST API, without
 * authentication.
 */
public class GithubClient {

    public static final String DEFAULT_API_URL = "https://api.github.com";

    private static final int PAGE_SIZE = 100;
    // A repository with more open items than this is not what the import is for. The limit also
    // ends a loop over "next" links that never stop.
    private static final int MAX_PAGES = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"next\"");
    private static final Pattern NAME = Pattern.compile("(?!\\.+$)[A-Za-z0-9._-]+");
    private static final TypeReference<List<GithubItem>> ITEMS = new TypeReference<>() {
    };

    private final String apiUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GithubClient() {
        this(DEFAULT_API_URL);
    }

    public GithubClient(@NotNull String apiUrl) {
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .proxy(ProxySelector.getDefault())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
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
        return readAll(repositoryUrl(owner, repository) + "/discussions?per_page=" + PAGE_SIZE).stream()
                .filter(GithubItem::isOpen)
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
        List<GithubItem> items = new ArrayList<>();
        String url = firstPageUrl;
        for (int page = 0; url != null && page < MAX_PAGES; page++) {
            HttpResponse<String> response = send(url);
            try {
                items.addAll(objectMapper.readValue(response.body(), ITEMS));
            } catch (IOException e) {
                throw new GithubClientException("GitHub answered " + url + " with an unreadable body", e);
            }
            url = nextPageUrl(response);
        }
        return items;
    }

    private HttpResponse<String> send(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new GithubClientException("GitHub did not answer " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GithubClientException("Interrupted while waiting for " + url, e);
        }
        if (isRateLimited(response)) {
            throw new GithubRateLimitException(resetTime(response));
        }
        if (response.statusCode() != 200) {
            throw new GithubClientException("GitHub answered " + url + " with status " + response.statusCode());
        }
        return response;
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
