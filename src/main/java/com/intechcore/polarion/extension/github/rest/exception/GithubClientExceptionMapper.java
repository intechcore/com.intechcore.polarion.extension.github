package com.intechcore.polarion.extension.github.rest.exception;

import ch.sbb.polarion.extension.generic.rest.model.ErrorEntity;
import com.intechcore.polarion.extension.github.client.GithubClientException;
import com.intechcore.polarion.extension.github.client.GithubRateLimitException;
import com.polarion.core.util.logging.Logger;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * GitHub failed, not this server: 502, or 429 when the rate limit is used up.
 */
@Provider
public class GithubClientExceptionMapper implements ExceptionMapper<GithubClientException> {

    private static final Logger logger = Logger.getLogger(GithubClientExceptionMapper.class);

    @Override
    public Response toResponse(GithubClientException e) {
        logger.error("GitHub request failed: " + e.getMessage(), e);
        Response.Status status = e instanceof GithubRateLimitException ? Response.Status.TOO_MANY_REQUESTS : Response.Status.BAD_GATEWAY;
        return Response.status(status)
                .entity(new ErrorEntity(e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
