import { expect, test } from '@playwright/test';

const VERSION = {
  bundleName: 'GitHub Integration for Polarion ALM',
  bundleVendor: 'Intechcore GmbH',
  supportEmail: 'polarion@intechcore.com',
  automaticModuleName: 'com.intechcore.polarion.extension.github',
  bundleVersion: '1.0.0',
  bundleBuildTimestamp: '2026-07-01 10:00',
};

const json = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(data) });

test.beforeEach(async ({ page }) => {
  await page.route('**/rest/internal/version', (r) => r.fulfill(json(VERSION)));
  await page.route('**/rest/internal/configuration-properties', (r) =>
    r.fulfill(json({ properties: [], obsoleteProperties: [] })),
  );
  await page.route('**/rest/internal/configuration-status**', (r) => r.fulfill(json([])));
  await page.route('**/rest/internal/readme', (r) =>
    r.fulfill({ status: 200, contentType: 'text/html', body: '<h1>GitHub Integration for Polarion ALM</h1>' }),
  );
});

test('the About page shows the extension information', async ({ page }) => {
  await page.goto('/?feature=about');

  await expect(page.getByText('com.intechcore.polarion.extension.github')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'GitHub Integration for Polarion ALM' })).toBeVisible();
});

test('an unknown feature falls back to the About page', async ({ page }) => {
  await page.goto('/?feature=unknown');

  await expect(page.getByText('com.intechcore.polarion.extension.github')).toBeVisible();
});
