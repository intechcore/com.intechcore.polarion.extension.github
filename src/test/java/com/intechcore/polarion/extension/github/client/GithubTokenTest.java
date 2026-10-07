package com.intechcore.polarion.extension.github.client;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubTokenTest {

    private static final Map<String, String> SECRETS = Map.of("github-token", " ghp_secret \n", "blank", " ");

    @Test
    void readsTheTokenFromTheNamedSecret() {
        assertThat(new GithubToken(() -> " github-token ", SECRETS::get).get()).isEqualTo("ghp_secret");
    }

    /** No name, no token: the client reads GitHub anonymously, as before. */
    @Test
    void hasNoTokenWithoutAName() {
        assertThat(new GithubToken(() -> null, name -> {
            throw new AssertionError("no secret to read");
        }).get()).isNull();
        assertThat(new GithubToken(() -> "  ", SECRETS::get).get()).isNull();
    }

    @Test
    void namesASecretThatHoldsNoToken() {
        assertThatThrownBy(() -> new GithubToken(() -> "missing", SECRETS::get).get())
                .isInstanceOf(GithubClientException.class).hasMessageContaining("'missing'").hasMessageContaining("does not exist");
        assertThatThrownBy(() -> new GithubToken(() -> "blank", SECRETS::get).get())
                .isInstanceOf(GithubClientException.class).hasMessageContaining("is empty");
    }

    /** A secrets manager may quote the value in its failure, so the message keeps only the kind of failure. */
    @Test
    void neverRepeatsTheTextOfAFailure() {
        GithubToken token = new GithubToken(() -> "github-token", name -> {
            throw new IllegalStateException("cannot decrypt ghp_secret");
        });

        assertThatThrownBy(token::get).isInstanceOf(GithubClientException.class)
                .hasMessageContaining("IllegalStateException").hasMessageNotContaining("ghp_secret");
    }
}
