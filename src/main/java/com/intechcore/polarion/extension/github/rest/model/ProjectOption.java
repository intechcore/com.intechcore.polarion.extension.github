package com.intechcore.polarion.extension.github.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A work item type or a link role of a project")
public record ProjectOption(String id, String name) {
}
