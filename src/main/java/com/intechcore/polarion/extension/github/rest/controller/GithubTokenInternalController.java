package com.intechcore.polarion.extension.github.rest.controller;

import com.intechcore.polarion.extension.github.client.GithubClient;
import com.intechcore.polarion.extension.github.client.GithubTokenInfo;
import com.intechcore.polarion.extension.github.client.RequestToken;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Checks the GitHub token a user keeps in the browser, before the page keeps it. The server stores
 * the token nowhere.
 */
@Tag(name = "GitHub token")
@Hidden
@Path("/internal")
@Singleton
public class GithubTokenInternalController {

    private final GithubClient githubClient;

    public GithubTokenInternalController() {
        this(GithubClient.shared());
    }

    public GithubTokenInternalController(GithubClient githubClient) {
        this.githubClient = githubClient;
    }

    @Operation(summary = "Asks GitHub who the token in the header belongs to, and what is left of its hourly limit")
    @GET
    @Path("/github-token")
    @Produces(MediaType.APPLICATION_JSON)
    public GithubTokenInfo checkToken(@Parameter(description = "The GitHub token to check")
                                      @HeaderParam(RequestToken.HEADER) String githubToken) {
        return RequestToken.with(githubToken, githubClient::checkUserToken);
    }
}
