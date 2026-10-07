package com.intechcore.polarion.extension.github.watch;

import com.intechcore.polarion.extension.github.service.ImportEntry;
import com.intechcore.polarion.extension.github.service.ItemKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * The mail of one check of one repository: the new items, each with its number as the link to GitHub,
 * and a link to the GitHub topic of the project. Every value from GitHub is escaped.
 */
public class NotificationMail {

    private static final Map<ItemKind, String> KINDS = Map.of(
            ItemKind.ISSUE, "Issue", ItemKind.DISCUSSION, "Discussion", ItemKind.PULL_REQUEST, "Pull request with failed checks");

    private final String projectId;
    private final String repository;
    private final @Nullable String baseUrl;

    /**
     * @param repository the short name or the owner/name of the repository
     * @param baseUrl    the address of Polarion, from {@code base.url}, or null without it
     */
    public NotificationMail(@NotNull String projectId, @NotNull String repository, @Nullable String baseUrl) {
        this.projectId = projectId;
        this.repository = repository;
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.replaceAll("/+$", "");
    }

    public @NotNull String subject(@NotNull List<ImportEntry> entries) {
        return "[GitHub] " + repository + ": " + entries.size() + (entries.size() == 1 ? " new item" : " new items");
    }

    public @NotNull String html(@NotNull List<ImportEntry> entries) {
        StringBuilder html = new StringBuilder("<p>New in <b>").append(escape(repository)).append("</b>, project ")
                .append(escape(projectId)).append(":</p><ul>");
        for (ImportEntry entry : entries) {
            String number = "#" + entry.getNumber();
            String link = entry.getUrl() != null && entry.getUrl().startsWith("https://github.com/")
                    ? "<a href=\"" + escape(entry.getUrl()) + "\">" + number + "</a>" : number;
            html.append("<li>").append(escape(KINDS.get(entry.getKind()))).append(' ').append(link).append(' ')
                    .append(escape(entry.getTitle()));
            if (entry.getFailedChecks() != null) {
                html.append(" (failed: ").append(escape(entry.getFailedChecks())).append(')');
            }
            html.append("</li>");
        }
        html.append("</ul>");
        if (baseUrl != null) {
            String topic = baseUrl + "/polarion/#/project/" + URLEncoder.encode(projectId, StandardCharsets.UTF_8) + "/github";
            html.append("<p><a href=\"").append(escape(topic)).append("\">Open the GitHub page of the project</a> to create work items.</p>");
        }
        return html.toString();
    }

    static @NotNull String escape(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
