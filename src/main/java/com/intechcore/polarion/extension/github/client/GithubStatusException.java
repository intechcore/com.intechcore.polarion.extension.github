package com.intechcore.polarion.extension.github.client;

import java.io.Serial;

/**
 * GitHub answered a request with an error status.
 */
public class GithubStatusException extends GithubClientException {

    @Serial
    private static final long serialVersionUID = 7193350218846605124L;

    private final int status;

    public GithubStatusException(String url, int status) {
        super("GitHub answered " + url + " with status " + status);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
