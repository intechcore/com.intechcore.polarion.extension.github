package com.intechcore.polarion.extension.github.rest.controller;

import ch.sbb.polarion.extension.generic.fields.FieldType;
import ch.sbb.polarion.extension.generic.fields.model.FieldMetadata;
import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.github.rest.model.ProjectField;
import com.intechcore.polarion.extension.github.rest.model.ProjectOption;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ProjectControllerTest {

    private PolarionService polarionService;
    private ITrackerProject project;
    private IContextId contextId;

    @BeforeEach
    void setUp() {
        polarionService = mock(PolarionService.class);
        project = mock(ITrackerProject.class);
        contextId = mock(IContextId.class);
        when(polarionService.getTrackerProject("elibrary")).thenReturn(project);
        when(project.getContextId()).thenReturn(contextId);
    }

    private static FieldMetadata field(String id, String label, boolean custom, FieldType type) {
        return FieldMetadata.builder().id(id).label(label).custom(custom).type(type.getType()).build();
    }

    @Test
    void listsTheWorkItemTypes() {
        IEnumeration<ITypeOpt> types = mock(IEnumeration.class);
        ITypeOpt task = mock(ITypeOpt.class);
        when(task.getId()).thenReturn("task");
        when(task.getName()).thenReturn("Task");
        when(types.getAllOptions()).thenReturn(List.of(task));
        when(project.getWorkItemTypeEnum()).thenReturn(types);

        assertThat(new ProjectInternalController(polarionService).getWorkItemTypes("elibrary"))
                .containsExactly(new ProjectOption("task", "Task"));
    }

    @Test
    void listsTheLinkRoles() {
        IEnumeration<ILinkRoleOpt> roles = mock(IEnumeration.class);
        ILinkRoleOpt parent = mock(ILinkRoleOpt.class);
        when(parent.getId()).thenReturn("parent");
        when(parent.getName()).thenReturn("has parent");
        when(roles.getAllOptions()).thenReturn(List.of(parent));
        when(project.getWorkItemLinkRoleEnum()).thenReturn(roles);

        assertThat(new ProjectInternalController(polarionService).getLinkRoles("elibrary"))
                .containsExactly(new ProjectOption("parent", "has parent"));
    }

    @Test
    void listsTheFieldsThatTakeAValueAndMarksTheOnesThatCanKeepAUrl() {
        FieldMetadata readOnly = field("created", "created", false, FieldType.STRING);
        readOnly.setReadOnly(true);
        FieldMetadata multi = field("tags", "Tags", true, FieldType.STRING);
        multi.setMulti(true);
        // What the settings cannot fill: structures, lists, and the fields the import fills itself.
        when(polarionService.getGeneralFields("WorkItem", contextId, "task")).thenReturn(Set.of(
                field("severity", "severity", false, FieldType.ENUM),
                field("assignee", "assignee", false, FieldType.LIST),
                field("categories", "categories", false, FieldType.LIST),
                field("approvals", "approvals", false, FieldType.UNKNOWN),
                field("title", "title", false, FieldType.STRING),
                field("description", "description", false, FieldType.TEXT),
                field("type", "type", false, FieldType.ENUM),
                readOnly));
        when(polarionService.getCustomFields("WorkItem", contextId, null))
                .thenReturn(Set.of(field("githubUrl", "GitHub URL", true, FieldType.STRING), multi));
        when(polarionService.getCustomFields("WorkItem", contextId, "task"))
                .thenReturn(Set.of(field("githubUrl", "GitHub URL of a task", true, FieldType.STRING), field("estimate", "Estimate", true, FieldType.FLOAT)));

        assertThat(new ProjectInternalController(polarionService).getFields("elibrary", "task")).containsExactly(
                new ProjectField("assignee", "assignee", false, false),
                new ProjectField("estimate", "Estimate", true, false),
                new ProjectField("githubUrl", "GitHub URL of a task", true, true),
                new ProjectField("severity", "severity", false, false));
    }

    @Test
    void runsTheTokenEndpointsPrivileged() {
        when(polarionService.callPrivileged(any(Callable.class))).thenAnswer(invocation -> ((Callable<Object>) invocation.getArgument(0)).call());
        IEnumeration<ITypeOpt> types = mock(IEnumeration.class);
        IEnumeration<ILinkRoleOpt> roles = mock(IEnumeration.class);
        when(project.getWorkItemTypeEnum()).thenReturn(types);
        when(project.getWorkItemLinkRoleEnum()).thenReturn(roles);
        ProjectApiController controller = new ProjectApiController(polarionService);

        assertThat(controller.getWorkItemTypes("elibrary")).isEmpty();
        assertThat(controller.getLinkRoles("elibrary")).isEmpty();
        assertThat(controller.getFields("elibrary", "task")).isEmpty();
        verify(polarionService, times(3)).callPrivileged(any(Callable.class));
    }

    /** The recipients of notifications: the enabled users, by name, and by ID when one has no name. */
    @Test
    @SuppressWarnings("unchecked")
    void listsTheEnabledUsersByName() {
        com.polarion.alm.projects.IProjectService projectService = mock(com.polarion.alm.projects.IProjectService.class);
        when(polarionService.getProjectService()).thenReturn(projectService);
        com.polarion.alm.projects.model.IUser bob = mock(com.polarion.alm.projects.model.IUser.class);
        when(bob.getId()).thenReturn("bob");
        when(bob.getName()).thenReturn("bob Builder");
        com.polarion.alm.projects.model.IUser alice = mock(com.polarion.alm.projects.model.IUser.class);
        when(alice.getId()).thenReturn("alice");
        com.polarion.alm.projects.model.IUser gone = mock(com.polarion.alm.projects.model.IUser.class);
        when(gone.isDisabled()).thenReturn(true);
        com.polarion.platform.persistence.model.IPObjectList users = mock(com.polarion.platform.persistence.model.IPObjectList.class);
        when(users.iterator()).thenAnswer(invocation -> java.util.List.of(bob, alice, gone).iterator());
        when(projectService.getUsers()).thenReturn(users);
        when(polarionService.callPrivileged(org.mockito.ArgumentMatchers.any(java.util.concurrent.Callable.class)))
                .thenAnswer(invocation -> ((java.util.concurrent.Callable<Object>) invocation.getArgument(0)).call());

        assertThat(new ProjectInternalController(polarionService).getUsers()).containsExactly(
                new com.intechcore.polarion.extension.github.rest.model.ProjectOption("alice", "alice"),
                new com.intechcore.polarion.extension.github.rest.model.ProjectOption("bob", "bob Builder"));
        assertThat(new ProjectApiController(polarionService).getUsers()).hasSize(2);
    }
}
