package com.intechcore.polarion.extension.github.settings;

import ch.sbb.polarion.extension.generic.context.CurrentContextConfig;
import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Reading, saving and the fallback to the global mapping run in ImportIntegrationTest. */
@ExtendWith(CurrentContextExtension.class)
@CurrentContextConfig("github")
class UserMappingTest {

    private final SettingsService service = mock(SettingsService.class);
    private UserMapping mapping;

    // The context of the extension exists only once the extension of the test ran.
    @BeforeEach
    void setUp() {
        mapping = new UserMapping(service);
    }

    @Test
    void keepsTheMappingUnderItsOwnFeature() {
        assertThat(mapping.getFeatureName()).isEqualTo("user-mapping");
        assertThat(mapping.getSettingsFolder()).isEqualTo(".polarion/extensions/github/user-mapping");
        assertThat(mapping.defaultValues().getUsers()).isEmpty();
        assertThat(mapping.isOwn("")).isTrue();
    }

    @Test
    void refusesToResetTheGlobalMapping() {
        assertThatThrownBy(() -> mapping.reset("")).hasMessage("Only a project uses the global mapping");
        verify(service, never()).delete(any());
    }

    @Test
    void refusesToSaveAnInvalidMapping() {
        UserMappingModel twice = new UserMappingModel(List.of(new UserLink("alice", "aj"), new UserLink("ALICE", "bk")));

        assertThatThrownBy(() -> mapping.save("", twice)).hasMessageContaining("given twice");
        verify(service, never()).save(any(), anyString());
    }
}
