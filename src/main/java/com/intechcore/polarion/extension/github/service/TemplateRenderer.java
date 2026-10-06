package com.intechcore.polarion.extension.github.service;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the {@code {{ NAME }}} placeholders of a title or description template. A name ignores case
 * and underscores, so {@code {{ SHORT_NAME }}}, {@code {{shortName}}} and {@code {{ short_name }}} are
 * one placeholder.
 */
@UtilityClass
public class TemplateRenderer {

    /** The placeholders of a title. A description takes them and {@code {{ BODY }}}. */
    public static final List<String> TITLE_PLACEHOLDERS = List.of(
            "SHORT_NAME", "REPOSITORY", "NUMBER", "TITLE", "AUTHOR", "URL", "LABELS", "TYPE", "CATEGORY");
    public static final String BODY = "BODY";

    // Anything between double braces, to tell a misspelled placeholder from text.
    private static final Pattern ANY_PLACEHOLDER = Pattern.compile("\\{\\{(.*?)}}");
    // The form before {{ NAME }}: one brace around a camel case name, for example {shortName}.
    private static final Pattern EARLIER_FORM = Pattern.compile("(?<!\\{)\\{([A-Za-z_]+)}(?!})");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z_]+)\\s*}}");

    /**
     * The reasons a template would not render as its author means, or none. A placeholder must be
     * one of {@code allowed}, written as {@code {{ NAME }}}.
     */
    public static @NotNull List<String> problems(@NotNull String template, @NotNull List<String> allowed) {
        Set<String> keys = new HashSet<>();
        allowed.forEach(name -> keys.add(key(name)));
        List<String> problems = new ArrayList<>();
        Matcher any = ANY_PLACEHOLDER.matcher(template);
        while (any.find()) {
            String name = any.group(1).trim();
            if (!name.matches("[A-Za-z_]+") || !keys.contains(key(name))) {
                problems.add("has the unknown placeholder " + any.group());
            }
        }
        Matcher earlier = EARLIER_FORM.matcher(template);
        while (earlier.find()) {
            String name = key(earlier.group(1));
            allowed.stream().filter(candidate -> key(candidate).equals(name)).findFirst().ifPresent(known ->
                    problems.add("writes " + earlier.group() + " in the earlier form. Write {{ " + known + " }}"));
        }
        return problems;
    }

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
        Map<String, String> byKey = new HashMap<>();
        values.forEach((name, value) -> byKey.put(key(name), value));
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = key(matcher.group(1));
            // An unknown placeholder stays as it is, so a typing error shows in the work item.
            String replacement = byKey.containsKey(name) ? encoder.apply(nullToEmpty(byKey.get(name))) : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String key(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
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
