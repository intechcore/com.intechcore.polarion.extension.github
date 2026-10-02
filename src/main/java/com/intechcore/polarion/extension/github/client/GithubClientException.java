package com.intechcore.polarion.extension.github.client;

import java.io.Serial;

/**
 * GitHub did not answer a request, or answered it with an error.
 */
public class GithubClientException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -2817463954027716452L;

    public GithubClientException(String message) {
        super(message);
    }

    public GithubClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
