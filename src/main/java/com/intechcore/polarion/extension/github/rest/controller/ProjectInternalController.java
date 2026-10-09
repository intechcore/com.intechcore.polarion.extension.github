package com.intechcore.polarion.extension.github.rest.controller;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.github.rest.model.ProjectField;
import com.intechcore.polarion.extension.github.rest.model.ProjectOption;
import com.polarion.alm.projects.model.IUser;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the settings page offers to choose from: the work item types, link roles and fields of a project.
 */
@Tag(name = "Project options")
@Hidden
@Path("/internal")
@Singleton
public class ProjectInternalController {

    private static final Set<String> FILLED_BY_THE_IMPORT = Set.of("title", "description", "type");
    // A list of users, which generic sets from user IDs separated by commas.
    private static final String ASSIGNEE = "assignee";

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

    @Operation(summary = "Returns the enabled Polarion users, the possible recipients of notifications")
    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProjectOption> getUsers() {
        List<ProjectOption> users = new ArrayList<>();
        for (IUser user : polarionService.getProjectService().getUsers()) {
            if (!user.isDisabled()) {
                users.add(new ProjectOption(user.getId(), user.getName() == null ? user.getId() : user.getName()));
            }
        }
        users.sort(Comparator.comparing(ProjectOption::name, String.CASE_INSENSITIVE_ORDER));
        return users;
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

    /**
     * Whether the settings can give the field a value. The value is one string, so structures such
     * as approvals, attachments or comments are out, and so are the fields the import fills itself.
     */
    private static boolean takesAValue(FieldMetadata field) {
        if (field.isReadOnly() || FILLED_BY_THE_IMPORT.contains(field.getId())) {
            return false;
        }
        if (ASSIGNEE.equals(field.getId())) {
            return true;
        }
        FieldType type = FieldType.recognize(field.getType());
        if (field.isMulti() || type == FieldType.LIST) {
            // Of the fields with several values, generic sets the enumerations from "a,b".
            return field.getOptions() != null;
        }
        return type != FieldType.UNKNOWN;
    }

    /** The enabled users of the project, the assignees generic accepts, by name. */
    private List<ProjectField.FieldOption> assignees(ITrackerProject project) {
        return polarionService.getProjectService().getProjectUsers(project).stream()
                .filter(user -> !user.isDisabled())
                .map(user -> new ProjectField.FieldOption(user.getId(), user.getName() == null ? user.getId() : user.getName(), null))
                .sorted(Comparator.comparing(ProjectField.FieldOption::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** The options of an enumeration field, by name, or null for any other field. */
    private static @Nullable List<ProjectField.FieldOption> options(FieldMetadata field) {
        return field.getOptions() == null ? null : field.getOptions().stream()
                .map(option -> new ProjectField.FieldOption(option.getKey(), option.getName() == null ? option.getKey() : option.getName(), option.getIconUrl()))
                .sorted(Comparator.comparing(ProjectField.FieldOption::name, String.CASE_INSENSITIVE_ORDER))
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
                .filter(ProjectInternalController::takesAValue)
                .map(field -> new ProjectField(field.getId(), field.getLabel(), field.isCustom(),
                        field.isCustom() && !field.isMulti() && FieldType.STRING.getType().equals(field.getType()),
                        field.isMulti() || FieldType.recognize(field.getType()) == FieldType.LIST,
                        ASSIGNEE.equals(field.getId()) ? assignees(project) : options(field)))
                .sorted(Comparator.comparing(ProjectField::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
