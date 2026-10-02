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
