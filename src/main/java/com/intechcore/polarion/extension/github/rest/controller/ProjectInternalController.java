package com.intechcore.polarion.extension.github.rest.controller;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.fields.model.Option;
import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.github.rest.model.ProjectField;
import com.intechcore.polarion.extension.github.rest.model.ProjectOption;
import com.intechcore.polarion.extension.github.rest.model.WorkItemRef;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.tracker.model.ICategory;
import com.polarion.alm.tracker.model.IStatusOpt;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.subterra.base.data.identification.IContextId;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What the settings page offers to choose from: the work item types, link roles and fields of a project.
 */
@Tag(name = "Project options")
@Hidden
@Path("/internal")
@Singleton
public class ProjectInternalController {

    private static final Set<String> FILLED_BY_THE_IMPORT = Set.of("title", "description", "type");
    // A picker shows the first ones; a person types more to find another.
    private static final int SEARCH_LIMIT = 20;
    private static final Pattern PROJECT_ID = Pattern.compile("[A-Za-z0-9_.\\-]+");
    private static final String LUCENE_SYNTAX = "+-&|!(){}[]^\"~*?:\\/";
    // A list of users, which generic sets from user IDs separated by commas.
    private static final String ASSIGNEE = "assignee";
    // A list of references to the categories of the project, which the import sets itself.
    private static final String CATEGORIES = "categories";

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

    @Operation(summary = "Finds work items of a project by the start of their ID or the words of their title, or one by its exact ID")
    @GET
    @Path("/projects/{projectId}/workitems")
    @Produces(MediaType.APPLICATION_JSON)
    public List<WorkItemRef> findWorkItems(@PathParam("projectId") String projectId,
                                           @Parameter(description = "The start of an ID, or words that start words of the title")
                                           @QueryParam("query") String query,
                                           @Parameter(description = "An exact ID. It replaces the query.")
                                           @QueryParam("id") String id) {
        String lucene = searchQuery(projectId, query, id);
        if (lucene == null) {
            return List.of();
        }
        List<WorkItemRef> found = new ArrayList<>();
        for (Object item : polarionService.getTrackerService().getDataService().searchInstances(IWorkItem.PROTO, lucene, "id", SEARCH_LIMIT)) {
            if (item instanceof IWorkItem workItem) {
                ITypeOpt type = workItem.getType();
                IStatusOpt status = workItem.getStatus();
                found.add(new WorkItemRef(workItem.getId(), workItem.getTitle(), type == null ? null : type.getName(),
                        type == null ? null : type.getProperty(IEnumOption.PROPERTY_KEY_ICON_URL), status == null ? null : status.getName()));
            }
        }
        return found;
    }

    /**
     * The Lucene query of a search in one project, or null when there is nothing to look for. The words
     * of a person are escaped, so they never change the query; the project ID must look like one.
     */
    static @Nullable String searchQuery(String projectId, @Nullable String query, @Nullable String id) {
        if (projectId == null || !PROJECT_ID.matcher(projectId).matches()) {
            throw new IllegalArgumentException("The project ID is not valid");
        }
        String inProject = "project.id:" + projectId + " AND ";
        if (id != null && !id.isBlank()) {
            return inProject + "id:\"" + id.trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) {
            return null;
        }
        String title = Arrays.stream(text.split("\\s+"))
                .map(word -> escape(word.toLowerCase(Locale.ROOT)) + "*")
                .collect(Collectors.joining(" AND "));
        return inProject + "(id:" + escape(text) + "* OR title:(" + title + "))";
    }

    /** Escapes the characters Lucene reads as syntax. */
    private static String escape(String text) {
        StringBuilder escaped = new StringBuilder();
        for (char character : text.toCharArray()) {
            if (LUCENE_SYNTAX.indexOf(character) >= 0) {
                escaped.append('\\');
            }
            if (!Character.isWhitespace(character)) {
                escaped.append(character);
            }
        }
        return escaped.toString();
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
        if (ASSIGNEE.equals(field.getId()) || CATEGORIES.equals(field.getId())) {
            return true;
        }
        FieldType type = FieldType.recognize(field.getType());
        if (type == FieldType.USER) {
            // Generic cannot set a reference to one user from a string. Custom user fields are enumerations.
            return false;
        }
        if (field.isMulti() || type == FieldType.LIST) {
            // Of the fields with several values, generic sets the enumerations from "a,b".
            return field.getOptions() != null;
        }
        return type != FieldType.UNKNOWN;
    }

    /** The options of a field: users for the assignee, the categories of the project, or those of an enumeration. */
    private @Nullable List<ProjectField.FieldOption> choices(FieldMetadata field, ITrackerProject project, String workItemType) {
        return switch (field.getId()) {
            case ASSIGNEE -> assignees(project);
            case CATEGORIES -> categories(project);
            default -> options(field, project.getContextId(), workItemType);
        };
    }

    /** The categories of the project, by name. */
    private static List<ProjectField.FieldOption> categories(ITrackerProject project) {
        List<ProjectField.FieldOption> categories = new ArrayList<>();
        for (Object category : project.getCategories()) {
            if (category instanceof ICategory known) {
                categories.add(new ProjectField.FieldOption(known.getId(), known.getName() == null ? known.getId() : known.getName(), null));
            }
        }
        categories.sort(Comparator.comparing(ProjectField.FieldOption::name, String.CASE_INSENSITIVE_ORDER));
        return categories;
    }

    /** The enabled users of the project, the assignees generic accepts, by name. */
    private List<ProjectField.FieldOption> assignees(ITrackerProject project) {
        return polarionService.getProjectService().getProjectUsers(project).stream()
                .filter(user -> !user.isDisabled())
                .map(user -> new ProjectField.FieldOption(user.getId(), user.getName() == null ? user.getId() : user.getName(), null))
                .sorted(Comparator.comparing(ProjectField.FieldOption::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * The options of an enumeration field for the work item type, by name, or null for any other field.
     * A custom field of all types comes with the options of all types, so they are read again.
     */
    private @Nullable List<ProjectField.FieldOption> options(FieldMetadata field, IContextId contextId, String workItemType) {
        if (field.getOptions() == null) {
            return null;
        }
        Set<Option> options = polarionService.getOptionsForEnum(field.getType(), contextId, workItemType);
        return (options == null ? field.getOptions() : options).stream()
                .map(option -> new ProjectField.FieldOption(option.getKey(), option.getName() == null ? option.getKey() : option.getName(), option.getIconUrl()))
                .sorted(Comparator.comparing(ProjectField.FieldOption::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** The kind of value of a field, for the control of the settings page. */
    private static String kind(FieldMetadata field) {
        return switch (FieldType.recognize(field.getType())) {
            case TEXT -> "text";
            case RICH -> "rich";
            case INTEGER -> "integer";
            case FLOAT -> "float";
            case CURRENCY -> "currency";
            case BOOLEAN -> "boolean";
            case DATE_ONLY -> "date";
            case TIME_ONLY -> "time";
            case DATE -> "dateTime";
            case DURATION -> "duration";
            case ENUM, LIST, USER -> "enum";
            default -> "string";
        };
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
                        choices(field, project, workItemType), kind(field)))
                .sorted(Comparator.comparing(ProjectField::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
