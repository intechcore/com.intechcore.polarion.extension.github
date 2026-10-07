package com.intechcore.polarion.extension.github.widget;

import ch.sbb.polarion.extension.generic.settings.SettingName;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.IEnumeration;
import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoriesEnumFactoryTest {

    private RepositorySettings repositorySettings;
    private RepositoriesEnumFactory factory;

    @BeforeEach
    void setUp() {
        repositorySettings = mock(RepositorySettings.class);
        when(repositorySettings.readNames("project/elibrary/")).thenReturn(List.of(
                SettingName.builder().name("tool").scope("project/elibrary/").build(),
                SettingName.builder().name("docs").scope("project/elibrary/").build()));
        factory = new RepositoriesEnumFactory(repositorySettings);
    }

    private static IContextId project(String id) {
        IContextId context = mock(IContextId.class);
        when(context.getContextName()).thenReturn(id);
        return context;
    }

    @Test
    void offersTheRepositorySettingsOfTheProjectByName() {
        IEnumeration<IEnumOption> enumeration = factory.getEnumeration(RepositoriesEnumFactory.ENUM_ID, project("elibrary"));

        assertThat(enumeration.getAllOptions()).extracting(IEnumOption::getId).containsExactly("docs", "tool");
        assertThat(enumeration.getAvailableOptions(null)).extracting(IEnumOption::getName).containsExactly("docs", "tool");
        assertThat(enumeration.getAvailableOptions(null, null)).hasSize(2);
        assertThat(enumeration.getAllOptions().get(0).getEnumId()).isEqualTo(RepositoriesEnumFactory.ENUM_ID);
        assertThat(enumeration.getControlKey()).isNull();
        assertThat(enumeration.getDefaultOption(null)).isNull();
    }

    /** A setting renamed or deleted since the widget was saved shows as a phantom, not as nothing. */
    @Test
    void marksASettingThatIsGoneAsAPhantom() {
        IEnumeration<IEnumOption> enumeration = factory.getEnumeration(RepositoriesEnumFactory.ENUM_ID, project("elibrary"));

        assertThat(enumeration.wrapOption("tool").isPhantom()).isFalse();
        assertThat(enumeration.wrapOption("tool", (Object) null).getName()).isEqualTo("tool");
        assertThat(enumeration.wrapOption("gone", (com.polarion.platform.persistence.model.IPObject) null).isPhantom()).isTrue();
    }

    @Test
    void offersNothingOutsideAProject() {
        assertThat(factory.getEnumeration(RepositoriesEnumFactory.ENUM_ID, null).getAllOptions()).isEmpty();
        assertThat(factory.getEnumeration(RepositoriesEnumFactory.ENUM_ID, project(" ")).getAllOptions()).isEmpty();
        verify(repositorySettings, never()).readNames(" ");
    }
}
