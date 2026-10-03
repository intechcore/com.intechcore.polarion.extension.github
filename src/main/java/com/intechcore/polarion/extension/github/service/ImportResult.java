package com.intechcore.polarion.extension.github.service;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of the import of one repository.
 */
@Data
@Schema(description = "The outcome of the import of one repository")
public class ImportResult {

    @Schema(description = "The repository as owner/name")
    private final String repository;

    @Schema(description = "Whether the import only reported what it would do")
    private final boolean dryRun;

    @Schema(description = "One entry per GitHub item")
    private final List<ImportEntry> entries = new ArrayList<>();

    @Schema(description = "When the oldest list of the repository was read from GitHub, as an ISO-8601 instant. Lists serve from a cache for five minutes.")
    private String readAt;

    public long count(ImportStatus status) {
        return entries.stream().filter(entry -> entry.getStatus() == status).count();
    }
}
