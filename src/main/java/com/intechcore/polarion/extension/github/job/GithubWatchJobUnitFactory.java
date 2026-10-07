package com.intechcore.polarion.extension.github.job;

import com.polarion.platform.jobs.IJobDescriptor;
import com.polarion.platform.jobs.IJobUnit;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.spi.BasicJobDescriptor;
import com.polarion.platform.jobs.spi.JobParameterPrimitiveType;
import com.polarion.platform.jobs.spi.SimpleJobParameter;

public class GithubWatchJobUnitFactory implements IJobUnitFactory {

    public static final String INTERVAL_MINUTES = "intervalMinutes";

    @Override
    public IJobUnit createJobUnit(String name) {
        return new GithubWatchJobUnitImpl(name, this);
    }

    @Override
    public IJobDescriptor getJobDescriptor(IJobUnit jobUnit) {
        BasicJobDescriptor descriptor = new BasicJobDescriptor("Mails new GitHub issues, discussions and failed pull requests", jobUnit);
        descriptor.addParameter(new SimpleJobParameter(descriptor.getRootParameterGroup(), INTERVAL_MINUTES,
                "How often the scheduler runs the job, in minutes, 15 by default. The first check after a start of Polarion"
                        + " takes the items of this window as new.",
                new JobParameterPrimitiveType("Integer", Integer.class)).setRequired(false));
        return descriptor;
    }

    @Override
    public String getName() {
        return GithubWatchJobUnit.JOB_NAME;
    }
}
