package com.intechcore.polarion.extension.github.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One GitHub login and the Polarion user it stands for.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "A GitHub login and the Polarion user it stands for")
public class UserLink {

    @Schema(description = "The GitHub login", example = "octocat")
    private String githubLogin;

    @Schema(description = "The ID of the Polarion user", example = "jdoe")
    private String polarionUser;
}
