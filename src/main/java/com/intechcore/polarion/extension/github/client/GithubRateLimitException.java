package com.intechcore.polarion.extension.github.client;

import java.io.Serial;
import java.time.Instant;

/**
 * GitHub refused a request because the rate limit is used up. An anonymous client gets 60 requests
 * per hour.
 */
public class GithubRateLimitException extends GithubClientException {

    @Serial
    private static final long serialVersionUID = 5273914406628130957L;

    private final Instant resetAt;

    public GithubRateLimitException(Instant resetAt) {
        super("GitHub rate limit exceeded" + (resetAt == null ? "" : ", it resets at " + resetAt));
        this.resetAt = resetAt;
    }

    /**
     * The time GitHub accepts requests again, or null when the answer did not name it.
     */
    public Instant getResetAt() {
        return resetAt;
    }
}
