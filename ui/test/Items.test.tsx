import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import App from '../src/App';
import { COLUMN_LABELS, loadLayout, visibleColumns } from '../src/services/columns';
import {
  DASHBOARD_2,
  DEFECT_ICON,
  DISCUSSION_30,
  DOCS_3,
  FAILED_PULL_REQUEST,
  IN_PROGRESS_ICON,
  ISSUE_4,
  ISSUE_7,
  ISSUE_10,
  ITEMS,
  SCOPE,
  itemsRoutes,
} from './fixtures/items';
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
// The item cell as a reader sees it: the number and the title. The kind shows as an icon.
const numbers = () =>
  Array.from(document.querySelectorAll('.items-table tbody tr:not(.items-empty-row)')).map(
    (row) => `${row.querySelector('.item-number')?.textContent} ${row.querySelector('.item-title')?.textContent}`,
  );
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
  await vi.waitFor(() => expect(numbers()).toHaveLength(7));
}

const settingsButton = () => document.querySelector<HTMLButtonElement>('button[aria-label="Table settings"]')!;
const headers = () =>
  Array.from(document.querySelectorAll('.items-table thead th:not(.table-settings-cell)')).map((th) => th.textContent);
const columnBox = (label: string) =>
  Array.from(document.querySelectorAll<HTMLLabelElement>('.table-settings-panel .columns-row label'))
    .find((l) => l.textContent === label)!
    .querySelector('input')!;

beforeEach(() => {
  setUrl(origUrl);
  window.localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  setUrl(origUrl);
});

describe('GitHub items page', () => {
  it('shows the open items of every repository with the state of their work items', async () => {
    await mount();

    expect(numbers()).toEqual([
      '#7 Crash on start',
      '#5 Old report',
      '#9 Old idea',
      '#10 Export to CSV',
      '#30 How to configure',
      '#3 Typo in the guide',
      '#4 Renamed on GitHub',
    ]);
    const cells = (row: number) =>
      Array.from(document.querySelectorAll('.items-table tbody tr')[row].children).map((cell) =>
        cell.textContent?.trim(),
      );
    // The repository shows its short name, the item its number as the link, the work item the icon of its type.
    const existing = cells(1);
    expect([existing[1], existing[3], existing[5], existing[6], existing[7], existing[8], existing[9]]).toEqual([
      'Tool',
      'Bug',
      'alice',
      'Has a work item',
      'EL-12',
      'In Progress',
      'Rob Project',
    ]);
    const rows = document.querySelectorAll('.items-table tbody tr');
    expect(rows[1].children[1].getAttribute('title')).toBe('acme/tool');
    expect(rows[1].querySelector<HTMLAnchorElement>('a.item-number')!.href).toBe(
      'https://github.com/acme/tool/issues/5',
    );
    expect(rows[1].querySelector('.item-title a')).toBeNull();
    expect(rows[1].querySelector<HTMLImageElement>('.work-item-cell img')!.getAttribute('src')).toBe(DEFECT_ICON);
    expect(rows[1].querySelector('.state-EXISTS svg')).not.toBeNull();
    expect(rows[1].children[8].querySelector('img')!.getAttribute('src')).toBe(IN_PROGRESS_ICON);
    // An item without a work item leaves the work item and the status empty.
    expect([cells(0)[7], cells(0)[8]]).toEqual(['', '']);
    expect(rows[0].querySelector('.work-item-cell')).toBeNull();
    // A status without an icon shows its name alone.
    expect(rows[6].children[8].textContent).toBe('Open');
    expect(rows[6].children[8].querySelector('img')).toBeNull();
    // Labels take the colors of GitHub, with dark text on a light label and white on a dark one.
    const chips = Array.from(rows[0].querySelectorAll<HTMLElement>('.label-chip'));
    expect(chips.map((chip) => [chip.textContent, chip.style.backgroundColor, chip.style.color])).toEqual([
      ['bug', 'rgb(215, 58, 74)', 'rgb(255, 255, 255)'],
      ['help wanted', 'rgb(162, 238, 239)', 'rgb(31, 35, 40)'],
    ]);
    expect(rows[2].querySelector<HTMLElement>('.label-chip')!.getAttribute('style')).toBeNull();
    expect(document.querySelector<HTMLAnchorElement>('.items-table a[target="_top"]')!.getAttribute('href')).toBe(
      '/polarion/#/project/elibrary/workitem?id=EL-12',
    );
    expect(document.querySelector('.items-summary')!.textContent).toBe('7 of 8 open item(s) shown. 1 hidden.');
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

  it('hides and moves columns, and keeps the layout for the next visit', async () => {
    await mount();
    await userEvent.click(settingsButton());
    expect(document.querySelector('.table-settings-panel')).not.toBeNull();

    await userEvent.click(columnBox('GitHub type'));
    await userEvent.click(document.querySelector<HTMLButtonElement>('[aria-label="Move Labels up"]')!);
    await userEvent.click(document.querySelector<HTMLButtonElement>('[aria-label="Move Status down"]')!);

    const changed = [
      '',
      'Repository',
      'Item',
      'Labels',
      'GitHub assignees',
      'State',
      'Work item',
      'Polarion assignees',
      'Status',
    ];
    expect(document.querySelector<HTMLButtonElement>('[aria-label="Move Status down"]')!.disabled).toBe(true);
    expect(headers()).toEqual(changed);
    expect(document.querySelector<HTMLButtonElement>('[aria-label="Move Repository up"]')!.disabled).toBe(true);
    expect(document.querySelectorAll('.items-table tbody tr')[0].children[3].textContent).toBe('bughelp wanted');

    // The next visit reads the layout from the browser.
    expect(loadLayout()).toEqual(JSON.parse(window.localStorage.getItem('github-items-columns')!));
    expect(visibleColumns(loadLayout()).map((id) => COLUMN_LABELS[id])).toEqual(changed.slice(1));

    await userEvent.click(button('Reset columns'));
    await vi.waitFor(() => expect(headers()).toContain('GitHub type'));
    expect(headers()[3]).toBe('GitHub type');
    await userEvent.click(settingsButton());
    expect(document.querySelector('.table-settings-panel')).toBeNull();
  });

  it('keeps the last visible column', async () => {
    await mount();
    await userEvent.click(settingsButton());
    expect(document.querySelector('.table-settings-panel')).not.toBeNull();

    for (const label of ['Repository', 'GitHub type', 'Labels', 'GitHub assignees', 'State', 'Work item', 'Status']) {
      await userEvent.click(columnBox(label));
    }
    await userEvent.click(columnBox('Polarion assignees'));

    expect(headers()).toEqual(['', 'Item']);
    expect(columnBox('Item').disabled).toBe(true);
  });

  it('hides an item for the project, lists the hidden ones on request and shows one again', async () => {
    await mount();
    expect(numbers()).not.toContain('#2 Dependency Dashboard');
    await userEvent.click(checkbox(ISSUE_7)!);

    await userEvent.click(document.querySelector<HTMLButtonElement>('[aria-label="Hide #7"]')!);

    await vi.waitFor(() => expect(numbers()).toHaveLength(6));
    expect(document.querySelector('.items-summary')!.textContent).toBe('6 of 8 open item(s) shown. 2 hidden.');
    const hides = fetchMock.mock.calls.filter((c) => /hidden-items$/.test(String(c[0])));
    expect(hides.map((c) => JSON.parse(String(c[1]!.body)))).toEqual([{ urls: [ISSUE_7], hidden: true }]);
    // A hidden item leaves the selection.
    expect(button('Create work items').disabled).toBe(true);

    await userEvent.click(settingsButton());
    const show = document.querySelector<HTMLLabelElement>('.table-settings-hidden')!;
    expect(show.textContent).toBe('Show hidden items (2)');
    await userEvent.click(show.querySelector('input')!);
    await vi.waitFor(() => expect(numbers()).toHaveLength(8));
    // The open panel below a short table makes the page taller, so a frame sized to the page shows it.
    const table = document.querySelector<HTMLElement>('.items-table')!;
    const panelBottom = document.querySelector('.table-settings-panel')!.getBoundingClientRect().bottom;
    expect(table.getBoundingClientRect().bottom + parseFloat(table.style.marginBottom)).toBeGreaterThanOrEqual(
      panelBottom,
    );
    // A click outside closes the table settings.
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    await vi.waitFor(() => expect(document.querySelector('.table-settings-panel')).toBeNull());
    expect(table.style.marginBottom).toBe('');
    expect(document.querySelectorAll('.items-table tr.item-hidden')).toHaveLength(2);
    expect(document.querySelector('.items-summary')!.textContent).toBe('8 of 8 open item(s) shown.');

    await userEvent.click(document.querySelector<HTMLButtonElement>('[aria-label="Show #2"]')!);
    await vi.waitFor(() => expect(document.querySelectorAll('.items-table tr.item-hidden')).toHaveLength(0));
    expect(
      JSON.parse(String(fetchMock.mock.calls.filter((c) => /hidden-items$/.test(String(c[0])))[1][1]!.body)),
    ).toEqual({
      urls: [DASHBOARD_2],
      hidden: false,
    });
  });

  it('reports an item that could not be hidden', async () => {
    await mount([
      {
        method: 'POST',
        match: /hidden-items$/,
        respond: () => jsonResponse({ message: 'No permission' }, 403),
      },
    ]);

    await userEvent.click(document.querySelector<HTMLButtonElement>('[aria-label="Hide #7"]')!);

    await vi.waitFor(() => expect(alerts()).toContain('No permission'));
    expect(numbers()).toHaveLength(7);
  });

  it('shows a pull request with its failed checks, and filters it by its kind', async () => {
    fetchMock = installFetchMock(
      itemsRoutes([
        { method: 'GET', match: /\/items$/, json: { ...ITEMS, entries: [...ITEMS.entries, FAILED_PULL_REQUEST] } },
      ]),
    );
    setUrl(`?feature=items&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
    render(<App />);
    await vi.waitFor(() => expect(numbers()).toHaveLength(8));

    const row = document.querySelectorAll('.items-table tbody tr')[7];
    expect(row.querySelector('.kind-PULL_REQUEST')).not.toBeNull();
    expect(row.querySelector('.item-checks')!.textContent).toBe('Failed checks: build, e2e');
    expect(checkbox(FAILED_PULL_REQUEST.url!)).not.toBeNull();

    await choose('Kind', 'Pull request');
    await vi.waitFor(() =>
      expect(numbers()).toEqual(['#421 fix(deps): update docx4j.version to v17.3.0Failed checks: build, e2e']),
    );
  });

  async function mountWidget(query: string) {
    fetchMock = installFetchMock(itemsRoutes());
    setUrl(`?feature=items&embedded=true&widget=true&scope=${encodeURIComponent(SCOPE)}${query}`);
    render(<App />);
    await vi.waitFor(() => expect(document.querySelector('.items-summary')).not.toBeNull());
  }

  it('opens as a report in a Live Report widget, with the filters of its settings', async () => {
    const posted = vi.spyOn(window.parent, 'postMessage');
    await mountWidget('&kinds=ISSUE&states=NEW');

    await vi.waitFor(() =>
      expect(numbers()).toEqual(['#7 Crash on start', '#10 Export to CSV', '#3 Typo in the guide']),
    );
    // No title of its own, no selection, no Create or Update, no hiding: the page is a report.
    expect(document.querySelector('h1')).toBeNull();
    expect(document.querySelector('.app.widget')).not.toBeNull();
    expect(checkbox(ISSUE_7)).toBeNull();
    expect(document.querySelector('[aria-label="Select all new and outdated items shown"]')).toBeNull();
    expect(document.querySelector('[aria-label="Hide #7"]')).toBeNull();
    expect(document.querySelector('.items-toolbar')!.textContent).not.toContain('Create work items');
    // The filters stay, and the reader can change them.
    expect(button('Clear filters').disabled).toBe(false);
    button('Clear filters').click();
    await vi.waitFor(() => expect(numbers()).toHaveLength(7));
    // The widget learns the height of the page, to fit its frame.
    expect(posted).toHaveBeenCalledWith(expect.objectContaining({ type: 'github-app-height' }), window.location.origin);
    posted.mockRestore();
  });

  it('shows only the table, in the columns of the widget, and keeps the layout of the reader', async () => {
    window.localStorage.setItem('github-items-columns', JSON.stringify({ order: ['labels'], hidden: [] }));
    await mountWidget('&hideFilters=true&columns=item%2Cstate');
    await vi.waitFor(() => expect(numbers()).toHaveLength(7));

    expect(document.querySelector('.item-filters')).toBeNull();
    expect(document.querySelector('.items-toolbar')).toBeNull();
    expect(headers()).toEqual(['Item', 'State']);

    await userEvent.click(settingsButton());
    await userEvent.click(columnBox('Labels'));
    expect(headers()).toEqual(['Item', 'State', 'Labels']);
    // The columns of the widget never overwrite what the reader chose on the topic.
    expect(JSON.parse(window.localStorage.getItem('github-items-columns')!)).toEqual({ order: ['labels'], hidden: [] });
  });

  it('lets a widget create work items when its settings allow it', async () => {
    await mountWidget('&hideFilters=true&allowCreate=true');
    await vi.waitFor(() => expect(numbers()).toHaveLength(7));

    expect(document.querySelector('.item-filters')).toBeNull();
    // Only the actions on the selection: reading GitHub again belongs to the filters it hides.
    expect(document.querySelector('.items-toolbar')!.textContent).toContain('Create work items');
    expect(document.querySelector('.items-toolbar')!.textContent).not.toContain('Refresh');
    expect(document.querySelector('.items-read-at')).toBeNull();
    await userEvent.click(checkbox(ISSUE_7)!);
    expect(button('Create work items').textContent).toContain('(1)');
  });

  it('shows the read time on the 24-hour clock', async () => {
    await mount([
      {
        method: 'GET',
        match: /\/items$/,
        json: {
          ...ITEMS,
          repositories: [{ setting: 'tool', repository: 'acme/tool', readAt: '2026-10-03T18:48:00Z', error: null }],
        },
      },
    ]);

    const text = document.querySelector('.items-read-at')!.textContent!;
    const local = new Date('2026-10-03T18:48:00Z');
    expect(text).toContain(`Read from GitHub at ${String(local.getHours()).padStart(2, '0')}:48.`);
    expect(text).not.toMatch(/AM|PM/);
  });

  it('gives the search box and the filters the control height of 23 pixels', async () => {
    await mount();

    const filter = document.querySelector<HTMLElement>('.item-filter .sd-trigger-multi')!.getBoundingClientRect();
    const search = document.querySelector<HTMLElement>('[aria-label="Search"]')!.getBoundingClientRect();
    expect(search.height).toBe(filter.height);
    expect(search.height).toBe(23);
    // Clear filters stands level with the filters.
    const clear = button('Clear filters').getBoundingClientRect();
    expect([clear.top, clear.height]).toEqual([search.top, 23]);
    // A selected value shows as a chip in the same height.
    await choose('Repository', 'Tool');
    await vi.waitFor(() => expect(document.querySelector('.item-filter .sd-chip')).not.toBeNull());
    expect(document.querySelector<HTMLElement>('.item-filter .sd-trigger-multi')!.getBoundingClientRect().height).toBe(
      23,
    );
  });

  it('filters by repository, type, assignee and state, and searches', async () => {
    await mount();

    await choose('Repository', 'Tool');
    await vi.waitFor(() => expect(numbers()).toHaveLength(6));
    await choose('GitHub assignee', 'alice');
    await vi.waitFor(() => expect(numbers()).toEqual(['#7 Crash on start', '#5 Old report']));
    await choose('State', 'New');
    await vi.waitFor(() => expect(numbers()).toEqual(['#7 Crash on start']));
    expect(document.querySelector('.items-summary')!.textContent).toBe('1 of 8 open item(s) shown. 1 hidden.');

    button('Clear filters').click();
    await vi.waitFor(() => expect(numbers()).toHaveLength(7));
    await choose('Work item type', 'Change Request');
    await vi.waitFor(() => expect(numbers()).toEqual(['#10 Export to CSV']));
    button('Clear filters').click();
    await choose('Kind', 'Discussion');
    await vi.waitFor(() => expect(numbers()).toEqual(['#30 How to configure']));
    button('Clear filters').click();
    await choose('GitHub type', 'Q&A');
    await vi.waitFor(() => expect(numbers()).toHaveLength(1));
    button('Clear filters').click();
    await choose('Polarion assignee', 'Rob Project');
    await vi.waitFor(() => expect(numbers()).toEqual(['#5 Old report']));
    button('Clear filters').click();

    await userEvent.fill(document.querySelector<HTMLInputElement>('[aria-label="Search"]')!, 'nothing like this');
    // The header stays with its table settings, and one row says why the table is empty.
    await vi.waitFor(() =>
      expect(document.querySelector('.items-empty')?.textContent).toBe('No item matches the filters.'),
    );
    expect(numbers()).toEqual([]);
    expect(document.querySelector('[aria-label="Table settings"]')).not.toBeNull();
    expect(document.querySelector('.items-summary')!.textContent).toBe('0 of 8 open item(s) shown. 1 hidden.');
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
      'Out of date: differs in title',
    ]);
    expect(button('Create work items').disabled).toBe(true);
  });

  it('selects all new items shown, and clears them again', async () => {
    await mount();
    await choose('Repository', 'Tool');
    await vi.waitFor(() => expect(numbers()).toHaveLength(6));

    const all = document.querySelector<HTMLInputElement>('[aria-label="Select all new and outdated items shown"]')!;
    await userEvent.click(all);

    expect([checkbox(ISSUE_7)!.checked, checkbox(ISSUE_10)!.checked, checkbox(DISCUSSION_30)!.checked]).toEqual([
      true,
      true,
      true,
    ]);
    expect(button('Create work items').textContent).toContain('(3)');
    expect(button('Update work items').textContent).toContain('(1)');
    await userEvent.click(all);
    expect(button('Update work items').disabled).toBe(true);
    expect(button('Create work items').disabled).toBe(true);
  });

  it('updates the selected outdated work items and leaves the other rows as they are', async () => {
    await mount();
    expect(checkbox(ISSUE_4)!.checked).toBe(false);
    await userEvent.click(checkbox(ISSUE_4)!);
    await userEvent.click(checkbox(ISSUE_7)!);
    expect(button('Update work items').textContent).toContain('(1)');
    expect(button('Create work items').textContent).toContain('(1)');

    button('Update work items').click();

    await vi.waitFor(() => expect(toastText()).toContain('1 work item(s) updated.'));
    const updates = fetchMock.mock.calls.filter((c) => /\/update$/.test(String(c[0])));
    expect(updates.map((c) => JSON.parse(String(c[1]!.body)))).toEqual([{ urls: [ISSUE_4] }]);
    const rows = document.querySelectorAll('.items-table tbody tr');
    expect(rows[6].querySelector('.state')!.textContent).toBe('Updated: updated title');
    // The new item #7 stays as it was, and stays selected for Create.
    expect(rows[0].querySelector('.state')!.textContent).toBe('New');
    expect(checkbox(ISSUE_7)!.checked).toBe(true);
    expect(button('Update work items').disabled).toBe(true);
  });

  it('reports a failed update', async () => {
    await mount([
      {
        method: 'POST',
        match: /\/update$/,
        json: {
          repository: 'acme/tool',
          dryRun: false,
          readAt: null,
          entries: [{ ...ITEMS.entries[6], status: 'FAILED', message: 'locked' }],
        },
      },
    ]);
    await userEvent.click(checkbox(ISSUE_4)!);

    button('Update work items').click();

    await vi.waitFor(() => expect(toastText()).toContain('0 work item(s) updated, 1 failed.'));
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
