package com.intechcore.polarion.extension.github.rest.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Which items of a repository to import")
public class ImportRequest {

    @Schema(description = "The GitHub URLs to import. Leave it out to import every open item.")
    private List<String> urls;
}
