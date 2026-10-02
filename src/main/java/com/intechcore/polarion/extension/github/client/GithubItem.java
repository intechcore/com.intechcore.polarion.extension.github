package com.intechcore.polarion.extension.github.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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
        @JsonProperty("pull_request") Object pullRequest) {

    public static final String STATE_OPEN = "open";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(String login) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Label(String name) {
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
