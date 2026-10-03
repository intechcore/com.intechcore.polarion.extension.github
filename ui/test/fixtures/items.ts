import type { ImportEntry } from '../../src/types';
import type { Route } from '../mockFetch';
import { SCOPE } from './repositories';

export { SCOPE };

const base: Omit<ImportEntry, 'kind' | 'number' | 'title' | 'url' | 'status'> = {
  workItemId: null,
  message: null,
  setting: 'tool',
  repository: 'acme/tool',
  githubType: null,
  labels: [],
  assignees: [],
  workItemType: 'task',
  workItemTypeName: 'Task',
  workItemStatus: null,
  workItemAssignees: [],
};

export const ISSUE_7 = 'https://github.com/acme/tool/issues/7';
export const ISSUE_10 = 'https://github.com/acme/tool/issues/10';
export const DISCUSSION_30 = 'https://github.com/acme/tool/discussions/30';
export const DOCS_3 = 'https://github.com/acme/docs/issues/3';

export const ENTRIES: ImportEntry[] = [
  {
    ...base,
    kind: 'ISSUE',
    number: 7,
    title: 'Crash on start',
    url: ISSUE_7,
    status: 'NEW',
    githubType: 'Bug',
    labels: ['bug'],
    assignees: ['alice', 'bob'],
    workItemType: 'defect',
    workItemTypeName: 'Defect',
  },
  {
    ...base,
    kind: 'ISSUE',
    number: 5,
    title: 'Old report',
    url: 'https://github.com/acme/tool/issues/5',
    status: 'EXISTS',
    githubType: 'Bug',
    assignees: ['alice'],
    workItemId: 'EL-12',
    workItemType: 'defect',
    workItemTypeName: 'Defect',
    workItemStatus: 'In Progress',
    workItemAssignees: ['Rob Project'],
  },
  {
    ...base,
    kind: 'ISSUE',
    number: 9,
    title: 'Old idea',
    url: 'https://github.com/acme/tool/issues/9',
    status: 'SKIPPED',
    labels: ['wontfix'],
    message: 'by the rule Label = wontfix',
    workItemType: null,
    workItemTypeName: null,
  },
  {
    ...base,
    kind: 'ISSUE',
    number: 10,
    title: 'Export to CSV',
    url: ISSUE_10,
    status: 'NEW',
    labels: ['enhancement'],
    workItemType: 'changerequest',
    workItemTypeName: 'Change Request',
  },
  {
    ...base,
    kind: 'DISCUSSION',
    number: 30,
    title: 'How to configure',
    url: DISCUSSION_30,
    status: 'NEW',
    githubType: 'Q&A',
  },
  {
    ...base,
    setting: 'docs',
    repository: 'acme/docs',
    kind: 'ISSUE',
    number: 3,
    title: 'Typo in the guide',
    url: DOCS_3,
    status: 'NEW',
    assignees: ['carol'],
  },
];

export const ITEMS = {
  repositories: [
    { setting: 'tool', repository: 'acme/tool', readAt: '2026-10-03T08:00:00Z', error: null },
    { setting: 'docs', repository: 'acme/docs', readAt: '2026-10-03T08:02:00Z', error: null },
    {
      setting: 'broken',
      repository: 'acme/broken',
      readAt: null,
      error: 'Discussions are turned off in the repository acme/broken',
    },
  ],
  entries: ENTRIES,
};

const created = (entry: ImportEntry, workItemId: string): ImportEntry => ({ ...entry, status: 'CREATED', workItemId });

/** The routes of a project with three repository settings. Overrides go first. */
export function itemsRoutes(overrides: Route[] = []): Route[] {
  return [
    ...overrides,
    { method: 'GET', match: /\/projects\/elibrary\/items(\?refresh=true)?$/, json: ITEMS },
    {
      method: 'POST',
      match: /\/repositories\/tool\/import\?dryRun=false/,
      json: {
        repository: 'acme/tool',
        dryRun: false,
        readAt: null,
        entries: [
          created(ENTRIES[0], 'EL-101'),
          { ...ENTRIES[3], status: 'FAILED', message: 'The field severity is required' },
        ],
      },
    },
    {
      method: 'POST',
      match: /\/repositories\/docs\/import\?dryRun=false/,
      json: { repository: 'acme/docs', dryRun: false, readAt: null, entries: [created(ENTRIES[5], 'EL-102')] },
    },
  ];
}
