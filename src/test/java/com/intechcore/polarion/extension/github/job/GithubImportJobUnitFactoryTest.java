package com.intechcore.polarion.extension.github.job;

import com.polarion.platform.jobs.IJobDescriptor;
import com.polarion.platform.jobs.IJobUnit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;

class GithubImportJobUnitFactoryTest {

    private final GithubImportJobUnitFactory factory = new GithubImportJobUnitFactory();

    @Test
    void carriesTheNameThatHivemoduleRegisters() {
        assertThat(factory.getName()).isEqualTo("github_import.job");
    }

    @Test
    void createsTheImportJobUnit() {
        try (MockedConstruction<GithubImportJobUnitImpl> units = mockConstruction(GithubImportJobUnitImpl.class)) {
            IJobUnit unit = factory.createJobUnit("import");

            assertThat(units.constructed()).containsExactly((GithubImportJobUnitImpl) unit);
        }
    }

    @Test
    void describesTwoOptionalParameters() {
        IJobDescriptor descriptor = factory.getJobDescriptor(mock(IJobUnit.class));

        assertThat(descriptor.getRootParameterGroup().getParameters()).containsExactly("repositories", "dryRun");
        assertThat(descriptor.getParameter("repositories").isRequired()).isFalse();
        assertThat(descriptor.getParameter("dryRun").isRequired()).isFalse();
    }
}
