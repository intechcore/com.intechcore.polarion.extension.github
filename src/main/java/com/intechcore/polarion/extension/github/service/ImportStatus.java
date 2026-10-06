package com.intechcore.polarion.extension.github.service;

/**
 * What the import did, or would do, with one GitHub item.
 */
public enum ImportStatus {
    /** No work item holds the item yet. A dry run stops here. */
    NEW,
    /** The import created a work item. */
    CREATED,
    /** A work item holds the item already, and it shows what the settings and GitHub say now. */
    EXISTS,
    /** A work item holds the item, but its title, description, type, field values or epic link differ. */
    OUTDATED,
    /** The update made the work item show what the settings and GitHub say now. */
    UPDATED,
    /** A rule of the settings leaves the item out. */
    SKIPPED,
    /** The import could not create the work item. */
    FAILED
}
