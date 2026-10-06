// Types owned by react-sbb-polarion, re-exported so this app imports them from one place.
export type { Revision, SettingName } from '@sbb-polarion/react-sbb-polarion';

/** Where a work item keeps the URL of its GitHub item (settings.DuplicateKey). */
export type DuplicateKey = 'HYPERLINK' | 'CUSTOM_FIELD';

/** What a rule compares of a GitHub item (settings.RuleMatch). */
export type RuleMatch = 'LABEL' | 'TYPE' | 'CATEGORY' | 'AUTHOR';

/** A rule for the GitHub items that match it (settings.ItemRule). */
export interface ItemRule {
  match: RuleMatch;
  value: string;
  skip: boolean;
  workItemType: string | null;
  fields: Record<string, string> | null;
}

/** How issues or discussions become work items (settings.ItemSettings). */
export interface ItemSettings {
  enabled: boolean;
  workItemType: string | null;
  titleTemplate: string | null;
  descriptionTemplate: string | null;
  duplicateKey: DuplicateKey;
  duplicateKeyField: string | null;
  epicId: string | null;
  epicLinkRole: string | null;
  fields: Record<string, string> | null;
  rules: ItemRule[] | null;
}

/** The import settings of one GitHub repository (settings.RepositorySettingsModel). */
export interface RepositorySettings {
  bundleTimestamp?: string;
  repository: string | null;
  shortName: string | null;
  issues: ItemSettings | null;
  discussions: ItemSettings | null;
  pullRequests: ItemSettings | null;
  /** The GitHub logins whose pull requests the import watches, separated by commas. */
  pullRequestAuthors: string | null;
}

/** A work item type or a link role of a project (rest.model.ProjectOption). */
export interface ProjectOption {
  id: string;
  name: string;
}

/** A work item field of a project (rest.model.ProjectField). */
export interface ProjectField {
  id: string;
  name: string;
  custom: boolean;
  urlKey: boolean;
}

export type ItemKind = 'ISSUE' | 'DISCUSSION' | 'PULL_REQUEST';

export type ImportStatus = 'NEW' | 'CREATED' | 'EXISTS' | 'OUTDATED' | 'UPDATED' | 'SKIPPED' | 'FAILED';

/** The outcome of the import for one GitHub item (service.ImportEntry). */
export interface ImportEntry {
  kind: ItemKind;
  number: number;
  title: string | null;
  url: string | null;
  status: ImportStatus;
  workItemId: string | null;
  message: string | null;
  setting: string | null;
  repository: string | null;
  /** The short name of the repository, from its setting. */
  shortName: string | null;
  /** The issue type, or the category of a discussion. */
  githubType: string | null;
  labels: string[] | null;
  /** Six hexadecimal digits by label name, as GitHub colors the label. */
  labelColors: Record<string, string> | null;
  /** GitHub logins. */
  assignees: string[] | null;
  /** The type of the work item, or the one the import would create. */
  workItemType: string | null;
  workItemTypeName: string | null;
  workItemTypeIcon: string | null;
  workItemStatus: string | null;
  workItemStatusIcon: string | null;
  /** Polarion user names. */
  workItemAssignees: string[] | null;
  /** The names of the failed checks of a pull request, separated by commas. */
  failedChecks: string | null;
  /** True when the project hides the item on its GitHub page. */
  hidden: boolean;
}

/** The outcome of the import of one repository (service.ImportResult). */
export interface ImportResult {
  repository: string;
  dryRun: boolean;
  entries: ImportEntry[];
  /** ISO-8601. GitHub lists serve from a cache of the server for five minutes. */
  readAt: string | null;
}

/** How the reading of one repository setting went (rest.model.RepositoryState). */
export interface RepositoryState {
  setting: string;
  repository: string | null;
  readAt: string | null;
  error: string | null;
}

/** The open GitHub items of all repository settings of a project (rest.model.ProjectItems). */
export interface ProjectItems {
  repositories: RepositoryState[];
  entries: ImportEntry[];
}
