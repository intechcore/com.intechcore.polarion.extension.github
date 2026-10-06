package com.intechcore.polarion.extension.github.widget;

import com.polarion.alm.shared.api.SharedContext;
import com.polarion.alm.shared.api.model.rp.parameter.ParameterFactory;
import com.polarion.alm.shared.api.model.rp.parameter.RichPageParameter;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetContext;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetRenderingContext;
import com.polarion.alm.shared.api.utils.collections.ReadOnlyStrictMap;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubItemsWidgetTest {

    private final GithubItemsWidget widget = new GithubItemsWidget();

    /** The icon must resolve inside the github-app webapp, or the palette shows a placeholder. */
    @Test
    void namesItselfAndPointsItsIconIntoTheAppWebapp() {
        assertThat(widget.getIcon(mock(RichPageWidgetContext.class))).isEqualTo("/polarion/github-app/ui/images/widget-icon.svg");
        assertThat(widget.getLabel(mock(SharedContext.class))).isEqualTo("GitHub Items");
        assertThat(widget.getDetailsHtml(mock(RichPageWidgetContext.class))).contains("GitHub issues");
        assertThat(widget.getTags(mock(SharedContext.class))).containsExactly(GithubItemsWidget.REPORTS);
    }

    /** The renderer reads the parameters back by these keys, so both must agree on every one. */
    @Test
    void definesTheParametersTheRendererReads() {
        ParameterFactory factory = mock(ParameterFactory.class, RETURNS_DEEP_STUBS);

        ReadOnlyStrictMap<String, RichPageParameter> parameters = widget.getParametersDefinition(factory);

        for (String key : new String[]{GithubItemsWidget.PARAMETER_REPOSITORIES, GithubItemsWidget.PARAMETER_KINDS,
                GithubItemsWidget.PARAMETER_STATES, GithubItemsWidget.PARAMETER_COLUMNS,
                GithubItemsWidget.PARAMETER_HIDE_FILTERS, GithubItemsWidget.PARAMETER_ALLOW_CREATE}) {
            assertThat(parameters.get(key)).as(key).isNotNull();
        }
        // A new widget is a report: creating work items stays off until the author turns it on.
        verify(factory.bool("Allow creating work items")).value(false);
        verify(factory.customEnum("Kinds").allowMultipleValues(true).allowNoValue(true)).addEnumItem("PULL_REQUEST", "Pull request");
        verify(factory.customEnum("Columns").allowMultipleValues(true).allowNoValue(true)).addEnumItem("workItemAssignees", "Polarion assignees");
    }

    @Test
    void leavesTheMarkupToTheRenderer() {
        try (MockedConstruction<GithubItemsWidgetRenderer> renderers = mockConstruction(GithubItemsWidgetRenderer.class,
                (renderer, context) -> when(renderer.render()).thenReturn("<iframe></iframe>"))) {
            assertThat(widget.renderHtml(mock(RichPageWidgetRenderingContext.class))).isEqualTo("<iframe></iframe>");
            assertThat(renderers.constructed()).hasSize(1);
        }
    }
}
