package com.intechcore.polarion.extension.github.rest;

import ch.sbb.polarion.extension.generic.rest.controller.info.ExtensionInfoInternalController;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import com.intechcore.polarion.extension.github.rest.controller.ImportApiController;
import com.intechcore.polarion.extension.github.rest.controller.ImportInternalController;
import com.intechcore.polarion.extension.github.rest.exception.GithubClientExceptionMapper;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * A controller missing from the application is not served, and its endpoint answers 404 on a
 * running server. The settings endpoints of generic serve only a registered feature.
 */
class GithubRestApplicationTest {

    @Test
    void servesTheControllersAndRegistersTheRepositorySettings() {
        try (MockedConstruction<RepositorySettings> settings = mockConstruction(RepositorySettings.class,
                (mock, context) -> when(mock.getFeatureName()).thenReturn(RepositorySettings.FEATURE_NAME))) {
            GithubRestApplication application = new GithubRestApplication();

            assertThat(application.getClasses()).contains(
                    ExtensionInfoInternalController.class,
                    ImportInternalController.class,
                    ImportApiController.class,
                    GithubClientExceptionMapper.class);
            assertThat(settings.constructed()).hasSize(1);
            assertThat(NamedSettingsRegistry.INSTANCE.getByFeatureName(RepositorySettings.FEATURE_NAME))
                    .isSameAs(settings.constructed().get(0));
        }
    }
}
