package com.intechcore.polarion.extension.github.rest.exception;

import ch.sbb.polarion.extension.generic.rest.model.ErrorEntity;
import com.intechcore.polarion.extension.github.client.GithubClientException;
import com.intechcore.polarion.extension.github.client.GithubRateLimitException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GithubClientExceptionMapperTest {

    private final GithubClientExceptionMapper mapper = new GithubClientExceptionMapper();

    @Test
    void answersAGithubFailureAsBadGateway() {
        Response response = mapper.toResponse(new GithubClientException("GitHub answered with status 404"));

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(((ErrorEntity) response.getEntity()).getMessage()).isEqualTo("GitHub answered with status 404");
    }

    @Test
    void answersAnExhaustedRateLimitAsTooManyRequests() {
        Response response = mapper.toResponse(new GithubRateLimitException(Instant.ofEpochSecond(1790948455L)));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(((ErrorEntity) response.getEntity()).getMessage()).contains("rate limit");
    }
}
