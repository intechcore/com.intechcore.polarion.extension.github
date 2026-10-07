package com.intechcore.polarion.extension.github.service;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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

    @Schema(description = "The name of the repository setting")
    private String setting;

    @Schema(description = "The repository as owner/name")
    private String repository;

    @Schema(description = "The short name of the repository, from its setting")
    private String shortName;

    @Schema(description = "The issue type, or the category of a discussion")
    private String githubType;

    @Schema(description = "The labels of the item")
    private List<String> labels;

    @Schema(description = "The color of each label as GitHub shows it, six hexadecimal digits by label name")
    private Map<String, String> labelColors;

    @Schema(description = "The GitHub logins the item is assigned to")
    private List<String> assignees;

    @Schema(description = "The ID of the work item type: of the work item, or the one the import would create")
    private String workItemType;

    @Schema(description = "The name of that work item type")
    private String workItemTypeName;

    @Schema(description = "The URL of the icon of that work item type")
    private String workItemTypeIcon;

    @Schema(description = "The status of the work item")
    private String workItemStatus;

    @Schema(description = "The URL of the icon of that status")
    private String workItemStatusIcon;

    @Schema(description = "The names of the users the work item is assigned to")
    private List<String> workItemAssignees;

    @Schema(description = "When the item was opened on GitHub, ISO-8601")
    private String createdAt;

    @Schema(description = "When the item last changed on GitHub, ISO-8601")
    private String updatedAt;

    @Schema(description = "The names of the failed checks of a pull request, separated by commas")
    private String failedChecks;

    @Schema(description = "True when the project hides the item on its GitHub page")
    private boolean hidden;
}
