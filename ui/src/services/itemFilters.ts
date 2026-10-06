import type { ImportEntry, ImportStatus, ItemKind } from '../types';

/** The value a filter offers for an item that has none, such as no assignee. */
export const NONE = '\u0000none';

export interface ItemFilters {
  settings: string[];
  kinds: string[];
  githubTypes: string[];
  workItemTypes: string[];
  assignees: string[];
  workItemAssignees: string[];
  states: string[];
  text: string;
}

export const NO_FILTERS: ItemFilters = {
  settings: [],
  kinds: [],
  githubTypes: [],
  workItemTypes: [],
  assignees: [],
  workItemAssignees: [],
  states: [],
  text: '',
};

export const KIND_LABELS: Record<ItemKind, string> = { ISSUE: 'Issue', DISCUSSION: 'Discussion' };

export const STATUS_LABELS: Record<ImportStatus, string> = {
  NEW: 'New',
  CREATED: 'Created',
  EXISTS: 'Has a work item',
  OUTDATED: 'Out of date',
  UPDATED: 'Updated',
  SKIPPED: 'Left out',
  FAILED: 'Failed',
};

const single = (value: string | null | undefined): string[] => [value || NONE];
const many = (values: string[] | null | undefined): string[] => (values && values.length > 0 ? values : [NONE]);

/** The values of an item for each filter. An item matches a filter when one of its values is selected. */
const values: Record<Exclude<keyof ItemFilters, 'text'>, (entry: ImportEntry) => string[]> = {
  settings: (entry) => single(entry.setting),
  kinds: (entry) => [entry.kind],
  githubTypes: (entry) => single(entry.githubType),
  workItemTypes: (entry) => single(entry.workItemTypeName ?? entry.workItemType),
  assignees: (entry) => many(entry.assignees),
  workItemAssignees: (entry) => many(entry.workItemAssignees),
  // A work item created or updated on this page counts as one that exists.
  states: (entry) => [entry.status === 'CREATED' || entry.status === 'UPDATED' ? 'EXISTS' : entry.status],
};

export function applyFilters(entries: ImportEntry[], filters: ItemFilters): ImportEntry[] {
  const text = filters.text.trim().toLowerCase();
  return entries.filter(
    (entry) =>
      (Object.keys(values) as (keyof typeof values)[]).every(
        (key) => filters[key].length === 0 || values[key](entry).some((value) => filters[key].includes(value)),
      ) &&
      (!text ||
        `${entry.title ?? ''} #${entry.number} ${entry.workItemId ?? ''} ${(entry.labels ?? []).join(' ')}`
          .toLowerCase()
          .includes(text)),
  );
}

/** The options of a filter: every value the items have, sorted, with the empty value last. */
export function optionsOf(
  entries: ImportEntry[],
  key: keyof typeof values,
  label: (value: string) => string = (value) => value,
  none = '(none)',
): { id: string; name: string }[] {
  const all = Array.from(new Set(entries.flatMap((entry) => values[key](entry))));
  return all
    .sort((a, b) => (a === NONE ? 1 : b === NONE ? -1 : label(a).localeCompare(label(b))))
    .map((value) => ({ id: value, name: value === NONE ? none : label(value) }));
}
