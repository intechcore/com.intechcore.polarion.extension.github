package com.intechcore.polarion.extension.github.widget;

import com.polarion.alm.server.api.model.rp.widget.AbstractWidgetRenderer;
import com.polarion.alm.shared.api.model.eo.EnumOption;
import com.polarion.alm.shared.api.model.rp.parameter.BooleanParameter;
import com.polarion.alm.shared.api.model.rp.parameter.CustomEnumParameter;
import com.polarion.alm.shared.api.model.rp.parameter.EnumParameter;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetCommonContext;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagBuilder;
import com.polarion.alm.shared.api.utils.html.RichTextRenderTarget;
import com.intechcore.polarion.extension.github.service.ImportService;
import com.intechcore.polarion.extension.github.service.ProjectItemsReader;
import com.intechcore.polarion.extension.github.settings.HiddenItems;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.UUID;

/**
 * Writes the page of the GitHub items into an iframe. Everything the widget settings say travels in
 * the query string of the page.
 */
public class GithubItemsWidgetRenderer extends AbstractWidgetRenderer {

    private static final String APP_URL = "/polarion/github-app/ui/app/index.html";

    static final String SELECT_REPOSITORIES = "Select the repositories in the settings of the GitHub Items widget.";

    /**
     * The height listener of the widget. A file, not a string here: a Java test can assert the text
     * of a script, never what it does; {@code ui/test/widgetHeight.test.ts} drives this file in a browser.
     */
    private static final String HEIGHT_SYNC_RESOURCE = "/js/widget-height.js";
    private static final String HEIGHT_SYNC_SCRIPT = readScript(
            GithubItemsWidgetRenderer.class.getResourceAsStream(HEIGHT_SYNC_RESOURCE), HEIGHT_SYNC_RESOURCE);

    private final @Nullable String projectId;
    private final List<String> repositories;
    private final List<String> kinds;
    private final List<String> states;
    private final List<String> columns;
    private final boolean hideFilters;
    private final boolean allowCreate;
    private final boolean printed;
    private final Supplier<ProjectItemsReader> reader;

    // The targets that turn the page into a document. A PDF export or a print shows no iframe.
    private static final Set<RichTextRenderTarget> DOCUMENT_TARGETS = Set.of(RichTextRenderTarget.PDF_EXPORT,
            RichTextRenderTarget.COMPARE_PDF_EXPORT, RichTextRenderTarget.PRINT, RichTextRenderTarget.COMPARE_PRINT);

    public GithubItemsWidgetRenderer(@NotNull RichPageWidgetCommonContext context) {
        this(context, () -> new ProjectItemsReader(new RepositorySettings(), new ImportService(), new HiddenItems()));
    }

    GithubItemsWidgetRenderer(@NotNull RichPageWidgetCommonContext context, @NotNull Supplier<ProjectItemsReader> reader) {
        super(context);
        this.reader = reader;
        printed = DOCUMENT_TARGETS.contains(context.target());
        projectId = context.getDisplayedScope().projectId();
        EnumParameter repositoriesParameter = context.parameter(GithubItemsWidget.PARAMETER_REPOSITORIES);
        repositories = repositoriesParameter.values().asList().stream().map(EnumOption::id).toList();
        kinds = values(context.parameter(GithubItemsWidget.PARAMETER_KINDS));
        states = values(context.parameter(GithubItemsWidget.PARAMETER_STATES));
        columns = values(context.parameter(GithubItemsWidget.PARAMETER_COLUMNS));
        hideFilters = isTrue(context.parameter(GithubItemsWidget.PARAMETER_HIDE_FILTERS));
        allowCreate = isTrue(context.parameter(GithubItemsWidget.PARAMETER_ALLOW_CREATE));
    }

    // A widget saved before a parameter existed has none of it.
    private static List<String> values(@Nullable CustomEnumParameter parameter) {
        return parameter == null || parameter.values() == null ? List.of() : parameter.values().asList();
    }

    private static boolean isTrue(@Nullable BooleanParameter parameter) {
        return parameter != null && parameter.value();
    }

    @Override
    protected void render(@NotNull HtmlFragmentBuilder builder) {
        // A new widget names no repository. It asks for them, rather than show every repository of the
        // project, which may be many and costs GitHub requests for each.
        if (repositories.isEmpty()) {
            builder.html(context.renderInfo(SELECT_REPOSITORIES));
            return;
        }
        if (printed) {
            builder.html(printedTable());
            return;
        }
        String iframeId = "github-items-" + UUID.randomUUID();

        HtmlTagBuilder iframe = builder.tag().byName("iframe");
        iframe.attributes()
                .id(iframeId)
                .byName("src", appUrl())
                .byName("scrolling", "no")
                .width("100%")
                .style("border:0;width:100%;min-height:120px;");

        // The id is a UUID this method made, so it needs no escaping.
        builder.tag().script().append().javaScript(HEIGHT_SYNC_SCRIPT + "%ngithubSyncIframeHeight('%s');".formatted(iframeId));
    }

    /** The table as the page shows it when it opens, read on the server, for a document without iframes. */
    @NotNull String printedTable() {
        if (projectId == null) {
            return "<p>The GitHub items belong to a project.</p>";
        }
        return new ItemsTableHtml(projectId, repositories, kinds, states, columns).render(reader.get().read(projectId, false, repositories));
    }

    @NotNull String appUrl() {
        // The page reads the project from the scope, as the topic passes it. A page outside a project
        // has none, and the table says it needs one.
        String scope = projectId == null ? "" : "project/" + projectId + "/";
        return APP_URL + "?feature=items&embedded=true&widget=true"
                + "&scope=" + enc(scope)
                + list("settings", repositories)
                + list("kinds", kinds)
                + list("states", states)
                + list("columns", columns)
                + (hideFilters ? "&hideFilters=true" : "")
                + (allowCreate ? "&allowCreate=true" : "");
    }

    private static String list(String name, List<String> values) {
        return values.isEmpty() ? "" : "&" + name + "=" + enc(String.join(",", values));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Reads a script of the bundle. A missing or unreadable one is a broken build, so it ends the call.
     *
     * @param resource the open resource, or null when the bundle does not carry it
     * @param name     the resource path, for the message
     */
    static @NotNull String readScript(@Nullable InputStream resource, @NotNull String name) {
        if (resource == null) {
            throw new IllegalStateException("Resource is missing from the bundle: " + name);
        }
        try (resource) {
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + name, e);
        }
    }
}
