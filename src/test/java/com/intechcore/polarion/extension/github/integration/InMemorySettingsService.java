package com.intechcore.polarion.extension.github.integration;

import ch.sbb.polarion.extension.generic.settings.GenericNamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.platform.service.repository.IRepositoryService;
import com.polarion.subterra.base.location.ILocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;

/**
 * The repository of Polarion as far as the settings framework of generic uses it: files by their
 * location, and a revision that changes with every write.
 */
final class InMemorySettingsService extends SettingsService {

    private final Map<String, String> files = new TreeMap<>();
    // Shared by all instances: generic caches the setting names of a folder by its location and revision,
    // across instances, so a revision must never come back in a later test.
    private static final AtomicInteger revision = new AtomicInteger(1000);

    InMemorySettingsService() {
        super(mock(IRepositoryService.class), mock(IProjectService.class), mock(ITrackerService.class));
    }

    @Override
    public void save(@NotNull ILocation location, @NotNull String content) {
        files.put(location.getLocationPath(), content);
        revision.incrementAndGet();
    }

    @Override
    public void delete(@NotNull ILocation location) {
        files.remove(location.getLocationPath());
        revision.incrementAndGet();
    }

    @Override
    public @Nullable String read(@NotNull ILocation location, String revisionName) {
        return files.get(location.getLocationPath());
    }

    @Override
    public boolean exists(@NotNull ILocation location) {
        return files.containsKey(location.getLocationPath());
    }

    @Override
    public Collection<String> getPersistedSettingFileNames(ILocation settingsFolderLocation) {
        String folder = settingsFolderLocation.getLocationPath() + "/";
        return files.keySet().stream()
                .filter(path -> path.startsWith(folder) && path.indexOf('/', folder.length()) < 0)
                .map(path -> path.substring(folder.length()).replace(GenericNamedSettings.SETTINGS_FILE_EXTENSION, ""))
                .toList();
    }

    @Override
    public @Nullable String getLastRevision(@NotNull ILocation location) {
        String path = location.getLocationPath();
        boolean exists = files.keySet().stream().anyMatch(file -> file.equals(path) || file.startsWith(path + "/"));
        return exists ? String.valueOf(revision.get()) : null;
    }
}
