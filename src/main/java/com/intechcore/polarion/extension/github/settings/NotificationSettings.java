package com.intechcore.polarion.extension.github.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Who hears of new GitHub items of a repository, and of which kinds. The watch job sends the mails.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Who hears of new GitHub items of a repository, and of which kinds")
public class NotificationSettings {

    @Schema(description = "The IDs of the Polarion users who get the mails")
    @Builder.Default
    private List<String> users = new ArrayList<>();

    @Schema(description = "Mail new open issues without a work item")
    private boolean issues;

    @Schema(description = "Mail new open discussions without a work item")
    private boolean discussions;

    @Schema(description = "Mail pull requests of the watched authors whose checks failed")
    private boolean pullRequests;

    /** Whether any kind is turned on. */
    public boolean isEnabled() {
        return issues || discussions || pullRequests;
    }
}
