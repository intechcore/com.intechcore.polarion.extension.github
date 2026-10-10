package com.intechcore.polarion.extension.github.rest.controller;

import ch.sbb.polarion.extension.generic.rest.filter.Secured;
import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.github.rest.model.ProjectField;
import com.intechcore.polarion.extension.github.rest.model.ProjectOption;
import com.intechcore.polarion.extension.github.rest.model.WorkItemRef;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;

import java.util.List;

@Secured
@Path("/api")
@Singleton
public class ProjectApiController extends ProjectInternalController {

    public ProjectApiController() {
        super();
    }

    public ProjectApiController(PolarionService polarionService) {
        super(polarionService);
    }

    @Override
    public List<ProjectOption> getWorkItemTypes(String projectId) {
        return polarionService.callPrivileged(() -> super.getWorkItemTypes(projectId));
    }

    @Override
    public List<ProjectOption> getUsers() {
        return polarionService.callPrivileged(super::getUsers);
    }

    @Override
    public List<ProjectOption> getLinkRoles(String projectId) {
        return polarionService.callPrivileged(() -> super.getLinkRoles(projectId));
    }

    @Override
    public List<WorkItemRef> findWorkItems(String projectId, String query, String id) {
        return polarionService.callPrivileged(() -> super.findWorkItems(projectId, query, id));
    }

    @Override
    public List<ProjectField> getFields(String projectId, String workItemType) {
        return polarionService.callPrivileged(() -> super.getFields(projectId, workItemType));
    }
}
