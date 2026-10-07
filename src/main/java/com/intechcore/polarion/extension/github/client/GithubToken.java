package com.intechcore.polarion.extension.github.client;

import com.intechcore.polarion.extension.github.properties.GithubExtensionConfiguration;
import com.polarion.core.util.vault.PolarionSecretsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The GitHub token of the server, or none. {@code polarion.properties} names a Polarion secret, and
 * the token is read from that secret, so neither the properties file nor the About page carries it.
 * Without a name the client reads GitHub anonymously.
 */
public class GithubToken implements Supplier<String> {

    private final Supplier<String> secretName;
    private final UnaryOperator<String> secrets;

    public GithubToken() {
        this(() -> GithubExtensionConfiguration.getInstance().getTokenSecret(),
                name -> PolarionSecretsManager.getInstance().readSecret(name));
    }

    /**
     * @param secretName reads the configured name of the secret
     * @param secrets    reads the value of a secret by its name
     */
    public GithubToken(@NotNull Supplier<String> secretName, @NotNull UnaryOperator<String> secrets) {
        this.secretName = secretName;
        this.secrets = secrets;
    }

    /**
     * @return the token, or null when no secret is named
     * @throws GithubClientException when a secret is named but holds no token
     */
    @Override
    public @Nullable String get() {
        String configured = secretName.get();
        String name = configured == null ? "" : configured.trim();
        if (name.isEmpty()) {
            return null;
        }
        String token;
        try {
            token = secrets.apply(name);
        } catch (RuntimeException e) {
            // The message names the secret and the kind of failure, never the text of the failure: a
            // secrets manager may quote the value in it.
            throw new GithubClientException("The GitHub token could not be read from the Polarion secret '%s' (%s)"
                    .formatted(name, e.getClass().getName()));
        }
        if (token == null || token.isBlank()) {
            throw new GithubClientException("The Polarion secret '%s', named as the GitHub token, is empty or does not exist".formatted(name));
        }
        return token.trim();
    }
}
