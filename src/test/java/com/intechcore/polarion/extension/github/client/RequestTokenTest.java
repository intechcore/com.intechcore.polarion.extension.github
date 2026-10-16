package com.intechcore.polarion.extension.github.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The token of the user lives for the request that brought it, and no longer. */
class RequestTokenTest {

    @Test
    void holdsTheTokenForTheActionOnly() {
        assertThat(RequestToken.with(" github_pat_11AB_cd ", RequestToken::current)).isEqualTo("github_pat_11AB_cd");
        assertThat(RequestToken.current()).isNull();
        assertThat(RequestToken.with("", RequestToken::current)).isNull();
        assertThat(RequestToken.with(null, RequestToken::current)).isNull();
    }

    @Test
    void forgetsTheTokenAlsoWhenTheActionFails() {
        assertThatThrownBy(() -> RequestToken.with("ghp_user", () -> {
            throw new GithubClientException("GitHub failed");
        })).hasMessage("GitHub failed");

        assertThat(RequestToken.current()).isNull();
    }

    @Test
    void restoresTheTokenOfAnOuterAction() {
        String inner = RequestToken.with("ghp_outer", () -> {
            String nested = RequestToken.with("ghp_inner", RequestToken::current);
            assertThat(RequestToken.current()).isEqualTo("ghp_outer");
            return nested;
        });

        assertThat(inner).isEqualTo("ghp_inner");
    }

    /** A header with a line break could add a header of its own to the request to GitHub. */
    @Test
    void refusesAHeaderThatHoldsNoTokenWithoutQuotingIt() {
        assertThatThrownBy(() -> RequestToken.with("ghp_a\r\nX-Evil: 1", RequestToken::current))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(RequestToken.HEADER)
                .hasMessageNotContaining("ghp_a");
        assertThat(RequestToken.current()).isNull();
    }
}
