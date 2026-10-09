package com.intechcore.polarion.extension.github.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A work item field of a project")
public record ProjectField(
        String id,
        String name,
        @Schema(description = "Whether the field is a custom field") boolean custom,
        @Schema(description = "Whether the field can keep the URL of a GitHub item: a custom field of the type String") boolean urlKey,
        @Schema(description = "Whether the field takes several values, separated by commas") boolean multi,
        @Schema(description = "The options of an enumeration field, or null for any other field") List<FieldOption> options,
        @Schema(description = "The kind of value, which the settings page gives its own control",
                allowableValues = {"string", "text", "rich", "integer", "float", "currency", "boolean", "date", "time", "dateTime", "duration", "enum"})
        String type) {

    /** An option of an enumeration field. */
    @Schema(description = "An option of an enumeration field")
    public record FieldOption(String id, String name, String iconUrl) {
    }
}
