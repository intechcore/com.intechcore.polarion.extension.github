package com.intechcore.polarion.extension.github.settings;

import ch.sbb.polarion.extension.generic.settings.SettingsModel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Polarion users that GitHub logins stand for. The import assigns a work item to the users of
 * the assignees of its issue or pull request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "The Polarion users that GitHub logins stand for")
public class UserMappingModel extends SettingsModel {

    private static final String USERS_ENTRY = "USERS";

    @Schema(description = "One entry per GitHub login")
    private List<UserLink> users = new ArrayList<>();

    @Override
    protected String serializeModelData() {
        return serializeEntry(USERS_ENTRY, users.toArray(UserLink[]::new));
    }

    @Override
    protected void deserializeModelData(String serializedString) {
        users = new ArrayList<>(Arrays.asList(deserializeEntry(USERS_ENTRY, serializedString, UserLink[].class, new UserLink[0])));
    }

    /**
     * Rejects an entry without a login or a user, and a login given twice. GitHub ignores the case of a login.
     *
     * @throws IllegalArgumentException with the reason
     */
    public void validate() {
        Set<String> logins = new HashSet<>();
        for (UserLink user : users) {
            if (user == null || isBlank(user.getGithubLogin())) {
                throw new IllegalArgumentException("Every entry needs a GitHub login");
            }
            if (isBlank(user.getPolarionUser())) {
                throw new IllegalArgumentException("The GitHub login '%s' needs a Polarion user".formatted(user.getGithubLogin().trim()));
            }
            if (!logins.add(user.getGithubLogin().trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("The GitHub login '%s' is given twice".formatted(user.getGithubLogin().trim()));
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
