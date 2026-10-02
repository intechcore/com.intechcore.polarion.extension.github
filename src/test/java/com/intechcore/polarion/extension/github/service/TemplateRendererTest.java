package com.intechcore.polarion.extension.github.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    @Test
    void fillsThePlaceholdersOfATitle() {
        Map<String, String> values = Map.of("shortName", "Tool", "title", "Crash on start");

        assertThat(TemplateRenderer.renderText("[GitHub] {shortName} : {title}", values))
                .isEqualTo("[GitHub] Tool : Crash on start");
    }

    @Test
    void keepsATitleOnOneLine() {
        assertThat(TemplateRenderer.renderText("{title}", Map.of("title", " two\nlines\t here ")))
                .isEqualTo("two lines here");
    }

    @Test
    void leavesAnUnknownPlaceholderAndFillsAMissingValueWithNothing() {
        Map<String, String> values = new HashMap<>();
        values.put("body", null);

        assertThat(TemplateRenderer.renderText("{unknown} [{body}] {not a placeholder}", values))
                .isEqualTo("{unknown} [] {not a placeholder}");
    }

    @Test
    void doesNotReadAValueAsATemplate() {
        Map<String, String> values = Map.of("title", "{url} $1 \\", "url", "https://github.com/acme/tool/issues/7");

        assertThat(TemplateRenderer.renderText("{title}", values)).isEqualTo("{url} $1 \\");
    }

    @Test
    void escapesValuesInHtmlAndKeepsTheMarkupOfTheTemplate() {
        Map<String, String> values = Map.of(
                "url", "https://github.com/acme/tool/issues/7?a=1&b=\"2\"",
                "body", "<script>alert('x')</script>\r\nsecond line\nthird");

        assertThat(TemplateRenderer.renderHtml("<a href=\"{url}\">{url}</a><p>{body}</p>", values))
                .isEqualTo("<a href=\"https://github.com/acme/tool/issues/7?a=1&amp;b=&quot;2&quot;\">"
                        + "https://github.com/acme/tool/issues/7?a=1&amp;b=&quot;2&quot;</a>"
                        + "<p>&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;<br/>second line<br/>third</p>");
    }
}
