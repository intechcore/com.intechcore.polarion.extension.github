package com.intechcore.polarion.extension.github.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    @Test
    void fillsThePlaceholdersOfATitle() {
        Map<String, String> values = Map.of("shortName", "Tool", "title", "Crash on start");

        assertThat(TemplateRenderer.renderText("[GitHub] {{ SHORT_NAME }} : {{ TITLE }}", values))
                .isEqualTo("[GitHub] Tool : Crash on start");
    }

    @Test
    void readsANameWithoutRegardToCaseUnderscoresOrSpaces() {
        Map<String, String> values = Map.of("shortName", "Tool");

        assertThat(TemplateRenderer.renderText("{{SHORT_NAME}} {{ shortName }} {{  short_name  }} {{ ShortName }}", values))
                .isEqualTo("Tool Tool Tool Tool");
    }

    @Test
    void keepsATitleOnOneLine() {
        assertThat(TemplateRenderer.renderText("{{ TITLE }}", Map.of("title", " two\nlines\t here ")))
                .isEqualTo("two lines here");
    }

    @Test
    void leavesAnUnknownPlaceholderAndTheOldFormAndFillsAMissingValueWithNothing() {
        Map<String, String> values = new HashMap<>();
        values.put("body", null);
        values.put("title", "T");

        assertThat(TemplateRenderer.renderText("{{ UNKNOWN }} [{{ BODY }}] {title} {{ not a placeholder }} {{TITLE", values))
                .isEqualTo("{{ UNKNOWN }} [] {title} {{ not a placeholder }} {{TITLE");
    }

    @Test
    void doesNotReadAValueAsATemplate() {
        Map<String, String> values = Map.of("title", "{{ URL }} $1 \\", "url", "https://github.com/acme/tool/issues/7");

        assertThat(TemplateRenderer.renderText("{{ TITLE }}", values)).isEqualTo("{{ URL }} $1 \\");
    }

    @Test
    void escapesValuesInHtmlAndKeepsTheMarkupOfTheTemplate() {
        Map<String, String> values = Map.of(
                "url", "https://github.com/acme/tool/issues/7?a=1&b=\"2\"",
                "body", "<script>alert('x')</script>\r\nsecond line\nthird");

        assertThat(TemplateRenderer.renderHtml("<a href=\"{{ URL }}\">{{ URL }}</a><p>{{ BODY }}</p>", values))
                .isEqualTo("<a href=\"https://github.com/acme/tool/issues/7?a=1&amp;b=&quot;2&quot;\">"
                        + "https://github.com/acme/tool/issues/7?a=1&amp;b=&quot;2&quot;</a>"
                        + "<p>&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;<br/>second line<br/>third</p>");
    }
}
