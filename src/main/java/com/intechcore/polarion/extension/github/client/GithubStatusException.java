package com.intechcore.polarion.extension.github.client;

import org.jetbrains.annotations.Nullable;

import java.io.Serial;

/**
 * GitHub answered a request with an error status. The message carries the reason GitHub gave, as the
 * policy of an organization that refuses a token.
 */
public class GithubStatusException extends GithubClientException {

    @Serial
    private static final long serialVersionUID = 7193350218846605124L;

    private final int status;

    public GithubStatusException(String url, int status) {
        this(url, status, null, false);
    }

    /**
     * @param reason  the {@code message} of the answer of GitHub, or null
     * @param ownToken true when the request carried the token of the user
     */
    public GithubStatusException(String url, int status, @Nullable String reason, boolean ownToken) {
        super("GitHub answered " + url + " with status " + status + (ownToken ? " for your GitHub token" : "")
                + (reason == null || reason.isBlank() ? "" : ": " + reason));
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
