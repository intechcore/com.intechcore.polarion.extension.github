package com.intechcore.polarion.extension.github.service;

/**
 * The kinds of GitHub items the import reads.
 */
public enum ItemKind {
    ISSUE,
    DISCUSSION,
    /** An open pull request of a watched author whose checks failed. */
    PULL_REQUEST,
    /** A security advisory of the repository: in triage, drafted or published. */
    ADVISORY
}
