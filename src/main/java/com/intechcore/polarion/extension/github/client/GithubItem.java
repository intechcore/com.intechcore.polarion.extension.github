package com.intechcore.polarion.extension.github.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An issue or a discussion of a GitHub repository. Both answer with the same fields in the REST API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubItem(
        long number,
        String title,
        String body,
        String state,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        User user,
        List<Label> labels,
        @JsonProperty("pull_request") Object pullRequest,
        Object type,
        Object category,
        List<User> assignees) {

    public static final String STATE_OPEN = "open";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(String login) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Label(String name) {
    }

    /**
     * The name of the issue type, or null. Only repositories of an organization that uses issue
     * types send one.
     */
    public @Nullable String typeName() {
        return nameOf(type);
    }

    /**
     * The name of the category of a discussion, or null.
     */
    public @Nullable String categoryName() {
        return nameOf(category);
    }

    public @NotNull List<String> assigneeLogins() {
        return assignees == null ? List.of() : assignees.stream().filter(Objects::nonNull).map(User::login).filter(Objects::nonNull).toList();
    }

    public @NotNull List<String> labelNames() {
        return labels == null ? List.of() : labels.stream().map(Label::name).filter(Objects::nonNull).toList();
    }

    // GitHub sends an object with a name. Read loosely, so another shape never breaks the whole list.
    private static @Nullable String nameOf(@Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.get("name") instanceof String name ? name : null;
        }
        return value instanceof String name ? name : null;
    }

    public boolean isOpen() {
        return STATE_OPEN.equals(state);
    }

    /**
     * The issues endpoint lists pull requests as well, and marks them with this field.
     */
    public boolean isPullRequest() {
        return pullRequest != null;
    }
}
