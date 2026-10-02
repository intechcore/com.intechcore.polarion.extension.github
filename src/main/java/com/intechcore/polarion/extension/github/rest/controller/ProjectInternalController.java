package com.intechcore.polarion.extension.github.rest.controller;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.github.rest.model.ProjectField;
import com.intechcore.polarion.extension.github.rest.model.ProjectOption;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.subterra.base.data.identification.IContextId;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the settings page offers to choose from: the work item types, link roles and fields of a project.
 */
@Tag(name = "Project options")
@Hidden
@Path("/internal")
@Singleton
public class ProjectInternalController {

    protected final PolarionService polarionService;

    public ProjectInternalController() {
        this(new PolarionService());
    }

    public ProjectInternalController(PolarionService polarionService) {
        this.polarionService = polarionService;
    }

    @Operation(summary = "Returns the work item types of a project")
    @GET
    @Path("/projects/{projectId}/workitem-types")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProjectOption> getWorkItemTypes(@PathParam("projectId") String projectId) {
        return polarionService.getTrackerProject(projectId).getWorkItemTypeEnum().getAllOptions().stream()
                .map(option -> new ProjectOption(option.getId(), option.getName()))
                .toList();
    }

    @Operation(summary = "Returns the work item link roles of a project")
    @GET
    @Path("/projects/{projectId}/link-roles")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProjectOption> getLinkRoles(@PathParam("projectId") String projectId) {
        return polarionService.getTrackerProject(projectId).getWorkItemLinkRoleEnum().getAllOptions().stream()
                .map(option -> new ProjectOption(option.getId(), option.getName()))
                .toList();
    }

    @Operation(summary = "Returns the fields of a work item type of a project")
    @GET
    @Path("/projects/{projectId}/workitem-types/{workItemType}/fields")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProjectField> getFields(@PathParam("projectId") String projectId, @PathParam("workItemType") String workItemType) {
        ITrackerProject project = polarionService.getTrackerProject(projectId);
        IContextId contextId = project.getContextId();

        // A custom field of the type replaces one defined for all types, and both replace nothing built in.
        Map<String, FieldMetadata> fields = new LinkedHashMap<>();
        polarionService.getGeneralFields(IWorkItem.PROTO, contextId, workItemType).forEach(field -> fields.put(field.getId(), field));
        polarionService.getCustomFields(IWorkItem.PROTO, contextId, null).forEach(field -> fields.put(field.getId(), field));
        polarionService.getCustomFields(IWorkItem.PROTO, contextId, workItemType).forEach(field -> fields.put(field.getId(), field));

        return fields.values().stream()
                .filter(field -> !field.isReadOnly())
                .map(field -> new ProjectField(field.getId(), field.getLabel(), field.isCustom(),
                        field.isCustom() && !field.isMulti() && FieldType.STRING.getType().equals(field.getType())))
                .sorted(Comparator.comparing(ProjectField::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
