package com.intechcore.polarion.extension.github.rest.controller;

import com.intechcore.polarion.extension.github.client.GithubClient;
import com.intechcore.polarion.extension.github.client.GithubClientException;
import com.intechcore.polarion.extension.github.client.GithubTokenInfo;
import com.intechcore.polarion.extension.github.client.RequestToken;
import com.intechcore.polarion.extension.github.client.UserTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GithubTokenControllerTest {

    private final GithubClient client = mock(GithubClient.class);
    private final UserTokens alice = new UserTokens(() -> "alice");
    private final GithubTokenInternalController controller = new GithubTokenInternalController(client, alice);

    @AfterEach
    void tearDown() {
        alice.forget();
    }

    /** The client checks the token of the header; the server keeps it for the user, not for the thread. */
    @Test
    void checksTheTokenOfTheHeaderAndKeepsIt() {
        when(client.checkUserToken()).thenAnswer(invocation -> {
            // During the check the token is not kept yet.
            assertThat(alice.current()).isNull();
            return new GithubTokenInfo(RequestToken.current(), 1, 2);
        });

        assertThat(controller.checkToken(" ghp_user ")).isEqualTo(new GithubTokenInfo("ghp_user", 1, 2));
        assertThat(RequestToken.current()).isNull();
        assertThat(alice.current()).isEqualTo("ghp_user");
    }

    /** A token GitHub refused replaces none the server keeps. */
    @Test
    void keepsNoTokenGithubRefused() {
        alice.remember("ghp_good");
        when(client.checkUserToken()).thenThrow(new GithubClientException("GitHub refused your GitHub token"));

        assertThatThrownBy(() -> controller.checkToken("ghp_wrong")).isInstanceOf(GithubClientException.class);
        assertThat(alice.current()).isEqualTo("ghp_good");
    }

    @Test
    void forgetsTheTokenOfTheUser() {
        alice.remember("ghp_user");

        controller.forgetToken();

        assertThat(alice.current()).isNull();
    }
}
