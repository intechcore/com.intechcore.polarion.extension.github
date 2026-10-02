package com.intechcore.polarion.extension.github.rest;

import ch.sbb.polarion.extension.generic.rest.GenericRestApplication;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;

import java.util.List;

public class GithubRestApplication extends GenericRestApplication {

    public GithubRestApplication() {
        NamedSettingsRegistry.INSTANCE.register(List.of(new RepositorySettings()));
    }
}
