package com.intechcore.polarion.extension.github.service;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.github.client.GithubClient;
import com.intechcore.polarion.extension.github.client.GithubItem;
import com.intechcore.polarion.extension.github.settings.DuplicateKey;
import com.intechcore.polarion.extension.github.settings.ItemSettings;
import com.intechcore.polarion.extension.github.settings.RepositorySettingsModel;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.tracker.model.IHyperlinkRoleOpt;
import com.polarion.alm.tracker.model.IHyperlinkStruct;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.types.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Creates work items from the open issues and discussions of a GitHub repository. One GitHub item
 * gets one work item: the work item keeps the URL of the item, and the import skips an item whose
 * URL a work item of the project holds already.
 */
public class ImportService {

    static final String GITHUB_URL = "https://github.com/";
    static final String HYPERLINK_ROLE = "ref_ext";

    // Everything that goes into an SQL literal must match this. It leaves no quote and no escape.
    private static final Pattern SQL_SAFE = Pattern.compile("[A-Za-z0-9._:/-]+");
    private static final String SQL_HYPERLINK = "SQL:(select WI.C_URI from WORKITEM WI"
            + " inner join PROJECT P on P.C_URI = WI.FK_URI_PROJECT"
            + " inner join STRUCT_WORKITEM_HYPERLINKS HL on HL.FK_URI_P_WORKITEM = WI.C_URI"
            + " where P.C_ID = '%s' and HL.C_URL like '%s%%')";
    private static final String SQL_CUSTOM_FIELD = "SQL:(select WI.C_URI from WORKITEM WI"
            + " inner join PROJECT P on P.C_URI = WI.FK_URI_PROJECT"
            + " inner join CF_WORKITEM CF on CF.FK_URI_WORKITEM = WI.C_URI"
            + " where P.C_ID = '%s' and CF.C_NAME = '%s' and CF.C_STRING_VALUE like '%s%%')";

    private final PolarionService polarionService;
    private final GithubClient githubClient;
    private final WriteTransaction writeTransaction;

    public ImportService() {
        this(new PolarionService(), new GithubClient(), new WriteTransaction() {
            @Override
            public <T> T execute(Supplier<T> action) {
                return TransactionalExecutor.executeInWriteTransaction(transaction -> action.get());
            }
        });
    }

    public ImportService(@NotNull PolarionService polarionService, @NotNull GithubClient githubClient, @NotNull WriteTransaction writeTransaction) {
        this.polarionService = polarionService;
        this.githubClient = githubClient;
        this.writeTransaction = writeTransaction;
    }

    /**
     * Imports one repository into a project.
     *
     * @param projectId the project that gets the work items
     * @param settings  the settings of the repository
     * @param dryRun    true to report only what the import would do
     * @param onlyUrls  the GitHub URLs to import, or null to import every open item
     * @throws IllegalArgumentException when the settings do not fit the project
     */
    public @NotNull ImportResult importRepository(@NotNull String projectId, @NotNull RepositorySettingsModel settings,
                                                  boolean dryRun, @Nullable Collection<String> onlyUrls) {
        settings.validate();
        ITrackerProject project = polarionService.getTrackerProject(projectId);
        String[] name = settings.getRepository().split("/");
        ImportResult result = new ImportResult(settings.getRepository(), dryRun);

        // Everything that can fail as a whole happens before the first work item: a wrong setting or
        // a GitHub failure then leaves the project untouched, and no outcome is lost.
        Target issueTarget = isEnabled(settings.getIssues()) ? resolveTarget(project, settings.getIssues()) : null;
        Target discussionTarget = isEnabled(settings.getDiscussions()) ? resolveTarget(project, settings.getDiscussions()) : null;
        List<GithubItem> issues = issueTarget == null ? List.of() : githubClient.getOpenIssues(name[0], name[1]);
        List<GithubItem> discussions = discussionTarget == null ? List.of() : githubClient.getOpenDiscussions(name[0], name[1]);

        if (issueTarget != null) {
            importItems(ItemKind.ISSUE, issues, issueTarget, settings, result, onlyUrls);
        }
        if (discussionTarget != null) {
            importItems(ItemKind.DISCUSSION, discussions, discussionTarget, settings, result, onlyUrls);
        }
        return result;
    }

    private static boolean isEnabled(@Nullable ItemSettings settings) {
        return settings != null && settings.isEnabled();
    }

    /**
     * Looks up everything the settings name in the project, so a wrong type, epic or role fails the
     * import once and not for every item.
     */
    private Target resolveTarget(ITrackerProject project, ItemSettings settings) {
        ITypeOpt type = project.getWorkItemTypeEnum().wrapOption(settings.getWorkItemType());
        if (type.isPhantom()) {
            throw new IllegalArgumentException("The project '%s' has no work item type '%s'".formatted(project.getId(), settings.getWorkItemType()));
        }
        IWorkItem epic = null;
        ILinkRoleOpt epicRole = null;
        if (settings.getEpicId() != null && !settings.getEpicId().isBlank()) {
            epic = polarionService.getWorkItem(project.getId(), settings.getEpicId());
            epicRole = project.getWorkItemLinkRoleEnum().wrapOption(settings.getEpicLinkRole(), type);
            if (epicRole.isPhantom()) {
                throw new IllegalArgumentException("The project '%s' has no link role '%s'".formatted(project.getId(), settings.getEpicLinkRole()));
            }
        }
        return new Target(project, settings, type, epic, epicRole);
    }

    private void importItems(ItemKind kind, List<GithubItem> items, Target target, RepositorySettingsModel settings,
                             ImportResult result, @Nullable Collection<String> onlyUrls) {
        String urlPrefix = GITHUB_URL + settings.getRepository() + "/";
        Map<String, String> existing = findExisting(target, urlPrefix);
        for (GithubItem item : items) {
            String url = item.htmlUrl();
            if (onlyUrls != null && !onlyUrls.contains(url)) {
                continue;
            }
            ImportEntry entry = ImportEntry.builder().kind(kind).number(item.number()).title(item.title()).url(url).build();
            result.getEntries().add(entry);
            if (url == null || !url.startsWith(urlPrefix)) {
                entry.setStatus(ImportStatus.FAILED);
                entry.setMessage("The item has no URL in the repository");
            } else if (existing.containsKey(url)) {
                entry.setStatus(ImportStatus.EXISTS);
                entry.setWorkItemId(existing.get(url));
            } else if (result.isDryRun()) {
                entry.setStatus(ImportStatus.NEW);
            } else {
                create(entry, item, target, settings);
                if (entry.getStatus() == ImportStatus.CREATED) {
                    existing.put(url, entry.getWorkItemId());
                }
            }
        }
    }

    private void create(ImportEntry entry, GithubItem item, Target target, RepositorySettingsModel settings) {
        try {
            entry.setWorkItemId(writeTransaction.execute(() -> createWorkItem(item, target, settings)));
            entry.setStatus(ImportStatus.CREATED);
        } catch (RuntimeException e) {
            entry.setStatus(ImportStatus.FAILED);
            entry.setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private String createWorkItem(GithubItem item, Target target, RepositorySettingsModel settings) {
        ItemSettings itemSettings = target.settings();
        Map<String, String> values = templateValues(item, settings);

        IWorkItem workItem = polarionService.getTrackerService().createWorkItem(target.project());
        workItem.setType(target.type());
        workItem.setTitle(TemplateRenderer.renderText(itemSettings.getTitleTemplate(), values));
        if (itemSettings.getDescriptionTemplate() != null && !itemSettings.getDescriptionTemplate().isBlank()) {
            workItem.setDescription(Text.html(TemplateRenderer.renderHtml(itemSettings.getDescriptionTemplate(), values)));
        }
        if (itemSettings.getFields() != null) {
            itemSettings.getFields().forEach((fieldId, value) -> polarionService.setFieldValue(workItem, fieldId, value));
        }
        if (itemSettings.getDuplicateKey() == DuplicateKey.CUSTOM_FIELD) {
            polarionService.setFieldValue(workItem, itemSettings.getDuplicateKeyField(), item.htmlUrl());
        } else {
            IHyperlinkRoleOpt role = target.project().getHyperlinkRoleEnum().wrapOption(HYPERLINK_ROLE);
            workItem.addHyperlink(item.htmlUrl(), role);
        }
        if (target.epic() != null) {
            workItem.addLinkedItem(target.epic(), target.epicRole(), null, false);
        }
        workItem.save();
        return workItem.getId();
    }

    private static Map<String, String> templateValues(GithubItem item, RepositorySettingsModel settings) {
        Map<String, String> values = new HashMap<>();
        values.put("shortName", settings.getShortName());
        values.put("repository", settings.getRepository());
        values.put("number", String.valueOf(item.number()));
        values.put("title", item.title());
        values.put("author", item.user() == null ? "" : item.user().login());
        values.put("url", item.htmlUrl());
        values.put("body", item.body());
        return values;
    }

    /**
     * The GitHub URLs the work items of the project hold already, with the ID of the work item.
     * The search goes through SQL: the Lucene index does not hold hyperlinks.
     */
    @SuppressWarnings("unchecked") // ITrackerService.queryWorkItems is declared with a raw IPObjectList
    private Map<String, String> findExisting(Target target, String urlPrefix) {
        ItemSettings settings = target.settings();
        boolean customField = settings.getDuplicateKey() == DuplicateKey.CUSTOM_FIELD;
        String projectId = sqlLiteral(target.project().getId());
        String query = customField
                ? SQL_CUSTOM_FIELD.formatted(projectId, sqlLiteral(settings.getDuplicateKeyField()), sqlLiteral(urlPrefix))
                : SQL_HYPERLINK.formatted(projectId, sqlLiteral(urlPrefix));

        Map<String, String> existing = new LinkedHashMap<>();
        List<Object> found = polarionService.getTrackerService().queryWorkItems(query, "id");
        for (Object object : found) {
            if (object instanceof IWorkItem workItem) {
                if (customField) {
                    addExisting(existing, workItem.getCustomField(settings.getDuplicateKeyField()), workItem, urlPrefix);
                } else {
                    for (Object hyperlink : workItem.getHyperlinks()) {
                        addExisting(existing, ((IHyperlinkStruct) hyperlink).getUri(), workItem, urlPrefix);
                    }
                }
            }
        }
        return existing;
    }

    private static void addExisting(Map<String, String> existing, @Nullable Object url, IWorkItem workItem, String urlPrefix) {
        // The SQL pattern is a prefix match where "_" matches any character, so check again here.
        if (url != null && url.toString().startsWith(urlPrefix)) {
            existing.putIfAbsent(url.toString(), workItem.getId());
        }
    }

    private static String sqlLiteral(String value) {
        if (value == null || !SQL_SAFE.matcher(value).matches()) {
            throw new IllegalArgumentException("Not usable in a work item search: " + value);
        }
        return value;
    }

    private record Target(ITrackerProject project, ItemSettings settings, ITypeOpt type, @Nullable IWorkItem epic, @Nullable ILinkRoleOpt epicRole) {
    }
}
