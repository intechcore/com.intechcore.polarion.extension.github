package com.intechcore.polarion.extension.github.rest;

import ch.sbb.polarion.extension.generic.rest.GenericRestApplication;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import com.intechcore.polarion.extension.github.rest.controller.ImportApiController;
import com.intechcore.polarion.extension.github.rest.controller.ImportInternalController;
import com.intechcore.polarion.extension.github.rest.controller.ProjectApiController;
import com.intechcore.polarion.extension.github.rest.controller.ProjectInternalController;
import com.intechcore.polarion.extension.github.rest.exception.GithubClientExceptionMapper;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class GithubRestApplication extends GenericRestApplication {

    public GithubRestApplication() {
        NamedSettingsRegistry.INSTANCE.register(List.of(new RepositorySettings()));
    }

    @Override
    protected @NotNull Set<Class<?>> getExtensionControllerClasses() {
        return Set.of(
                ImportApiController.class,
                ImportInternalController.class,
                ProjectApiController.class,
                ProjectInternalController.class
        );
    }

    @Override
    protected @NotNull Set<Class<?>> getExtensionExceptionMapperClasses() {
        return Set.of(GithubClientExceptionMapper.class);
    }
}
