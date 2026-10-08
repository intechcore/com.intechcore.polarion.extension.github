package com.intechcore.polarion.extension.github.job;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import com.intechcore.polarion.extension.github.client.GithubRateLimitException;
import com.intechcore.polarion.extension.github.service.ImportEntry;
import com.intechcore.polarion.extension.github.service.ImportResult;
import com.intechcore.polarion.extension.github.service.ImportService;
import com.intechcore.polarion.extension.github.service.ImportStatus;
import com.intechcore.polarion.extension.github.service.ItemKind;
import com.intechcore.polarion.extension.github.settings.HiddenItems;
import com.intechcore.polarion.extension.github.settings.NotificationSettings;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import com.intechcore.polarion.extension.github.settings.RepositorySettingsModel;
import com.intechcore.polarion.extension.github.watch.Mailer;
import com.intechcore.polarion.extension.github.watch.NewItemsWatcher;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IProjectGroup;
import com.polarion.alm.projects.model.IUser;
import com.polarion.platform.jobs.IJob;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.ILogger;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.platform.persistence.model.IPObjectList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubWatchJobUnitImplTest {

    private static final String SCOPE = "project/elibrary/";
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private IProjectService projectService;
    private RepositorySettings repositorySettings;
    private ImportService importService;
    private HiddenItems hiddenItems;
    private Mailer mailer;
    private NewItemsWatcher watcher;
    private GithubWatchJobUnitImpl job;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        PolarionService polarionService = mock(PolarionService.class);
        projectService = mock(IProjectService.class);
        when(polarionService.getProjectService()).thenReturn(projectService);
        IProject elibrary = mock(IProject.class);
        when(elibrary.getId()).thenReturn("elibrary");
        IPObjectList projects = mock(IPObjectList.class);
        when(projects.iterator()).thenAnswer(invocation -> List.of(elibrary, "not a project").iterator());
        IProjectGroup root = mock(IProjectGroup.class);
        when(root.getDeepContainedProjects()).thenReturn(projects);
        when(projectService.getRootProjectGroup()).thenReturn(root);
        user("alice", "alice@example.com");
        user("nomail", " ");
        // Polarion answers an unknown ID with a phantom user without an address.
        user("nobody", null);

        repositorySettings = mock(RepositorySettings.class);
        importService = mock(ImportService.class);
        hiddenItems = mock(HiddenItems.class);
        when(hiddenItems.urls("elibrary")).thenReturn(Set.of("https://github.com/acme/tool/issues/2"));
        mailer = mock(Mailer.class);
        watcher = new NewItemsWatcher();

        job = new GithubWatchJobUnitImpl("watch", mock(IJobUnitFactory.class), polarionService, repositorySettings, importService,
                hiddenItems, watcher, () -> mailer, Clock.fixed(NOW, ZoneOffset.UTC));
        job.setLogger(mock(ILogger.class));
        // The status of a job unit is built from its job.
        job.setJob(mock(IJob.class));
    }

    private void user(String id, String email) {
        IUser user = mock(IUser.class);
        when(user.getEmail()).thenReturn(email);
        when(projectService.getUser(id)).thenReturn(user);
    }

    private RepositorySettingsModel setting(String name, NotificationSettings notifications, ImportEntry... entries) {
        RepositorySettingsModel settings = RepositorySettingsModel.builder().repository("acme/" + name).shortName(name.toUpperCase())
                .notifications(notifications).build();
        settings.setName(name);
        when(repositorySettings.read(eq(SCOPE), argThat(id -> id != null && name.equals(id.getIdentifier())), isNull())).thenReturn(settings);
        ImportResult result = new ImportResult("acme/" + name, true);
        result.getEntries().addAll(List.of(entries));
        when(importService.importRepository("elibrary", settings, true, null)).thenReturn(result);
        return settings;
    }

    private void names(String... names) {
        List<SettingName> settingNames = new ArrayList<>();
        for (String name : names) {
            settingNames.add(SettingName.builder().name(name).scope(SCOPE).build());
        }
        when(repositorySettings.readNames(SCOPE)).thenReturn(settingNames);
    }

    private static ImportEntry entry(long number, ItemKind kind, ImportStatus status) {
        return ImportEntry.builder().number(number).kind(kind).status(status).title("Item " + number)
                .url("https://github.com/acme/tool/issues/" + number).createdAt("2026-10-07T11:55:00Z").updatedAt("2026-10-07T11:55:00Z").build();
    }

    @Test
    void mailsTheNewItemsOfTheWatchedKindsToTheUsersWithAnAddress() {
        names("tool", "quiet");
        setting("tool", NotificationSettings.builder().users(List.of("alice", "nomail")).issues(true).pullRequests(true).build(),
                entry(1, ItemKind.ISSUE, ImportStatus.NEW),
                entry(2, ItemKind.ISSUE, ImportStatus.NEW),
                entry(3, ItemKind.ISSUE, ImportStatus.EXISTS),
                entry(4, ItemKind.DISCUSSION, ImportStatus.NEW),
                entry(5, ItemKind.PULL_REQUEST, ImportStatus.NEW));
        setting("quiet", new NotificationSettings());

        IJobStatus status = job.runInternal(mock(IProgressMonitor.class));

        assertThat(status.getType()).isEqualTo(IJobStatus.JobStatusType.STATUS_TYPE_OK);
        assertThat(status.getMessage()).isEqualTo("1 repositories checked, 1 mails sent");
        // #2 is hidden, #3 has a work item, discussions are not watched.
        verify(mailer).send(eq(List.of("alice@example.com")), eq("[GitHub] TOOL: 2 new items"),
                argThat(html -> html.contains("#1<") && html.contains("#5<") && !html.contains("#2<") && !html.contains("#4<")));
        verify(importService, never()).importRepository(eq("elibrary"), argThat(s -> "quiet".equals(s.getName())), eq(true), isNull());

        // The next run has nothing new and mails nothing.
        job.runInternal(mock(IProgressMonitor.class));
        verify(mailer).send(anyList(), anyString(), anyString());
    }

    @Test
    void reportsASettingWhoseUsersHaveNoAddressAndGoesOn() {
        names("tool", "docs");
        setting("tool", NotificationSettings.builder().users(List.of("nomail", "nobody")).issues(true).build(), entry(1, ItemKind.ISSUE, ImportStatus.NEW));
        setting("docs", NotificationSettings.builder().users(List.of("alice")).issues(true).build(), entry(7, ItemKind.ISSUE, ImportStatus.NEW));

        IJobStatus status = job.runInternal(mock(IProgressMonitor.class));

        assertThat(status.getType()).isEqualTo(IJobStatus.JobStatusType.STATUS_TYPE_FAILED);
        assertThat(status.getMessage()).contains("2 repositories checked, 1 mails sent").contains("elibrary/tool: None of the users");
        verify(mailer).send(eq(List.of("alice@example.com")), anyString(), anyString());
    }

    @Test
    void reportsAMailThatFailed() {
        names("tool");
        setting("tool", NotificationSettings.builder().users(List.of("alice")).issues(true).build(), entry(1, ItemKind.ISSUE, ImportStatus.NEW));
        doThrow(new IllegalStateException("No SMTP server")).when(mailer).send(anyList(), anyString(), anyString());

        IJobStatus status = job.runInternal(mock(IProgressMonitor.class));

        assertThat(status.getType()).isEqualTo(IJobStatus.JobStatusType.STATUS_TYPE_FAILED);
        assertThat(status.getMessage()).contains("No SMTP server");
    }

    /** The rate limit ends the run: every further request would fail the same way. */
    @Test
    void stopsAtTheRateLimit() {
        names("tool", "docs");
        RepositorySettingsModel tool = setting("tool", NotificationSettings.builder().users(List.of("alice")).issues(true).build());
        when(importService.importRepository("elibrary", tool, true, null)).thenThrow(new GithubRateLimitException(null, false));

        job.setIntervalMinutes(30);
        IJobStatus status = job.runInternal(mock(IProgressMonitor.class));

        assertThat(status.getType()).isEqualTo(IJobStatus.JobStatusType.STATUS_TYPE_FAILED);
        assertThat(status.getMessage()).contains("rate limit");
        verify(repositorySettings, never()).read(eq(SCOPE), argThat(id -> id != null && "docs".equals(id.getIdentifier())), any());
    }

    @Test
    void takesTheAdvisoriesOnlyWhenTheSettingWatchesThem() {
        ImportEntry advisory = ImportEntry.builder().kind(ItemKind.ADVISORY).status(ImportStatus.NEW).url("https://github.com/x").build();

        assertThat(GithubWatchJobUnitImpl.candidates(List.of(advisory), NotificationSettings.builder().advisories(true).build(), Set.of()))
                .containsExactly(advisory);
        assertThat(GithubWatchJobUnitImpl.candidates(List.of(advisory), NotificationSettings.builder().issues(true).build(), Set.of()))
                .isEmpty();
    }
}
