package com.intechcore.polarion.extension.github.settings;

import ch.sbb.polarion.extension.generic.settings.GenericNamedSettings;
import ch.sbb.polarion.extension.generic.settings.NamedSettings;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.polarion.subterra.base.location.ILocation;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Polarion users of GitHub logins. One setting, global for all projects. A project may keep
 * its own, which then replaces the global one. The generic endpoints save a setting by its name
 * under a new file in a project, so this setting has endpoints of its own.
 */
public class UserMapping extends GenericNamedSettings<UserMappingModel> {

    public static final String FEATURE_NAME = "user-mapping";
    // A fixed file, as for the hidden items: the page reads and saves this one setting only.
    private static final SettingId ID = SettingId.fromId(NamedSettings.DEFAULT_NAME);

    public UserMapping() {
        super(FEATURE_NAME);
    }

    public UserMapping(SettingsService settingsService) {
        super(FEATURE_NAME, settingsService);
    }

    /** The Polarion user ID of each GitHub login of a project, by the login in lower case. */
    public @NotNull Map<String, String> users(@NotNull String projectId) {
        Map<String, String> users = new LinkedHashMap<>();
        for (UserLink user : read(ScopeUtils.getScopeFromProject(projectId)).getUsers()) {
            if (user != null && user.getGithubLogin() != null && user.getPolarionUser() != null) {
                users.putIfAbsent(user.getGithubLogin().trim().toLowerCase(Locale.ROOT), user.getPolarionUser().trim());
            }
        }
        return users;
    }

    /** The mapping a scope uses: its own, or the global one. */
    public @NotNull UserMappingModel read(@NotNull String scope) {
        return read(scope, ID, null);
    }

    /** Whether the scope keeps a mapping of its own. The global scope always does. */
    public boolean isOwn(@NotNull String scope) {
        return scope.isEmpty() || getSettingsService().exists(location(scope));
    }

    public @NotNull UserMappingModel save(@NotNull String scope, @NotNull UserMappingModel mapping) {
        return save(scope, ID, mapping);
    }

    /** Removes the mapping of a project, which then uses the global one again. */
    public void reset(@NotNull String scope) {
        if (scope.isEmpty()) {
            throw new IllegalArgumentException("Only a project uses the global mapping");
        }
        if (isOwn(scope)) {
            delete(scope, ID);
        }
    }

    private ILocation location(String scope) {
        return ScopeUtils.getContextLocation(scope)
                .append(getSettingsFolder() + "/" + NamedSettings.DEFAULT_NAME + SETTINGS_FILE_EXTENSION);
    }

    @Override
    public void beforeSave(@NotNull UserMappingModel what) {
        what.validate();
    }

    @Override
    public @NotNull UserMappingModel defaultValues() {
        return new UserMappingModel();
    }
}
