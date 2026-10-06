package com.intechcore.polarion.extension.github.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Which items to hide or to show again on the GitHub page")
public class HideRequest {

    @Schema(description = "The GitHub URLs of the items")
    private List<String> urls;

    @Schema(description = "True to hide the items, false to show them again")
    private boolean hidden;
}
