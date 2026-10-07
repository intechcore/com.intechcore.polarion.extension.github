package com.intechcore.polarion.extension.github.widget;

import ch.sbb.polarion.extension.generic.settings.SettingName;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import com.polarion.platform.persistence.IEnumFactory;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.model.IPObject;
import com.polarion.platform.persistence.spi.EnumOption;
import com.polarion.subterra.base.data.identification.IContextId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The enumeration {@code @GitHubRepositories}: the repository settings of the project in context, by
 * name. The widget offers them as a multi-select, as Polarion offers its users through {@code @user}.
 */
public class RepositoriesEnumFactory implements IEnumFactory {

    public static final String ENUM_ID = "@GitHubRepositories";

    private final RepositorySettings repositorySettings;

    public RepositoriesEnumFactory() {
        this(new RepositorySettings());
    }

    public RepositoriesEnumFactory(@NotNull RepositorySettings repositorySettings) {
        this.repositorySettings = repositorySettings;
    }

    @Override
    public IEnumeration<IEnumOption> getEnumeration(String enumId, @Nullable IContextId contextId) {
        String projectId = contextId == null ? null : contextId.getContextName();
        return new Repositories(enumId, projectId);
    }

    /** The names of the repository settings of a project. Outside a project there are none. */
    List<String> names(@Nullable String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return List.of();
        }
        return repositorySettings.readNames(ScopeUtils.getScopeFromProject(projectId)).stream()
                .map(SettingName::getName)
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private final class Repositories implements IEnumeration<IEnumOption> {

        private final String enumId;
        private final @Nullable String projectId;

        private Repositories(String enumId, @Nullable String projectId) {
            this.enumId = enumId;
            this.projectId = projectId;
        }

        @Override
        public IEnumOption wrapOption(String optionId) {
            // A setting renamed or deleted since the widget was saved stays in the widget, marked.
            return names(projectId).contains(optionId) ? option(optionId) : new EnumOption(enumId, optionId, optionId, 0, false) {
                @Override
                public boolean isPhantom() {
                    return true;
                }
            };
        }

        @Override
        public IEnumOption wrapOption(String optionId, Object controlValue) {
            return wrapOption(optionId);
        }

        @Override
        public IEnumOption wrapOption(String optionId, IPObject object) {
            return wrapOption(optionId);
        }

        @Override
        public List<IEnumOption> getAllOptions() {
            return names(projectId).stream().map(this::option).toList();
        }

        @Override
        public List<IEnumOption> getAvailableOptions(Object controlValue) {
            return getAllOptions();
        }

        @Override
        public List<IEnumOption> getAvailableOptions(Object controlValue, IEnumOption currentValue) {
            return getAllOptions();
        }

        @Override
        public @Nullable String getControlKey() {
            return null;
        }

        @Override
        public @Nullable IEnumOption getDefaultOption(Object controlValue) {
            return null;
        }

        private IEnumOption option(String name) {
            return new EnumOption(enumId, name, name, 0, false);
        }
    }
}
