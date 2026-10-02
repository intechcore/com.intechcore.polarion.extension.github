package com.intechcore.polarion.extension.github.rest.controller;

import ch.sbb.polarion.extension.generic.rest.filter.Secured;
import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.github.rest.model.ImportRequest;
import com.intechcore.polarion.extension.github.service.ImportResult;
import com.intechcore.polarion.extension.github.service.ImportService;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;

@Secured
@Path("/api")
@Singleton
public class ImportApiController extends ImportInternalController {

    public ImportApiController() {
        super();
    }

    public ImportApiController(PolarionService polarionService, RepositorySettings repositorySettings, ImportService importService) {
        super(polarionService, repositorySettings, importService);
    }

    @Override
    public ImportResult importRepository(String projectId, String name, boolean dryRun, ImportRequest request) {
        return polarionService.callPrivileged(() -> super.importRepository(projectId, name, dryRun, request));
    }
}
