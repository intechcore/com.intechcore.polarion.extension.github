package com.intechcore.polarion.extension.github.settings;

import ch.sbb.polarion.extension.generic.settings.SettingsModel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The GitHub items a project hides on its GitHub page, by URL.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "The GitHub items a project hides on its GitHub page")
public class HiddenItemsModel extends SettingsModel {

    private static final String URLS_ENTRY = "URLS";

    @Schema(description = "The URLs of the hidden items")
    private List<String> urls = new ArrayList<>();

    @Override
    protected String serializeModelData() {
        return serializeEntry(URLS_ENTRY, urls.toArray(String[]::new));
    }

    @Override
    protected void deserializeModelData(String serializedString) {
        urls = new ArrayList<>(Arrays.asList(deserializeEntry(URLS_ENTRY, serializedString, String[].class, new String[0])));
    }
}
