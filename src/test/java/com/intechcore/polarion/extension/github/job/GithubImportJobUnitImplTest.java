package com.intechcore.polarion.extension.github.job;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import com.intechcore.polarion.extension.github.client.GithubRateLimitException;
import com.intechcore.polarion.extension.github.service.ImportEntry;
import com.intechcore.polarion.extension.github.service.ImportResult;
import com.intechcore.polarion.extension.github.service.ImportService;
import com.intechcore.polarion.extension.github.service.ImportStatus;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import com.intechcore.polarion.extension.github.settings.RepositorySettingsModel;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IProject;
import com.polarion.platform.context.IContext;
import com.polarion.platform.jobs.IJob;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.ILogger;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubImportJobUnitImplTest {

    private static final String SCOPE = "project/elibrary/";

    private RepositorySettings repositorySettings;
    private ImportService importService;
    private ILogger logger;
    private IProgressMonitor progress;
    private GithubImportJobUnitImpl job;

    @BeforeEach
    void setUp() {
        PolarionService polarionService = mock(PolarionService.class);
        IProjectService projectService = mock(IProjectService.class);
        IProject project = mock(IProject.class);
        IContext scope = mock(IContext.class);
        IContextId contextId = mock(IContextId.class);
        when(scope.getId()).thenReturn(contextId);
        when(polarionService.getProjectService()).thenReturn(projectService);
        when(projectService.getProjectForContextId(contextId)).thenReturn(project);
        when(project.getId()).thenReturn("elibrary");

        repositorySettings = mock(RepositorySettings.class);
        importService = mock(ImportService.class);
        logger = mock(ILogger.class);
        progress = mock(IProgressMonitor.class);

        job = new GithubImportJobUnitImpl("import", mock(IJobUnitFactory.class), polarionService, repositorySettings, importService);
        job.setScope(scope);
        job.setLogger(logger);
        // The status of a job unit is built from its job.
        job.setJob(mock(IJob.class));
    }

    private RepositorySettingsModel setting(String name, boolean enabled) {
        RepositorySettingsModel settings = RepositorySettingsModel.builder().repository("acme/" + name).enabled(enabled).build();
        when(repositorySettings.read(eq(SCOPE), argThat(id -> id != null && name.equals(id.getIdentifier())), isNull())).thenReturn(settings);
        return settings;
    }

    private void names(String... names) {
        when(repositorySettings.readNames(SCOPE)).thenReturn(
                List.of(names).stream().map(name -> SettingName.builder().id(name).name(name).scope(SCOPE).build()).toList());
    }

    private static ImportResult result(String repository, boolean dryRun, ImportStatus... statuses) {
        ImportResult result = new ImportResult(repository, dryRun);
        for (ImportStatus status : statuses) {
            result.getEntries().add(ImportEntry.builder().url("https://github.com/" + repository + "/issues/1").status(status)
                    .workItemId(status == ImportStatus.CREATED ? "EL-1" : null).message(status == ImportStatus.FAILED ? "No" : null).build());
        }
        return result;
    }

    @Test
    void importsEveryEnabledRepositoryOfTheProject() {
        names("tool", "off");
        RepositorySettingsModel tool = setting("tool", true);
        RepositorySettingsModel off = setting("off", false);
        when(importService.importRepository("elibrary", tool, false, null))
                .thenReturn(result("acme/tool", false, ImportStatus.CREATED, ImportStatus.EXISTS));

        IJobStatus status = job.runInternal(progress);

        assertThat(status.getType()).isEqualTo(IJobStatus.JobStatusType.STATUS_TYPE_OK);
        verify(importService, never()).importRepository(anyString(), eq(off), anyBoolean(), any());
        verify(logger).info("https://github.com/acme/tool/issues/1 CREATED EL-1");
        verify(logger).info("Repository setting 'tool' (acme/tool): 1 created, 0 new, 1 existing, 0 left out, 0 failed");
        verify(logger).info(contains("'off' is disabled"));
        verify(progress).done();
    }

    @Test
    void importsTheNamedRepositoriesEvenWhenDisabled() {
        RepositorySettingsModel off = setting("off", false);
        RepositorySettingsModel tool = setting("tool", true);
        when(importService.importRepository(eq("elibrary"), any(), eq(true), isNull()))
                .thenAnswer(invocation -> result(((RepositorySettingsModel) invocation.getArgument(1)).getRepository(), true, ImportStatus.NEW));
        job.setRepositories(" off, ,tool ");
        job.setDryRun(true);

        IJobStatus status = job.runInternal(progress);

        assertThat(status.getType()).isEqualTo(IJobStatus.JobStatusType.STATUS_TYPE_OK);
        verify(importService).importRepository("elibrary", off, true, null);
        verify(importService).importRepository("elibrary", tool, true, null);
        verify(repositorySettings, never()).readNames(anyString());
        verify(logger).info("Repository setting 'off' (acme/off), dry run: 0 created, 1 new, 0 existing, 0 left out, 0 failed");
    }

    @Test
    void failsWhenAnItemFailedAndStillImportsTheRest() {
        names("bad", "tool");
        RepositorySettingsModel bad = setting("bad", true);
        RepositorySettingsModel tool = setting("tool", true);
        when(importService.importRepository("elibrary", bad, false, null)).thenReturn(result("acme/bad", false, ImportStatus.FAILED));
        when(importService.importRepository("elibrary", tool, false, null)).thenReturn(result("acme/tool", false, ImportStatus.CREATED));

        IJobStatus status = job.runInternal(progress);

        assertThat(status.getType()).isEqualTo(IJobStatus.JobStatusType.STATUS_TYPE_FAILED);
        assertThat(status.getMessage()).contains("1 failure");
        verify(logger).error("https://github.com/acme/bad/issues/1 failed: No");
        verify(importService).importRepository("elibrary", tool, false, null);
    }

    @Test
    void failsWhenARepositoryFailedAndStillImportsTheRest() {
        names("bad", "tool");
        RepositorySettingsModel bad = setting("bad", true);
        RepositorySettingsModel tool = setting("tool", true);
        when(importService.importRepository("elibrary", bad, false, null)).thenThrow(new IllegalArgumentException("no work item type"));
        when(importService.importRepository("elibrary", tool, false, null)).thenReturn(result("acme/tool", false));

        IJobStatus status = job.runInternal(progress);

        assertThat(status.getType()).isEqualTo(IJobStatus.JobStatusType.STATUS_TYPE_FAILED);
        verify(logger).error("Repository setting 'bad' failed: no work item type");
        verify(importService).importRepository("elibrary", tool, false, null);
    }

    @Test
    void stopsAtAnExhaustedRateLimit() {
        names("first", "second");
        RepositorySettingsModel first = setting("first", true);
        RepositorySettingsModel second = setting("second", true);
        when(importService.importRepository("elibrary", first, false, null)).thenThrow(new GithubRateLimitException(Instant.ofEpochSecond(1790948455L)));

        IJobStatus status = job.runInternal(progress);

        assertThat(status.getType()).isEqualTo(IJobStatus.JobStatusType.STATUS_TYPE_FAILED);
        assertThat(status.getMessage()).contains("rate limit");
        verify(importService, never()).importRepository("elibrary", second, false, null);
        verify(progress).done();
    }

    @Test
    void needsTheScopeOfAProject() {
        job.setScope(null);

        IJobStatus status = job.runInternal(progress);

        assertThat(status.getType()).isEqualTo(IJobStatus.JobStatusType.STATUS_TYPE_FAILED);
        assertThat(status.getMessage()).contains("scope of a project");
        verify(importService, never()).importRepository(anyString(), any(), anyBoolean(), any());
    }

    @Test
    void needsAScopeThatIsAProject() {
        IContext global = mock(IContext.class);
        job.setScope(global);

        assertThat(job.runInternal(progress).getType()).isEqualTo(IJobStatus.JobStatusType.STATUS_TYPE_FAILED);
    }
}
