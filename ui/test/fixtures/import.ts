import type { Route } from '../mockFetch';
import { NAMES, SCOPE } from './repositories';

export { SCOPE };

export const ISSUE_7 = 'https://github.com/acme/tool/issues/7';
export const ISSUE_8 = 'https://github.com/acme/tool/issues/8';
export const DISCUSSION_30 = 'https://github.com/acme/tool/discussions/30';

const entry = (
  kind: string,
  number: number,
  title: string,
  url: string | null,
  status: string,
  workItemId: string | null = null,
) => ({
  kind,
  number,
  title,
  url,
  status,
  workItemId,
  message: null,
});

export const PREVIEW = {
  repository: 'acme/tool',
  dryRun: true,
  entries: [
    entry('ISSUE', 7, 'Crash on start', ISSUE_7, 'NEW'),
    entry('ISSUE', 8, 'Typo in the guide', ISSUE_8, 'NEW'),
    entry('ISSUE', 5, 'Old report', 'https://github.com/acme/tool/issues/5', 'EXISTS', 'EL-12'),
    entry('DISCUSSION', 30, 'How to configure', DISCUSSION_30, 'NEW'),
  ],
};

export const IMPORTED = {
  repository: 'acme/tool',
  dryRun: false,
  entries: [
    entry('ISSUE', 7, 'Crash on start', ISSUE_7, 'CREATED', 'EL-101'),
    {
      ...entry('DISCUSSION', 30, 'How to configure', DISCUSSION_30, 'FAILED'),
      message: 'The field severity is required',
    },
  ],
};

/** The routes of a project with two repository settings. Overrides go first. */
export function importRoutes(overrides: Route[] = []): Route[] {
  return [
    ...overrides,
    { method: 'GET', match: /\/settings\/repositories\/names\?/, json: [...NAMES, { name: 'other', scope: SCOPE }] },
    { method: 'POST', match: /\/import\?dryRun=true/, json: PREVIEW },
    { method: 'POST', match: /\/import\?dryRun=false/, json: IMPORTED },
  ];
}
