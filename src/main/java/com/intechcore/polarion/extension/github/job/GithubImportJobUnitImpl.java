package com.intechcore.polarion.extension.github.job;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.intechcore.polarion.extension.github.client.GithubRateLimitException;
import com.intechcore.polarion.extension.github.service.ImportEntry;
import com.intechcore.polarion.extension.github.service.ImportResult;
import com.intechcore.polarion.extension.github.service.ImportService;
import com.intechcore.polarion.extension.github.service.ImportStatus;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import com.intechcore.polarion.extension.github.settings.RepositorySettingsModel;
import com.polarion.alm.projects.model.IProject;
import com.polarion.platform.context.IContext;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.platform.jobs.spi.AbstractJobUnit;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

/**
 * Imports the configured repositories of the project the job is scoped to.
 */
public class GithubImportJobUnitImpl extends AbstractJobUnit implements GithubImportJobUnit {

    private final PolarionService polarionService;
    private final RepositorySettings repositorySettings;
    private final ImportService importService;

    @Setter
    private String repositories;
    @Setter
    private Boolean dryRun;

    public GithubImportJobUnitImpl(String name, IJobUnitFactory creator) {
        this(name, creator, new PolarionService(), new RepositorySettings(), new ImportService());
    }

    public GithubImportJobUnitImpl(String name, IJobUnitFactory creator, PolarionService polarionService,
                                   RepositorySettings repositorySettings, ImportService importService) {
        super(name, creator);
        this.polarionService = polarionService;
        this.repositorySettings = repositorySettings;
        this.importService = importService;
    }

    @Override
    protected IJobStatus runInternal(IProgressMonitor progress) {
        progress.beginTask(getName(), IProgressMonitor.UNKNOWN);
        try {
            IContext scope = getScope();
            IProject project = scope == null ? null : polarionService.getProjectService().getProjectForContextId(scope.getId());
            if (project == null) {
                return getStatusFailed("The job needs the scope of a project, for example scope=\"project:myproject\"", null);
            }
            int failures = importRepositories(project.getId());
            return failures == 0
                    ? getStatusOK("The import finished")
                    : getStatusFailed("The import finished with " + failures + " failure(s), see the job log", null);
        } catch (GithubRateLimitException e) {
            // Every further request would fail the same way, so the job stops here.
            return getStatusFailed(e.getMessage(), e);
        } finally {
            progress.done();
        }
    }

    private int importRepositories(String projectId) {
        String scope = ScopeUtils.getScopeFromProject(projectId);
        boolean named = repositories != null && !repositories.isBlank();
        List<String> names = named
                ? Arrays.stream(repositories.split(",")).map(String::trim).filter(name -> !name.isEmpty()).toList()
                : repositorySettings.readNames(scope).stream().map(SettingName::getName).toList();

        int failures = 0;
        for (String name : names) {
            try {
                RepositorySettingsModel settings = repositorySettings.read(scope, SettingId.fromName(name), null);
                if (!named && !settings.isEnabled()) {
                    getLogger().info("Repository setting '" + name + "' is disabled, skipped");
                    continue;
                }
                failures += log(name, importService.importRepository(projectId, settings, Boolean.TRUE.equals(dryRun), null));
            } catch (GithubRateLimitException e) {
                throw e;
            } catch (RuntimeException e) {
                failures++;
                getLogger().error("Repository setting '" + name + "' failed: " + e.getMessage());
            }
        }
        return failures;
    }

    private int log(String name, ImportResult result) {
        for (ImportEntry entry : result.getEntries()) {
            if (entry.getStatus() == ImportStatus.FAILED) {
                getLogger().error(entry.getUrl() + " failed: " + entry.getMessage());
            } else if (entry.getStatus() == ImportStatus.NEW || entry.getStatus() == ImportStatus.CREATED) {
                getLogger().info(entry.getUrl() + " " + entry.getStatus() + (entry.getWorkItemId() == null ? "" : " " + entry.getWorkItemId()));
            }
        }
        getLogger().info("Repository setting '%s' (%s)%s: %d created, %d new, %d existing, %d left out, %d failed".formatted(
                name, result.getRepository(), result.isDryRun() ? ", dry run" : "",
                result.count(ImportStatus.CREATED), result.count(ImportStatus.NEW),
                result.count(ImportStatus.EXISTS), result.count(ImportStatus.SKIPPED), result.count(ImportStatus.FAILED)));
        return (int) result.count(ImportStatus.FAILED);
    }
}
