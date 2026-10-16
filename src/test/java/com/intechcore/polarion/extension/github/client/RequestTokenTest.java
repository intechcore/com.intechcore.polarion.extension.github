package com.intechcore.polarion.extension.github.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The token of the user lives in the thread for the request that brought it, and in memory for the user. */
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

    /** The server keeps the last token of each user for the PDF export; a token to check it keeps not yet. */
    @Test
    void keepsTheTokenForTheUser() {
        UserTokens alice = new UserTokens(() -> "alice");
        try {
            RequestToken.with(" ghp_first ", alice, RequestToken::current);
            RequestToken.with(null, alice, RequestToken::current);
            assertThat(alice.current()).isEqualTo("ghp_first");

            RequestToken.with("ghp_second", alice, RequestToken::current);
            RequestToken.withoutKeeping("ghp_checked", RequestToken::current);
            assertThat(alice.current()).isEqualTo("ghp_second");
            assertThatThrownBy(() -> RequestToken.with("ghp a", alice, RequestToken::current)).isInstanceOf(IllegalArgumentException.class);
            assertThat(alice.current()).isEqualTo("ghp_second");
        } finally {
            alice.forget();
        }
        assertThat(alice.current()).isNull();
    }

    /** Outside a running Polarion there is no user, so nothing is kept. */
    @Test
    void keepsNothingWithoutAUser() {
        UserTokens nobody = new UserTokens(() -> null);
        nobody.remember("ghp_user");
        nobody.forget();

        assertThat(nobody.current()).isNull();
        assertThat(UserTokens.shared().current()).isNull();
        assertThat(RequestToken.with("ghp_user", RequestToken::current)).isEqualTo("ghp_user");
    }
}
