package com.intechcore.polarion.extension.github.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A local HTTP server that answers like the GitHub REST API for the repository {@code acme/tool}.
 * The bodies come from {@code src/test/resources/github}, written after real GitHub answers.
 */
final class FakeGithub implements AutoCloseable {

    record Answer(int status, Map<String, String> headers, String body) {
    }

    private final HttpServer server;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();

    FakeGithub() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
        // The issues come in two pages, linked the way GitHub links them.
        answer("/repos/acme/tool/issues?state=open&per_page=100",
                new Answer(200, Map.of("Link", "<" + url() + "/repos/acme/tool/issues?state=open&per_page=100&page=2>; rel=\"next\""), resource("issues-page-1.json")));
        answer("/repos/acme/tool/issues?state=open&per_page=100&page=2", new Answer(200, Map.of(), resource("issues-page-2.json")));
        answer("/repos/acme/tool/discussions?per_page=100", new Answer(200, Map.of(), resource("discussions.json")));
        answer("/repos/acme/tool/pulls?state=open&per_page=100", new Answer(200, Map.of(), resource("pulls.json")));
        answer("/repos/acme/tool/commits/5c935690be108c980c6996dd9b60d9800d30eb17/check-runs?per_page=100",
                new Answer(200, Map.of(), resource("check-runs-failed.json")));
        answer("/repos/acme/tool/commits/2222222222222222222222222222222222222222/check-runs?per_page=100",
                new Answer(200, Map.of(), resource("check-runs-passed.json")));
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** Replaces the answer to a path with its query. */
    void answer(String pathAndQuery, Answer answer) {
        answers.put(pathAndQuery, answer);
    }

    /** What GitHub answers for the discussions of a repository that has them turned off. */
    void discussionsTurnedOff() {
        answer("/repos/acme/tool/discussions?per_page=100", new Answer(410, Map.of(), "{\"message\":\"Discussions are disabled for this repo\"}"));
    }

    /** What GitHub answers once an anonymous client has used its 60 requests of the hour. */
    void rateLimitUsedUp() {
        answer("/repos/acme/tool/issues?state=open&per_page=100",
                new Answer(403, Map.of("x-ratelimit-remaining", "0", "x-ratelimit-reset", "1790948455"), "{\"message\":\"API rate limit exceeded\"}"));
    }

    List<String> requests() {
        return requests;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String pathAndQuery = exchange.getRequestURI().toString();
        requests.add(pathAndQuery);
        Answer answer = answers.getOrDefault(pathAndQuery, new Answer(404, Map.of(), "{\"message\":\"Not Found\"}"));
        answer.headers().forEach(exchange.getResponseHeaders()::add);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answer.status(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String resource(String name) {
        try (InputStream stream = FakeGithub.class.getResourceAsStream("/github/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource github/" + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
