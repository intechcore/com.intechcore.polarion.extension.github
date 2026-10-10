package com.intechcore.polarion.extension.github.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A work item as a picker shows it: its ID, title, and the icon of its type")
public record WorkItemRef(
        String id,
        String title,
        @Schema(description = "The name of the type of the work item") String typeName,
        @Schema(description = "The icon of the type, as configured in the project") String typeIcon,
        @Schema(description = "The name of the status of the work item") String status) {
}
