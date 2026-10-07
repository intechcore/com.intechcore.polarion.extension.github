package com.intechcore.polarion.extension.github.widget;

import com.polarion.alm.shared.api.SharedContext;
import com.polarion.alm.shared.api.model.rp.parameter.CustomEnumParameter;
import com.polarion.alm.shared.api.model.rp.parameter.ParameterFactory;
import com.polarion.alm.shared.api.model.rp.parameter.RichPageParameter;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidget;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetContext;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetRenderingContext;
import com.polarion.alm.shared.api.utils.collections.ImmutableStrictList;
import com.polarion.alm.shared.api.utils.collections.ReadOnlyStrictMap;
import com.polarion.alm.shared.api.utils.collections.StrictMap;
import com.polarion.alm.shared.api.utils.collections.StrictMapImpl;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The GitHub items of the project of a Live Report page: the table of the topic GitHub, with its
 * filters, embedded in the page. The settings choose what the table shows when the page opens.
 */
public class GithubItemsWidget extends RichPageWidget {

    public static final String REPORTS = "Reports";

    public static final String PARAMETER_REPOSITORIES = "repositories";
    public static final String PARAMETER_KINDS = "kinds";
    public static final String PARAMETER_STATES = "states";
    public static final String PARAMETER_COLUMNS = "columns";
    public static final String PARAMETER_HIDE_FILTERS = "hideFilters";
    public static final String PARAMETER_ALLOW_CREATE = "allowCreate";

    /** The kinds of items, by the ID the page filters with. */
    static final Map<String, String> KINDS = ordered(
            "ISSUE", "Issue",
            "DISCUSSION", "Discussion",
            "PULL_REQUEST", "Pull request");

    /** The states a filter offers, by the ID the page filters with. */
    static final Map<String, String> STATES = ordered(
            "NEW", "New",
            "EXISTS", "Has a work item",
            "OUTDATED", "Out of date",
            "SKIPPED", "Left out");

    /** The columns of the table, by the ID the page knows them by, in their default order. */
    static final Map<String, String> COLUMNS = ordered(
            "repository", "Repository",
            "item", "Item",
            "githubType", "GitHub type",
            "labels", "Labels",
            "assignees", "GitHub assignees",
            "state", "State",
            "workItem", "Work item",
            "status", "Status",
            "workItemAssignees", "Polarion assignees");

    private static Map<String, String> ordered(String... idsAndNames) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < idsAndNames.length; i += 2) {
            map.put(idsAndNames[i], idsAndNames[i + 1]);
        }
        return map;
    }

    @Override
    public @NotNull String getIcon(@NotNull RichPageWidgetContext context) {
        return "/polarion/github-app/ui/images/widget-icon.svg";
    }

    @Override
    public @NotNull String getLabel(@NotNull SharedContext context) {
        return "GitHub Items";
    }

    @Override
    public @NotNull String getDetailsHtml(@NotNull RichPageWidgetContext context) {
        return "The open GitHub issues, discussions and pull requests of the project, with their work items";
    }

    @Override
    public @NotNull ReadOnlyStrictMap<String, RichPageParameter> getParametersDefinition(@NotNull ParameterFactory factory) {
        StrictMap<String, RichPageParameter> parameters = new StrictMapImpl<>();
        // The repository settings of the project of the page. None chosen shows all of them.
        parameters.put(PARAMETER_REPOSITORIES, factory.enumeration("Repositories", RepositoriesEnumFactory.ENUM_ID)
                .allowMultipleValues(true)
                .build());
        parameters.put(PARAMETER_KINDS, multiple(factory, "Kinds", KINDS));
        parameters.put(PARAMETER_STATES, multiple(factory, "States", STATES));
        // Empty keeps the columns each viewer chose on the page.
        parameters.put(PARAMETER_COLUMNS, multiple(factory, "Columns", COLUMNS));
        parameters.put(PARAMETER_HIDE_FILTERS, factory.bool("Hide filters").value(false).build());
        // Off, the page is a report: no selection, no Create or Update.
        parameters.put(PARAMETER_ALLOW_CREATE, factory.bool("Allow creating work items").value(false).build());
        return parameters;
    }

    private static CustomEnumParameter multiple(ParameterFactory factory, String label, Map<String, String> items) {
        CustomEnumParameter.Builder builder = factory.customEnum(label).allowMultipleValues(true).allowNoValue(true);
        items.forEach(builder::addEnumItem);
        return builder.build();
    }

    @Override
    public @NotNull String renderHtml(@NotNull RichPageWidgetRenderingContext context) {
        return new GithubItemsWidgetRenderer(context).render();
    }

    @Override
    public @NotNull Iterable<String> getTags(@NotNull SharedContext context) {
        return new ImmutableStrictList<>(REPORTS);
    }
}
