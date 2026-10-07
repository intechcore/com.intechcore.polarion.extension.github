package com.intechcore.polarion.extension.github.job;

import com.polarion.platform.jobs.IJobDescriptor;
import com.polarion.platform.jobs.IJobUnit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;

class GithubWatchJobUnitFactoryTest {

    private final GithubWatchJobUnitFactory factory = new GithubWatchJobUnitFactory();

    /** The scheduler finds the job by this name. */
    @Test
    void namesTheJobAndDescribesItsParameter() {
        assertThat(factory.getName()).isEqualTo("github_watch.job");
        try (MockedConstruction<GithubWatchJobUnitImpl> units = mockConstruction(GithubWatchJobUnitImpl.class)) {
            IJobUnit unit = factory.createJobUnit("github_watch.job");
            assertThat(unit).isSameAs(units.constructed().get(0));

            IJobDescriptor descriptor = factory.getJobDescriptor(unit);
            assertThat(descriptor.getLabel()).contains("Mails new GitHub");
            assertThat(descriptor.getRootParameterGroup().getParameters()).containsExactly(GithubWatchJobUnitFactory.INTERVAL_MINUTES);
            assertThat(descriptor.getParameter(GithubWatchJobUnitFactory.INTERVAL_MINUTES).isRequired()).isFalse();
        }
    }
}
