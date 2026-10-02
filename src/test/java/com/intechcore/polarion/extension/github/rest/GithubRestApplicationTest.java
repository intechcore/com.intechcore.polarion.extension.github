package com.intechcore.polarion.extension.github.rest;

import ch.sbb.polarion.extension.generic.rest.controller.info.ExtensionInfoInternalController;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The extension has no controller of its own yet. The About page needs the ones generic brings.
 */
class GithubRestApplicationTest {

    @Test
    void servesTheGenericControllers() {
        assertThat(new GithubRestApplication().getClasses()).contains(ExtensionInfoInternalController.class);
    }
}
