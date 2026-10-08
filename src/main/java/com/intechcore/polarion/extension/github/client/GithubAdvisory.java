package com.intechcore.polarion.extension.github.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A security advisory of a repository. Anyone sees a published one; one in triage or a draft only a
 * token of an administrator or security manager of the repository sees.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubAdvisory(
        @JsonProperty("ghsa_id") String ghsaId,
        @JsonProperty("cve_id") String cveId,
        String summary,
        String description,
        String state,
        String severity,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        GithubItem.User author,
        Object cvss,
        @JsonProperty("cwe_ids") List<String> cweIds) {

    /** The states the import shows: still open, and published. A closed or withdrawn advisory needs no work. */
    public static final Set<String> SHOWN_STATES = Set.of("triage", "draft", "published");

    public boolean isShown() {
        return state != null && SHOWN_STATES.contains(state);
    }

    /** The CVSS score, or null when the advisory has none yet. GitHub sends an object; read loosely. */
    public @Nullable String cvssScore() {
        return cvss instanceof Map<?, ?> map && map.get("score") instanceof Number score ? String.valueOf(score) : null;
    }

    public @NotNull String cwes() {
        return cweIds == null ? "" : String.join(", ", cweIds);
    }

    /** The advisory as an item of the import: the summary is its title, the description its body. */
    public @NotNull GithubItem toItem() {
        return new GithubItem(0, summary, description, state, htmlUrl, createdAt, updatedAt, author, null, null, null, null, null, null, null);
    }
}
