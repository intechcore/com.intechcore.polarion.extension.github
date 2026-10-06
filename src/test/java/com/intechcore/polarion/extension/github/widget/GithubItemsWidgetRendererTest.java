package com.intechcore.polarion.extension.github.widget;

import com.polarion.alm.shared.api.Scope;
import com.polarion.alm.shared.api.model.rp.parameter.BooleanParameter;
import com.polarion.alm.shared.api.model.rp.parameter.CustomEnumParameter;
import com.polarion.alm.shared.api.model.rp.parameter.StringParameter;
import com.polarion.alm.shared.api.model.rp.widget.RichPageWidgetCommonContext;
import com.polarion.alm.shared.api.utils.collections.StrictList;
import com.polarion.alm.shared.api.utils.html.HtmlAttributesBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlContentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlFragmentBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagBuilder;
import com.polarion.alm.shared.api.utils.html.HtmlTagSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The renderer turns the widget settings into the URL of the page of the GitHub items. Each setting
 * is asserted where it lands in the query string.
 */
class GithubItemsWidgetRendererTest {

    private RichPageWidgetCommonContext context;
    private Scope scope;
    private HtmlFragmentBuilder builder;
    private HtmlAttributesBuilder attributes;
    private HtmlContentBuilder scriptContent;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        context = mock(RichPageWidgetCommonContext.class);
        scope = mock(Scope.class);
        when(context.getDisplayedScope()).thenReturn(scope);
        when(scope.projectId()).thenReturn("elibrary");
        // Polarion passes every parameter the widget defines; one never filled has no value.
        StringParameter repositories = mock(StringParameter.class);
        when(context.<StringParameter>parameter(GithubItemsWidget.PARAMETER_REPOSITORIES)).thenReturn(repositories);

        attributes = mock(HtmlAttributesBuilder.class, RETURNS_SELF);
        HtmlTagBuilder iframe = mock(HtmlTagBuilder.class);
        when(iframe.attributes()).thenReturn(attributes);
        scriptContent = mock(HtmlContentBuilder.class);
        HtmlTagBuilder script = mock(HtmlTagBuilder.class);
        when(script.append()).thenReturn(scriptContent);
        HtmlTagSelector<HtmlTagBuilder> tags = mock(HtmlTagSelector.class);
        when(tags.byName("iframe")).thenReturn(iframe);
        when(tags.script()).thenReturn(script);
        builder = mock(HtmlFragmentBuilder.class);
        when(builder.tag()).thenReturn(tags);
    }

    private String renderedUrl() {
        new GithubItemsWidgetRenderer(context).render(builder);
        ArgumentCaptor<String> src = ArgumentCaptor.forClass(String.class);
        verify(attributes).byName(eq("src"), src.capture());
        return src.getValue();
    }

    @SuppressWarnings("unchecked")
    private void choose(String key, String... values) {
        // Built before the stubbing: a mock created inside thenReturn(...) opens a second stubbing.
        StrictList<String> list = mock(StrictList.class);
        when(list.asList()).thenReturn(List.of(values));
        CustomEnumParameter parameter = mock(CustomEnumParameter.class);
        when(parameter.values()).thenReturn(list);
        when(context.<CustomEnumParameter>parameter(key)).thenReturn(parameter);
    }

    private void check(String key) {
        BooleanParameter parameter = mock(BooleanParameter.class);
        when(parameter.value()).thenReturn(true);
        when(context.<BooleanParameter>parameter(key)).thenReturn(parameter);
    }

    /** A widget saved without settings, or before a setting existed, shows the page as the topic does. */
    @Test
    void opensThePageOfTheProjectOfThePage() {
        assertThat(renderedUrl()).isEqualTo("/polarion/github-app/ui/app/index.html?feature=items&embedded=true&widget=true"
                + "&scope=project%2Felibrary%2F");
    }

    @Test
    void passesEverySettingOfTheWidget() {
        StringParameter repositories = mock(StringParameter.class);
        when(repositories.value()).thenReturn(" tool , ,pdf-exporter");
        when(context.<StringParameter>parameter(GithubItemsWidget.PARAMETER_REPOSITORIES)).thenReturn(repositories);
        choose(GithubItemsWidget.PARAMETER_KINDS, "ISSUE", "PULL_REQUEST");
        choose(GithubItemsWidget.PARAMETER_STATES, "NEW");
        choose(GithubItemsWidget.PARAMETER_COLUMNS, "item", "state", "workItem");
        check(GithubItemsWidget.PARAMETER_HIDE_FILTERS);
        check(GithubItemsWidget.PARAMETER_ALLOW_CREATE);

        assertThat(renderedUrl()).endsWith("&scope=project%2Felibrary%2F&settings=tool%2Cpdf-exporter"
                + "&kinds=ISSUE%2CPULL_REQUEST&states=NEW&columns=item%2Cstate%2CworkItem&hideFilters=true&allowCreate=true");
    }

    /** A page outside a project gets no scope, and the page of the items says it needs one. */
    @Test
    void passesNoScopeOutsideAProject() {
        when(scope.projectId()).thenReturn(null);
        CustomEnumParameter kinds = mock(CustomEnumParameter.class);
        when(context.<CustomEnumParameter>parameter(GithubItemsWidget.PARAMETER_KINDS)).thenReturn(kinds);

        assertThat(renderedUrl()).endsWith("&widget=true&scope=");
    }

    @Test
    void bindsTheHeightListenerToItsOwnFrame() {
        String url = renderedUrl();

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        verify(attributes).id(id.capture());
        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        verify(scriptContent).javaScript(script.capture());
        assertThat(id.getValue()).startsWith("github-items-");
        assertThat(script.getValue()).contains("function githubSyncIframeHeight(frameId)")
                .endsWith("githubSyncIframeHeight('" + id.getValue() + "');");
        assertThat(url).startsWith("/polarion/github-app/");
    }

    @Test
    void refusesAMissingOrUnreadableScript() throws IOException {
        assertThatThrownBy(() -> GithubItemsWidgetRenderer.readScript(null, "/js/x.js"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("missing");
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk");
            }
        };
        assertThatThrownBy(() -> GithubItemsWidgetRenderer.readScript(broken, "/js/x.js"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot read");
        try (InputStream text = new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8))) {
            assertThat(GithubItemsWidgetRenderer.readScript(text, "/js/x.js")).isEqualTo("ok");
        }
    }
}
