package com.intechcore.polarion.extension.github.rest.controller;

import com.intechcore.polarion.extension.github.client.GithubClient;
import com.intechcore.polarion.extension.github.client.GithubTokenInfo;
import com.intechcore.polarion.extension.github.client.RequestToken;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GithubTokenControllerTest {

    /** The client checks the token of the header, and the server forgets it afterwards. */
    @Test
    void checksTheTokenOfTheHeader() {
        GithubClient client = mock(GithubClient.class);
        when(client.checkUserToken()).thenAnswer(invocation -> new GithubTokenInfo(RequestToken.current(), 1, 2));

        assertThat(new GithubTokenInternalController(client).checkToken("ghp_user")).isEqualTo(new GithubTokenInfo("ghp_user", 1, 2));
        assertThat(RequestToken.current()).isNull();
    }
}
