package com.intechcore.polarion.extension.github.job;

import com.polarion.platform.jobs.IJobUnit;

/**
 * The parameters of the watch job. Polarion sets them from the job configuration of the scheduler.
 */
public interface GithubWatchJobUnit extends IJobUnit {

    String JOB_NAME = "github_watch.job";

    /** How often the scheduler runs the job, in minutes: the window of the first check after a start. */
    void setIntervalMinutes(Integer intervalMinutes);
}
