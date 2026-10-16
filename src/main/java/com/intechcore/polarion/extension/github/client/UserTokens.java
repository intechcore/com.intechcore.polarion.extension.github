package com.intechcore.polarion.extension.github.client;

import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.security.ISecurityService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The GitHub token each Polarion user last sent from the browser, kept in memory for a render without
 * a browser: the PDF export or the print of the widget. The server forgets a token when the user
 * removes it, when GitHub refuses it, and when Polarion stops.
 */
public final class UserTokens {

    // Shared by all instances: they differ only in how they find the current user.
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();
    private static final UserTokens SHARED = new UserTokens(UserTokens::polarionUser);

    private final Supplier<String> currentUser;

    /**
     * @param currentUser supplies the id of the current Polarion user, or null when there is none
     */
    public UserTokens(@NotNull Supplier<String> currentUser) {
        this.currentUser = currentUser;
    }

    /** The tokens of the users of the current Polarion. */
    public static @NotNull UserTokens shared() {
        return SHARED;
    }

    /** Keeps the token for the current user, in place of the one kept before. */
    public void remember(@NotNull String token) {
        String user = currentUser.get();
        if (user != null) {
            TOKENS.put(user, token);
        }
    }

    /** The token the current user sent last, or null. */
    public @Nullable String current() {
        String user = currentUser.get();
        return user == null ? null : TOKENS.get(user);
    }

    /** Forgets the token of the current user. */
    public void forget() {
        String user = currentUser.get();
        if (user != null) {
            TOKENS.remove(user);
        }
    }

    /** Forgets a token GitHub refused, for every user who sent it. */
    public void forgetToken(@NotNull String token) {
        TOKENS.values().removeIf(token::equals);
    }

    private static @Nullable String polarionUser() {
        // Outside a running Polarion, as in a unit test, there is no user to keep a token for.
        return PlatformContext.isInitialized()
                ? PlatformContext.getPlatform().lookupService(ISecurityService.class).getCurrentUser()
                : null;
    }
}
