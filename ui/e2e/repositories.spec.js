import { expect, test } from '@playwright/test';

const SCOPE = 'project/elibrary/';

const CONTENT = {
  repository: 'acme/tool',
  shortName: 'Tool',
  issues: {
    enabled: true,
    workItemType: 'task',
    titleTemplate: '[GitHub] {shortName} : {title}',
    descriptionTemplate: '<a href="{url}">{url}</a>',
    duplicateKey: 'HYPERLINK',
    duplicateKeyField: null,
    epicId: null,
    epicLinkRole: null,
    fields: {},
  },
  discussions: { enabled: false, duplicateKey: 'HYPERLINK', fields: {} },
};

const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(data) });

let saved;

test.beforeEach(async ({ page }) => {
  saved = undefined;
  await page.route('**/rest/internal/settings/repositories/names?**', (r) =>
    r.fulfill(json([{ name: 'tool', scope: SCOPE }])),
  );
  await page.route('**/rest/internal/settings/repositories/names/tool/content?**', (r) => {
    if (r.request().method() === 'PUT') {
      saved = r.request().postDataJSON();
      return r.fulfill({ status: 204 });
    }
    return r.fulfill(json(CONTENT));
  });
  await page.route('**/rest/internal/projects/elibrary/workitem-types', (r) =>
    r.fulfill(json([{ id: 'task', name: 'Task' }])),
  );
  await page.route('**/rest/internal/projects/elibrary/link-roles', (r) => r.fulfill(json([])));
  await page.route('**/rest/internal/projects/elibrary/workitem-types/task/fields', (r) => r.fulfill(json([])));
});

test('edits and saves a repository setting', async ({ page }) => {
  await page.goto(`/?feature=repositories&embedded=true&scope=${encodeURIComponent(SCOPE)}`);

  await expect(page.getByLabel('GitHub repository:')).toHaveValue('acme/tool');
  await page.getByLabel('Short name:').fill('Renamed');
  await page.getByRole('button', { name: 'Save' }).click();

  await expect(page.getByText('Data successfully saved.')).toBeVisible();
  expect(saved.shortName).toBe('Renamed');
  expect(saved.issues.workItemType).toBe('task');
});

test('asks for a project outside of one', async ({ page }) => {
  await page.goto('/?feature=repositories&embedded=true');

  await expect(page.getByText('Open this page from a project')).toBeVisible();
});
