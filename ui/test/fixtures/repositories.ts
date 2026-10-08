import type { Route } from '../mockFetch';

export const SCOPE = 'project/elibrary/';

export const NAMES = [{ name: 'tool', scope: SCOPE }];

export const CONTENT = {
  bundleTimestamp: '2026-10-02 14:25',
  repository: 'acme/tool',
  shortName: 'Tool',
  issues: {
    enabled: true,
    workItemType: 'task',
    titleTemplate: '[GitHub] {{ SHORT_NAME }} : {{ TITLE }}',
    descriptionTemplate: '<a href="{{ URL }}">{{ URL }}</a>',
    duplicateKey: 'HYPERLINK',
    duplicateKeyField: null,
    epicId: 'EL-1',
    epicLinkRole: 'parent',
    fields: { severity: 'major' },
    rules: [
      { match: 'LABEL', value: 'wontfix', skip: true, workItemType: null, fields: {} },
      { match: 'TYPE', value: 'Bug', skip: false, workItemType: 'issue', fields: { priority: 'high' } },
    ],
  },
  discussions: {
    enabled: false,
    workItemType: null,
    titleTemplate: '[GitHub] {{ SHORT_NAME }} : {{ TITLE }}',
    descriptionTemplate: '<a href="{{ URL }}">{{ URL }}</a>',
    duplicateKey: 'HYPERLINK',
    duplicateKeyField: null,
    epicId: null,
    epicLinkRole: null,
    fields: {},
    rules: [],
  },
};

export const WORKITEM_TYPES = [
  { id: 'task', name: 'Task' },
  { id: 'issue', name: 'Issue' },
];

export const LINK_ROLES = [
  { id: 'parent', name: 'has parent' },
  { id: 'relates_to', name: 'relates to' },
];

export const FIELDS = [
  { id: 'githubUrl', name: 'GitHub URL', custom: true, urlKey: true },
  { id: 'severity', name: 'Severity', custom: false, urlKey: false },
  // A built-in field: Polarion names it by its ID.
  { id: 'priority', name: 'priority', custom: false, urlKey: false },
  // An enumeration, and one that takes several options.
  {
    id: 'budget',
    name: 'Budget Projekt/Programm',
    custom: true,
    urlKey: false,
    multi: false,
    options: [
      { id: 'internal', name: 'Internal/all', iconUrl: null },
      { id: 'external', name: 'External/all', iconUrl: null },
    ],
  },
  {
    id: 'categories',
    name: 'Categories',
    custom: false,
    urlKey: false,
    multi: true,
    options: [
      { id: 'plugin', name: 'External/Plugin', iconUrl: null },
      { id: 'core', name: 'Core', iconUrl: null },
    ],
  },
];

export const USERS = [
  { id: 'alice', name: 'Alice Admin' },
  { id: 'bob', name: 'Bob Builder' },
];

export const REVISIONS = [
  { name: '120', date: '2026-10-02 14:25', author: 'admin', baseline: '', description: 'Saved' },
  { name: '110', date: '2026-10-01 09:00', author: 'admin', baseline: '', description: 'Created' },
];

/** The routes of a project with one saved repository setting. Later entries can be put first to override. */
export function repositoriesRoutes(overrides: Route[] = []): Route[] {
  return [
    ...overrides,
    { method: 'GET', match: /\/settings\/repositories\/names\?/, json: NAMES },
    { method: 'GET', match: /\/settings\/repositories\/names\/[^/]+\/revisions/, json: REVISIONS },
    { method: 'GET', match: /\/settings\/repositories\/names\/[^/]+\/content/, json: CONTENT },
    {
      method: 'PUT',
      match: /\/settings\/repositories\/names\/[^/]+\/content/,
      status: 204,
      respond: () => new Response(null, { status: 204 }),
    },
    { method: 'GET', match: /\/projects\/[^/]+\/workitem-types\/[^/]+\/fields/, json: FIELDS },
    { method: 'GET', match: /\/internal\/users$/, json: USERS },
    { method: 'GET', match: /\/projects\/[^/]+\/workitem-types$/, json: WORKITEM_TYPES },
    { method: 'GET', match: /\/projects\/[^/]+\/link-roles$/, json: LINK_ROLES },
  ];
}
