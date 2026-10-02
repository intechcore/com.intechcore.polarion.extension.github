package com.intechcore.polarion.extension.github.rest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A controller missing from this set is not served, and the endpoint answers 404 on a running
 * server rather than failing the build.
 */
class GithubRestApplicationTest {

    @Test
    void registersNoControllerOfItsOwnYet() {
        assertThat(new GithubRestApplication().getExtensionControllerClasses()).isEmpty();
    }
}
