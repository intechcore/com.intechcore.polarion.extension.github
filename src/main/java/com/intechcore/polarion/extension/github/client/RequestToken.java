package com.intechcore.polarion.extension.github.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The GitHub token a user keeps in the browser, for the request that brought it. The page sends it in
 * the header {@link #HEADER}; the server uses it for that request only and stores it nowhere.
 */
public final class RequestToken {

    /** The header that carries the token of the user. */
    public static final String HEADER = "X-GitHub-Token";

    // The characters of every GitHub token kind: ghp_, gho_, github_pat_ and the classic hex tokens.
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_]{1,255}");
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestToken() {
    }

    /**
     * Runs an action with the token of the user, and forgets the token afterwards.
     *
     * @param token the token from the header, or null or blank for none
     * @throws IllegalArgumentException when the header holds no GitHub token
     */
    public static <T> T with(@Nullable String token, @NotNull Supplier<T> action) {
        String value = token == null ? "" : token.trim();
        if (!value.isEmpty() && !TOKEN.matcher(value).matches()) {
            // The message never quotes the header: it may be a token with a typo.
            throw new IllegalArgumentException("The header " + HEADER + " holds no GitHub token");
        }
        String previous = CURRENT.get();
        CURRENT.set(value.isEmpty() ? null : value);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** The token of the user for the running request, or null. */
    public static @Nullable String current() {
        return CURRENT.get();
    }
}
