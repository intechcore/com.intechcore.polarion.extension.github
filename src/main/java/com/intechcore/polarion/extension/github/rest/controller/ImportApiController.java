package com.intechcore.polarion.extension.github.rest.controller;

import ch.sbb.polarion.extension.generic.rest.filter.Secured;
import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.github.rest.model.HideRequest;
import com.intechcore.polarion.extension.github.rest.model.ImportRequest;
import com.intechcore.polarion.extension.github.rest.model.ProjectItems;
import com.intechcore.polarion.extension.github.service.ImportResult;
import com.intechcore.polarion.extension.github.service.ImportService;
import com.intechcore.polarion.extension.github.settings.HiddenItems;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;

import java.util.List;
import java.util.Set;

@Secured
@Path("/api")
@Singleton
public class ImportApiController extends ImportInternalController {

    public ImportApiController() {
        super();
    }

    public ImportApiController(PolarionService polarionService, RepositorySettings repositorySettings, ImportService importService,
                               HiddenItems hiddenItems) {
        super(polarionService, repositorySettings, importService, hiddenItems);
    }

    @Override
    public ProjectItems getItems(String projectId, boolean refresh, List<String> settings, String githubToken) {
        return polarionService.callPrivileged(() -> super.getItems(projectId, refresh, settings, githubToken));
    }

    @Override
    public Set<String> hideItems(String projectId, HideRequest request) {
        return polarionService.callPrivileged(() -> super.hideItems(projectId, request));
    }

    @Override
    public ImportResult updateRepository(String projectId, String name, ImportRequest request, String githubToken) {
        return polarionService.callPrivileged(() -> super.updateRepository(projectId, name, request, githubToken));
    }

    @Override
    public ImportResult importRepository(String projectId, String name, boolean dryRun, ImportRequest request, String githubToken) {
        return polarionService.callPrivileged(() -> super.importRepository(projectId, name, dryRun, request, githubToken));
    }
}
