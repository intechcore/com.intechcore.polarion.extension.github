package com.intechcore.polarion.extension.github.settings;

import ch.sbb.polarion.extension.generic.settings.GenericNamedSettings;
import ch.sbb.polarion.extension.generic.settings.NamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The items a project hides on its GitHub page. One setting per project, apart from the repository
 * settings, so that hiding an item never races an edit of a repository setting.
 */
public class HiddenItems extends GenericNamedSettings<HiddenItemsModel> {

    public static final String FEATURE_NAME = "hidden-items";
    // A fixed file: a setting found by its name has no file until an administration page creates it.
    private static final SettingId ID = SettingId.fromId(NamedSettings.DEFAULT_NAME);

    public HiddenItems() {
        super(FEATURE_NAME);
    }

    public HiddenItems(SettingsService settingsService) {
        super(FEATURE_NAME, settingsService);
    }

    /** The URLs a project hides. */
    public @NotNull Set<String> urls(@NotNull String projectId) {
        return new LinkedHashSet<>(read(ScopeUtils.getScopeFromProject(projectId), ID, null).getUrls());
    }

    /** Hides the items or shows them again, and answers with all URLs the project hides now. */
    public @NotNull Set<String> change(@NotNull String projectId, @NotNull Collection<String> urls, boolean hidden) {
        String scope = ScopeUtils.getScopeFromProject(projectId);
        if (scope.isEmpty()) {
            throw new IllegalArgumentException("Items are hidden in a project");
        }
        Set<String> result = urls(projectId);
        if (hidden) {
            result.addAll(urls);
        } else {
            result.removeAll(urls);
        }
        save(scope, ID, new HiddenItemsModel(List.copyOf(result)));
        return result;
    }

    @Override
    public @NotNull HiddenItemsModel defaultValues() {
        return new HiddenItemsModel();
    }
}
