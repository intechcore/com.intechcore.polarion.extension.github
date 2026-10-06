import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { appButton, captureApp } from './captureApp';
import { SCOPE, itemsRoutes } from './fixtures/items';
import { type Route, installFetchMock } from './mockFetch';

// Docker-only full-page snapshots of the page of the GitHub items (the topic GitHub of a project and the
// administration entry), one per state a user meets.

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

const rows = () => document.querySelectorAll('.items-table tbody tr');

async function open(overrides: Route[] = [], scope = SCOPE) {
  installFetchMock(itemsRoutes(overrides));
  window.history.replaceState({}, '', `?feature=items&embedded=true&scope=${encodeURIComponent(scope)}`);
  render(<App />);
}

describe.skipIf(!__PIXEL_REFERENCES__)('GitHub items page visual', () => {
  it('the items of all repositories, one repository failed', async () => {
    await open();

    await vi.waitFor(() => expect(rows()).toHaveLength(7));
    await captureApp('items-loaded');
  });

  it('the items after a selection was created', async () => {
    await open();
    await vi.waitFor(() => expect(rows()).toHaveLength(7));
    document
      .querySelectorAll<HTMLInputElement>('.items-table tbody input[type="checkbox"]')
      .forEach((box) => box.click());
    await vi.waitFor(() => expect(appButton('Create work items (4)')).toBeDefined());

    appButton('Create work items (4)').click();

    await vi.waitFor(() => expect(document.querySelectorAll('.state-CREATED')).toHaveLength(2));
    await captureApp('items-created');
  });

  it('the list of columns, one column hidden', async () => {
    window.localStorage.setItem(
      'github-items-columns',
      JSON.stringify({
        order: ['item', 'repository', 'labels', 'state', 'workItem', 'status'],
        hidden: ['githubType'],
      }),
    );
    await open();
    await vi.waitFor(() => expect(rows()).toHaveLength(7));

    appButton('Columns').click();

    await vi.waitFor(() => expect(document.querySelector('.columns-panel')).not.toBeNull());
    await captureApp('items-columns');
    window.localStorage.clear();
  });

  it('a project without a repository setting', async () => {
    await open([{ method: 'GET', match: /\/items$/, json: { repositories: [], entries: [] } }]);

    await vi.waitFor(() => expect(document.querySelector('.page')?.textContent).toContain('no repository setting'));
    await captureApp('items-no-settings');
  });

  it('the page opened outside a project', async () => {
    await open([], '');

    await vi.waitFor(() => expect(document.querySelector('.alert-error')).not.toBeNull());
    await captureApp('items-no-project');
  });
});
