package com.intechcore.polarion.extension.github.integration;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IHyperlinkRoleOpt;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.tracker.model.IHyperlinkStruct;
import com.polarion.alm.tracker.model.IStatusOpt;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.ILinkedWorkItemStruct;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.types.Text;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.model.IPObject;
import com.polarion.platform.persistence.model.IPObjectList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The part of Polarion the import talks to, kept in memory: one project with its work item types and
 * link roles, the work items saved in it, and the SQL search the import runs over them.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class FakePolarion {

    static final String PROJECT = "elibrary";

    /** What a saved work item holds. */
    static final class WorkItem {
        String id;
        String type;
        String title;
        Text description;
        final List<String> hyperlinks = new ArrayList<>();
        final Map<String, Object> fields = new LinkedHashMap<>();
        final List<String> links = new ArrayList<>();
        int saves;
    }

    // The two search shapes the import sends, read the way the database would.
    private static final Pattern HYPERLINK_QUERY = Pattern.compile(
            "SQL:\\(select WI\\.C_URI from WORKITEM WI inner join PROJECT P on P\\.C_URI = WI\\.FK_URI_PROJECT"
                    + " inner join STRUCT_WORKITEM_HYPERLINKS HL on HL\\.FK_URI_P_WORKITEM = WI\\.C_URI"
                    + " where P\\.C_ID = '([^']*)' and HL\\.C_URL like '([^']*)%'\\)");
    private static final Pattern CUSTOM_FIELD_QUERY = Pattern.compile(
            "SQL:\\(select WI\\.C_URI from WORKITEM WI inner join PROJECT P on P\\.C_URI = WI\\.FK_URI_PROJECT"
                    + " inner join CF_WORKITEM CF on CF\\.FK_URI_WORKITEM = WI\\.C_URI"
                    + " where P\\.C_ID = '([^']*)' and CF\\.C_NAME = '([^']*)' and CF\\.C_STRING_VALUE like '([^']*)%'\\)");

    final PolarionService polarionService = mock(PolarionService.class);
    final List<WorkItem> saved = new ArrayList<>();
    final List<String> queries = new ArrayList<>();
    private final Map<IWorkItem, WorkItem> states = new LinkedHashMap<>();
    // The work items a search returned, to answer reads of their fields.
    private final Map<IWorkItem, WorkItem> read = new LinkedHashMap<>();

    FakePolarion(Set<String> workItemTypes, Set<String> linkRoles) {
        ITrackerService trackerService = mock(ITrackerService.class);
        ITrackerProject project = mock(ITrackerProject.class);
        when(polarionService.getTrackerService()).thenReturn(trackerService);
        when(polarionService.getTrackerProject(PROJECT)).thenReturn(project);
        when(polarionService.callPrivileged(any(Callable.class))).thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call());
        when(project.getId()).thenReturn(PROJECT);

        IEnumeration types = mock(IEnumeration.class);
        when(project.getWorkItemTypeEnum()).thenReturn(types);
        when(types.wrapOption(anyString())).thenAnswer(invocation -> option(ITypeOpt.class, invocation.getArgument(0), workItemTypes));
        IEnumeration roles = mock(IEnumeration.class);
        when(project.getWorkItemLinkRoleEnum()).thenReturn(roles);
        // The import passes the work item type, which is an IPObject: both overloads answer the same.
        when(roles.wrapOption(anyString(), any(Object.class))).thenAnswer(invocation -> option(ILinkRoleOpt.class, invocation.getArgument(0), linkRoles));
        when(roles.wrapOption(anyString(), any(IPObject.class))).thenAnswer(invocation -> option(ILinkRoleOpt.class, invocation.getArgument(0), linkRoles));
        IEnumeration hyperlinkRoles = mock(IEnumeration.class);
        when(project.getHyperlinkRoleEnum()).thenReturn(hyperlinkRoles);
        when(hyperlinkRoles.wrapOption(anyString())).thenAnswer(invocation -> option(IHyperlinkRoleOpt.class, invocation.getArgument(0), Set.of("ref_ext")));

        when(trackerService.createWorkItem(project)).thenAnswer(invocation -> newWorkItem());
        when(trackerService.queryWorkItems(anyString(), anyString())).thenAnswer(invocation -> search(invocation.getArgument(0)));
        when(polarionService.getWorkItem(any(), anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(1);
            // A saved work item comes back writable, anything else as an epic to link to.
            WorkItem state = saved.stream().filter(item -> id.equals(item.id)).findFirst().orElse(null);
            if (state != null) {
                return writable(state);
            }
            IWorkItem epic = mock(IWorkItem.class);
            when(epic.getId()).thenReturn(id);
            return epic;
        });
        when(polarionService.getFieldValue(any(IWorkItem.class), anyString())).thenAnswer(invocation -> {
            WorkItem state = read.get(invocation.<IWorkItem>getArgument(0));
            return state == null ? null : state.fields.get(invocation.<String>getArgument(1));
        });
        doAnswer(invocation -> {
            states.get(invocation.<IWorkItem>getArgument(0)).fields.put(invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(polarionService).setFieldValue(any(IWorkItem.class), anyString(), any());
    }

    private static <T extends com.polarion.platform.persistence.IEnumOption> T option(Class<T> type, String id, Set<String> known) {
        T option = mock(type);
        when(option.getId()).thenReturn(id);
        when(option.isPhantom()).thenReturn(!known.contains(id));
        when(option.getProperty(com.polarion.platform.persistence.IEnumOption.PROPERTY_KEY_ICON_URL)).thenReturn("/polarion/icons/" + id + ".gif");
        return option;
    }

    private IWorkItem newWorkItem() {
        return writable(new WorkItem());
    }

    /** A work item the import changes, new or loaded. Its save keeps a new one, and keeps the ID of a loaded one. */
    private IWorkItem writable(WorkItem state) {
        IWorkItem workItem = mock(IWorkItem.class);
        states.put(workItem, state);
        doAnswer(invocation -> state.type = invocation.<ITypeOpt>getArgument(0).getId()).when(workItem).setType(any());
        doAnswer(invocation -> state.title = invocation.getArgument(0)).when(workItem).setTitle(anyString());
        doAnswer(invocation -> state.description = invocation.getArgument(0)).when(workItem).setDescription(any());
        when(workItem.addHyperlink(anyString(), any())).thenAnswer(invocation -> state.hyperlinks.add(invocation.getArgument(0)));
        when(workItem.addLinkedItem(any(), any(), any(), any(Boolean.class))).thenAnswer(invocation ->
                state.links.add(invocation.<ILinkRoleOpt>getArgument(1).getId() + ":" + invocation.<IWorkItem>getArgument(0).getId()));
        when(workItem.getTitle()).thenAnswer(invocation -> state.title);
        when(workItem.getId()).thenAnswer(invocation -> state.id);
        // What the workflow of the type sets on save: the initial status and an assignee.
        IStatusOpt status = mock(IStatusOpt.class);
        when(status.getName()).thenReturn("Open");
        when(workItem.getStatus()).thenReturn(status);
        IUser assignee = mock(IUser.class);
        when(assignee.getName()).thenReturn("Rob Project");
        IPObjectList assignees = mock(IPObjectList.class);
        when(assignees.iterator()).thenAnswer(invocation -> List.of(assignee).iterator());
        when(workItem.getAssignees()).thenReturn(assignees);
        doAnswer(invocation -> {
            if (state.id == null) {
                state.id = "EL-" + (100 + saved.size());
                saved.add(state);
            }
            state.saves++;
            return null;
        }).when(workItem).save();
        return workItem;
    }

    private IPObjectList search(String query) {
        queries.add(query);
        List<IWorkItem> found = new ArrayList<>();
        Matcher hyperlink = HYPERLINK_QUERY.matcher(query);
        Matcher customField = CUSTOM_FIELD_QUERY.matcher(query);
        if (hyperlink.matches()) {
            String project = hyperlink.group(1);
            String prefix = hyperlink.group(2);
            saved.stream()
                    .filter(item -> PROJECT.equals(project) && item.hyperlinks.stream().anyMatch(url -> url.startsWith(prefix)))
                    .forEach(item -> found.add(stored(item)));
        } else if (customField.matches()) {
            String project = customField.group(1);
            String field = customField.group(2);
            String prefix = customField.group(3);
            saved.stream()
                    .filter(item -> PROJECT.equals(project) && item.fields.get(field) instanceof String value && value.startsWith(prefix))
                    .forEach(item -> found.add(stored(item)));
        } else {
            throw new IllegalArgumentException("The database would not understand this query: " + query);
        }
        IPObjectList list = mock(IPObjectList.class);
        when(list.iterator()).thenAnswer(invocation -> found.iterator());
        return list;
    }

    /** A saved work item as a search returns it. */
    private IWorkItem stored(WorkItem state) {
        IWorkItem workItem = mock(IWorkItem.class);
        read.put(workItem, state);
        when(workItem.getId()).thenReturn(state.id);
        when(workItem.getTitle()).thenReturn(state.title);
        when(workItem.getDescription()).thenReturn(state.description);
        List<ILinkedWorkItemStruct> links = new ArrayList<>();
        for (String link : state.links) {
            String[] roleAndId = link.split(":", 2);
            ILinkedWorkItemStruct struct = mock(ILinkedWorkItemStruct.class);
            ILinkRoleOpt role = mock(ILinkRoleOpt.class);
            when(role.getId()).thenReturn(roleAndId[0]);
            IWorkItem target = mock(IWorkItem.class);
            when(target.getId()).thenReturn(roleAndId[1]);
            when(struct.getLinkRole()).thenReturn(role);
            when(struct.getLinkedItem()).thenReturn(target);
            links.add(struct);
        }
        when(workItem.getLinkedWorkItemsStructsDirect()).thenReturn(links);
        List<IHyperlinkStruct> hyperlinks = new ArrayList<>();
        for (String url : state.hyperlinks) {
            IHyperlinkStruct hyperlink = mock(IHyperlinkStruct.class);
            when(hyperlink.getUri()).thenReturn(url);
            hyperlinks.add(hyperlink);
        }
        when(workItem.getHyperlinks()).thenReturn(hyperlinks);
        when(workItem.getCustomField(anyString())).thenAnswer(invocation -> state.fields.get(invocation.<String>getArgument(0)));
        // What Polarion holds of a work item after the import: its type, a status and an assignee.
        ITypeOpt type = mock(ITypeOpt.class);
        when(type.getId()).thenReturn(state.type);
        when(type.getName()).thenReturn(state.type.substring(0, 1).toUpperCase() + state.type.substring(1));
        when(type.getProperty(com.polarion.platform.persistence.IEnumOption.PROPERTY_KEY_ICON_URL)).thenReturn("/polarion/icons/" + state.type + ".gif");
        when(workItem.getType()).thenReturn(type);
        IStatusOpt status = mock(IStatusOpt.class);
        when(status.getName()).thenReturn("Open");
        when(workItem.getStatus()).thenReturn(status);
        IUser assignee = mock(IUser.class);
        when(assignee.getName()).thenReturn("Rob Project");
        IPObjectList assignees = mock(IPObjectList.class);
        when(assignees.iterator()).thenAnswer(invocation -> List.of(assignee).iterator());
        when(workItem.getAssignees()).thenReturn(assignees);
        return workItem;
    }
}
