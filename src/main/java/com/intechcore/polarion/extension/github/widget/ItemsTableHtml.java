package com.intechcore.polarion.extension.github.widget;

import com.intechcore.polarion.extension.github.rest.model.ProjectItems;
import com.intechcore.polarion.extension.github.rest.model.RepositoryState;
import com.intechcore.polarion.extension.github.service.ImportEntry;
import com.intechcore.polarion.extension.github.service.ImportStatus;
import com.intechcore.polarion.extension.github.service.ItemKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * The table of the widget as static HTML, for a PDF export or a print of the page: an iframe has no
 * content there. It shows what the page shows when it opens with the settings of the widget.
 * Every value from GitHub or Polarion is escaped; the styles are inline, since the export carries
 * no style sheet of this extension.
 */
public class ItemsTableHtml {

    private static final Map<ItemKind, String> KIND_LABELS = Map.of(
            ItemKind.ISSUE, "Issue", ItemKind.DISCUSSION, "Discussion", ItemKind.PULL_REQUEST, "Pull request");

    private static final Map<ImportStatus, String> STATUS_LABELS = Map.of(
            ImportStatus.NEW, "New",
            ImportStatus.CREATED, "Created",
            ImportStatus.EXISTS, "Has a work item",
            ImportStatus.OUTDATED, "Out of date",
            ImportStatus.UPDATED, "Updated",
            ImportStatus.SKIPPED, "Left out",
            ImportStatus.FAILED, "Failed");

    private static final String CELL = "border:1px solid #d5d5d5;padding:3px 6px;text-align:left;vertical-align:top;";
    private static final String ICON = "width:16px;height:16px;vertical-align:text-bottom;margin-right:4px;";

    private final String projectId;
    private final List<String> repositories;
    private final List<String> kinds;
    private final List<String> states;
    private final List<String> columns;

    /**
     * @param repositories the settings to show, or none for all
     * @param kinds        the kinds to show, or none for all
     * @param states       the states to show, or none for all
     * @param columns      the columns in their order, or none for all in the default order
     */
    public ItemsTableHtml(@NotNull String projectId, @NotNull List<String> repositories, @NotNull List<String> kinds,
                          @NotNull List<String> states, @NotNull List<String> columns) {
        this.projectId = projectId;
        this.repositories = repositories;
        this.kinds = kinds;
        this.states = states;
        this.columns = columns.isEmpty() ? List.copyOf(GithubItemsWidget.COLUMNS.keySet()) : columns;
    }

    public @NotNull String render(@NotNull ProjectItems items) {
        StringBuilder html = new StringBuilder("<div class=\"github-items\">");
        for (RepositoryState state : items.getRepositories()) {
            if (state.getError() != null && shows(repositories, state.getSetting())) {
                html.append("<p style=\"color:#b42318;\">").append(escape(state.getSetting()))
                        .append(state.getRepository() == null ? "" : " (" + escape(state.getRepository()) + ")")
                        .append(": ").append(escape(state.getError())).append("</p>");
            }
        }
        List<ImportEntry> shown = items.getEntries().stream().filter(this::shows).toList();
        if (shown.isEmpty()) {
            return html.append("<p>No open GitHub item.</p></div>").toString();
        }
        html.append("<table style=\"border-collapse:collapse;width:100%;font-size:9pt;\"><thead><tr>");
        for (String column : columns) {
            html.append("<th style=\"").append(CELL).append("background:#f3f3f0;\">")
                    .append(escape(GithubItemsWidget.COLUMNS.get(column))).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (ImportEntry entry : shown) {
            html.append("<tr>");
            for (String column : columns) {
                html.append("<td style=\"").append(CELL).append("\">").append(cell(column, entry)).append("</td>");
            }
            html.append("</tr>");
        }
        return html.append("</tbody></table></div>").toString();
    }

    /** What the page shows when it opens: no hidden item, and only what the filters of the widget let through. */
    private boolean shows(ImportEntry entry) {
        return !entry.isHidden()
                && shows(repositories, entry.getSetting())
                && shows(kinds, entry.getKind() == null ? null : entry.getKind().name())
                && shows(states, entry.getStatus() == null ? null : entry.getStatus().name());
    }

    private static boolean shows(List<String> wanted, @Nullable String value) {
        return wanted.isEmpty() || wanted.contains(value);
    }

    private String cell(String column, ImportEntry entry) {
        return switch (column) {
            case "repository" -> escape(entry.getShortName() == null || entry.getShortName().isBlank() ? entry.getSetting() : entry.getShortName());
            case "item" -> item(entry);
            case "githubType" -> escape(entry.getGithubType());
            case "labels" -> labels(entry);
            case "assignees" -> escape(join(entry.getAssignees()));
            case "state" -> escape(STATUS_LABELS.get(entry.getStatus())) + (entry.getMessage() == null ? "" : ": " + escape(entry.getMessage()));
            case "workItem" -> workItem(entry);
            case "status" -> entry.getWorkItemStatus() == null ? "" : icon(entry.getWorkItemStatusIcon()) + escape(entry.getWorkItemStatus());
            case "workItemAssignees" -> escape(join(entry.getWorkItemAssignees()));
            default -> "";
        };
    }

    private static String item(ImportEntry entry) {
        String number = "#" + entry.getNumber();
        String link = isGithubUrl(entry.getUrl()) ? "<a href=\"" + escape(entry.getUrl()) + "\">" + number + "</a>" : number;
        String checks = entry.getFailedChecks() == null ? ""
                : "<br/><span style=\"color:#b42318;font-size:85%;\">Failed checks: " + escape(entry.getFailedChecks()) + "</span>";
        return "<span style=\"color:#6b6b6b;\">" + escape(KIND_LABELS.get(entry.getKind())) + "</span> " + link + " "
                + escape(entry.getTitle()) + checks;
    }

    private static String labels(ImportEntry entry) {
        StringBuilder html = new StringBuilder();
        for (String label : entry.getLabels() == null ? List.<String>of() : entry.getLabels()) {
            String color = entry.getLabelColors() == null ? null : entry.getLabelColors().get(label);
            // The colors are six hexadecimal digits: GithubItem lets no other value through.
            String style = color != null && color.matches("[0-9a-f]{6}")
                    ? "background:#" + color + ";color:" + textColor(color) + ";"
                    : "background:#e8e8e8;color:#1f2328;";
            html.append("<span style=\"").append(style).append("border-radius:8px;padding:0 6px;margin:0 3px 2px 0;white-space:nowrap;display:inline-block;\">")
                    .append(escape(label)).append("</span>");
        }
        return html.toString();
    }

    /** Dark text on a light label and white text on a dark one, as GitHub and the page pick it. */
    static String textColor(String color) {
        int red = Integer.parseInt(color.substring(0, 2), 16);
        int green = Integer.parseInt(color.substring(2, 4), 16);
        int blue = Integer.parseInt(color.substring(4, 6), 16);
        return (red * 299 + green * 587 + blue * 114) / 1000 > 150 ? "#1f2328" : "#ffffff";
    }

    private String workItem(ImportEntry entry) {
        if (entry.getWorkItemId() == null) {
            return "";
        }
        String href = "/polarion/#/project/" + encode(projectId) + "/workitem?id=" + encode(entry.getWorkItemId());
        return icon(entry.getWorkItemTypeIcon()) + "<a href=\"" + escape(href) + "\">" + escape(entry.getWorkItemId()) + "</a>";
    }

    // Polarion serves the icons of types and statuses from its own paths.
    private static String icon(@Nullable String url) {
        return url == null || !url.startsWith("/") ? "" : "<img src=\"" + escape(url) + "\" alt=\"\" style=\"" + ICON + "\"/>";
    }

    private static boolean isGithubUrl(@Nullable String url) {
        return url != null && url.startsWith("https://github.com/");
    }

    private static String join(@Nullable List<String> values) {
        return values == null ? "" : String.join(", ", values);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static @NotNull String escape(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
