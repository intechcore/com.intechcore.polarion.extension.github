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
  setting: 'tool',
  repository: 'acme/tool',
  githubType: 'Bug',
  labels: ['bug'],
  assignees: ['alice'],
  workItemType: 'defect',
  workItemTypeName: 'Defect',
  workItemStatus: null,
  workItemAssignees: [],
});

test('the topic lists the items and creates the work item of the selected one', async ({ page }) => {
  let requested;
  await page.route('**/rest/internal/projects/elibrary/items', (r) =>
    r.fulfill(
      json({
        repositories: [{ setting: 'tool', repository: 'acme/tool', readAt: '2026-10-03T08:00:00Z', error: null }],
        entries: [entry('NEW')],
      }),
    ),
  );
  await page.route('**/rest/internal/projects/elibrary/repositories/tool/import?dryRun=false', (r) => {
    requested = r.request().postDataJSON();
    return r.fulfill(
      json({ repository: 'acme/tool', dryRun: false, readAt: null, entries: [entry('CREATED', 'EL-101')] }),
    );
  });

  await page.goto(`/?feature=items&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
  // The number is the link to GitHub, the title is text beside it.
  await expect(page.getByRole('link', { name: '#7' })).toHaveAttribute('href', ISSUE_7);
  await expect(page.getByText('Crash on start')).toBeVisible();
  await page.getByLabel(`Select ${ISSUE_7}`).check();
  await page.getByRole('button', { name: 'Create work items (1)' }).click();

  await expect(page.getByRole('link', { name: 'EL-101' })).toBeVisible();
  await expect(page.getByText('1 work item(s) created.')).toBeVisible();
  expect(requested).toEqual({ urls: [ISSUE_7] });
});
