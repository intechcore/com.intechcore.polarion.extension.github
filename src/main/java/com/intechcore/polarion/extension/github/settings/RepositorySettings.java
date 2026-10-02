package com.intechcore.polarion.extension.github.settings;

import ch.sbb.polarion.extension.generic.settings.GenericNamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;

/**
 * The repositories a project imports from. One named setting holds one repository, and settings
 * exist in the scope of a project only.
 */
public class RepositorySettings extends GenericNamedSettings<RepositorySettingsModel> {

    public static final String FEATURE_NAME = "repositories";

    public RepositorySettings() {
        super(FEATURE_NAME);
    }

    public RepositorySettings(SettingsService settingsService) {
        super(FEATURE_NAME, settingsService);
    }

    @Override
    public Collection<SettingName> readNames(@NotNull String scope) {
        return super.readNames(scope).stream()
                .filter(name -> Objects.equals(name.getScope(), scope))
                .toList();
    }

    @Override
    public @NotNull RepositorySettingsModel save(@NotNull String scope, @NotNull SettingId id, @NotNull RepositorySettingsModel what) {
        if (scope.isEmpty()) {
            throw new IllegalArgumentException("Repositories are configured in a project");
        }
        return super.save(scope, id, what);
    }

    /**
     * A setting without a repository is a draft: the administration page creates a setting by its
     * name alone and fills it afterwards. A draft is stored as it is, and the import rejects it.
     */
    @Override
    public void beforeSave(@NotNull RepositorySettingsModel what) {
        if (what.getRepository() != null && !what.getRepository().isBlank()) {
            what.validate();
        }
    }

    @Override
    public @NotNull RepositorySettingsModel defaultValues() {
        return RepositorySettingsModel.builder()
                .repository("")
                .shortName("")
                .issues(ItemSettings.builder().enabled(true).build())
                .discussions(new ItemSettings())
                .build();
    }
}
