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
