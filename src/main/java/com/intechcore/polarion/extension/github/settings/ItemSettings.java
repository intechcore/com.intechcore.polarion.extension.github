package com.intechcore.polarion.extension.github.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How one kind of GitHub item, issues or discussions, becomes work items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "How issues or discussions become work items")
public class ItemSettings {

    public static final String DEFAULT_TITLE_TEMPLATE = "[GitHub] {shortName} : {title}";
    public static final String DEFAULT_DESCRIPTION_TEMPLATE = "<a href=\"{url}\">{url}</a>";

    @Schema(description = "Whether the import creates work items for this kind of item")
    private boolean enabled;

    @Schema(description = "The type of the created work items")
    private String workItemType;

    @Schema(description = "The title template. Placeholders: {shortName}, {repository}, {number}, {title}, {author}, {url}, {labels}, {type}, {category}")
    @Builder.Default
    private String titleTemplate = DEFAULT_TITLE_TEMPLATE;

    @Schema(description = "The HTML description template. Placeholders: the ones of the title, and {body}")
    @Builder.Default
    private String descriptionTemplate = DEFAULT_DESCRIPTION_TEMPLATE;

    @Schema(description = "Where the work item keeps the URL of the GitHub item")
    @Builder.Default
    private DuplicateKey duplicateKey = DuplicateKey.HYPERLINK;

    @Schema(description = "The custom field that keeps the URL, when duplicateKey is CUSTOM_FIELD")
    private String duplicateKeyField;

    @Schema(description = "The ID of the work item the created ones link to, for example an epic")
    private String epicId;

    @Schema(description = "The role of the link to the epic")
    private String epicLinkRole;

    @Schema(description = "Field values of the created work items, by field ID")
    @Builder.Default
    private Map<String, String> fields = new LinkedHashMap<>();

    @Schema(description = "Rules for the items that need another work item type, or no import. The first matching rule applies.")
    @Builder.Default
    private List<ItemRule> rules = new ArrayList<>();
}
