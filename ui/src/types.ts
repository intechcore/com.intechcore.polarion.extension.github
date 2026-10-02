// Types owned by react-sbb-polarion, re-exported so this app imports them from one place.
export type { Revision, SettingName } from '@sbb-polarion/react-sbb-polarion';

/** Where a work item keeps the URL of its GitHub item (settings.DuplicateKey). */
export type DuplicateKey = 'HYPERLINK' | 'CUSTOM_FIELD';

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
}

/** The import settings of one GitHub repository (settings.RepositorySettingsModel). */
export interface RepositorySettings {
  bundleTimestamp?: string;
  repository: string | null;
  shortName: string | null;
  enabled: boolean;
  issues: ItemSettings | null;
  discussions: ItemSettings | null;
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
