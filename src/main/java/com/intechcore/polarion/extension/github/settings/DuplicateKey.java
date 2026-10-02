package com.intechcore.polarion.extension.github.settings;

/**
 * Where a work item keeps the URL of its GitHub issue or discussion. The import looks there to
 * find a work item it created before.
 */
public enum DuplicateKey {
    /** A hyperlink of the work item. Needs no configuration in the project. */
    HYPERLINK,
    /** A custom field of the work item, named in the settings. */
    CUSTOM_FIELD
}
