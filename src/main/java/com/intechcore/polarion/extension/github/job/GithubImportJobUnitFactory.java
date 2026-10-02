package com.intechcore.polarion.extension.github.job;

import com.polarion.platform.jobs.IJobDescriptor;
import com.polarion.platform.jobs.IJobUnit;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.spi.BasicJobDescriptor;
import com.polarion.platform.jobs.spi.JobParameterPrimitiveType;
import com.polarion.platform.jobs.spi.SimpleJobParameter;

public class GithubImportJobUnitFactory implements IJobUnitFactory {

    public static final String REPOSITORIES = "repositories";
    public static final String DRY_RUN = "dryRun";

    @Override
    public IJobUnit createJobUnit(String name) {
        return new GithubImportJobUnitImpl(name, this);
    }

    @Override
    public IJobDescriptor getJobDescriptor(IJobUnit jobUnit) {
        BasicJobDescriptor descriptor = new BasicJobDescriptor("Creates work items from GitHub issues and discussions", jobUnit);

        descriptor.addParameter(new SimpleJobParameter(descriptor.getRootParameterGroup(), REPOSITORIES,
                "Comma-separated names of the repository settings to import. Without it the job imports every enabled one.",
                new JobParameterPrimitiveType("String", String.class)).setRequired(false));
        descriptor.addParameter(new SimpleJobParameter(descriptor.getRootParameterGroup(), DRY_RUN,
                "True to log what the import would do, without creating work items",
                new JobParameterPrimitiveType("Boolean", Boolean.class)).setRequired(false));

        return descriptor;
    }

    @Override
    public String getName() {
        return GithubImportJobUnit.JOB_NAME;
    }
}
