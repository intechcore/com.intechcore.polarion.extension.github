import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import App from '../src/App';
import { DISCUSSION_30, ISSUE_7, ISSUE_8, SCOPE, importRoutes } from './fixtures/import';
import { type FetchMock, type Route, installFetchMock, jsonResponse } from './mockFetch';

// Behavior tests for the Import page, driven through the real App. REST is mocked at the global
// fetch boundary, so neither Polarion nor GitHub is needed.

const origUrl = window.location.pathname + window.location.search;
const setUrl = (search: string) => window.history.replaceState({}, '', search);

const button = (label: string): HTMLButtonElement => {
  const found = Array.from(document.querySelectorAll<HTMLButtonElement>('.sbb-btn')).find(
    (b) => (b.textContent ?? '').trim() === label,
  );
  if (!found) throw new Error(`button "${label}" not found`);
  return found;
};
const rows = () =>
  Array.from(document.querySelectorAll('.import-table tbody tr')).map((row) =>
    Array.from(row.querySelectorAll('td'))
      .slice(1)
      .map((cell) => cell.textContent?.trim()),
  );
const checkbox = (url: string) => document.querySelector<HTMLInputElement>(`[aria-label="Select ${url}"]`);
const toastText = () => document.querySelector('[data-sonner-toast]')?.textContent ?? '';
const alertText = () => document.querySelector('.alert-error')?.textContent ?? '';
const mousedown = (el: Element) =>
  el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, composed: true }));

let fetchMock: FetchMock;

async function mount(overrides: Route[] = []) {
  fetchMock = installFetchMock(importRoutes(overrides));
  setUrl(`?feature=import&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
  render(<App />);
  await vi.waitFor(() => expect(document.querySelector('.import-controls .sd-trigger')).not.toBeNull());
  await vi.waitFor(() =>
    expect(document.querySelector<HTMLInputElement>('.import-controls .sd-trigger')!.value).toBe('tool'),
  );
}

async function read() {
  button('Read from GitHub').click();
  await vi.waitFor(() => expect(rows()).toHaveLength(4));
}

const importCall = () => fetchMock.mock.calls.find((c) => /dryRun=false/.test(String(c[0])));

beforeEach(() => setUrl(origUrl));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  setUrl(origUrl);
});

describe('Import page', () => {
  it('reads the open items and selects the new ones', async () => {
    await mount();
    expect(button('Create work items').disabled).toBe(true);

    await read();

    expect(rows()).toEqual([
      ['Issue', '7', 'Crash on start', 'New', ''],
      ['Issue', '8', 'Typo in the guide', 'New', ''],
      ['Issue', '5', 'Old report', 'Exists', 'EL-12'],
      ['Discussion', '30', 'How to configure', 'New', ''],
    ]);
    expect(document.querySelector('.import-summary')!.textContent).toBe(
      '4 open item(s): 3 new, 1 with a work item, 0 failed.',
    );
    expect(checkbox(ISSUE_7)!.checked).toBe(true);
    // An item with a work item cannot be selected, and its work item opens in Polarion itself.
    expect(checkbox('https://github.com/acme/tool/issues/5')).toBeNull();
    const workItem = document.querySelector<HTMLAnchorElement>('.import-table a[target="_top"]')!;
    expect(workItem.getAttribute('href')).toBe('/polarion/#/project/elibrary/workitem?id=EL-12');
    expect(document.querySelector<HTMLAnchorElement>('.import-table a[target="_blank"]')!.href).toBe(ISSUE_7);
    expect(String(fetchMock.mock.calls.at(-1)![0])).toBe(
      '/polarion/github/rest/internal/projects/elibrary/repositories/tool/import?dryRun=true',
    );
  });

  it('creates the work items of the selected items and shows the outcome in place', async () => {
    await mount();
    await read();
    await userEvent.click(checkbox(ISSUE_8)!);

    button('Create work items').click();

    await vi.waitFor(() => expect(toastText()).toContain('1 work item(s) created, 1 failed.'));
    expect(JSON.parse(String(importCall()![1]!.body))).toEqual({ urls: [ISSUE_7, DISCUSSION_30] });
    expect(rows()).toEqual([
      ['Issue', '7', 'Crash on start', 'Created', 'EL-101'],
      ['Issue', '8', 'Typo in the guide', 'New', ''],
      ['Issue', '5', 'Old report', 'Exists', 'EL-12'],
      ['Discussion', '30', 'How to configure', 'Failed: The field severity is required', ''],
    ]);
    expect(document.querySelector('.import-summary')!.textContent).toBe(
      '4 open item(s): 1 new, 2 with a work item, 1 failed.',
    );
    // The selection is spent. The item left out can be selected again.
    expect(button('Create work items').disabled).toBe(true);
    await userEvent.click(checkbox(ISSUE_8)!);
    expect(button('Create work items').disabled).toBe(false);
  });

  it('reports a full success', async () => {
    await mount([
      {
        method: 'POST',
        match: /dryRun=false/,
        json: { repository: 'acme/tool', dryRun: false, entries: [] },
      },
    ]);
    await read();

    button('Create work items').click();

    await vi.waitFor(() => expect(toastText()).toContain('0 work item(s) created.'));
  });

  it('shows why GitHub did not answer', async () => {
    await mount([
      {
        method: 'POST',
        match: /dryRun=true/,
        respond: () => jsonResponse({ message: 'GitHub rate limit exceeded, it resets at 2026-10-02T15:00:55Z' }, 429),
      },
    ]);

    button('Read from GitHub').click();

    await vi.waitFor(() => expect(alertText()).toContain('rate limit exceeded'));
    expect(document.querySelector('.import-table')).toBeNull();
  });

  it('shows why the work items were not created', async () => {
    await mount([
      { method: 'POST', match: /dryRun=false/, respond: () => jsonResponse({ message: 'No permission' }, 403) },
    ]);
    await read();

    button('Create work items').click();

    await vi.waitFor(() => expect(alertText()).toContain('No permission'));
    // The list stays, so the user can try again.
    expect(rows()).toHaveLength(4);
  });

  it('drops the list when another repository is selected', async () => {
    await mount();
    await read();

    mousedown(document.querySelector('.import-controls .sd-trigger')!);
    await vi.waitFor(() => expect(document.querySelectorAll('.sd-portal .option').length).toBeGreaterThan(1));
    mousedown(
      Array.from(document.querySelectorAll<HTMLElement>('.sd-portal .option')).find(
        (option) => option.textContent?.trim() === 'other',
      )!,
    );

    await vi.waitFor(() => expect(document.querySelector('.import-table')).toBeNull());
    button('Read from GitHub').click();
    await vi.waitFor(() =>
      expect(fetchMock.mock.calls.some((c) => /\/repositories\/other\/import/.test(String(c[0])))).toBe(true),
    );
  });

  it('shows an empty repository without a table', async () => {
    await mount([
      { method: 'POST', match: /dryRun=true/, json: { repository: 'acme/tool', dryRun: true, entries: [] } },
    ]);

    button('Read from GitHub').click();

    await vi.waitFor(() => expect(document.querySelector('.import-summary')?.textContent).toContain('0 open item(s)'));
    expect(document.querySelector('.import-table')).toBeNull();
  });

  it('shows an item without a URL as plain text', async () => {
    await mount([
      {
        method: 'POST',
        match: /dryRun=true/,
        json: {
          repository: 'acme/tool',
          dryRun: true,
          entries: [
            {
              kind: 'ISSUE',
              number: 9,
              title: 'No URL',
              url: null,
              status: 'FAILED',
              workItemId: null,
              message: 'The item has no URL in the repository',
            },
          ],
        },
      },
    ]);

    button('Read from GitHub').click();

    await vi.waitFor(() => expect(rows()).toHaveLength(1));
    expect(document.querySelector('.import-table tbody a')).toBeNull();
    expect(rows()[0][3]).toBe('Failed: The item has no URL in the repository');
  });

  it('points to the Repositories page while the project has no setting', async () => {
    fetchMock = installFetchMock(importRoutes([{ method: 'GET', match: /\/names\?/, json: [] }]));
    setUrl(`?feature=import&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('.page')?.textContent).toContain('no repository setting'));
    expect(document.querySelector('.import-controls')).toBeNull();
  });

  it('says why the settings did not load', async () => {
    fetchMock = installFetchMock(
      importRoutes([{ method: 'GET', match: /\/names\?/, respond: () => new Response('down', { status: 500 }) }]),
    );
    setUrl(`?feature=import&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
    render(<App />);

    await vi.waitFor(() => expect(alertText()).toContain('down'));
  });

  it('drops the settings that arrive after the page is gone', async () => {
    const base = installFetchMock(importRoutes());
    for (const answer of [() => base('/names?', undefined), () => Promise.reject(new Error('late'))]) {
      const late = vi.fn(
        () => new Promise<Response>((resolve, reject) => setTimeout(() => answer().then(resolve, reject), 150)),
      );
      vi.stubGlobal('fetch', late);
      setUrl(`?feature=import&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
      render(<App />);
      await vi.waitFor(() => expect(late).toHaveBeenCalled());
      cleanup();
      await new Promise((resolve) => setTimeout(resolve, 300));
      expect(document.querySelector('.import-controls')).toBeNull();
    }
  });

  it('asks for a project when opened without one', async () => {
    fetchMock = installFetchMock(importRoutes());
    setUrl('?feature=import&embedded=true&scope=');
    render(<App />);

    await vi.waitFor(() => expect(alertText()).toContain('Open this page from a project'));
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
