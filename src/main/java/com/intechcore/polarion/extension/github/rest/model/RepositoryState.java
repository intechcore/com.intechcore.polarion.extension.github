package com.intechcore.polarion.extension.github.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "How the reading of one repository setting went")
public class RepositoryState {

    @Schema(description = "The name of the repository setting")
    private String setting;

    @Schema(description = "The repository as owner/name")
    private String repository;

    @Schema(description = "When the oldest list of the repository was read from GitHub, as an ISO-8601 instant")
    private String readAt;

    @Schema(description = "Why the repository could not be read")
    private String error;
}
