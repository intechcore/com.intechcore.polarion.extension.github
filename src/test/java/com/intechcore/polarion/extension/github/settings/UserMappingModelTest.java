package com.intechcore.polarion.extension.github.settings;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserMappingModelTest {

    private static UserMappingModel mapping(UserLink... users) {
        return new UserMappingModel(new ArrayList<>(Arrays.asList(users)));
    }

    @Test
    void acceptsDistinctLoginsWithTheirUsers() {
        mapping(new UserLink("alice", "aj"), new UserLink("bob", "aj")).validate();
        mapping().validate();
    }

    @Test
    void rejectsAnEntryWithoutALogin() {
        assertThatThrownBy(() -> mapping(new UserLink(" ", "aj")).validate()).hasMessage("Every entry needs a GitHub login");
        assertThatThrownBy(() -> mapping((UserLink) null).validate()).hasMessage("Every entry needs a GitHub login");
    }

    @Test
    void rejectsALoginWithoutAUser() {
        assertThatThrownBy(() -> mapping(new UserLink(" alice ", null)).validate()).hasMessage("The GitHub login 'alice' needs a Polarion user");
    }

    @Test
    void rejectsALoginGivenTwiceInAnyCase() {
        assertThatThrownBy(() -> mapping(new UserLink("alice", "aj"), new UserLink("Alice ", "bk")).validate())
                .hasMessage("The GitHub login 'Alice' is given twice");
    }

    @Test
    void keepsTheUsersThroughSerialization() {
        UserMappingModel mapping = mapping(new UserLink("alice", "aj"));

        UserMappingModel read = new UserMappingModel();
        read.deserializeModelData(mapping.serializeModelData());

        assertThat(read.getUsers()).containsExactly(new UserLink("alice", "aj"));

        read.deserializeModelData("");
        assertThat(read.getUsers()).isEqualTo(List.of());
    }
}
