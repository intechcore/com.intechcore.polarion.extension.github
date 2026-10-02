package com.intechcore.polarion.extension.github.service;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the {@code {name}} placeholders of a title or description template.
 */
@UtilityClass
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");

    /**
     * Renders a plain-text template. The values go in unchanged.
     */
    public static @NotNull String renderText(@NotNull String template, @NotNull Map<String, String> values) {
        return render(template, values, UnaryOperator.identity()).replaceAll("\\s+", " ").trim();
    }

    /**
     * Renders an HTML template. Every value is escaped, so text from GitHub never becomes markup.
     * Line breaks of a value become {@code <br/>}.
     */
    public static @NotNull String renderHtml(@NotNull String template, @NotNull Map<String, String> values) {
        return render(template, values, TemplateRenderer::escapeHtml);
    }

    private static String render(String template, Map<String, String> values, UnaryOperator<String> encoder) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            // An unknown placeholder stays as it is, so a typing error shows in the work item.
            String replacement = values.containsKey(name) ? encoder.apply(nullToEmpty(values.get(name))) : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static @NotNull String escapeHtml(@NotNull String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\r\n", "\n")
                .replace("\n", "<br/>");
    }
}
