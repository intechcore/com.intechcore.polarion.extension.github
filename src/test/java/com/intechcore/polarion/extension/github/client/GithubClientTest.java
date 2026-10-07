package com.intechcore.polarion.extension.github.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the client against a local HTTP server that answers like the GitHub REST API.
 */
class GithubClientTest {

    private HttpServer server;
    private String baseUrl;
    private GithubClient client;
    private final List<String> requests = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        client = new GithubClient(baseUrl + "/");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void answer(String path, int status, Map<String, String> headers, String body) {
        server.createContext(path, exchange -> {
            requests.add(exchange.getRequestURI().toString());
            respond(exchange, status, headers, body);
        });
    }

    private static void respond(HttpExchange exchange, int status, Map<String, String> headers, String body) throws IOException {
        headers.forEach(exchange.getResponseHeaders()::add);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void readsOpenIssuesAndLeavesPullRequestsOut() {
        answer("/repos/acme/tool/issues", 200, Map.of(), """
                [
                  {"number": 7, "title": "Crash on start", "body": "It crashes", "state": "open",
                   "html_url": "https://github.com/acme/tool/issues/7", "created_at": "2026-09-01T10:00:00Z",
                   "updated_at": "2026-09-02T10:00:00Z", "user": {"login": "alice"},
                   "labels": [{"name": "bug"}], "unknown_field": 1},
                  {"number": 8, "title": "Fix crash", "state": "open", "pull_request": {"url": "x"}}
                ]""");

        List<GithubItem> issues = client.getOpenIssues("acme", "tool");

        assertThat(issues).hasSize(1);
        GithubItem issue = issues.get(0);
        assertThat(issue.number()).isEqualTo(7);
        assertThat(issue.title()).isEqualTo("Crash on start");
        assertThat(issue.body()).isEqualTo("It crashes");
        assertThat(issue.htmlUrl()).isEqualTo("https://github.com/acme/tool/issues/7");
        assertThat(issue.createdAt()).isEqualTo("2026-09-01T10:00:00Z");
        assertThat(issue.user().login()).isEqualTo("alice");
        assertThat(issue.labels()).extracting(GithubItem.Label::name).containsExactly("bug");
        assertThat(requests).containsExactly("/repos/acme/tool/issues?state=open&per_page=100");
    }

    @Test
    void readsTheTypeTheCategoryAndTheLabels() {
        answer("/repos/acme/tool/issues", 200, Map.of(), """
                [
                  {"number": 1, "state": "open", "type": {"id": 3, "name": "Bug"}, "labels": [{"name": "a", "color": "D73A4A"}, {"id": 5}, {"name": "b", "color": "red"}, {"name": "c", "color": "0e8a16"}]},
                  {"number": 2, "state": "open", "type": null},
                  {"number": 3, "state": "open", "type": "Feature", "labels": []},
                  {"number": 4, "state": "open", "type": {"id": 3}},
                  {"number": 5, "state": "open", "type": 7}
                ]""");
        answer("/repos/acme/tool/discussions", 200, Map.of(), """
                [{"number": 30, "state": "open", "category": {"name": "Q&A", "slug": "q-a"}}]""");

        List<GithubItem> issues = client.getOpenIssues("acme", "tool");

        assertThat(issues).extracting(GithubItem::typeName).containsExactly("Bug", null, "Feature", null, null);
        assertThat(issues.get(0).labelNames()).containsExactly("a", "b", "c");
        // A color that is not six hexadecimal digits never reaches the page, which writes it into a style.
        assertThat(issues.get(0).labelColors()).containsExactly(Map.entry("a", "d73a4a"), Map.entry("c", "0e8a16"));
        assertThat(issues.get(1).labelColors()).isEmpty();
        assertThat(issues.get(1).labelNames()).isEmpty();
        assertThat(issues.get(0).categoryName()).isNull();
        assertThat(client.getOpenDiscussions("acme", "tool").get(0).categoryName()).isEqualTo("Q&A");
    }

    @Test
    void servesAListFromTheCacheForFiveMinutes() {
        answer("/repos/acme/tool/issues", 200, Map.of(), "[{\"number\": 1, \"state\": \"open\", \"assignees\": [{\"login\": \"alice\"}, null, {\"id\": 3}]}]");
        java.util.concurrent.atomic.AtomicReference<Instant> now = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-03T08:00:00Z"));
        java.time.Clock clock = new java.time.Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return java.time.ZoneOffset.UTC;
            }

            @Override
            public java.time.Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        GithubClient cached = new GithubClient(baseUrl, clock);
        assertThat(cached.readAt("acme", "tool")).isNull();

        assertThat(cached.getOpenIssues("acme", "tool").get(0).assigneeLogins()).containsExactly("alice");
        now.set(now.get().plus(GithubClient.CACHE_TIME));
        cached.getOpenIssues("acme", "tool");

        assertThat(requests).hasSize(1);
        assertThat(cached.readAt("acme", "tool")).isEqualTo(Instant.parse("2026-10-03T08:00:00Z"));

        now.set(now.get().plusSeconds(1));
        cached.getOpenIssues("acme", "tool");

        assertThat(requests).hasSize(2);
        assertThat(cached.readAt("acme", "tool")).isEqualTo(Instant.parse("2026-10-03T08:05:01Z"));
        assertThat(cached.readAt("acme", "other")).isNull();
        assertThat(GithubClient.shared()).isSameAs(GithubClient.shared());
    }

    @Test
    void forgetsTheListsOfARepositoryOnlyAfterAMinute() {
        answer("/repos/acme/tool/issues", 200, Map.of(), "[{\"number\": 1, \"state\": \"open\"}]");
        answer("/repos/acme/tool/discussions", 200, Map.of(), "[]");
        answer("/repos/acme/tool/commits/" + SHA + "/check-runs", 200, Map.of(), "{\"check_runs\": []}");
        java.util.concurrent.atomic.AtomicReference<Instant> now = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-03T08:00:00Z"));
        GithubClient cached = new GithubClient(baseUrl, new java.time.Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return java.time.ZoneOffset.UTC;
            }

            @Override
            public java.time.Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        });
        cached.getOpenIssues("acme", "tool");
        cached.getOpenDiscussions("acme", "tool");
        cached.getFailedChecks("acme", "tool", SHA);
        requests.remove(requests.size() - 1);

        now.set(now.get().plusSeconds(59));
        cached.forget("acme", "tool");
        cached.getOpenIssues("acme", "tool");
        assertThat(requests).hasSize(2);

        now.set(now.get().plusSeconds(1));
        cached.forget("acme", "other");
        cached.getOpenIssues("acme", "tool");
        assertThat(requests).hasSize(2);

        cached.forget("acme", "tool");
        cached.getOpenIssues("acme", "tool");
        cached.getOpenDiscussions("acme", "tool");
        assertThat(requests).hasSize(4);
        // The checks of the repository were dropped as well.
        cached.getFailedChecks("acme", "tool", SHA);
        assertThat(requests).hasSize(5);
        requests.remove(4);
        assertThat(cached.readAt("acme", "tool")).isEqualTo(Instant.parse("2026-10-03T08:01:00Z"));
    }

    private static final String SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void readsTheOpenPullRequestsWithTheirCommit() {
        answer("/repos/acme/tool/pulls", 200, Map.of(), """
                [{"number": 421, "state": "open", "title": "Update docx4j", "user": {"login": "renovate[bot]"},
                  "html_url": "https://github.com/acme/tool/pull/421", "head": {"sha": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "ref": "renovate/docx4j"}},
                 {"number": 422, "state": "open", "head": "unexpected"}]""");

        List<GithubItem> pulls = client.getOpenPullRequests("acme", "tool");

        assertThat(pulls).extracting(GithubItem::headSha).containsExactly(SHA, null);
        assertThat(requests).containsExactly("/repos/acme/tool/pulls?state=open&per_page=100");
    }

    @Test
    void namesEachFailedCheckOnceAndServesThemFromTheCache() {
        answer("/repos/acme/tool/commits/" + SHA + "/check-runs", 200, Map.of(), """
                {"total_count": 6, "check_runs": [
                  {"name": "build", "status": "completed", "conclusion": "failure"},
                  {"name": "build", "status": "completed", "conclusion": "failure"},
                  {"name": "e2e", "status": "completed", "conclusion": "timed_out"},
                  {"name": "lint", "status": "completed", "conclusion": "success"},
                  {"name": "older", "status": "completed", "conclusion": "cancelled"},
                  {"name": "sonar", "status": "in_progress", "conclusion": null},
                  {"status": "completed", "conclusion": "failure"}, null]}""");

        assertThat(client.getFailedChecks("acme", "tool", SHA)).containsExactly("build", "e2e");
        assertThat(client.getFailedChecks("acme", "tool", SHA)).containsExactly("build", "e2e");
        assertThat(requests).containsExactly("/repos/acme/tool/commits/" + SHA + "/check-runs?per_page=100");

        // A refresh asks again, once the minute has passed: here the cache entry is older than none.
        answer("/repos/acme/fresh/commits/" + SHA + "/check-runs", 200, Map.of(), "{\"check_runs\": null}");
        assertThat(client.getFailedChecks("acme", "fresh", SHA)).isEmpty();
    }

    @Test
    void refusesAnythingButACommitShaAndAnUnreadableAnswer() {
        assertThatThrownBy(() -> client.getFailedChecks("acme", "tool", "../issues"))
                .isInstanceOf(IllegalArgumentException.class);
        answer("/repos/acme/tool/commits/" + SHA + "/check-runs", 200, Map.of(), "[]");
        assertThatThrownBy(() -> client.getFailedChecks("acme", "tool", SHA))
                .isInstanceOf(GithubClientException.class)
                .hasMessageContaining("unreadable");
    }

    private final List<String> authorizations = new ArrayList<>();

    private void answerWithHeaders(String path, int status, Map<String, String> headers, String body) {
        server.createContext(path, exchange -> {
            requests.add(exchange.getRequestURI().toString());
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            respond(exchange, status, headers, body);
        });
    }

    private GithubClient withToken(String token) {
        return new GithubClient(baseUrl, java.time.Clock.systemUTC(), () -> token);
    }

    @Test
    void sendsTheTokenAndNoneWithoutOne() {
        answerWithHeaders("/repos/acme/tool/issues", 200, Map.of(), "[]");
        answerWithHeaders("/repos/acme/other/issues", 200, Map.of(), "[]");

        withToken("ghp_secret").getOpenIssues("acme", "tool");
        client.getOpenIssues("acme", "other");

        assertThat(authorizations).containsExactly("Bearer ghp_secret", "null");
    }

    /** A renamed repository answers with a redirect on the API host, which keeps the token. */
    @Test
    void followsARedirectOnTheApiHostWithTheToken() {
        answerWithHeaders("/repos/acme/old/issues", 301, Map.of("Location", baseUrl + "/repositories/42/issues?state=open&per_page=100"), "");
        answerWithHeaders("/repositories/42/issues", 200, Map.of(), "[{\"number\": 1, \"state\": \"open\"}]");

        assertThat(withToken("ghp_secret").getOpenIssues("acme", "old")).hasSize(1);
        assertThat(authorizations).containsExactly("Bearer ghp_secret", "Bearer ghp_secret");
    }

    @Test
    void refusesARedirectAwayFromTheApiHost() {
        answerWithHeaders("/repos/acme/tool/issues", 302, Map.of("Location", "https://example.invalid/steal"), "");

        assertThatThrownBy(() -> withToken("ghp_secret").getOpenIssues("acme", "tool"))
                .isInstanceOf(GithubClientException.class).hasMessageContaining("redirected");
        assertThat(requests).hasSize(1);
    }

    @Test
    void saysThatGithubRefusedTheToken() {
        answerWithHeaders("/repos/acme/tool/issues", 401, Map.of(), "{\"message\": \"Bad credentials\"}");

        assertThatThrownBy(() -> withToken("ghp_wrong").getOpenIssues("acme", "tool"))
                .isInstanceOf(GithubClientException.class).hasMessageContaining("refused the token")
                .hasMessageNotContaining("ghp_wrong");
    }

    /** Without a token the rate limit names the way out; with one it does not. */
    @Test
    void namesTheTokenWhenTheAnonymousLimitIsUsedUp() {
        answerWithHeaders("/repos/acme/tool/issues", 403, Map.of("x-ratelimit-remaining", "0"), "{}");

        assertThatThrownBy(() -> client.getOpenIssues("acme", "tool"))
                .isInstanceOf(GithubRateLimitException.class).hasMessageContaining("token.secret");
        assertThatThrownBy(() -> withToken("ghp_secret").getOpenIssues("acme", "tool"))
                .isInstanceOf(GithubRateLimitException.class).hasMessageNotContaining("token.secret");
    }

    @Test
    void followsTheNextLinkUntilTheLastPage() {
        server.createContext("/repos/acme/tool/issues", exchange -> {
            String uri = exchange.getRequestURI().toString();
            requests.add(uri);
            if (uri.contains("page=2")) {
                respond(exchange, 200, Map.of(), "[{\"number\": 2, \"state\": \"open\"}]");
            } else {
                String next = "<" + baseUrl + "/repos/acme/tool/issues?page=2>; rel=\"next\", <" + baseUrl + "/x>; rel=\"last\"";
                respond(exchange, 200, Map.of("Link", next), "[{\"number\": 1, \"state\": \"open\"}]");
            }
        });

        assertThat(client.getOpenIssues("acme", "tool")).extracting(GithubItem::number).containsExactly(1L, 2L);
        assertThat(requests).hasSize(2);
    }

    @Test
    void doesNotFollowANextLinkToAnotherHost() {
        answer("/repos/acme/tool/issues", 200, Map.of("Link", "<http://example.invalid/issues?page=2>; rel=\"next\""),
                "[{\"number\": 1, \"state\": \"open\"}]");

        assertThat(client.getOpenIssues("acme", "tool")).hasSize(1);
        assertThat(requests).hasSize(1);
    }

    @Test
    void readsOnlyOpenDiscussions() {
        answer("/repos/acme/tool/discussions", 200, Map.of(), """
                [
                  {"number": 30, "title": "How to", "state": "open", "html_url": "https://github.com/acme/tool/discussions/30"},
                  {"number": 29, "title": "Solved", "state": "closed"}
                ]""");

        List<GithubItem> discussions = client.getOpenDiscussions("acme", "tool");

        assertThat(discussions).extracting(GithubItem::number).containsExactly(30L);
        assertThat(requests).containsExactly("/repos/acme/tool/discussions?per_page=100");
    }

    @Test
    void saysThatDiscussionsAreTurnedOff() {
        answer("/repos/acme/tool/discussions", 410, Map.of(), "{}");
        answer("/repos/acme/broken/discussions", 500, Map.of(), "{}");

        assertThatThrownBy(() -> client.getOpenDiscussions("acme", "tool"))
                .isExactlyInstanceOf(GithubClientException.class)
                .hasMessage("Discussions are turned off in the repository acme/tool");
        assertThatThrownBy(() -> client.getOpenDiscussions("acme", "broken"))
                .isExactlyInstanceOf(GithubStatusException.class)
                .hasMessageContaining("status 500");
    }

    @Test
    void reportsAnExhaustedRateLimitWithItsResetTime() {
        answer("/repos/acme/tool/issues", 403, Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", "1790948455"), "{}");

        assertThatThrownBy(() -> client.getOpenIssues("acme", "tool"))
                .isInstanceOfSatisfying(GithubRateLimitException.class,
                        e -> assertThat(e.getResetAt()).isEqualTo(Instant.ofEpochSecond(1790948455L)))
                .hasMessageContaining("rate limit");
    }

    @Test
    void reportsASecondaryRateLimitWithoutAResetTime() {
        answer("/repos/acme/tool/issues", 429, Map.of("x-ratelimit-reset", "soon"), "{}");

        assertThatThrownBy(() -> client.getOpenIssues("acme", "tool"))
                .isInstanceOfSatisfying(GithubRateLimitException.class, e -> assertThat(e.getResetAt()).isNull());
    }

    @Test
    void reportsAnyOtherErrorStatus() {
        answer("/repos/acme/tool/issues", 404, Map.of(), "{}");
        answer("/repos/acme/private/issues", 403, Map.of("x-ratelimit-remaining", "12"), "{}");

        assertThatThrownBy(() -> client.getOpenIssues("acme", "tool"))
                .isInstanceOfSatisfying(GithubStatusException.class, e -> assertThat(e.getStatus()).isEqualTo(404))
                .hasMessageContaining("status 404");
        assertThatThrownBy(() -> client.getOpenIssues("acme", "private"))
                .isExactlyInstanceOf(GithubStatusException.class)
                .hasMessageContaining("status 403");
    }

    @Test
    void reportsAnUnreadableBody() {
        answer("/repos/acme/tool/issues", 200, Map.of(), "{\"message\": \"not a list\"}");

        assertThatThrownBy(() -> client.getOpenIssues("acme", "tool"))
                .isExactlyInstanceOf(GithubClientException.class)
                .hasMessageContaining("unreadable body");
    }

    @Test
    void reportsAServerThatDoesNotAnswer() {
        server.stop(0);

        assertThatThrownBy(() -> client.getOpenIssues("acme", "tool"))
                .isExactlyInstanceOf(GithubClientException.class)
                .hasMessageContaining("did not answer");
    }

    @Test
    void rejectsANameThatWouldChangeTheRequestPath() {
        assertThatThrownBy(() -> client.getOpenIssues("acme/../other", "tool"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getOpenIssues("..", "tool"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getOpenDiscussions("acme", "tool?x=1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(requests).isEmpty();
    }

    @Test
    void usesTheGithubApiByDefault() {
        assertThat(GithubClient.DEFAULT_API_URL).isEqualTo("https://api.github.com");
        assertThat(new GithubClient()).isNotNull();
    }
}
