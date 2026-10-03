package com.intechcore.polarion.extension.github;

import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GithubNavigationExtenderTest {

    private final GithubNavigationExtender extender = new GithubNavigationExtender();

    @Test
    void opensTheItemsPageOfTheProject() {
        IContextId project = mock(IContextId.class);
        when(project.getContextName()).thenReturn("elibrary");

        assertThat(extender.getPageUrl(project))
                .isEqualTo("/polarion/github-app/ui/app/index.html?feature=items&embedded=true&scope=project/elibrary/");
    }

    @Test
    void opensThePageWithoutAScopeOutsideAProject() {
        assertThat(extender.getPageUrl(mock(IContextId.class)))
                .isEqualTo("/polarion/github-app/ui/app/index.html?feature=items&embedded=true&scope=");
    }

    @Test
    void isTheTopicGithubWithTheIconOfTheExtension() {
        IContextId project = mock(IContextId.class);

        assertThat(extender.getId()).isEqualTo("github");
        assertThat(extender.getLabel()).isEqualTo("GitHub");
        assertThat(extender.getIconUrl()).isEqualTo("/polarion/github-app/ui/images/menu/30x30/_parent.svg");
        assertThat(extender.requiresToken()).isFalse();
        assertThat(extender.getRootNodes(project)).isEmpty();
    }
}
