package com.intechcore.polarion.extension.github.job;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.intechcore.polarion.extension.github.client.GithubRateLimitException;
import com.intechcore.polarion.extension.github.properties.GithubExtensionConfiguration;
import com.intechcore.polarion.extension.github.service.ImportEntry;
import com.intechcore.polarion.extension.github.service.ImportService;
import com.intechcore.polarion.extension.github.service.ImportStatus;
import com.intechcore.polarion.extension.github.service.ItemKind;
import com.intechcore.polarion.extension.github.settings.HiddenItems;
import com.intechcore.polarion.extension.github.settings.NotificationSettings;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import com.intechcore.polarion.extension.github.settings.RepositorySettingsModel;
import com.intechcore.polarion.extension.github.watch.Mailer;
import com.intechcore.polarion.extension.github.watch.NewItemsWatcher;
import com.intechcore.polarion.extension.github.watch.NotificationMail;
import com.intechcore.polarion.extension.github.watch.SmtpMailer;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IUser;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.platform.jobs.spi.AbstractJobUnit;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Checks every repository setting with notifications, in every project, and mails the users of the
 * setting the items that are new since the last check. It only reads: GitHub, the settings and the
 * work items. It writes nothing into Polarion, so the system user that runs it needs no write access.
 */
public class GithubWatchJobUnitImpl extends AbstractJobUnit implements GithubWatchJobUnit {

    static final int DEFAULT_INTERVAL_MINUTES = 15;

    // The job unit is made for every run; what the checks saw lives across them.
    private static final NewItemsWatcher SHARED_WATCHER = new NewItemsWatcher();

    private final PolarionService polarionService;
    private final RepositorySettings repositorySettings;
    private final ImportService importService;
    private final HiddenItems hiddenItems;
    private final NewItemsWatcher watcher;
    private final Supplier<Mailer> mailer;
    private final Clock clock;

    @Setter
    private Integer intervalMinutes;

    public GithubWatchJobUnitImpl(String name, IJobUnitFactory creator) {
        this(name, creator, new PolarionService(), new RepositorySettings(), new ImportService(), new HiddenItems(), SHARED_WATCHER,
                () -> new SmtpMailer(System.getProperties(), GithubExtensionConfiguration.getInstance().getMailFrom()), Clock.systemUTC());
    }

    @SuppressWarnings("java:S107") // the collaborators of a job, given one by one so a test can replace each
    GithubWatchJobUnitImpl(String name, IJobUnitFactory creator, PolarionService polarionService, RepositorySettings repositorySettings,
                           ImportService importService, HiddenItems hiddenItems, NewItemsWatcher watcher, Supplier<Mailer> mailer, Clock clock) {
        super(name, creator);
        this.polarionService = polarionService;
        this.repositorySettings = repositorySettings;
        this.importService = importService;
        this.hiddenItems = hiddenItems;
        this.watcher = watcher;
        this.mailer = mailer;
        this.clock = clock;
    }

    @Override
    protected IJobStatus runInternal(IProgressMonitor progress) {
        progress.beginTask(getName(), IProgressMonitor.UNKNOWN);
        try {
            Duration interval = Duration.ofMinutes(intervalMinutes == null || intervalMinutes <= 0 ? DEFAULT_INTERVAL_MINUTES : intervalMinutes);
            Run run = new Run(interval);
            for (Object project : polarionService.getProjectService().getRootProjectGroup().getDeepContainedProjects()) {
                if (project instanceof IProject p) {
                    run.project(p.getId());
                }
            }
            String summary = "%d repositories checked, %d mails sent".formatted(run.checked, run.mails);
            return run.failures.isEmpty() ? getStatusOK(summary)
                    : getStatusFailed(summary + ", " + run.failures.size() + " failed: " + String.join("; ", run.failures), null);
        } catch (GithubRateLimitException e) {
            // Every further request would fail the same way.
            return getStatusFailed(e.getMessage(), e);
        } finally {
            progress.done();
        }
    }

    /** One run of the job, with its counters. */
    private final class Run {
        private final Duration interval;
        private final List<String> failures = new ArrayList<>();
        private int checked;
        private int mails;
        private Mailer smtp;

        private Run(Duration interval) {
            this.interval = interval;
        }

        private void project(String projectId) {
            String scope = ScopeUtils.getScopeFromProject(projectId);
            Set<String> hidden = null;
            for (SettingName name : repositorySettings.readNames(scope)) {
                String key = projectId + "/" + name.getName();
                try {
                    RepositorySettingsModel settings = repositorySettings.read(scope, SettingId.fromName(name.getName()), null);
                    NotificationSettings notifications = settings.getNotifications();
                    if (notifications == null || !notifications.isEnabled()) {
                        continue;
                    }
                    if (hidden == null) {
                        hidden = hiddenItems.urls(projectId);
                    }
                    checked++;
                    List<ImportEntry> candidates = candidates(importService.importRepository(projectId, settings, true, null).getEntries(),
                            notifications, hidden);
                    List<ImportEntry> news = watcher.news(key, candidates, clock.instant(), interval);
                    if (!news.isEmpty()) {
                        mail(projectId, settings, notifications, news);
                    }
                } catch (GithubRateLimitException e) {
                    throw e;
                } catch (RuntimeException e) {
                    failures.add(key + ": " + e.getMessage());
                    getLogger().error("Repository setting '" + key + "' failed: " + e.getMessage());
                }
            }
        }

        private void mail(String projectId, RepositorySettingsModel settings, NotificationSettings notifications, List<ImportEntry> news) {
            List<String> recipients = addresses(notifications.getUsers());
            if (recipients.isEmpty()) {
                throw new IllegalStateException("None of the users " + notifications.getUsers() + " has a mail address");
            }
            if (smtp == null) {
                smtp = mailer.get();
            }
            String repository = settings.getShortName() == null || settings.getShortName().isBlank() ? settings.getRepository() : settings.getShortName();
            NotificationMail mail = new NotificationMail(projectId, repository, System.getProperty("base.url"));
            smtp.send(recipients, mail.subject(news), mail.html(news));
            mails++;
            getLogger().info("Mailed %d new item(s) of '%s/%s' to %s".formatted(news.size(), projectId, settings.getName(), recipients));
        }

        private List<String> addresses(@Nullable List<String> userIds) {
            List<String> addresses = new ArrayList<>();
            for (String userId : userIds == null ? List.<String>of() : userIds) {
                // An unknown ID gives a phantom user, without an address.
                IUser user = polarionService.getProjectService().getUser(userId);
                String address = user.getEmail();
                if (address == null || address.isBlank()) {
                    getLogger().warn("User '" + userId + "' has no mail address, left out");
                } else {
                    addresses.add(address.trim());
                }
            }
            return addresses;
        }
    }

    /** The items that call for a mail: open, without a work item, not hidden, and of a kind the setting watches. */
    static @NotNull List<ImportEntry> candidates(@NotNull List<ImportEntry> entries, @NotNull NotificationSettings notifications,
                                                 @NotNull Set<String> hidden) {
        return entries.stream()
                .filter(entry -> entry.getStatus() == ImportStatus.NEW && entry.getUrl() != null && !hidden.contains(entry.getUrl()))
                .filter(entry -> switch (entry.getKind()) {
                    case ISSUE -> notifications.isIssues();
                    case DISCUSSION -> notifications.isDiscussions();
                    case PULL_REQUEST -> notifications.isPullRequests();
                    case ADVISORY -> notifications.isAdvisories();
                })
                .toList();
    }
}
