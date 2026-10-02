package com.intechcore.polarion.extension.github.service;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The outcome of the import for one GitHub item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "The outcome of the import for one GitHub item")
public class ImportEntry {

    @Schema(description = "Issue or discussion")
    private ItemKind kind;

    @Schema(description = "The number of the item in its repository")
    private long number;

    @Schema(description = "The title of the item on GitHub")
    private String title;

    @Schema(description = "The URL of the item on GitHub")
    private String url;

    @Schema(description = "What the import did, or would do")
    private ImportStatus status;

    @Schema(description = "The ID of the work item, when one exists or was created")
    private String workItemId;

    @Schema(description = "The reason of a failure")
    private String message;
}
