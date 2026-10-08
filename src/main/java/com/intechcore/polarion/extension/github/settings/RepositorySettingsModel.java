package com.intechcore.polarion.extension.github.settings;

import ch.sbb.polarion.extension.generic.settings.SettingsModel;
import com.intechcore.polarion.extension.github.service.TemplateRenderer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The import settings of one GitHub repository.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
// A field this version does not know, such as the dropped job flag, must not refuse the setting.
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "The import settings of one GitHub repository")
public class RepositorySettingsModel extends SettingsModel {

    private static final String REPOSITORY_ENTRY = "REPOSITORY";
    private static final String SHORT_NAME_ENTRY = "SHORT_NAME";
    private static final String ISSUES_ENTRY = "ISSUES";
    private static final String DISCUSSIONS_ENTRY = "DISCUSSIONS";
    private static final String PULL_REQUESTS_ENTRY = "PULL_REQUESTS";
    private static final String PULL_REQUEST_AUTHORS_ENTRY = "PULL_REQUEST_AUTHORS";
    private static final String NOTIFICATIONS_ENTRY = "NOTIFICATIONS";
    private static final String ADVISORIES_ENTRY = "ADVISORIES";

    /** The bot whose pull requests a new setting watches. */
    public static final String DEFAULT_PULL_REQUEST_AUTHORS = "renovate[bot]";

    private static final Pattern REPOSITORY_PATTERN = Pattern.compile("(?!\\.+/)[A-Za-z0-9._-]+/(?!\\.+$)[A-Za-z0-9._-]+");

    @Schema(description = "The repository as owner/name", example = "intechcore/com.intechcore.polarion.extension.github")
    private String repository;

    @Schema(description = "The short project name used in work item titles")
    private String shortName;

    @Schema(description = "How issues become work items")
    private ItemSettings issues;

    @Schema(description = "How discussions become work items")
    private ItemSettings discussions;

    @Schema(description = "How open pull requests with failed checks become work items")
    private ItemSettings pullRequests;

    @Schema(description = "The GitHub logins whose pull requests the import watches, separated by commas", example = DEFAULT_PULL_REQUEST_AUTHORS)
    private String pullRequestAuthors;

    @Schema(description = "How the security advisories of the repository become work items")
    private ItemSettings advisories;

    @Schema(description = "Who hears of new items of the repository, and of which kinds")
    private NotificationSettings notifications;

    @Override
    protected String serializeModelData() {
        return serializeEntry(REPOSITORY_ENTRY, repository) +
                serializeEntry(SHORT_NAME_ENTRY, shortName) +
                serializeEntry(ISSUES_ENTRY, issues) +
                serializeEntry(DISCUSSIONS_ENTRY, discussions) +
                serializeEntry(PULL_REQUESTS_ENTRY, pullRequests) +
                serializeEntry(PULL_REQUEST_AUTHORS_ENTRY, pullRequestAuthors) +
                serializeEntry(ADVISORIES_ENTRY, advisories) +
                serializeEntry(NOTIFICATIONS_ENTRY, notifications);
    }

    @Override
    protected void deserializeModelData(String serializedString) {
        repository = deserializeEntry(REPOSITORY_ENTRY, serializedString);
        shortName = deserializeEntry(SHORT_NAME_ENTRY, serializedString);
        issues = deserializeEntry(ISSUES_ENTRY, serializedString, ItemSettings.class, new ItemSettings());
        discussions = deserializeEntry(DISCUSSIONS_ENTRY, serializedString, ItemSettings.class, new ItemSettings());
        pullRequests = deserializeEntry(PULL_REQUESTS_ENTRY, serializedString, ItemSettings.class, new ItemSettings());
        pullRequestAuthors = deserializeEntry(PULL_REQUEST_AUTHORS_ENTRY, serializedString, DEFAULT_PULL_REQUEST_AUTHORS);
        advisories = deserializeEntry(ADVISORIES_ENTRY, serializedString, ItemSettings.class, new ItemSettings());
        notifications = deserializeEntry(NOTIFICATIONS_ENTRY, serializedString, NotificationSettings.class, new NotificationSettings());
    }

    /**
     * Rejects settings the import cannot run with.
     *
     * @throws IllegalArgumentException with the reason
     */
    public void validate() {
        if (repository == null || !REPOSITORY_PATTERN.matcher(repository).matches()) {
            throw new IllegalArgumentException("The repository must be given as owner/name");
        }
        if (isBlank(shortName)) {
            throw new IllegalArgumentException("The short name is required");
        }
        validate("issues", issues, Set.of(RuleMatch.CATEGORY));
        validate("discussions", discussions, Set.of(RuleMatch.TYPE));
        validate("pull requests", pullRequests, Set.of(RuleMatch.TYPE, RuleMatch.CATEGORY));
        validate("security advisories", advisories, Set.of(RuleMatch.LABEL, RuleMatch.TYPE, RuleMatch.CATEGORY));
        if (pullRequests != null && pullRequests.isEnabled() && authors().isEmpty()) {
            throw new IllegalArgumentException("The authors of the pull requests are required");
        }
        if (notifications != null && notifications.isEnabled() && (notifications.getUsers() == null || notifications.getUsers().isEmpty())) {
            throw new IllegalArgumentException("The users who get the notifications are required");
        }
    }

    /** The GitHub logins whose pull requests the import watches. */
    public @NotNull List<String> authors() {
        return pullRequestAuthors == null ? List.of() : Arrays.stream(pullRequestAuthors.split(","))
                .map(String::trim).filter(author -> !author.isEmpty()).toList();
    }

    private static void validate(String kind, ItemSettings settings, Set<RuleMatch> notForThisKind) {
        if (settings == null || !settings.isEnabled()) {
            return;
        }
        if (isBlank(settings.getWorkItemType())) {
            throw new IllegalArgumentException("The work item type for " + kind + " is required");
        }
        if (isBlank(settings.getTitleTemplate())) {
            throw new IllegalArgumentException("The title template for " + kind + " is required");
        }
        validateTemplate("The title template for " + kind, settings.getTitleTemplate(), TemplateRenderer.TITLE_PLACEHOLDERS);
        List<String> descriptionPlaceholders = new ArrayList<>(TemplateRenderer.TITLE_PLACEHOLDERS);
        descriptionPlaceholders.add(TemplateRenderer.BODY);
        validateTemplate("The description template for " + kind, settings.getDescriptionTemplate(), descriptionPlaceholders);
        if (settings.getDuplicateKey() == DuplicateKey.CUSTOM_FIELD && isBlank(settings.getDuplicateKeyField())) {
            throw new IllegalArgumentException("The custom field that keeps the URL for " + kind + " is required");
        }
        if (!isBlank(settings.getEpicId()) && isBlank(settings.getEpicLinkRole())) {
            throw new IllegalArgumentException("The link role to the epic for " + kind + " is required");
        }
        if (settings.getRules() != null) {
            for (int i = 0; i < settings.getRules().size(); i++) {
                validate("Rule " + (i + 1) + " for " + kind, settings.getRules().get(i), notForThisKind);
            }
        }
    }

    private static void validateTemplate(String template, String text, List<String> placeholders) {
        if (text == null) {
            return;
        }
        List<String> problems = TemplateRenderer.problems(text, placeholders);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(template + " " + String.join("; ", problems));
        }
    }

    private static void validate(String rule, ItemRule settings, Set<RuleMatch> notForThisKind) {
        if (settings == null || settings.getMatch() == null) {
            throw new IllegalArgumentException(rule + " needs what to compare");
        }
        if (notForThisKind.contains(settings.getMatch())) {
            throw new IllegalArgumentException(rule + " compares what these items do not have");
        }
        if (isBlank(settings.getValue())) {
            throw new IllegalArgumentException(rule + " needs the value to compare with");
        }
        if (!settings.isSkip() && isBlank(settings.getWorkItemType())) {
            throw new IllegalArgumentException(rule + " needs a work item type");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
