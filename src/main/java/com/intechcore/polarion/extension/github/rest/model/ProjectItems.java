package com.intechcore.polarion.extension.github.rest.model;

import com.intechcore.polarion.extension.github.service.ImportEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "The open GitHub items of all repository settings of a project")
public class ProjectItems {

    @Schema(description = "One state per repository setting")
    private final List<RepositoryState> repositories = new ArrayList<>();

    @Schema(description = "The open issues and discussions, with what the import would do with each")
    private final List<ImportEntry> entries = new ArrayList<>();
}
