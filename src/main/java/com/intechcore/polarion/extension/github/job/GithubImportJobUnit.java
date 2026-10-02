package com.intechcore.polarion.extension.github.job;

import com.polarion.platform.jobs.IJobUnit;

/**
 * The parameters of the import job. Polarion sets them from the job configuration.
 */
public interface GithubImportJobUnit extends IJobUnit {

    String JOB_NAME = "github_import.job";

    void setRepositories(String repositories);

    void setDryRun(Boolean dryRun);
}
