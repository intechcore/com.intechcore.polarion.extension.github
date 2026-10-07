package com.intechcore.polarion.extension.github.widget;

import com.intechcore.polarion.extension.github.rest.model.ProjectItems;
import com.intechcore.polarion.extension.github.rest.model.RepositoryState;
import com.intechcore.polarion.extension.github.service.ImportEntry;
import com.intechcore.polarion.extension.github.service.ImportStatus;
import com.intechcore.polarion.extension.github.service.ItemKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The static table a PDF export or a print of the widget shows. It must show what the page shows when it
 * opens, and never let text from GitHub become markup.
 */
class ItemsTableHtmlTest {

    private static ImportEntry entry(long number, String setting, ItemKind kind, ImportStatus status) {
        return ImportEntry.builder().number(number).setting(setting).shortName(setting.toUpperCase()).kind(kind).status(status)
                .title("Item " + number).url("https://github.com/acme/" + setting + "/issues/" + number).build();
    }

    private static ProjectItems items(ImportEntry... entries) {
        ProjectItems items = new ProjectItems();
        items.getEntries().addAll(List.of(entries));
        return items;
    }

    @Test
    void showsWhatThePageShowsWhenItOpensWithTheWidgetSettings() {
        ImportEntry hidden = entry(2, "tool", ItemKind.ISSUE, ImportStatus.NEW);
        hidden.setHidden(true);
        ProjectItems items = items(entry(7, "tool", ItemKind.ISSUE, ImportStatus.NEW), hidden,
                entry(8, "tool", ItemKind.ISSUE, ImportStatus.EXISTS), entry(30, "tool", ItemKind.DISCUSSION, ImportStatus.NEW),
                entry(3, "docs", ItemKind.ISSUE, ImportStatus.NEW));

        String html = new ItemsTableHtml("elibrary", List.of("tool"), List.of("ISSUE"), List.of("NEW"), List.of("item", "state")).render(items);

        assertThat(html).contains("<th style=").contains(">Item</th>").contains(">State</th>").doesNotContain(">Labels</th>");
        assertThat(html.indexOf(">Item</th>")).isLessThan(html.indexOf(">State</th>"));
        assertThat(html).contains("<a href=\"https://github.com/acme/tool/issues/7\">#7</a> Item 7").contains(">New</td>")
                .doesNotContain("#2<").doesNotContain("#8<").doesNotContain("#30<").doesNotContain("#3<");
    }

    @Test
    void showsEveryColumnWithoutAChoice() {
        ImportEntry pullRequest = entry(421, "tool", ItemKind.PULL_REQUEST, ImportStatus.OUTDATED);
        pullRequest.setMessage("differs in title");
        pullRequest.setFailedChecks("build");
        pullRequest.setGithubType("Bug");
        pullRequest.setLabels(List.of("bug", "help wanted", "plain"));
        pullRequest.setLabelColors(Map.of("bug", "d73a4a", "help wanted", "a2eeef"));
        pullRequest.setAssignees(List.of("alice", "bob"));
        pullRequest.setWorkItemId("EL 12");
        pullRequest.setWorkItemTypeIcon("/polarion/icons/defect.gif");
        pullRequest.setWorkItemStatus("In Progress");
        pullRequest.setWorkItemStatusIcon("javascript:alert(1)");
        pullRequest.setWorkItemAssignees(List.of("Rob Project"));

        String html = new ItemsTableHtml("e library", List.of(), List.of(), List.of(), List.of()).render(items(pullRequest));

        assertThat(html).contains(">Repository</th>").contains(">Polarion assignees</th>").contains(">TOOL</td>")
                .contains("Pull request</span> <a href=\"https://github.com/acme/tool/issues/421\">#421</a>")
                .contains("Failed checks: build")
                .contains("background:#d73a4a;color:#ffffff;").contains("background:#a2eeef;color:#1f2328;").contains("background:#e8e8e8;")
                .contains(">alice, bob</td>").contains(">Out of date: differs in title</td>")
                .contains("<img src=\"/polarion/icons/defect.gif\"")
                .contains("<a href=\"/polarion/#/project/e+library/workitem?id=EL+12\">EL 12</a>")
                .contains(">Rob Project</td>")
                // Only a path of Polarion becomes an icon.
                .doesNotContain("javascript:").contains(">In Progress</td>");
    }

    @Test
    void escapesEveryValueAndLinksOnlyToGithub() {
        ImportEntry entry = entry(1, "tool", ItemKind.ISSUE, ImportStatus.NEW);
        entry.setTitle("<script>alert('x')</script> & \"more\"");
        entry.setUrl("javascript:alert(1)");
        entry.setLabels(List.of("<b>"));
        entry.setLabelColors(Map.of("<b>", "red;x"));
        entry.setShortName(" ");

        String html = new ItemsTableHtml("elibrary", List.of(), List.of(), List.of(), List.of("repository", "item", "labels")).render(items(entry));

        assertThat(html).doesNotContain("<script>").doesNotContain("javascript:").doesNotContain("red;x")
                .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;more&quot;")
                .contains(">tool</td>").contains("#1 ").contains("&lt;b&gt;");
    }

    @Test
    void namesTheRepositoriesThatFailedAndSaysWhenNothingIsLeft() {
        ProjectItems items = new ProjectItems();
        items.getRepositories().add(new RepositoryState("broken", "acme/broken", null, "Discussions are turned off <here>"));
        items.getRepositories().add(new RepositoryState("draft", null, null, "The repository must be given"));
        items.getRepositories().add(new RepositoryState("other", "acme/other", null, "Not shown"));
        items.getRepositories().add(new RepositoryState("fine", "acme/fine", "2026-10-07T08:00:00Z", null));

        String html = new ItemsTableHtml("elibrary", List.of("broken", "draft", "fine"), List.of(), List.of(), List.of()).render(items);

        assertThat(html).contains("broken (acme/broken): Discussions are turned off &lt;here&gt;")
                .contains("draft: The repository must be given").doesNotContain("Not shown")
                .contains("No open GitHub item.").doesNotContain("<table");
        assertThat(ItemsTableHtml.escape(null)).isEmpty();
    }
}
