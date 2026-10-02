import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import App from '../src/App';
import { SCOPE, repositoriesRoutes } from './fixtures/repositories';
import { type FetchMock, type Route, installFetchMock, jsonResponse } from './mockFetch';

// Behavior tests for the Repositories page, driven through the real App (feature router + Toaster).
// REST is mocked at the global fetch boundary, so no Polarion is needed.

const origUrl = window.location.pathname + window.location.search;
const setUrl = (search: string) => window.history.replaceState({}, '', search);

const input = (id: string) => document.getElementById(id) as HTMLInputElement;
// The shared dropdown hides the <select> that carries the id and draws its trigger right after it.
const trigger = (id: string) =>
  document.getElementById(id)!.nextElementSibling!.querySelector<HTMLInputElement>('.sd-trigger')!;
const button = (label: string): HTMLButtonElement => {
  const found = Array.from(document.querySelectorAll<HTMLButtonElement>('.sbb-btn')).find(
    (b) => (b.textContent ?? '').trim() === label,
  );
  if (!found) throw new Error(`button "${label}" not found`);
  return found;
};
const toastText = () => document.querySelector('[data-sonner-toast]')?.textContent ?? '';
const mousedown = (el: Element) =>
  el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, composed: true }));

/** Opens a dropdown and picks the option with the given text. The shared dropdown selects on mousedown. */
async function pick(triggerElement: HTMLElement, text: string) {
  mousedown(triggerElement);
  await vi.waitFor(() =>
    expect(
      Array.from(document.querySelectorAll('.sd-portal .option')).some((o) => o.textContent?.trim() === text),
    ).toBe(true),
  );
  mousedown(
    Array.from(document.querySelectorAll<HTMLElement>('.sd-portal .option')).find(
      (o) => o.textContent?.trim() === text,
    )!,
  );
}

let fetchMock: FetchMock;

async function mount(overrides: Route[] = []) {
  fetchMock = installFetchMock(repositoriesRoutes(overrides));
  setUrl(`?feature=repositories&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
  render(<App />);
  await vi.waitFor(() => expect(input('repository')?.value).toBe('acme/tool'), { timeout: 5000 });
  await vi.waitFor(() => expect(trigger('issues-type').value).toBe('Task'));
}

const savedBody = () => {
  const call = fetchMock.mock.calls.find((c) => c[1]?.method === 'PUT' && /\/content/.test(String(c[0])));
  return call ? JSON.parse(String(call[1]!.body)) : undefined;
};

beforeEach(() => setUrl(origUrl));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  setUrl(origUrl);
});

describe('Repositories page', () => {
  it('shows the saved setting', async () => {
    await mount();

    expect(input('short-name').value).toBe('Tool');
    expect(input('enabled').checked).toBe(true);
    expect(input('issues-enabled').checked).toBe(true);
    expect(input('issues-title').value).toBe('[GitHub] {shortName} : {title}');
    expect(input('issues-epic').value).toBe('EL-1');
    await vi.waitFor(() => expect(trigger('issues-role').value).toBe('has parent'));
    expect(trigger('issues-key').value).toBe('A hyperlink of the work item');
    expect(document.querySelector<HTMLInputElement>('[aria-label="Value 1 of issues"]')!.value).toBe('major');
    // Discussions are off, so their details stay hidden.
    expect(input('discussions-enabled').checked).toBe(false);
    expect(document.getElementById('discussions-title')).toBeNull();
    // The fields of the work item type were read for the issues block only.
    expect(fetchMock.mock.calls.filter((c) => /\/workitem-types\/task\/fields/.test(String(c[0])))).toHaveLength(1);
  });

  it('saves what the form holds', async () => {
    await mount();
    await userEvent.fill(input('repository'), ' acme/other ');
    await userEvent.fill(input('short-name'), 'Other');
    await userEvent.click(input('enabled'));
    await userEvent.fill(input('issues-epic'), '');

    button('Save').click();

    await vi.waitFor(() => expect(toastText()).toContain('successfully saved'));
    expect(savedBody()).toEqual({
      repository: 'acme/other',
      shortName: 'Other',
      enabled: false,
      issues: {
        enabled: true,
        workItemType: 'task',
        titleTemplate: '[GitHub] {shortName} : {title}',
        descriptionTemplate: '<a href="{url}">{url}</a>',
        duplicateKey: 'HYPERLINK',
        duplicateKeyField: null,
        epicId: null,
        // Without an epic the role has nothing to describe.
        epicLinkRole: null,
        fields: { severity: 'major' },
      },
      discussions: {
        enabled: false,
        workItemType: null,
        titleTemplate: '[GitHub] {shortName} : {title}',
        descriptionTemplate: '<a href="{url}">{url}</a>',
        duplicateKey: 'HYPERLINK',
        duplicateKeyField: null,
        epicId: null,
        epicLinkRole: null,
        fields: {},
      },
    });
  });

  it('offers only the fields that can keep a URL as the custom field', async () => {
    await mount();

    await pick(trigger('issues-key'), 'A custom field');
    await vi.waitFor(() => expect(document.getElementById('issues-key-field')).not.toBeNull());
    mousedown(trigger('issues-key-field'));
    await vi.waitFor(() =>
      expect(Array.from(document.querySelectorAll('.sd-portal .option')).map((o) => o.textContent?.trim())).toContain(
        'GitHub URL (githubUrl)',
      ),
    );
    const offered = Array.from(document.querySelectorAll('.sd-portal .option')).map((o) => o.textContent?.trim());
    expect(offered).not.toContain('Severity (severity)');
    expect(offered).not.toContain('priority');
    await pick(trigger('issues-key-field'), 'GitHub URL (githubUrl)');

    button('Save').click();

    await vi.waitFor(() => expect(savedBody()).toBeDefined());
    expect(savedBody().issues.duplicateKey).toBe('CUSTOM_FIELD');
    expect(savedBody().issues.duplicateKeyField).toBe('githubUrl');
  });

  it('edits the discussions block and its field values', async () => {
    await mount();
    await userEvent.click(input('discussions-enabled'));
    await vi.waitFor(() => expect(document.getElementById('discussions-title')).not.toBeNull());
    await pick(trigger('discussions-type'), 'Issue');
    await vi.waitFor(() =>
      expect(fetchMock.mock.calls.some((c) => /\/workitem-types\/issue\/fields/.test(String(c[0])))).toBe(true),
    );
    await userEvent.fill(input('discussions-title'), 'Discussion {number}');
    await userEvent.fill(document.getElementById('discussions-description') as HTMLTextAreaElement, '<p>{body}</p>');
    await userEvent.fill(input('discussions-epic'), 'EL-2');
    await pick(trigger('discussions-role'), 'relates to');

    // Two rows, the second one stays without a field and is not saved. The first is then removed again.
    const addButtons = () =>
      Array.from(document.querySelectorAll<HTMLButtonElement>('.sbb-btn')).filter(
        (b) => b.textContent?.trim() === 'Add a field value',
      );
    addButtons()[1].click();
    await vi.waitFor(() => expect(document.querySelector('[aria-label="Value 1 of discussions"]')).not.toBeNull());
    const fieldTrigger = document.querySelectorAll<HTMLInputElement>('.field-row .sd-trigger')[1];
    await pick(fieldTrigger, 'Severity (severity)');
    await userEvent.fill(document.querySelector<HTMLInputElement>('[aria-label="Value 1 of discussions"]')!, 'minor');
    addButtons()[1].click();
    await vi.waitFor(() => expect(document.querySelector('[aria-label="Value 2 of discussions"]')).not.toBeNull());

    button('Save').click();
    await vi.waitFor(() => expect(savedBody()).toBeDefined());
    expect(savedBody().discussions).toEqual({
      enabled: true,
      workItemType: 'issue',
      titleTemplate: 'Discussion {number}',
      descriptionTemplate: '<p>{body}</p>',
      duplicateKey: 'HYPERLINK',
      duplicateKeyField: null,
      epicId: 'EL-2',
      epicLinkRole: 'relates_to',
      fields: { severity: 'minor' },
    });
  });

  it('shows a field named by its ID once', async () => {
    await mount();

    mousedown(document.querySelector<HTMLInputElement>('.field-row .sd-trigger')!);
    await vi.waitFor(() => expect(document.querySelectorAll('.sd-portal .option').length).toBeGreaterThan(1));

    const offered = Array.from(document.querySelectorAll('.sd-portal .option')).map((o) => o.textContent?.trim());
    expect(offered).toContain('priority');
    expect(offered).toContain('Severity (severity)');
    expect(offered).not.toContain('priority (priority)');
  });

  it('removes a field value', async () => {
    await mount();

    button('Remove').click();
    await vi.waitFor(() => expect(document.querySelector('[aria-label="Value 1 of issues"]')).toBeNull());
    button('Save').click();

    await vi.waitFor(() => expect(savedBody()).toBeDefined());
    expect(savedBody().issues.fields).toEqual({});
  });

  it('shows the reason when the server rejects the setting', async () => {
    await mount([
      {
        method: 'PUT',
        match: /\/content/,
        respond: () => jsonResponse({ message: 'The repository must be given as owner/name' }, 400),
      },
    ]);

    button('Save').click();

    await vi.waitFor(() => expect(toastText()).toContain('owner/name'));
  });

  it('restores the saved setting on Cancel', async () => {
    await mount();
    await userEvent.fill(input('short-name'), 'Changed');

    button('Cancel').click();

    await vi.waitFor(() => expect(input('short-name').value).toBe('Tool'));
  });

  it('reports a failure of Cancel', async () => {
    await mount();
    fetchMock.mockImplementation(() => Promise.resolve(new Response('gone', { status: 500 })));

    button('Cancel').click();

    await vi.waitFor(() => expect(toastText()).toContain('gone'));
  });

  it('loads a revision into the form', async () => {
    await mount([
      {
        method: 'GET',
        match: /\/content\?.*revision=110/,
        json: {
          repository: null,
          shortName: 'Old name',
          enabled: false,
          issues: null,
          // A revision written by hand can miss every value.
          discussions: {
            enabled: true,
            workItemType: null,
            titleTemplate: null,
            descriptionTemplate: null,
            duplicateKey: null,
            duplicateKeyField: null,
            epicId: null,
            epicLinkRole: null,
            fields: null,
          },
        },
      },
    ]);
    button('Revisions').click();
    await vi.waitFor(() => expect(document.querySelector('.revert-to-revision-button')).not.toBeNull());

    document.querySelectorAll<HTMLButtonElement>('.revert-to-revision-button')[1].click();

    await vi.waitFor(() => expect(input('short-name').value).toBe('Old name'));
    await vi.waitFor(() => expect(toastText()).toContain('Reverted to revision 110'));
    // A revision without a block reads as that block turned off, and missing values as empty.
    expect(input('issues-enabled').checked).toBe(false);
    expect(input('repository').value).toBe('');
    expect(input('discussions-title').value).toBe('');
    expect(trigger('discussions-key').value).toBe('A hyperlink of the work item');
    // The table can be hidden again.
    button('Revisions').click();
    await vi.waitFor(() => expect(document.querySelector('.revert-to-revision-button')).toBeNull());
  });

  it('reports a revision that does not load', async () => {
    await mount([{ method: 'GET', match: /revision=/, respond: () => new Response('', { status: 404 }) }]);
    button('Revisions').click();
    await vi.waitFor(() => expect(document.querySelector('.revert-to-revision-button')).not.toBeNull());

    document.querySelector<HTMLButtonElement>('.revert-to-revision-button')!.click();

    await vi.waitFor(() => expect(toastText()).toContain('HTTP 404'));
  });

  it('shows no form while the project has no repository setting', async () => {
    fetchMock = installFetchMock(
      repositoriesRoutes([{ method: 'GET', match: /\/settings\/repositories\/names\?/, json: [] }]),
    );
    setUrl(`?feature=repositories&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
    render(<App />);

    await vi.waitFor(() => expect(button('Add new')).toBeDefined());
    expect(document.getElementById('repository')).toBeNull();
  });

  it('says why the project options are missing', async () => {
    fetchMock = installFetchMock(
      repositoriesRoutes([
        { method: 'GET', match: /\/link-roles$/, respond: () => jsonResponse({ message: 'No such project' }, 404) },
        { method: 'GET', match: /\/fields$/, respond: () => new Response('', { status: 500 }) },
      ]),
    );
    setUrl(`?feature=repositories&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('.alert-error')?.textContent).toContain('No such project'));
  });

  it('drops the answers that arrive after the page is gone', async () => {
    const base = installFetchMock(repositoriesRoutes());
    const late = vi.fn((url: RequestInfo | URL, init?: RequestInit) =>
      /workitem-types|link-roles/.test(String(url))
        ? new Promise<Response>((resolve) => setTimeout(() => resolve(base(url, init)), 200))
        : base(url, init),
    );
    vi.stubGlobal('fetch', late);
    setUrl(`?feature=repositories&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
    render(<App />);
    await vi.waitFor(() => expect(input('repository')?.value).toBe('acme/tool'));
    await vi.waitFor(() => expect(late.mock.calls.some((c) => /\/fields$/.test(String(c[0])))).toBe(true));

    cleanup();
    await new Promise((resolve) => setTimeout(resolve, 400));

    // Nothing is left to update, and a late answer raised no error.
    expect(document.getElementById('repository')).toBeNull();
  });

  it('asks for a project when opened without one', async () => {
    fetchMock = installFetchMock(repositoriesRoutes());
    setUrl('?feature=repositories&embedded=true&scope=');
    render(<App />);

    await vi.waitFor(() =>
      expect(document.querySelector('.alert-error')?.textContent).toContain('Open this page from a project'),
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
