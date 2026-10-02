import { expect, test } from '@playwright/test';

const SCOPE = 'project/elibrary/';
const ISSUE_7 = 'https://github.com/acme/tool/issues/7';

const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(data) });
const entry = (status, workItemId = null) => ({
  kind: 'ISSUE',
  number: 7,
  title: 'Crash on start',
  url: ISSUE_7,
  status,
  workItemId,
  message: null,
});

test('reads the items and creates the work items of the selected ones', async ({ page }) => {
  let requested;
  await page.route('**/rest/internal/settings/repositories/names?**', (r) =>
    r.fulfill(json([{ name: 'tool', scope: SCOPE }])),
  );
  await page.route('**/rest/internal/projects/elibrary/repositories/tool/import?dryRun=true', (r) =>
    r.fulfill(json({ repository: 'acme/tool', dryRun: true, entries: [entry('NEW')] })),
  );
  await page.route('**/rest/internal/projects/elibrary/repositories/tool/import?dryRun=false', (r) => {
    requested = r.request().postDataJSON();
    return r.fulfill(json({ repository: 'acme/tool', dryRun: false, entries: [entry('CREATED', 'EL-101')] }));
  });

  await page.goto(`/?feature=import&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
  await page.getByRole('button', { name: 'Read from GitHub' }).click();
  await expect(page.getByRole('link', { name: 'Crash on start' })).toBeVisible();
  await page.getByRole('button', { name: 'Create work items' }).click();

  await expect(page.getByRole('link', { name: 'EL-101' })).toBeVisible();
  await expect(page.getByText('1 work item(s) created.')).toBeVisible();
  expect(requested).toEqual({ urls: [ISSUE_7] });
});
