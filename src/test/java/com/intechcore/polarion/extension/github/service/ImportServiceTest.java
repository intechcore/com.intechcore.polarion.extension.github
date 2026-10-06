package com.intechcore.polarion.extension.github.service;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.github.client.GithubClient;
import com.intechcore.polarion.extension.github.client.GithubClientException;
import com.intechcore.polarion.extension.github.client.GithubItem;
import com.intechcore.polarion.extension.github.settings.DuplicateKey;
import com.intechcore.polarion.extension.github.settings.ItemRule;
import com.intechcore.polarion.extension.github.settings.ItemSettings;
import com.intechcore.polarion.extension.github.settings.RepositorySettingsModel;
import com.intechcore.polarion.extension.github.settings.RuleMatch;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IHyperlinkRoleOpt;
import com.polarion.alm.tracker.model.IHyperlinkStruct;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.types.Text;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.platform.persistence.model.IPObjectList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class ImportServiceTest {

    private static final String PROJECT = "elibrary";
    private static final String ISSUE_7 = "https://github.com/acme/tool/issues/7";
    private static final String ISSUE_8 = "https://github.com/acme/tool/issues/8";

    private PolarionService polarionService;
    private ITrackerService trackerService;
    private ITrackerProject project;
    private GithubClient githubClient;
    private ITypeOpt type;
    private IHyperlinkRoleOpt hyperlinkRole;
    private final List<IWorkItem> created = new ArrayList<>();
    private final List<String> queries = new ArrayList<>();
    private final List<Object> found = new ArrayList<>();
    private int transactions;
    private ImportService service;

    @BeforeEach
    void setUp() {
        polarionService = mock(PolarionService.class);
        trackerService = mock(ITrackerService.class);
        project = mock(ITrackerProject.class);
        githubClient = mock(GithubClient.class);
        type = mock(ITypeOpt.class);
        hyperlinkRole = mock(IHyperlinkRoleOpt.class);

        when(polarionService.getTrackerService()).thenReturn(trackerService);
        when(polarionService.getTrackerProject(PROJECT)).thenReturn(project);
        when(project.getId()).thenReturn(PROJECT);

        IEnumeration<ITypeOpt> types = mock(IEnumeration.class);
        when(project.getWorkItemTypeEnum()).thenReturn(types);
        when(types.wrapOption("task")).thenReturn(type);
        ITypeOpt phantom = mock(ITypeOpt.class);
        when(phantom.isPhantom()).thenReturn(true);
        when(types.wrapOption("nothing")).thenReturn(phantom);

        IEnumeration<IHyperlinkRoleOpt> hyperlinkRoles = mock(IEnumeration.class);
        when(project.getHyperlinkRoleEnum()).thenReturn(hyperlinkRoles);
        when(hyperlinkRoles.wrapOption("ref_ext")).thenReturn(hyperlinkRole);

        when(trackerService.queryWorkItems(anyString(), anyString())).thenAnswer(invocation -> {
            queries.add(invocation.getArgument(0));
            IPObjectList list = mock(IPObjectList.class);
            when(list.iterator()).thenAnswer(i -> found.iterator());
            return list;
        });
        when(trackerService.createWorkItem(project)).thenAnswer(invocation -> {
            IWorkItem workItem = mock(IWorkItem.class);
            when(workItem.getId()).thenReturn("EL-" + (100 + created.size()));
            created.add(workItem);
            return workItem;
        });

        service = new ImportService(polarionService, githubClient, new WriteTransaction() {
            @Override
            public <T> T execute(Supplier<T> action) {
                transactions++;
                return action.get();
            }
        });
    }

    private static GithubItem item(long number, String title, String url) {
        return new GithubItem(number, title, "Body of " + number, "open", url, null, null,
                new GithubItem.User("alice"), List.of(new GithubItem.Label("bug")), null, Map.of("name", "Bug"), null,
                List.of(new GithubItem.User("alice"), new GithubItem.User("bob")));
    }

    private static RepositorySettingsModel settings() {
        return RepositorySettingsModel.builder()
                .repository("acme/tool")
                .shortName("Tool")
                .issues(ItemSettings.builder().enabled(true).workItemType("task").build())
                .discussions(new ItemSettings())
                .build();
    }

    /** A work item as Polarion returns it: its assignees are never null, at most empty. */
    private static IWorkItem workItem() {
        IWorkItem workItem = mock(IWorkItem.class);
        IPObjectList assignees = mock(IPObjectList.class);
        when(assignees.iterator()).thenAnswer(invocation -> List.of().iterator());
        when(workItem.getAssignees()).thenReturn(assignees);
        return workItem;
    }

    private static IWorkItem existingWithHyperlink(String id, String... urls) {
        IWorkItem workItem = workItem();
        when(workItem.getId()).thenReturn(id);
        List<IHyperlinkStruct> hyperlinks = new ArrayList<>();
        for (String url : urls) {
            IHyperlinkStruct hyperlink = mock(IHyperlinkStruct.class);
            when(hyperlink.getUri()).thenReturn(url);
            hyperlinks.add(hyperlink);
        }
        when(workItem.getHyperlinks()).thenReturn(hyperlinks);
        return workItem;
    }

    @Test
    void createsAWorkItemForEveryNewIssue() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7), item(8, "Typo", ISSUE_8)));

        ImportResult result = service.importRepository(PROJECT, settings(), false, null);

        assertThat(result.getRepository()).isEqualTo("acme/tool");
        assertThat(result.isDryRun()).isFalse();
        assertThat(result.count(ImportStatus.CREATED)).isEqualTo(2);
        assertThat(result.getEntries()).extracting(ImportEntry::getWorkItemId).containsExactly("EL-100", "EL-101");
        assertThat(result.getEntries().get(0)).satisfies(entry -> {
            assertThat(entry.getKind()).isEqualTo(ItemKind.ISSUE);
            assertThat(entry.getNumber()).isEqualTo(7);
            assertThat(entry.getTitle()).isEqualTo("Crash on start");
            assertThat(entry.getUrl()).isEqualTo(ISSUE_7);
        });

        IWorkItem workItem = created.get(0);
        verify(workItem).setType(type);
        verify(workItem).setTitle("[GitHub] Tool : Crash on start");
        ArgumentCaptor<Text> description = ArgumentCaptor.forClass(Text.class);
        verify(workItem).setDescription(description.capture());
        assertThat(description.getValue().getType()).isEqualTo(Text.TYPE_HTML);
        assertThat(description.getValue().getContent()).isEqualTo("<a href=\"" + ISSUE_7 + "\">" + ISSUE_7 + "</a>");
        verify(workItem).addHyperlink(ISSUE_7, hyperlinkRole);
        verify(workItem).save();
        // One transaction per work item: a failure of one item leaves the others in place.
        assertThat(transactions).isEqualTo(2);
        verify(githubClient, never()).getOpenDiscussions(anyString(), anyString());
    }

    @Test
    void reportsWhatThePageShowsOfANewItem() {
        RepositorySettingsModel settings = settings();
        settings.setName("tool");
        when(type.getId()).thenReturn("task");
        when(type.getName()).thenReturn("Task");
        when(type.getProperty(com.polarion.platform.persistence.IEnumOption.PROPERTY_KEY_ICON_URL)).thenReturn("/polarion/icons/default/enums/type_task.gif");
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7)));
        when(githubClient.readAt("acme", "tool")).thenReturn(java.time.Instant.parse("2026-10-03T08:00:00Z"));

        ImportResult result = service.importRepository(PROJECT, settings, true, null);

        assertThat(result.getReadAt()).isEqualTo("2026-10-03T08:00:00Z");
        assertThat(result.getEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.getSetting()).isEqualTo("tool");
            assertThat(entry.getRepository()).isEqualTo("acme/tool");
            assertThat(entry.getGithubType()).isEqualTo("Bug");
            assertThat(entry.getLabels()).containsExactly("bug");
            assertThat(entry.getAssignees()).containsExactly("alice", "bob");
            assertThat(entry.getWorkItemType()).isEqualTo("task");
            assertThat(entry.getWorkItemTypeName()).isEqualTo("Task");
            assertThat(entry.getWorkItemTypeIcon()).isEqualTo("/polarion/icons/default/enums/type_task.gif");
            assertThat(entry.getShortName()).isEqualTo("Tool");
            assertThat(entry.getLabelColors()).isEmpty();
            assertThat(entry.getWorkItemStatus()).isNull();
        });
    }

    @Test
    void reportsTheTypeStatusAndAssigneesOfAnExistingWorkItem() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7)));
        IWorkItem existing = existingWithHyperlink("EL-5", ISSUE_7);
        ITypeOpt defect = mock(ITypeOpt.class);
        when(defect.getId()).thenReturn("defect");
        when(defect.getName()).thenReturn("Defect");
        when(defect.getProperty(com.polarion.platform.persistence.IEnumOption.PROPERTY_KEY_ICON_URL)).thenReturn("/icons/defect.gif");
        when(existing.getType()).thenReturn(defect);
        com.polarion.alm.tracker.model.IStatusOpt open = mock(com.polarion.alm.tracker.model.IStatusOpt.class);
        when(open.getName()).thenReturn("Open");
        when(open.getProperty(com.polarion.platform.persistence.IEnumOption.PROPERTY_KEY_ICON_URL)).thenReturn("/icons/open.gif");
        when(existing.getStatus()).thenReturn(open);
        com.polarion.alm.projects.model.IUser named = mock(com.polarion.alm.projects.model.IUser.class);
        when(named.getName()).thenReturn("Alice Admin");
        com.polarion.alm.projects.model.IUser unnamed = mock(com.polarion.alm.projects.model.IUser.class);
        when(unnamed.getId()).thenReturn("bob");
        IPObjectList assignees = mock(IPObjectList.class);
        when(assignees.iterator()).thenAnswer(invocation -> List.of(named, unnamed, "not a user").iterator());
        when(existing.getAssignees()).thenReturn(assignees);
        found.add(existing);

        ImportEntry entry = service.importRepository(PROJECT, settings(), true, null).getEntries().get(0);

        assertThat(entry.getStatus()).isEqualTo(ImportStatus.OUTDATED);
        assertThat(entry.getWorkItemType()).isEqualTo("defect");
        assertThat(entry.getWorkItemTypeName()).isEqualTo("Defect");
        assertThat(entry.getWorkItemTypeIcon()).isEqualTo("/icons/defect.gif");
        assertThat(entry.getWorkItemStatus()).isEqualTo("Open");
        assertThat(entry.getWorkItemStatusIcon()).isEqualTo("/icons/open.gif");
        assertThat(entry.getWorkItemAssignees()).containsExactly("Alice Admin", "bob");
        assertThat(service.importRepository(PROJECT, settings(), true, null).getReadAt()).isNull();
    }

    @Test
    void readsAnExistingWorkItemWithoutTypeStatusOrAssignees() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7)));
        found.add(existingWithHyperlink("EL-5", ISSUE_7));

        ImportEntry entry = service.importRepository(PROJECT, settings(), true, null).getEntries().get(0);

        assertThat(entry.getWorkItemType()).isNull();
        assertThat(entry.getWorkItemStatus()).isNull();
        assertThat(entry.getWorkItemAssignees()).isEmpty();
    }

    @Test
    void skipsAnIssueThatAWorkItemHoldsAlready() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7), item(8, "Typo", ISSUE_8)));
        // The second link belongs to a repository whose name the SQL pattern matches by its "_" wildcard.
        found.add(existingWithHyperlink("EL-5", ISSUE_7, "https://github.com/acmeXtool/issues/8", "https://example.com"));
        found.add("not a work item");

        ImportResult result = service.importRepository(PROJECT, settings(), false, null);

        assertThat(result.getEntries()).extracting(ImportEntry::getStatus).containsExactly(ImportStatus.OUTDATED, ImportStatus.CREATED);
        assertThat(result.getEntries().get(0).getWorkItemId()).isEqualTo("EL-5");
        assertThat(created).hasSize(1);
        assertThat(queries).containsExactly("SQL:(select WI.C_URI from WORKITEM WI"
                + " inner join PROJECT P on P.C_URI = WI.FK_URI_PROJECT"
                + " inner join STRUCT_WORKITEM_HYPERLINKS HL on HL.FK_URI_P_WORKITEM = WI.C_URI"
                + " where P.C_ID = 'elibrary' and HL.C_URL like 'https://github.com/acme/tool/%')");
    }

    @Test
    void doesNotCreateTheSameItemTwiceInOneRun() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7), item(7, "Crash on start", ISSUE_7)));

        ImportResult result = service.importRepository(PROJECT, settings(), false, null);

        assertThat(result.getEntries()).extracting(ImportEntry::getStatus).containsExactly(ImportStatus.CREATED, ImportStatus.EXISTS);
        assertThat(result.getEntries().get(1).getWorkItemId()).isEqualTo("EL-100");
    }

    @Test
    void onlyReportsInADryRun() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7), item(8, "Typo", ISSUE_8)));
        found.add(existingWithHyperlink("EL-5", ISSUE_8));

        ImportResult result = service.importRepository(PROJECT, settings(), true, null);

        assertThat(result.isDryRun()).isTrue();
        assertThat(result.getEntries()).extracting(ImportEntry::getStatus).containsExactly(ImportStatus.NEW, ImportStatus.OUTDATED);
        assertThat(created).isEmpty();
        assertThat(transactions).isZero();
    }

    @Test
    void importsOnlyTheRequestedUrls() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7), item(8, "Typo", ISSUE_8)));

        ImportResult result = service.importRepository(PROJECT, settings(), false, List.of(ISSUE_8));

        assertThat(result.getEntries()).extracting(ImportEntry::getUrl).containsExactly(ISSUE_8);
        assertThat(created).hasSize(1);
    }

    @Test
    void importsDiscussionsWithTheirOwnSettings() {
        String discussion = "https://github.com/acme/tool/discussions/30";
        RepositorySettingsModel settings = settings();
        settings.getIssues().setEnabled(false);
        settings.setDiscussions(ItemSettings.builder()
                .enabled(true)
                .workItemType("task")
                .titleTemplate("{{ REPOSITORY }} #{{ NUMBER }} by {{ AUTHOR }}")
                .descriptionTemplate("<p>{{ BODY }}</p>")
                .fields(Map.of("severity", "minor"))
                .build());
        when(githubClient.getOpenDiscussions("acme", "tool")).thenReturn(List.of(item(30, "How to <b>", discussion)));

        ImportResult result = service.importRepository(PROJECT, settings, false, null);

        assertThat(result.getEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.getKind()).isEqualTo(ItemKind.DISCUSSION);
            assertThat(entry.getStatus()).isEqualTo(ImportStatus.CREATED);
        });
        IWorkItem workItem = created.get(0);
        verify(workItem).setTitle("acme/tool #30 by alice");
        ArgumentCaptor<Text> description = ArgumentCaptor.forClass(Text.class);
        verify(workItem).setDescription(description.capture());
        assertThat(description.getValue().getContent()).isEqualTo("<p>Body of 30</p>");
        verify(polarionService).setFieldValue(workItem, "severity", "minor");
        verify(githubClient, never()).getOpenIssues(anyString(), anyString());
    }

    @Test
    void keepsTheUrlInACustomFieldWhenConfigured() {
        RepositorySettingsModel settings = settings();
        settings.getIssues().setDuplicateKey(DuplicateKey.CUSTOM_FIELD);
        settings.getIssues().setDuplicateKeyField("githubUrl");
        settings.getIssues().setDescriptionTemplate(" ");
        settings.getIssues().setFields(null);
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7), item(8, "Typo", ISSUE_8)));
        IWorkItem existing = workItem();
        when(existing.getId()).thenReturn("EL-5");
        when(existing.getCustomField("githubUrl")).thenReturn(ISSUE_7);
        found.add(existing);
        found.add(workItem());

        ImportResult result = service.importRepository(PROJECT, settings, false, null);

        assertThat(result.getEntries()).extracting(ImportEntry::getStatus).containsExactly(ImportStatus.OUTDATED, ImportStatus.CREATED);
        IWorkItem workItem = created.get(0);
        verify(polarionService).setFieldValue(workItem, "githubUrl", ISSUE_8);
        verify(workItem, never()).addHyperlink(anyString(), any());
        verify(workItem, never()).setDescription(any());
        assertThat(queries).containsExactly("SQL:(select WI.C_URI from WORKITEM WI"
                + " inner join PROJECT P on P.C_URI = WI.FK_URI_PROJECT"
                + " inner join CF_WORKITEM CF on CF.FK_URI_WORKITEM = WI.C_URI"
                + " where P.C_ID = 'elibrary' and CF.C_NAME = 'githubUrl' and CF.C_STRING_VALUE like 'https://github.com/acme/tool/%')");
    }

    @Test
    void linksEveryCreatedWorkItemToTheEpic() {
        RepositorySettingsModel settings = settings();
        settings.getIssues().setEpicId("EL-1");
        settings.getIssues().setEpicLinkRole("parent");
        IWorkItem epic = mock(IWorkItem.class);
        when(polarionService.getWorkItem(PROJECT, "EL-1")).thenReturn(epic);
        IEnumeration<ILinkRoleOpt> roles = mock(IEnumeration.class);
        ILinkRoleOpt role = mock(ILinkRoleOpt.class);
        when(project.getWorkItemLinkRoleEnum()).thenReturn(roles);
        when(roles.wrapOption("parent", type)).thenReturn(role);
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7)));

        service.importRepository(PROJECT, settings, false, null);

        verify(created.get(0)).addLinkedItem(epic, role, null, false);
    }

    @Test
    void rejectsALinkRoleTheProjectDoesNotHave() {
        RepositorySettingsModel settings = settings();
        settings.getIssues().setEpicId("EL-1");
        settings.getIssues().setEpicLinkRole("nothing");
        IEnumeration<ILinkRoleOpt> roles = mock(IEnumeration.class);
        ILinkRoleOpt phantom = mock(ILinkRoleOpt.class);
        when(phantom.isPhantom()).thenReturn(true);
        when(project.getWorkItemLinkRoleEnum()).thenReturn(roles);
        when(roles.wrapOption("nothing", type)).thenReturn(phantom);

        assertThatThrownBy(() -> service.importRepository(PROJECT, settings, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no link role 'nothing'");
        verify(githubClient, never()).getOpenIssues(anyString(), anyString());
    }

    @Test
    void rejectsAWorkItemTypeTheProjectDoesNotHave() {
        RepositorySettingsModel settings = settings();
        settings.getIssues().setWorkItemType("nothing");

        assertThatThrownBy(() -> service.importRepository(PROJECT, settings, true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no work item type 'nothing'");
    }

    @Test
    void createsNothingWhenGithubFailsForOneKind() {
        RepositorySettingsModel settings = settings();
        settings.setDiscussions(ItemSettings.builder().enabled(true).workItemType("task").build());
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7)));
        when(githubClient.getOpenDiscussions("acme", "tool")).thenThrow(new GithubClientException("Discussions are turned off"));

        assertThatThrownBy(() -> service.importRepository(PROJECT, settings, false, null))
                .isInstanceOf(GithubClientException.class);
        assertThat(created).isEmpty();
    }

    private static GithubItem labeled(long number, String url, Object type, Object category, String... labels) {
        return new GithubItem(number, "Item " + number, null, "open", url, null, null, null,
                java.util.Arrays.stream(labels).map(GithubItem.Label::new).toList(), null, type, category, null);
    }

    private ITypeOpt type(String id) {
        ITypeOpt option = mock(ITypeOpt.class);
        when(project.getWorkItemTypeEnum().wrapOption(id)).thenReturn(option);
        return option;
    }

    @Test
    void appliesTheFirstMatchingRule() {
        ITypeOpt defect = type("defect");
        ITypeOpt change = type("changerequest");
        RepositorySettingsModel settings = settings();
        settings.getIssues().setFields(Map.of("severity", "minor", "component", "core"));
        settings.getIssues().setRules(List.of(
                ItemRule.builder().match(RuleMatch.LABEL).value("WontFix").skip(true).build(),
                ItemRule.builder().match(RuleMatch.TYPE).value("bug").workItemType("defect").fields(Map.of("severity", "major")).build(),
                ItemRule.builder().match(RuleMatch.LABEL).value(" enhancement ").workItemType("changerequest").fields(null).build(),
                ItemRule.builder().match(RuleMatch.LABEL).value("bug").workItemType("task").build()));
        String base = "https://github.com/acme/tool/issues/";
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(
                labeled(1, base + 1, Map.of("name", "Bug"), null, "bug", "wontfix"),
                labeled(2, base + 2, Map.of("name", "Bug"), null, "bug", "enhancement"),
                labeled(3, base + 3, "Feature", null, "Enhancement"),
                labeled(4, base + 4, null, null, "question"),
                labeled(5, base + 5, Map.of("id", 7), null)));

        ImportResult result = service.importRepository(PROJECT, settings, false, null);

        assertThat(result.getEntries()).extracting(ImportEntry::getStatus).containsExactly(
                ImportStatus.SKIPPED, ImportStatus.CREATED, ImportStatus.CREATED, ImportStatus.CREATED, ImportStatus.CREATED);
        assertThat(result.getEntries().get(0).getMessage()).isEqualTo("by the rule Label = WontFix");
        assertThat(created).hasSize(4);
        // Item 2 matches the type rule before the label rules. Its field values replace the ones of the block.
        verify(created.get(0)).setType(defect);
        verify(polarionService).setFieldValue(created.get(0), "severity", "major");
        verify(polarionService).setFieldValue(created.get(0), "component", "core");
        // Item 3: a rule without field values keeps the ones of the block.
        verify(created.get(1)).setType(change);
        verify(polarionService).setFieldValue(created.get(1), "severity", "minor");
        // Items 4 and 5 match no rule and get the type of the block.
        verify(created.get(2)).setType(type);
        verify(created.get(3)).setType(type);
    }

    @Test
    void showsAnItemLeftOutByARuleInADryRunAndKeepsAnExistingOne() {
        RepositorySettingsModel settings = settings();
        settings.getIssues().setRules(List.of(ItemRule.builder().match(RuleMatch.LABEL).value("wontfix").skip(true).build()));
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(
                labeled(7, ISSUE_7, null, null, "wontfix"), labeled(8, ISSUE_8, null, null, "wontfix")));
        found.add(existingWithHyperlink("EL-5", ISSUE_7));

        ImportResult result = service.importRepository(PROJECT, settings, true, null);

        // A work item that exists already is reported, whatever a later rule says.
        assertThat(result.getEntries()).extracting(ImportEntry::getStatus).containsExactly(ImportStatus.EXISTS, ImportStatus.SKIPPED);
    }

    @Test
    void appliesCategoryRulesToDiscussionsAndFillsTheNewPlaceholders() {
        ITypeOpt question = type("question");
        RepositorySettingsModel settings = settings();
        settings.getIssues().setEnabled(false);
        settings.setDiscussions(ItemSettings.builder()
                .enabled(true)
                .workItemType("task")
                .titleTemplate("{{ CATEGORY }} [{{ LABELS }}] [{{ TYPE }}] {{ TITLE }}")
                .rules(List.of(ItemRule.builder().match(RuleMatch.CATEGORY).value("q&a").workItemType("question").build()))
                .build());
        String base = "https://github.com/acme/tool/discussions/";
        when(githubClient.getOpenDiscussions("acme", "tool")).thenReturn(List.of(
                labeled(30, base + 30, null, Map.of("name", "Q&A"), "docs", "help"),
                labeled(31, base + 31, null, Map.of("name", "Ideas"))));

        service.importRepository(PROJECT, settings, false, null);

        verify(created.get(0)).setType(question);
        verify(created.get(0)).setTitle("Q&A [docs, help] [] Item 30");
        verify(created.get(1)).setType(type);
        verify(created.get(1)).setTitle("Ideas [] [] Item 31");
    }

    @Test
    void rejectsAWorkItemTypeOfARuleTheProjectDoesNotHave() {
        RepositorySettingsModel settings = settings();
        settings.getIssues().setRules(List.of(ItemRule.builder().match(RuleMatch.LABEL).value("bug").workItemType("nothing").build()));

        assertThatThrownBy(() -> service.importRepository(PROJECT, settings, true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no work item type 'nothing'");
        verify(githubClient, never()).getOpenIssues(anyString(), anyString());
    }

    @Test
    void looksUpTheLinkRoleForTheTypeOfEachRule() {
        ITypeOpt defect = type("defect");
        RepositorySettingsModel settings = settings();
        settings.getIssues().setEpicId("EL-1");
        settings.getIssues().setEpicLinkRole("parent");
        settings.getIssues().setRules(List.of(ItemRule.builder().match(RuleMatch.LABEL).value("bug").workItemType("defect").build()));
        IWorkItem epic = mock(IWorkItem.class);
        when(polarionService.getWorkItem(PROJECT, "EL-1")).thenReturn(epic);
        IEnumeration<ILinkRoleOpt> roles = mock(IEnumeration.class);
        ILinkRoleOpt roleOfTask = mock(ILinkRoleOpt.class);
        ILinkRoleOpt roleOfDefect = mock(ILinkRoleOpt.class);
        when(project.getWorkItemLinkRoleEnum()).thenReturn(roles);
        when(roles.wrapOption("parent", type)).thenReturn(roleOfTask);
        when(roles.wrapOption("parent", defect)).thenReturn(roleOfDefect);
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(labeled(7, ISSUE_7, null, null, "bug")));

        service.importRepository(PROJECT, settings, false, null);

        verify(created.get(0)).addLinkedItem(epic, roleOfDefect, null, false);
    }

    /** A work item that shows exactly what the default settings make of {@link #ISSUE_7}. */
    private IWorkItem upToDate() {
        IWorkItem workItem = existingWithHyperlink("EL-5", ISSUE_7);
        when(workItem.getTitle()).thenReturn("[GitHub] Tool : Crash on start");
        when(workItem.getDescription()).thenReturn(Text.html("<a href=\"" + ISSUE_7 + "\">" + ISSUE_7 + "</a>"));
        when(workItem.getType()).thenReturn(type);
        return workItem;
    }

    @Test
    void reportsAWorkItemThatShowsWhatGithubSaysAsExisting() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7)));
        found.add(upToDate());

        ImportEntry entry = service.importRepository(PROJECT, settings(), true, null).getEntries().get(0);

        assertThat(entry.getStatus()).isEqualTo(ImportStatus.EXISTS);
        assertThat(entry.getMessage()).isNull();
    }

    @Test
    void namesWhatDiffersInAnOutdatedWorkItem() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7)));
        IWorkItem workItem = upToDate();
        when(workItem.getTitle()).thenReturn("[GitHub] {shortName}: {title}");
        when(workItem.getDescription()).thenReturn(null);
        when(workItem.getType()).thenReturn(null);
        found.add(workItem);
        RepositorySettingsModel settings = settings();
        settings.getIssues().setFields(Map.of("severity", "major", "component", "core"));
        when(polarionService.getFieldValue(workItem, "severity", String.class)).thenReturn("major");
        when(polarionService.getFieldValue(workItem, "component", String.class)).thenThrow(new IllegalArgumentException("unknown"));

        ImportEntry entry = service.importRepository(PROJECT, settings, true, null).getEntries().get(0);

        assertThat(entry.getStatus()).isEqualTo(ImportStatus.OUTDATED);
        assertThat(entry.getMessage()).isEqualTo("differs in title, description, type, component");
        assertThat(entry.getWorkItemId()).isEqualTo("EL-5");
    }

    @Test
    void comparesTheEpicLinkWithTheRoleOfTheType() {
        RepositorySettingsModel settings = settings();
        settings.getIssues().setEpicId("EL-1");
        settings.getIssues().setEpicLinkRole("parent");
        IWorkItem epic = mock(IWorkItem.class);
        when(epic.getId()).thenReturn("EL-1");
        when(polarionService.getWorkItem(PROJECT, "EL-1")).thenReturn(epic);
        IEnumeration<ILinkRoleOpt> roles = mock(IEnumeration.class);
        ILinkRoleOpt parent = mock(ILinkRoleOpt.class);
        when(parent.getId()).thenReturn("parent");
        ILinkRoleOpt relates = mock(ILinkRoleOpt.class);
        when(relates.getId()).thenReturn("relates_to");
        when(project.getWorkItemLinkRoleEnum()).thenReturn(roles);
        when(roles.wrapOption("parent", type)).thenReturn(parent);
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7)));
        IWorkItem workItem = upToDate();
        found.add(workItem);
        IWorkItem other = mock(IWorkItem.class);
        when(other.getId()).thenReturn("EL-2");
        com.polarion.alm.tracker.model.ILinkedWorkItemStruct toOther = mock(com.polarion.alm.tracker.model.ILinkedWorkItemStruct.class);
        when(toOther.getLinkedItem()).thenReturn(other);
        when(toOther.getLinkRole()).thenReturn(parent);
        com.polarion.alm.tracker.model.ILinkedWorkItemStruct wrongRole = mock(com.polarion.alm.tracker.model.ILinkedWorkItemStruct.class);
        when(wrongRole.getLinkedItem()).thenReturn(epic);
        when(wrongRole.getLinkRole()).thenReturn(relates);
        com.polarion.alm.tracker.model.ILinkedWorkItemStruct noItem = mock(com.polarion.alm.tracker.model.ILinkedWorkItemStruct.class);
        com.polarion.alm.tracker.model.ILinkedWorkItemStruct noRole = mock(com.polarion.alm.tracker.model.ILinkedWorkItemStruct.class);
        when(noRole.getLinkedItem()).thenReturn(epic);
        when(workItem.getLinkedWorkItemsStructsDirect()).thenReturn(List.of(toOther, wrongRole, noItem, noRole));

        assertThat(service.importRepository(PROJECT, settings, true, null).getEntries().get(0).getMessage()).isEqualTo("differs in epic link");

        com.polarion.alm.tracker.model.ILinkedWorkItemStruct toEpic = mock(com.polarion.alm.tracker.model.ILinkedWorkItemStruct.class);
        when(toEpic.getLinkedItem()).thenReturn(epic);
        when(toEpic.getLinkRole()).thenReturn(parent);
        when(workItem.getLinkedWorkItemsStructsDirect()).thenReturn(List.of(toEpic));

        assertThat(service.importRepository(PROJECT, settings, true, null).getEntries().get(0).getStatus()).isEqualTo(ImportStatus.EXISTS);
    }

    @Test
    void updatesWhatDiffersAndNothingElse() {
        ITypeOpt defect = type("defect");
        when(defect.getId()).thenReturn("defect");
        RepositorySettingsModel settings = settings();
        settings.getIssues().setFields(Map.of("severity", "major", "component", "core"));
        settings.getIssues().setEpicId("EL-1");
        settings.getIssues().setEpicLinkRole("parent");
        settings.getIssues().setRules(List.of(ItemRule.builder().match(RuleMatch.TYPE).value("bug").workItemType("defect").build()));
        IWorkItem epic = mock(IWorkItem.class);
        when(epic.getId()).thenReturn("EL-1");
        IEnumeration<ILinkRoleOpt> roles = mock(IEnumeration.class);
        ILinkRoleOpt parent = mock(ILinkRoleOpt.class);
        when(project.getWorkItemLinkRoleEnum()).thenReturn(roles);
        when(roles.wrapOption("parent", type)).thenReturn(parent);
        when(roles.wrapOption("parent", defect)).thenReturn(parent);
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7), item(8, "Typo", ISSUE_8)));
        IWorkItem stored = upToDate();
        when(stored.getTitle()).thenReturn("old title");
        found.add(stored);
        when(polarionService.getFieldValue(stored, "component", String.class)).thenReturn("core");
        IWorkItem writable = mock(IWorkItem.class);
        when(polarionService.getWorkItem(PROJECT, "EL-1")).thenReturn(epic);
        when(polarionService.getWorkItem(PROJECT, "EL-5")).thenReturn(writable);

        ImportResult result = service.updateRepository(PROJECT, settings, List.of(ISSUE_7, ISSUE_8));

        assertThat(result.isDryRun()).isFalse();
        assertThat(result.getEntries()).extracting(ImportEntry::getStatus).containsExactly(ImportStatus.UPDATED, ImportStatus.NEW);
        assertThat(result.getEntries().get(0).getMessage()).isEqualTo("updated title, type, severity, epic link");
        verify(writable).setTitle("[GitHub] Tool : Crash on start");
        verify(writable).setType(defect);
        verify(polarionService).setFieldValue(writable, "severity", "major");
        verify(polarionService, never()).setFieldValue(writable, "component", "core");
        verify(writable).addLinkedItem(epic, parent, null, false);
        verify(writable, never()).setDescription(any());
        verify(writable, never()).addHyperlink(anyString(), any());
        verify(writable).save();
        // An update creates nothing: the new item waits for Create.
        assertThat(created).isEmpty();
    }

    @Test
    void updatesTheDescriptionAndReportsAFailedUpdate() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7)));
        IWorkItem stored = upToDate();
        when(stored.getDescription()).thenReturn(Text.html("old"));
        found.add(stored);
        IWorkItem writable = mock(IWorkItem.class);
        when(polarionService.getWorkItem(PROJECT, "EL-5")).thenReturn(writable);

        assertThat(service.updateRepository(PROJECT, settings(), List.of(ISSUE_7)).getEntries().get(0).getStatus()).isEqualTo(ImportStatus.UPDATED);
        verify(writable).setDescription(any(Text.class));

        doThrow(new IllegalStateException("locked")).when(writable).save();
        ImportEntry failed = service.updateRepository(PROJECT, settings(), List.of(ISSUE_7)).getEntries().get(0);
        assertThat(failed.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(failed.getMessage()).isEqualTo("locked");

        doThrow(new IllegalStateException()).when(writable).save();
        assertThat(service.updateRepository(PROJECT, settings(), List.of(ISSUE_7)).getEntries().get(0).getMessage()).isEqualTo("IllegalStateException");
    }

    @Test
    void leavesAWorkItemAloneThatARuleNowLeavesOut() {
        RepositorySettingsModel settings = settings();
        settings.getIssues().setRules(List.of(ItemRule.builder().match(RuleMatch.LABEL).value("bug").skip(true).build()));
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7)));
        IWorkItem stored = upToDate();
        when(stored.getTitle()).thenReturn("old title");
        found.add(stored);

        ImportEntry entry = service.updateRepository(PROJECT, settings, List.of(ISSUE_7)).getEntries().get(0);

        assertThat(entry.getStatus()).isEqualTo(ImportStatus.EXISTS);
        verify(polarionService, never()).getWorkItem(PROJECT, "EL-5");
    }

    @Test
    void comparesNoDescriptionWithoutADescriptionTemplate() {
        RepositorySettingsModel settings = settings();
        settings.getIssues().setDescriptionTemplate(" ");
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7)));
        IWorkItem stored = upToDate();
        when(stored.getDescription()).thenReturn(Text.html("written by hand"));
        found.add(stored);

        assertThat(service.importRepository(PROJECT, settings, true, null).getEntries().get(0).getStatus()).isEqualTo(ImportStatus.EXISTS);
    }

    @Test
    void refreshesTheListsOfTheRepositoryOfASetting() {
        service.refresh(settings());

        verify(githubClient).forget("acme", "tool");
    }

    @Test
    void refusesToRefreshAnInvalidSetting() {
        RepositorySettingsModel settings = settings();
        settings.setRepository("");

        assertThatThrownBy(() -> service.refresh(settings)).isInstanceOf(IllegalArgumentException.class);
        verify(githubClient, never()).forget(anyString(), anyString());
    }

    @Test
    void rejectsInvalidSettingsBeforeAnyRequest() {
        RepositorySettingsModel settings = settings();
        settings.setShortName("");

        assertThatThrownBy(() -> service.importRepository(PROJECT, settings, false, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(polarionService, never()).getTrackerProject(anyString());
    }

    @Test
    void rejectsAFieldNameThatDoesNotFitAnSqlLiteral() {
        RepositorySettingsModel settings = settings();
        settings.getIssues().setDuplicateKey(DuplicateKey.CUSTOM_FIELD);
        settings.getIssues().setDuplicateKeyField("x' or '1'='1");
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of());

        assertThatThrownBy(() -> service.importRepository(PROJECT, settings, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not usable in a work item search");
        assertThat(queries).isEmpty();
    }

    @Test
    void reportsAnItemThatCannotBeSavedAndGoesOn() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7), item(8, "Typo", ISSUE_8)));
        // doAnswer, because when(...) would run the answer of setUp once and count a work item.
        doAnswer(invocation -> {
            IWorkItem workItem = mock(IWorkItem.class);
            when(workItem.getId()).thenReturn("EL-" + (100 + created.size()));
            if (created.isEmpty()) {
                doThrow(new IllegalStateException("The field severity is required")).when(workItem).save();
            }
            created.add(workItem);
            return workItem;
        }).when(trackerService).createWorkItem(project);

        ImportResult result = service.importRepository(PROJECT, settings(), false, null);

        assertThat(result.getEntries()).extracting(ImportEntry::getStatus).containsExactly(ImportStatus.FAILED, ImportStatus.CREATED);
        assertThat(result.getEntries().get(0).getMessage()).isEqualTo("The field severity is required");
        assertThat(result.getEntries().get(0).getWorkItemId()).isNull();
    }

    @Test
    void triesAFailedItemAgainWhenItComesTwice() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7), item(7, "Crash on start", ISSUE_7)));
        doThrow(new IllegalStateException("No")).when(trackerService).createWorkItem(project);

        ImportResult result = service.importRepository(PROJECT, settings(), false, null);

        assertThat(result.getEntries()).extracting(ImportEntry::getStatus).containsExactly(ImportStatus.FAILED, ImportStatus.FAILED);
    }

    @Test
    void namesTheExceptionWhenAFailureHasNoMessage() {
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(item(7, "Crash on start", ISSUE_7)));
        doThrow(new IllegalStateException()).when(trackerService).createWorkItem(project);

        ImportResult result = service.importRepository(PROJECT, settings(), false, null);

        assertThat(result.getEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.getStatus()).isEqualTo(ImportStatus.FAILED);
            assertThat(entry.getMessage()).isEqualTo("IllegalStateException");
        });
    }

    @Test
    void refusesAnItemWhoseUrlIsNotInTheRepository() {
        GithubItem withoutAuthor = new GithubItem(9, "No URL", null, "open", null, null, null, null, null, null, null, null, null);
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(withoutAuthor, item(10, "Elsewhere", "https://example.com/acme/tool/issues/10")));

        ImportResult result = service.importRepository(PROJECT, settings(), false, null);

        assertThat(result.getEntries()).extracting(ImportEntry::getStatus).containsOnly(ImportStatus.FAILED);
        assertThat(result.getEntries()).extracting(ImportEntry::getMessage).containsOnly("The item has no URL in the repository");
        assertThat(created).isEmpty();
    }

    @Test
    void acceptsAnItemWithoutAnAuthor() {
        RepositorySettingsModel settings = settings();
        settings.getIssues().setTitleTemplate("{{ TITLE }} by [{{ AUTHOR }}]");
        GithubItem withoutAuthor = new GithubItem(7, "Crash", null, "open", ISSUE_7, null, null, null, null, null, null, null, null);
        when(githubClient.getOpenIssues("acme", "tool")).thenReturn(List.of(withoutAuthor));

        service.importRepository(PROJECT, settings, false, null);

        verify(created.get(0)).setTitle("Crash by []");
    }
}
