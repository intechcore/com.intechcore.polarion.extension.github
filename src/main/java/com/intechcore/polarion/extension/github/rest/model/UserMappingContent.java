package com.intechcore.polarion.extension.github.rest.model;

import com.intechcore.polarion.extension.github.settings.UserLink;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The user mapping a scope uses, and whether it comes from the global scope.
 */
@Schema(description = "The user mapping a scope uses")
public record UserMappingContent(
        @Schema(description = "The GitHub logins and their Polarion users") List<UserLink> users,
        @Schema(description = "True when a project uses the global mapping") boolean inherited) {
}
