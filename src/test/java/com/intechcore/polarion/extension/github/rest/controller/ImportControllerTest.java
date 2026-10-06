package com.intechcore.polarion.extension.github.rest.controller;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import com.intechcore.polarion.extension.github.rest.model.ImportRequest;
import com.intechcore.polarion.extension.github.service.ImportResult;
import com.intechcore.polarion.extension.github.service.ImportService;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import com.intechcore.polarion.extension.github.settings.RepositorySettingsModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportControllerTest {

    private PolarionService polarionService;
    private RepositorySettings repositorySettings;
    private ImportService importService;
    private RepositorySettingsModel settings;
    private ImportResult result;

    @BeforeEach
    void setUp() {
        polarionService = mock(PolarionService.class);
        repositorySettings = mock(RepositorySettings.class);
        importService = mock(ImportService.class);
        settings = new RepositorySettingsModel();
        result = new ImportResult("acme/tool", false);
        when(repositorySettings.load(eq("elibrary"), any())).thenReturn(settings);
    }

    @Test
    void importsTheRepositoryOfTheNamedSetting() {
        when(importService.importRepository("elibrary", settings, false, null)).thenReturn(result);

        ImportResult answer = new ImportInternalController(polarionService, repositorySettings, importService)
                .importRepository("elibrary", "tool", false, null);

        assertThat(answer).isSameAs(result);
        ArgumentCaptor<SettingId> id = ArgumentCaptor.forClass(SettingId.class);
        verify(repositorySettings).load(eq("elibrary"), id.capture());
        assertThat(id.getValue().getIdentifier()).isEqualTo("tool");
        assertThat(id.getValue().isUseName()).isTrue();
        verify(polarionService, never()).callPrivileged(any(Callable.class));
    }

    @Test
    void passesTheDryRunFlagAndTheRequestedUrls() {
        List<String> urls = List.of("https://github.com/acme/tool/issues/7");
        when(importService.importRepository("elibrary", settings, true, urls)).thenReturn(result);

        ImportResult answer = new ImportInternalController(polarionService, repositorySettings, importService)
                .importRepository("elibrary", "tool", true, new ImportRequest(urls));

        assertThat(answer).isSameAs(result);
    }

    @Test
    void collectsTheItemsOfEverySettingAndTheReasonOfAFailure() {
        try (org.mockito.MockedStatic<ch.sbb.polarion.extension.generic.util.ScopeUtils> scopeUtils =
                     org.mockito.Mockito.mockStatic(ch.sbb.polarion.extension.generic.util.ScopeUtils.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            when(repositorySettings.readNames("project/elibrary/")).thenReturn(List.of(
                    ch.sbb.polarion.extension.generic.settings.SettingName.builder().name("tool").scope("project/elibrary/").build(),
                    ch.sbb.polarion.extension.generic.settings.SettingName.builder().name("broken").scope("project/elibrary/").build(),
                    ch.sbb.polarion.extension.generic.settings.SettingName.builder().name("draft").scope("project/elibrary/").build()));
            RepositorySettingsModel tool = RepositorySettingsModel.builder().repository("acme/tool").build();
            RepositorySettingsModel broken = RepositorySettingsModel.builder().repository("acme/broken").build();
            RepositorySettingsModel draft = RepositorySettingsModel.builder().repository("").build();
            when(repositorySettings.read(eq("project/elibrary/"), any(), org.mockito.ArgumentMatchers.isNull())).thenAnswer(invocation -> switch (invocation.<SettingId>getArgument(1).getIdentifier()) {
                case "tool" -> tool;
                case "broken" -> broken;
                default -> draft;
            });
            ImportResult toolResult = new ImportResult("acme/tool", true);
            toolResult.setReadAt("2026-10-03T08:00:00Z");
            toolResult.getEntries().add(com.intechcore.polarion.extension.github.service.ImportEntry.builder().number(7).build());
            when(importService.importRepository("elibrary", tool, true, null)).thenReturn(toolResult);
            when(importService.importRepository("elibrary", broken, true, null))
                    .thenThrow(new com.intechcore.polarion.extension.github.client.GithubClientException("Discussions are turned off in the repository acme/broken"));
            when(importService.importRepository("elibrary", draft, true, null)).thenThrow(new IllegalArgumentException("The repository must be given as owner/name"));

            com.intechcore.polarion.extension.github.rest.model.ProjectItems items =
                    new ImportInternalController(polarionService, repositorySettings, importService).getItems("elibrary", false);

            assertThat(items.getEntries()).extracting(com.intechcore.polarion.extension.github.service.ImportEntry::getNumber).containsExactly(7L);
            verify(importService, never()).refresh(any());

            new ImportInternalController(polarionService, repositorySettings, importService).getItems("elibrary", true);

            verify(importService).refresh(tool);
            verify(importService).refresh(broken);
            assertThat(items.getRepositories()).containsExactly(
                    new com.intechcore.polarion.extension.github.rest.model.RepositoryState("tool", "acme/tool", "2026-10-03T08:00:00Z", null),
                    new com.intechcore.polarion.extension.github.rest.model.RepositoryState("broken", "acme/broken", null, "Discussions are turned off in the repository acme/broken"),
                    new com.intechcore.polarion.extension.github.rest.model.RepositoryState("draft", "", null, "The repository must be given as owner/name"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsTheItemsPrivilegedThroughTheTokenEndpoint() {
        when(polarionService.callPrivileged(any(Callable.class))).thenAnswer(invocation -> ((Callable<Object>) invocation.getArgument(0)).call());
        when(repositorySettings.readNames("project/elibrary/")).thenReturn(List.of());

        assertThat(new ImportApiController(polarionService, repositorySettings, importService).getItems("elibrary", true).getEntries()).isEmpty();
        verify(polarionService).callPrivileged(any(Callable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updatesTheWorkItemsOfTheNamedUrls() {
        List<String> urls = List.of("https://github.com/acme/tool/issues/7");
        when(importService.updateRepository("elibrary", settings, urls)).thenReturn(result);
        when(polarionService.callPrivileged(any(Callable.class))).thenAnswer(invocation -> ((Callable<Object>) invocation.getArgument(0)).call());

        assertThat(new ImportInternalController(polarionService, repositorySettings, importService)
                .updateRepository("elibrary", "tool", new ImportRequest(urls))).isSameAs(result);
        assertThat(new ImportApiController(polarionService, repositorySettings, importService)
                .updateRepository("elibrary", "tool", new ImportRequest(urls))).isSameAs(result);
    }

    @Test
    void refusesAnUpdateWithoutUrls() {
        ImportInternalController controller = new ImportInternalController(polarionService, repositorySettings, importService);

        for (ImportRequest request : new ImportRequest[]{null, new ImportRequest(null), new ImportRequest(List.of())}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.updateRepository("elibrary", "tool", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("GitHub URLs");
        }
        verify(importService, never()).updateRepository(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void runsTheTokenEndpointPrivileged() {
        when(importService.importRepository("elibrary", settings, false, null)).thenReturn(result);
        when(polarionService.callPrivileged(any(Callable.class))).thenAnswer(invocation -> ((Callable<ImportResult>) invocation.getArgument(0)).call());

        ImportResult answer = new ImportApiController(polarionService, repositorySettings, importService)
                .importRepository("elibrary", "tool", false, null);

        assertThat(answer).isSameAs(result);
        verify(polarionService).callPrivileged(any(Callable.class));
    }
}
