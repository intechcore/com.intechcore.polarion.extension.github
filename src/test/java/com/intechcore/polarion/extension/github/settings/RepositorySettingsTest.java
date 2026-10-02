package com.intechcore.polarion.extension.github.settings;

import ch.sbb.polarion.extension.generic.context.CurrentContextConfig;
import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.polarion.subterra.base.location.ILocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, CurrentContextExtension.class})
@CurrentContextConfig("github")
class RepositorySettingsTest {

    private static final String PROJECT_SCOPE = "project/elibrary/";

    private static RepositorySettingsModel valid() {
        return RepositorySettingsModel.builder()
                .repository("acme/tool")
                .shortName("Tool")
                .issues(ItemSettings.builder().enabled(true).workItemType("task").build())
                .discussions(new ItemSettings())
                .build();
    }

    private static ILocation location() {
        ILocation location = mock(ILocation.class);
        when(location.append(anyString())).thenReturn(location);
        return location;
    }

    @Test
    void keepsSettingsUnderTheRepositoriesFeature() {
        RepositorySettings settings = new RepositorySettings(mock(SettingsService.class));

        assertThat(settings.getFeatureName()).isEqualTo("repositories");
        assertThat(settings.getSettingsFolder()).isEqualTo(".polarion/extensions/github/repositories");
    }

    @Test
    void offersIssuesOnlyByDefault() {
        RepositorySettingsModel defaults = new RepositorySettings(mock(SettingsService.class)).defaultValues();

        assertThat(defaults.isEnabled()).isFalse();
        assertThat(defaults.getIssues().isEnabled()).isTrue();
        assertThat(defaults.getIssues().getTitleTemplate()).isEqualTo(ItemSettings.DEFAULT_TITLE_TEMPLATE);
        assertThat(defaults.getDiscussions().isEnabled()).isFalse();
    }

    @Test
    void savesValidSettingsInAProject() {
        try (MockedStatic<ScopeUtils> scopeUtils = mockStatic(ScopeUtils.class)) {
            ILocation location = location();
            scopeUtils.when(() -> ScopeUtils.getContextLocation(PROJECT_SCOPE)).thenReturn(location);
            SettingsService service = mock(SettingsService.class);

            new RepositorySettings(service).save(PROJECT_SCOPE, SettingId.fromId("tool"), valid());

            verify(service).save(any(), contains("acme/tool"));
        }
    }

    @Test
    void savesADraftWithoutARepository() {
        try (MockedStatic<ScopeUtils> scopeUtils = mockStatic(ScopeUtils.class)) {
            ILocation location = location();
            scopeUtils.when(() -> ScopeUtils.getContextLocation(PROJECT_SCOPE)).thenReturn(location);
            SettingsService service = mock(SettingsService.class);
            RepositorySettings settings = new RepositorySettings(service);

            settings.save(PROJECT_SCOPE, SettingId.fromId("tool"), settings.defaultValues());

            verify(service).save(any(), anyString());
        }
    }

    @Test
    void refusesToSaveOutsideAProject() {
        SettingsService service = mock(SettingsService.class);
        RepositorySettings settings = new RepositorySettings(service);
        SettingId id = SettingId.fromId("tool");
        RepositorySettingsModel model = valid();

        assertThatThrownBy(() -> settings.save("", id, model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("in a project");
        verify(service, never()).save(any(), anyString());
    }

    @Test
    void refusesToSaveInvalidSettings() {
        try (MockedStatic<ScopeUtils> scopeUtils = mockStatic(ScopeUtils.class)) {
            ILocation location = location();
            scopeUtils.when(() -> ScopeUtils.getContextLocation(PROJECT_SCOPE)).thenReturn(location);
            SettingsService service = mock(SettingsService.class);
            RepositorySettings settings = new RepositorySettings(service);
            SettingId id = SettingId.fromId("tool");
            RepositorySettingsModel model = valid();
            model.setRepository("not a repository");

            assertThatThrownBy(() -> settings.save(PROJECT_SCOPE, id, model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("owner/name");
            verify(service, never()).save(any(), anyString());
        }
    }

    @Test
    void listsOnlyTheNamesOfTheRequestedScope() {
        try (MockedStatic<ScopeUtils> scopeUtils = mockStatic(ScopeUtils.class)) {
            ILocation projectLocation = location();
            ILocation defaultLocation = location();
            scopeUtils.when(() -> ScopeUtils.getContextLocation(PROJECT_SCOPE)).thenReturn(projectLocation);
            scopeUtils.when(() -> ScopeUtils.getContextLocation("")).thenReturn(defaultLocation);
            SettingsService service = mock(SettingsService.class);
            when(service.getLastRevision(projectLocation)).thenReturn("1");
            when(service.getPersistedSettingFileNames(projectLocation)).thenReturn(List.of("tool"));
            when(service.read(any(), any())).thenReturn(valid().serialize());

            assertThat(new RepositorySettings(service).readNames(PROJECT_SCOPE))
                    .extracting(SettingName::getScope)
                    .containsOnly(PROJECT_SCOPE);
        }
    }
}
