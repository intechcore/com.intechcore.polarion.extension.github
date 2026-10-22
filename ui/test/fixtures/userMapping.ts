import type { Route } from '../mockFetch';

export const PROJECT_SCOPE = 'project/elibrary/';

export const USERS = [
  { id: 'aj', name: 'Alice Jones' },
  { id: 'bk', name: 'Bob King' },
];

export const MAPPING = {
  users: [
    { githubLogin: 'alice', polarionUser: 'aj' },
    { githubLogin: 'bob-gh', polarionUser: 'bk' },
  ],
  inherited: false,
};

/** The routes of the User Mapping page. Overrides go first, so the first match wins. */
export function userMappingRoutes(overrides: Route[] = []): Route[] {
  return [
    ...overrides,
    { method: 'GET', match: /\/users$/, json: USERS },
    { method: 'GET', match: /\/user-mapping\?scope=/, json: MAPPING },
  ];
}
