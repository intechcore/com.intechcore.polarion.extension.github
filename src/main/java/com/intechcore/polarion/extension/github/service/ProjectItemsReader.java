package com.intechcore.polarion.extension.github.service;

import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.intechcore.polarion.extension.github.client.GithubClientException;
import com.intechcore.polarion.extension.github.rest.model.ProjectItems;
import com.intechcore.polarion.extension.github.rest.model.RepositoryState;
import com.intechcore.polarion.extension.github.settings.HiddenItems;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import com.intechcore.polarion.extension.github.settings.RepositorySettingsModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * The items of every repository setting of a project, with what the import would do with each: what
 * the GitHub topic, the Live Report widget and its PDF show. A setting that fails reports its reason
 * and leaves the others readable.
 */
public class ProjectItemsReader {

    private final RepositorySettings repositorySettings;
    private final ImportService importService;
    private final HiddenItems hiddenItems;

    public ProjectItemsReader(@NotNull RepositorySettings repositorySettings, @NotNull ImportService importService,
                              @NotNull HiddenItems hiddenItems) {
        this.repositorySettings = repositorySettings;
        this.importService = importService;
        this.hiddenItems = hiddenItems;
    }

    /**
     * @param refresh true to read GitHub again instead of the lists of the last five minutes
     */
    public @NotNull ProjectItems read(@NotNull String projectId, boolean refresh) {
        return read(projectId, refresh, List.of());
    }

    /**
     * @param refresh  true to read GitHub again instead of the lists of the last five minutes
     * @param settings the settings to read, in the order to show them, or none for all
     */
    public @NotNull ProjectItems read(@NotNull String projectId, boolean refresh, @NotNull List<String> settings) {
        String scope = ScopeUtils.getScopeFromProject(projectId);
        Set<String> hidden = hiddenItems.urls(projectId);
        ProjectItems items = new ProjectItems();
        for (String name : names(scope, settings)) {
            RepositoryState state = new RepositoryState(name, null, null, null);
            items.getRepositories().add(state);
            try {
                RepositorySettingsModel model = repositorySettings.read(scope, SettingId.fromName(name), null);
                state.setRepository(model.getRepository());
                if (refresh) {
                    importService.refresh(model);
                }
                ImportResult result = importService.importRepository(projectId, model, true, null);
                state.setReadAt(result.getReadAt());
                result.getEntries().forEach(entry -> entry.setHidden(hidden.contains(entry.getUrl())));
                items.getEntries().addAll(result.getEntries());
            } catch (GithubClientException | IllegalArgumentException e) {
                // One repository that fails leaves the others readable.
                state.setError(e.getMessage());
            }
        }
        return items;
    }

    /** The settings asked for that still exist, so a widget never asks GitHub for the others. */
    private @NotNull List<String> names(@NotNull String scope, @NotNull List<String> settings) {
        List<String> all = repositorySettings.readNames(scope).stream().map(SettingName::getName).toList();
        return settings.isEmpty() ? all : settings.stream().distinct().filter(all::contains).toList();
    }
}
