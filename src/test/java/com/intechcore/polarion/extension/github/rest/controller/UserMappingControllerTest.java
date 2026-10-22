package com.intechcore.polarion.extension.github.rest.controller;

import com.intechcore.polarion.extension.github.rest.model.UserMappingContent;
import com.intechcore.polarion.extension.github.settings.UserLink;
import com.intechcore.polarion.extension.github.settings.UserMapping;
import com.intechcore.polarion.extension.github.settings.UserMappingModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserMappingControllerTest {

    private static final String SCOPE = "project/elibrary/";

    private final UserMapping userMapping = mock(UserMapping.class);
    private final UserMappingInternalController controller = new UserMappingApiController(userMapping);

    @Test
    void answersWithTheMappingAProjectInherits() {
        when(userMapping.read(SCOPE)).thenReturn(new UserMappingModel(List.of(new UserLink("alice", "aj"))));

        UserMappingContent content = controller.getUserMapping(SCOPE);

        assertThat(content.users()).containsExactly(new UserLink("alice", "aj"));
        assertThat(content.inherited()).isTrue();
    }

    @Test
    void savesAMappingAndAnswersWithIt() {
        UserMappingModel mapping = new UserMappingModel(List.of(new UserLink("bob", "bk")));
        when(userMapping.read(SCOPE)).thenReturn(mapping);
        when(userMapping.isOwn(SCOPE)).thenReturn(true);

        UserMappingContent content = controller.saveUserMapping(SCOPE, mapping);

        verify(userMapping).save(SCOPE, mapping);
        assertThat(content.inherited()).isFalse();
    }

    @Test
    void refusesASaveWithoutUsers() {
        UserMappingModel withoutUsers = new UserMappingModel(null);
        assertThatThrownBy(() -> controller.saveUserMapping(SCOPE, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.saveUserMapping(SCOPE, withoutUsers)).isInstanceOf(IllegalArgumentException.class);
        verify(userMapping, never()).save(anyString(), any(UserMappingModel.class));
    }

    @Test
    void resetsTheMappingOfAProject() {
        when(userMapping.read(SCOPE)).thenReturn(new UserMappingModel());

        assertThat(controller.resetUserMapping(SCOPE).inherited()).isTrue();
        verify(userMapping).reset(SCOPE);
    }
}
