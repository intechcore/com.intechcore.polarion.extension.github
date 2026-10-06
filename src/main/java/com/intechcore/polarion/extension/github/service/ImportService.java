package com.intechcore.polarion.extension.github.service;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import com.intechcore.polarion.extension.github.client.GithubClient;
import com.intechcore.polarion.extension.github.client.GithubItem;
import com.intechcore.polarion.extension.github.settings.DuplicateKey;
import com.intechcore.polarion.extension.github.settings.ItemRule;
import com.intechcore.polarion.extension.github.settings.ItemSettings;
import com.intechcore.polarion.extension.github.settings.RepositorySettingsModel;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.tracker.model.IHyperlinkRoleOpt;
import com.polarion.alm.projects.model.IUser;
import com.polarion.alm.tracker.model.IHyperlinkStruct;
import com.polarion.alm.tracker.model.IStatusOpt;
import com.polarion.alm.tracker.model.ILinkRoleOpt;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.types.Text;
import com.polarion.platform.persistence.IEnumOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
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
        this(new PolarionService(), GithubClient.shared(), new WriteTransaction() {
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
        Instant readAt = githubClient.readAt(name[0], name[1]);
        result.setReadAt(readAt == null ? null : readAt.toString());
        return result;
    }

    /**
     * Makes the next import of the repository read GitHub again instead of the cache, unless its
     * lists were read within the last minute.
     */
    public void refresh(@NotNull RepositorySettingsModel settings) {
        settings.validate();
        String[] name = settings.getRepository().split("/");
        githubClient.forget(name[0], name[1]);
    }

    private static boolean isEnabled(@Nullable ItemSettings settings) {
        return settings != null && settings.isEnabled();
    }

    /**
     * Looks up everything the settings name in the project, so a wrong type, epic or role fails the
     * import once and not for every item.
     */
    private Target resolveTarget(ITrackerProject project, ItemSettings settings) {
        boolean linked = settings.getEpicId() != null && !settings.getEpicId().isBlank();
        IWorkItem epic = linked ? polarionService.getWorkItem(project.getId(), settings.getEpicId()) : null;
        Map<String, String> fields = settings.getFields() == null ? Map.of() : settings.getFields();
        Outcome fallback = resolveOutcome(project, settings, settings.getWorkItemType(), fields, linked);

        List<ResolvedRule> rules = new ArrayList<>();
        if (settings.getRules() != null) {
            for (ItemRule rule : settings.getRules()) {
                Outcome outcome = null;
                if (!rule.isSkip()) {
                    Map<String, String> merged = new LinkedHashMap<>(fields);
                    if (rule.getFields() != null) {
                        merged.putAll(rule.getFields());
                    }
                    outcome = resolveOutcome(project, settings, rule.getWorkItemType(), merged, linked);
                }
                rules.add(new ResolvedRule(rule, outcome));
            }
        }
        return new Target(project, settings, epic, fallback, rules);
    }

    private static Outcome resolveOutcome(ITrackerProject project, ItemSettings settings, String typeId,
                                          Map<String, String> fields, boolean linked) {
        ITypeOpt type = project.getWorkItemTypeEnum().wrapOption(typeId);
        if (type.isPhantom()) {
            throw new IllegalArgumentException("The project '%s' has no work item type '%s'".formatted(project.getId(), typeId));
        }
        ILinkRoleOpt epicRole = null;
        if (linked) {
            // A link role can be limited to work item types, so it is looked up for each type.
            epicRole = project.getWorkItemLinkRoleEnum().wrapOption(settings.getEpicLinkRole(), type);
            if (epicRole.isPhantom()) {
                throw new IllegalArgumentException("The project '%s' has no link role '%s'".formatted(project.getId(), settings.getEpicLinkRole()));
            }
        }
        return new Outcome(type, epicRole, fields);
    }

    private static boolean matches(ItemRule rule, GithubItem item) {
        String value = rule.getValue().trim();
        return switch (rule.getMatch()) {
            case LABEL -> item.labelNames().stream().anyMatch(value::equalsIgnoreCase);
            case TYPE -> value.equalsIgnoreCase(item.typeName());
            case CATEGORY -> value.equalsIgnoreCase(item.categoryName());
        };
    }

    private void importItems(ItemKind kind, List<GithubItem> items, Target target, RepositorySettingsModel settings,
                             ImportResult result, @Nullable Collection<String> onlyUrls) {
        String urlPrefix = GITHUB_URL + settings.getRepository() + "/";
        Map<String, Existing> existing = findExisting(target, urlPrefix);
        for (GithubItem item : items) {
            String url = item.htmlUrl();
            if (onlyUrls != null && !onlyUrls.contains(url)) {
                continue;
            }
            ImportEntry entry = ImportEntry.builder()
                    .kind(kind).number(item.number()).title(item.title()).url(url)
                    .setting(settings.getName()).repository(settings.getRepository()).shortName(settings.getShortName())
                    .githubType(kind == ItemKind.ISSUE ? item.typeName() : item.categoryName())
                    .labels(item.labelNames()).labelColors(item.labelColors()).assignees(item.assigneeLogins())
                    .build();
            result.getEntries().add(entry);
            if (url == null || !url.startsWith(urlPrefix)) {
                entry.setStatus(ImportStatus.FAILED);
                entry.setMessage("The item has no URL in the repository");
            } else if (existing.containsKey(url)) {
                Existing workItem = existing.get(url);
                entry.setStatus(ImportStatus.EXISTS);
                entry.setWorkItemId(workItem.id());
                entry.setWorkItemType(workItem.type());
                entry.setWorkItemTypeName(workItem.typeName());
                entry.setWorkItemTypeIcon(workItem.typeIcon());
                entry.setWorkItemStatus(workItem.status());
                entry.setWorkItemAssignees(workItem.assignees());
            } else {
                importNewItem(entry, item, target, settings, result.isDryRun());
                if (entry.getStatus() == ImportStatus.CREATED) {
                    existing.put(url, new Existing(entry.getWorkItemId(), entry.getWorkItemType(), entry.getWorkItemTypeName(),
                            entry.getWorkItemTypeIcon(), null, List.of()));
                }
            }
        }
    }

    /**
     * Decides about an item no work item holds yet. The first matching rule applies. Without one,
     * the settings of the block do.
     */
    private void importNewItem(ImportEntry entry, GithubItem item, Target target, RepositorySettingsModel settings, boolean dryRun) {
        ResolvedRule rule = target.rules().stream().filter(candidate -> matches(candidate.rule(), item)).findFirst().orElse(null);
        if (rule != null && rule.outcome() == null) {
            entry.setStatus(ImportStatus.SKIPPED);
            entry.setMessage("by the rule " + rule.rule().describe());
            return;
        }
        Outcome outcome = rule == null ? target.fallback() : rule.outcome();
        entry.setWorkItemType(outcome.type().getId());
        entry.setWorkItemTypeName(outcome.type().getName());
        entry.setWorkItemTypeIcon(iconOf(outcome.type()));
        if (dryRun) {
            entry.setStatus(ImportStatus.NEW);
        } else {
            create(entry, item, target, outcome, settings);
        }
    }

    private void create(ImportEntry entry, GithubItem item, Target target, Outcome outcome, RepositorySettingsModel settings) {
        try {
            entry.setWorkItemId(writeTransaction.execute(() -> createWorkItem(item, target, outcome, settings)));
            entry.setStatus(ImportStatus.CREATED);
        } catch (RuntimeException e) {
            entry.setStatus(ImportStatus.FAILED);
            entry.setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private String createWorkItem(GithubItem item, Target target, Outcome outcome, RepositorySettingsModel settings) {
        ItemSettings itemSettings = target.settings();
        Map<String, String> values = templateValues(item, settings);

        IWorkItem workItem = polarionService.getTrackerService().createWorkItem(target.project());
        workItem.setType(outcome.type());
        workItem.setTitle(TemplateRenderer.renderText(itemSettings.getTitleTemplate(), values));
        if (itemSettings.getDescriptionTemplate() != null && !itemSettings.getDescriptionTemplate().isBlank()) {
            workItem.setDescription(Text.html(TemplateRenderer.renderHtml(itemSettings.getDescriptionTemplate(), values)));
        }
        outcome.fields().forEach((fieldId, value) -> polarionService.setFieldValue(workItem, fieldId, value));
        if (itemSettings.getDuplicateKey() == DuplicateKey.CUSTOM_FIELD) {
            polarionService.setFieldValue(workItem, itemSettings.getDuplicateKeyField(), item.htmlUrl());
        } else {
            IHyperlinkRoleOpt role = target.project().getHyperlinkRoleEnum().wrapOption(HYPERLINK_ROLE);
            workItem.addHyperlink(item.htmlUrl(), role);
        }
        if (target.epic() != null) {
            workItem.addLinkedItem(target.epic(), outcome.epicRole(), null, false);
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
        values.put("labels", String.join(", ", item.labelNames()));
        values.put("type", item.typeName());
        values.put("category", item.categoryName());
        return values;
    }

    /**
     * The GitHub URLs the work items of the project hold already, with what the page shows of the work item.
     * The search goes through SQL: the Lucene index does not hold hyperlinks.
     */
    @SuppressWarnings("unchecked") // ITrackerService.queryWorkItems is declared with a raw IPObjectList
    private Map<String, Existing> findExisting(Target target, String urlPrefix) {
        ItemSettings settings = target.settings();
        boolean customField = settings.getDuplicateKey() == DuplicateKey.CUSTOM_FIELD;
        String projectId = sqlLiteral(target.project().getId());
        String query = customField
                ? SQL_CUSTOM_FIELD.formatted(projectId, sqlLiteral(settings.getDuplicateKeyField()), sqlLiteral(urlPrefix))
                : SQL_HYPERLINK.formatted(projectId, sqlLiteral(urlPrefix));

        Map<String, Existing> existing = new LinkedHashMap<>();
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

    private static void addExisting(Map<String, Existing> existing, @Nullable Object url, IWorkItem workItem, String urlPrefix) {
        // The SQL pattern is a prefix match where "_" matches any character, so check again here.
        if (url != null && url.toString().startsWith(urlPrefix)) {
            existing.computeIfAbsent(url.toString(), key -> Existing.of(workItem));
        }
    }

    private static String sqlLiteral(String value) {
        if (value == null || !SQL_SAFE.matcher(value).matches()) {
            throw new IllegalArgumentException("Not usable in a work item search: " + value);
        }
        return value;
    }

    /** A work item that holds a GitHub URL, as the page shows it. */
    private record Existing(String id, @Nullable String type, @Nullable String typeName, @Nullable String typeIcon,
                            @Nullable String status, List<String> assignees) {

        @SuppressWarnings("unchecked") // IWorkItem.getAssignees is declared with a raw IPObjectList
        static Existing of(IWorkItem workItem) {
            ITypeOpt type = workItem.getType();
            IStatusOpt status = workItem.getStatus();
            List<String> assignees = new ArrayList<>();
            List<Object> users = workItem.getAssignees();
            for (Object user : users) {
                if (user instanceof IUser assignee) {
                    assignees.add(assignee.getName() == null ? assignee.getId() : assignee.getName());
                }
            }
            return new Existing(workItem.getId(), type == null ? null : type.getId(), type == null ? null : type.getName(),
                    iconOf(type), status == null ? null : status.getName(), assignees);
        }
    }

    /** The icon Polarion shows for a work item type, as configured in the project. */
    private static @Nullable String iconOf(@Nullable ITypeOpt type) {
        return type == null ? null : type.getProperty(IEnumOption.PROPERTY_KEY_ICON_URL);
    }

    /** What the import needs of one block of the settings, looked up in the project. */
    private record Target(ITrackerProject project, ItemSettings settings, @Nullable IWorkItem epic, Outcome fallback, List<ResolvedRule> rules) {
    }

    /** The work item an item becomes: its type, the role of its link to the epic, and its field values. */
    private record Outcome(ITypeOpt type, @Nullable ILinkRoleOpt epicRole, Map<String, String> fields) {
    }

    /** A rule with its outcome. A rule that leaves items out has none. */
    private record ResolvedRule(ItemRule rule, @Nullable Outcome outcome) {
    }
}
