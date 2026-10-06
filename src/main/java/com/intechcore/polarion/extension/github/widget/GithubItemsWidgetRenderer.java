package com.intechcore.polarion.extension.github.widget;

import com.polarion.alm.server.api.model.rp.widget.AbstractWidgetRenderer;
import com.polarion.alm.shared.api.model.rp.parameter.BooleanParameter;
import com.polarion.alm.shared.api.model.rp.parameter.CustomEnumParameter;
import com.polarion.alm.shared.api.model.rp.parameter.StringParameter;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetCommonContext;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Writes the page of the GitHub items into an iframe. Everything the widget settings say travels in
 * the query string of the page.
 */
public class GithubItemsWidgetRenderer extends AbstractWidgetRenderer {

    private static final String APP_URL = "/polarion/github-app/ui/app/index.html";

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

    public GithubItemsWidgetRenderer(@NotNull RichPageWidgetCommonContext context) {
        super(context);
        projectId = context.getDisplayedScope().projectId();
        StringParameter repositoriesParameter = context.parameter(GithubItemsWidget.PARAMETER_REPOSITORIES);
        String names = repositoriesParameter.value();
        repositories = names == null ? List.of() : Arrays.stream(names.split(",")).map(String::trim).filter(name -> !name.isEmpty()).toList();
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
