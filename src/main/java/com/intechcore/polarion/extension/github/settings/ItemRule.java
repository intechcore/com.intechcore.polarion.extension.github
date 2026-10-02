package com.intechcore.polarion.extension.github.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A rule for the GitHub items that match it: another work item type and field values, or no import
 * at all. The first matching rule of a list applies.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "A rule for the GitHub items that match it")
public class ItemRule {

    @Schema(description = "What the rule compares: a label, the issue type or the discussion category")
    private RuleMatch match;

    @Schema(description = "The value to compare with, without regard to case")
    private String value;

    @Schema(description = "True to leave the matching items out of the import")
    private boolean skip;

    @Schema(description = "The type of the work items created from the matching items")
    private String workItemType;

    @Schema(description = "Field values of those work items. They are added to the field values of the block and replace them.")
    @Builder.Default
    private Map<String, String> fields = new LinkedHashMap<>();

    /**
     * The rule as a reader names it, for example {@code Label = bug}.
     */
    public String describe() {
        String name = match == null ? "" : match.name().charAt(0) + match.name().substring(1).toLowerCase();
        return name + " = " + value;
    }
}
