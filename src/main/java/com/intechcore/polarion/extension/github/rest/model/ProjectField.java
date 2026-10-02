package com.intechcore.polarion.extension.github.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A work item field of a project")
public record ProjectField(
        String id,
        String name,
        @Schema(description = "Whether the field is a custom field") boolean custom,
        @Schema(description = "Whether the field can keep the URL of a GitHub item: a custom field of the type String") boolean urlKey) {
}
