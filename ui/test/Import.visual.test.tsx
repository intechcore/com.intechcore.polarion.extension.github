import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { appButton, captureApp } from './captureApp';
import { SCOPE, importRoutes } from './fixtures/import';
import { type Route, installFetchMock, jsonResponse } from './mockFetch';

// Docker-only full-page snapshots of the Import page (hivemodule.xml, extender `import`), one per
// state of a manual import.

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

const rows = () => document.querySelectorAll('.import-table tbody tr');

function open(overrides: Route[] = [], scope = SCOPE) {
  installFetchMock(importRoutes(overrides));
  window.history.replaceState({}, '', `?feature=import&embedded=true&scope=${encodeURIComponent(scope)}`);
  render(<App />);
}

async function ready() {
  await vi.waitFor(() =>
    expect(document.querySelector<HTMLInputElement>('.import-controls .sd-trigger')?.value).toBe('tool'),
  );
}

describe.skipIf(!__PIXEL_REFERENCES__)('Import page visual', () => {
  it('before anything is read', async () => {
    open();

    await ready();
    await captureApp('import-initial');
  });

  it('the list read from GitHub, with the new items selected', async () => {
    open();
    await ready();

    appButton('Read from GitHub').click();

    await vi.waitFor(() => expect(rows()).toHaveLength(5));
    await captureApp('import-preview');
  });

  it('the list after an import', async () => {
    open();
    await ready();
    appButton('Read from GitHub').click();
    await vi.waitFor(() => expect(rows()).toHaveLength(5));

    appButton('Create work items').click();

    await vi.waitFor(() => expect(document.querySelector('.status-CREATED')).not.toBeNull());
    await captureApp('import-done');
  });

  it('a repository without open items', async () => {
    open([{ method: 'POST', match: /dryRun=true/, json: { repository: 'acme/tool', dryRun: true, entries: [] } }]);
    await ready();

    appButton('Read from GitHub').click();

    await vi.waitFor(() => expect(document.querySelector('.import-summary')).not.toBeNull());
    await captureApp('import-no-items');
  });

  it('a GitHub failure', async () => {
    open([
      {
        method: 'POST',
        match: /dryRun=true/,
        respond: () => jsonResponse({ message: 'GitHub rate limit exceeded, it resets at 2026-10-02T15:00:55Z' }, 429),
      },
    ]);
    await ready();

    appButton('Read from GitHub').click();

    await vi.waitFor(() => expect(document.querySelector('.alert-error')).not.toBeNull());
    await captureApp('import-github-error');
  });

  it('a project without a repository setting', async () => {
    open([{ method: 'GET', match: /\/names\?/, json: [] }]);

    await vi.waitFor(() => expect(document.querySelector('.page')?.textContent).toContain('no repository setting'));
    await captureApp('import-no-settings');
  });

  it('the page opened outside a project', async () => {
    open([], '');

    await vi.waitFor(() => expect(document.querySelector('.alert-error')).not.toBeNull());
    await captureApp('import-no-project');
  });
});
