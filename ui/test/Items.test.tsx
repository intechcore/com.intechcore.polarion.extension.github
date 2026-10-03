import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import App from '../src/App';
import { DISCUSSION_30, DOCS_3, ISSUE_7, ISSUE_10, ITEMS, SCOPE, itemsRoutes } from './fixtures/items';
import { type FetchMock, type Route, installFetchMock, jsonResponse } from './mockFetch';

// Behavior tests for the page of the GitHub items: the topic of a project and the administration entry.
// REST is mocked at the global fetch boundary, so neither Polarion nor GitHub is needed.

const origUrl = window.location.pathname + window.location.search;
const setUrl = (search: string) => window.history.replaceState({}, '', search);

const button = (label: string): HTMLButtonElement => {
  const found = Array.from(document.querySelectorAll<HTMLButtonElement>('.sbb-btn')).find((b) =>
    (b.textContent ?? '').trim().startsWith(label),
  );
  if (!found) throw new Error(`button "${label}" not found`);
  return found;
};
const numbers = () =>
  Array.from(document.querySelectorAll('.items-table tbody tr')).map((row) => row.children[2].textContent?.trim());
const checkbox = (url: string) => document.querySelector<HTMLInputElement>(`[aria-label="Select ${url}"]`);
// A multi-select draws its trigger as a div, which carries the label as the hidden <select> does.
const dropdown = (label: string) => document.querySelector<HTMLElement>(`div.sd-trigger[aria-label="${label}"]`)!;
const toastText = () => document.querySelector('[data-sonner-toast]')?.textContent ?? '';
const alerts = () => Array.from(document.querySelectorAll('.alert-error')).map((alert) => alert.textContent?.trim());
const mousedown = (el: Element) =>
  el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, composed: true }));

/** Opens a multi-select filter and toggles the options with these texts. */
async function choose(label: string, ...texts: string[]) {
  mousedown(dropdown(label));
  await vi.waitFor(() => expect(document.querySelectorAll('.sd-portal .option').length).toBeGreaterThan(0));
  for (const text of texts) {
    const option = Array.from(document.querySelectorAll<HTMLElement>('.sd-portal .option')).find(
      (o) => o.textContent?.trim() === text,
    );
    if (!option) throw new Error(`option "${text}" not found`);
    mousedown(option);
  }
  document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
}

let fetchMock: FetchMock;

async function mount(overrides: Route[] = []) {
  fetchMock = installFetchMock(itemsRoutes(overrides));
  setUrl(`?feature=items&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
  render(<App />);
  await vi.waitFor(() => expect(numbers()).toHaveLength(6));
}

beforeEach(() => setUrl(origUrl));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  setUrl(origUrl);
});

describe('GitHub items page', () => {
  it('shows the open items of every repository with the state of their work items', async () => {
    await mount();

    expect(numbers()).toEqual([
      'Issue #7 Crash on start',
      'Issue #5 Old report',
      'Issue #9 Old idea',
      'Issue #10 Export to CSV',
      'Discussion #30 How to configure',
      'Issue #3 Typo in the guide',
    ]);
    const existing = Array.from(document.querySelectorAll('.items-table tbody tr')[1].children).map((cell) =>
      cell.textContent?.trim(),
    );
    expect(existing).toEqual([
      '',
      'acme/tool',
      'Issue #5 Old report',
      'Bug',
      '',
      'alice',
      'Has a work item',
      'EL-12',
      'Defect',
      'In Progress',
      'Rob Project',
    ]);
    expect(document.querySelector<HTMLAnchorElement>('.items-table a[target="_top"]')!.getAttribute('href')).toBe(
      '/polarion/#/project/elibrary/workitem?id=EL-12',
    );
    expect(document.querySelector('.items-summary')!.textContent).toBe('6 of 6 open item(s) shown.');
    // A repository that failed is named with the reason. The others stay readable.
    expect(alerts()).toEqual(['broken (acme/broken): Discussions are turned off in the repository acme/broken']);
    expect(document.querySelector('.items-read-at')!.textContent).toContain('The server keeps the lists for 5 minutes');
    // Only new items can be selected, and none is selected at first.
    expect(checkbox(ISSUE_7)!.checked).toBe(false);
    expect(checkbox('https://github.com/acme/tool/issues/5')).toBeNull();
    expect(checkbox('https://github.com/acme/tool/issues/9')).toBeNull();
    expect(button('Create work items').disabled).toBe(true);
    expect(String(fetchMock.mock.calls[0][0])).toBe('/polarion/github/rest/internal/projects/elibrary/items');
  });

  it('filters by repository, type, assignee and state, and searches', async () => {
    await mount();

    await choose('Repository', 'tool');
    await vi.waitFor(() => expect(numbers()).toHaveLength(5));
    await choose('GitHub assignee', 'alice');
    await vi.waitFor(() => expect(numbers()).toEqual(['Issue #7 Crash on start', 'Issue #5 Old report']));
    await choose('State', 'New');
    await vi.waitFor(() => expect(numbers()).toEqual(['Issue #7 Crash on start']));
    expect(document.querySelector('.items-summary')!.textContent).toBe('1 of 6 open item(s) shown.');

    button('Clear filters').click();
    await vi.waitFor(() => expect(numbers()).toHaveLength(6));
    await choose('Work item type', 'Change Request');
    await vi.waitFor(() => expect(numbers()).toEqual(['Issue #10 Export to CSV']));
    button('Clear filters').click();
    await choose('Kind', 'Discussion');
    await vi.waitFor(() => expect(numbers()).toEqual(['Discussion #30 How to configure']));
    button('Clear filters').click();
    await choose('GitHub type', 'Q&A');
    await vi.waitFor(() => expect(numbers()).toHaveLength(1));
    button('Clear filters').click();
    await choose('Polarion assignee', 'Rob Project');
    await vi.waitFor(() => expect(numbers()).toEqual(['Issue #5 Old report']));
    button('Clear filters').click();

    await userEvent.fill(document.querySelector<HTMLInputElement>('[aria-label="Search"]')!, 'nothing like this');
    await vi.waitFor(() => expect(document.querySelector('.items-table')).toBeNull());
    expect(document.querySelector('.items-summary')!.textContent).toBe('0 of 6 open item(s) shown.');
  });

  it('creates the work items of the selected items, one request per repository', async () => {
    await mount();
    await userEvent.click(checkbox(ISSUE_7)!);
    await userEvent.click(checkbox(ISSUE_10)!);
    await userEvent.click(checkbox(DOCS_3)!);
    expect(button('Create work items').textContent).toContain('(3)');

    button('Create work items').click();

    await vi.waitFor(() => expect(toastText()).toContain('2 work item(s) created, 1 failed.'));
    const imports = fetchMock.mock.calls.filter((c) => /dryRun=false/.test(String(c[0])));
    expect(imports.map((c) => [String(c[0]).replace(/.*repositories\//, ''), JSON.parse(String(c[1]!.body))])).toEqual([
      ['tool/import?dryRun=false', { urls: [ISSUE_7, ISSUE_10] }],
      ['docs/import?dryRun=false', { urls: [DOCS_3] }],
    ]);
    const states = Array.from(document.querySelectorAll('.items-table tbody tr')).map((row) =>
      row.children[6].textContent?.trim(),
    );
    expect(states).toEqual([
      'Created',
      'Has a work item',
      'Left out: by the rule Label = wontfix',
      'Failed: The field severity is required',
      'New',
      'Created',
    ]);
    expect(button('Create work items').disabled).toBe(true);
  });

  it('selects all new items shown, and clears them again', async () => {
    await mount();
    await choose('Repository', 'tool');
    await vi.waitFor(() => expect(numbers()).toHaveLength(5));

    const all = document.querySelector<HTMLInputElement>('[aria-label="Select all new items shown"]')!;
    await userEvent.click(all);

    expect([checkbox(ISSUE_7)!.checked, checkbox(ISSUE_10)!.checked, checkbox(DISCUSSION_30)!.checked]).toEqual([
      true,
      true,
      true,
    ]);
    expect(button('Create work items').textContent).toContain('(3)');
    await userEvent.click(all);
    expect(button('Create work items').disabled).toBe(true);
  });

  it('reports a full success and a repository whose request failed', async () => {
    await mount([
      {
        method: 'POST',
        match: /\/repositories\/tool\/import/,
        json: { repository: 'acme/tool', dryRun: false, entries: [] },
      },
      {
        method: 'POST',
        match: /\/repositories\/docs\/import/,
        respond: () => jsonResponse({ message: 'No permission' }, 403),
      },
    ]);
    await userEvent.click(checkbox(ISSUE_7)!);

    button('Create work items').click();
    await vi.waitFor(() => expect(toastText()).toContain('0 work item(s) created.'));

    await userEvent.click(checkbox(DOCS_3)!);
    button('Create work items').click();
    await vi.waitFor(() => expect(alerts()).toContain('docs: No permission'));
  });

  it('reads the items again on Refresh, and GitHub again on Update from GitHub', async () => {
    await mount();

    button('Refresh').click();
    await vi.waitFor(() => expect(fetchMock.mock.calls.filter((c) => /\/items$/.test(String(c[0])))).toHaveLength(2));

    // The buttons stay disabled while a read runs.
    await vi.waitFor(() => expect(button('Update from GitHub').disabled).toBe(false));
    button('Update from GitHub').click();
    await vi.waitFor(() =>
      expect(fetchMock.mock.calls.map((c) => String(c[0]))).toContain(
        '/polarion/github/rest/internal/projects/elibrary/items?refresh=true',
      ),
    );
  });

  it('shows why the items did not load', async () => {
    fetchMock = installFetchMock(
      itemsRoutes([{ method: 'GET', match: /\/items$/, respond: () => new Response('down', { status: 500 }) }]),
    );
    setUrl(`?feature=items&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
    render(<App />);

    await vi.waitFor(() => expect(alerts()).toEqual(['down']));
  });

  it('points to the Repositories page while the project has no setting', async () => {
    fetchMock = installFetchMock(
      itemsRoutes([{ method: 'GET', match: /\/items$/, json: { repositories: [], entries: [] } }]),
    );
    setUrl(`?feature=items&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('.page')?.textContent).toContain('no repository setting'));
    expect(document.querySelector('.item-filters')).toBeNull();
  });

  it('shows an item without a URL as plain text and leaves out the read time when nothing was read', async () => {
    fetchMock = installFetchMock(
      itemsRoutes([
        {
          method: 'GET',
          match: /\/items$/,
          json: {
            repositories: [
              { setting: 'draft', repository: null, readAt: null, error: 'The repository must be given as owner/name' },
            ],
            entries: [
              { ...ITEMS.entries[0], url: null, status: 'FAILED', message: 'The item has no URL in the repository' },
            ],
          },
        },
      ]),
    );
    setUrl(`?feature=items&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
    render(<App />);

    await vi.waitFor(() => expect(numbers()).toHaveLength(1));
    expect(document.querySelector('.items-table tbody a')).toBeNull();
    expect(document.querySelector('.items-read-at')).toBeNull();
    expect(alerts()).toEqual(['draft: The repository must be given as owner/name']);
  });

  it('asks for a project when opened without one', async () => {
    fetchMock = installFetchMock(itemsRoutes());
    setUrl('?feature=items&embedded=true&scope=');
    render(<App />);

    await vi.waitFor(() =>
      expect(alerts()).toEqual(['The GitHub items belong to a project. Open this page from a project.']),
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
