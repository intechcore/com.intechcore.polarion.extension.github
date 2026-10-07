package com.intechcore.polarion.extension.github.client;

import java.io.Serial;
import java.time.Instant;

/**
 * GitHub refused a request because the rate limit is used up. An anonymous client gets 60 requests
 * per hour for the server, a client with a token 5000.
 */
public class GithubRateLimitException extends GithubClientException {

    @Serial
    private static final long serialVersionUID = 5273914406628130957L;

    private final Instant resetAt;

    public GithubRateLimitException(Instant resetAt) {
        this(resetAt, true);
    }

    /**
     * @param withToken false when the request carried no token: the message then names the way out
     */
    public GithubRateLimitException(Instant resetAt, boolean withToken) {
        super("GitHub rate limit exceeded" + (resetAt == null ? "" : ", it resets at " + resetAt)
                + (withToken ? "" : ". Without a token GitHub allows 60 requests per hour for the server;"
                + " a token allows 5000, see the property token.secret on the About page"));
        this.resetAt = resetAt;
    }

    /**
     * The time GitHub accepts requests again, or null when the answer did not name it.
     */
    public Instant getResetAt() {
        return resetAt;
    }
}
