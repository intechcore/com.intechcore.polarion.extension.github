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
        String scope = ScopeUtils.getScopeFromProject(projectId);
        Set<String> hidden = hiddenItems.urls(projectId);
        ProjectItems items = new ProjectItems();
        for (SettingName name : repositorySettings.readNames(scope)) {
            RepositoryState state = new RepositoryState(name.getName(), null, null, null);
            items.getRepositories().add(state);
            try {
                RepositorySettingsModel settings = repositorySettings.read(scope, SettingId.fromName(name.getName()), null);
                state.setRepository(settings.getRepository());
                if (refresh) {
                    importService.refresh(settings);
                }
                ImportResult result = importService.importRepository(projectId, settings, true, null);
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
}
