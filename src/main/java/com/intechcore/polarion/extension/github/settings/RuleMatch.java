package com.intechcore.polarion.extension.github.settings;

/**
 * What a rule compares of a GitHub item.
 */
public enum RuleMatch {
    /** A label of the item. Issues and discussions have labels. */
    LABEL,
    /** The issue type. Only repositories of an organization that uses issue types have it. */
    TYPE,
    /** The category of a discussion. */
    CATEGORY
}
