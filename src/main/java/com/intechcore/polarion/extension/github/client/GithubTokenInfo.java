package com.intechcore.polarion.extension.github.client;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The GitHub user a token belongs to, and what is left of its hourly limit")
public record GithubTokenInfo(
        @Schema(description = "The login of the GitHub user") String login,
        @Schema(description = "The requests left in the current hour, or null when GitHub did not say") Integer remaining,
        @Schema(description = "The requests of an hour, or null when GitHub did not say") Integer limit) {
}
