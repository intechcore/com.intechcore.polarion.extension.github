package com.intechcore.polarion.extension.github.rest;

import ch.sbb.polarion.extension.generic.rest.controller.info.ExtensionInfoInternalController;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * The extension has no controller of its own yet. The About page needs the ones generic brings,
 * and the settings endpoints of generic serve only a registered feature.
 */
class GithubRestApplicationTest {

    @Test
    void servesTheGenericControllersAndRegistersTheRepositorySettings() {
        try (MockedConstruction<RepositorySettings> settings = mockConstruction(RepositorySettings.class,
                (mock, context) -> when(mock.getFeatureName()).thenReturn(RepositorySettings.FEATURE_NAME))) {
            GithubRestApplication application = new GithubRestApplication();

            assertThat(application.getClasses()).contains(ExtensionInfoInternalController.class);
            assertThat(settings.constructed()).hasSize(1);
            assertThat(NamedSettingsRegistry.INSTANCE.getByFeatureName(RepositorySettings.FEATURE_NAME))
                    .isSameAs(settings.constructed().get(0));
        }
    }
}
