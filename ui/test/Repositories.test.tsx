import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import App from '../src/App';
import { CONTENT, SCOPE, repositoriesRoutes } from './fixtures/repositories';
import { type FetchMock, type Route, installFetchMock, jsonResponse } from './mockFetch';

// Behavior tests for the Repositories page, driven through the real App (feature router + Toaster).
// REST is mocked at the global fetch boundary, so no Polarion is needed.

const origUrl = window.location.pathname + window.location.search;
const setUrl = (search: string) => window.history.replaceState({}, '', search);

const input = (id: string) => document.getElementById(id) as HTMLInputElement;
// The shared dropdown hides the <select> that carries the id and draws its trigger right after it.
const trigger = (id: string) =>
  document.getElementById(id)!.nextElementSibling!.querySelector<HTMLInputElement>('.sd-trigger')!;
const byLabel = <T extends HTMLElement>(label: string) => document.querySelector<T>(`[aria-label="${label}"]`)!;
// A dropdown carries its label on the hidden <select> and on the trigger. The trigger is what a user sees.
const dropdown = (label: string) =>
  document.querySelector<HTMLInputElement>(`input.sd-trigger[aria-label="${label}"]`)!;
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
  it('copies the saved setting under a new name and opens the copy', async () => {
    await mount();
    await userEvent.fill(input('short-name'), 'Edited, not saved');

    button('Copy').click();
    await vi.waitFor(() => expect(input('copy-name').value).toBe('tool copy'));
    // The form waits, dimmed, until the copy is made or cancelled.
    expect(document.querySelector('.repository-form.dimmed')).not.toBeNull();
    await userEvent.fill(input('copy-name'), 'tool two');
    button('Copy').click();

    await vi.waitFor(() => expect(toastText()).toContain('Copied tool as tool two.'));
    // The copy takes the saved setting, not the edits on the form.
    expect(savedBody()).toEqual(CONTENT);
    const put = fetchMock.mock.calls.find((c) => c[1]?.method === 'PUT')!;
    expect(String(put[0])).toContain('/settings/repositories/names/tool%20two/content');
    expect(input('copy-name')).toBeNull();
    expect(document.querySelector('.repository-form.dimmed')).toBeNull();
  });

  it('refuses a copy under a name that is taken or not allowed, and cancels it', async () => {
    await mount([
      {
        method: 'GET',
        match: /\/settings\/repositories\/names\?/,
        json: [
          { name: 'tool', scope: SCOPE },
          { name: 'docs', scope: SCOPE },
        ],
      },
    ]);

    button('Copy').click();
    await vi.waitFor(() => expect(input('copy-name')).not.toBeNull());
    await userEvent.fill(input('copy-name'), 'docs');
    button('Copy').click();
    await vi.waitFor(() =>
      expect(document.querySelector('.copy-setting .alert-error')?.textContent).toBe(
        'A repository with this name already exists',
      ),
    );
    await userEvent.fill(input('copy-name'), 'tool/2');
    button('Copy').click();
    await vi.waitFor(() =>
      expect(document.querySelector('.copy-setting .alert-error')?.textContent).toBe(
        'Only alphanumeric characters, hyphens and spaces are allowed',
      ),
    );
    expect(savedBody()).toBeUndefined();

    button('Cancel').click();
    await vi.waitFor(() => expect(input('copy-name')).toBeNull());
    expect(document.querySelector('.repository-form.dimmed')).toBeNull();
  });

  it('shows the saved setting', async () => {
    await mount();

    expect(input('short-name').value).toBe('Tool');
    expect(input('issues-enabled').checked).toBe(true);
    expect(input('issues-title').value).toBe('[GitHub] {{ SHORT_NAME }} : {{ TITLE }}');
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
    await userEvent.fill(input('issues-epic'), '');

    button('Save').click();

    await vi.waitFor(() => expect(toastText()).toContain('successfully saved'));
    expect(savedBody()).toEqual({
      repository: 'acme/other',
      shortName: 'Other',
      issues: {
        enabled: true,
        workItemType: 'task',
        titleTemplate: '[GitHub] {{ SHORT_NAME }} : {{ TITLE }}',
        descriptionTemplate: '<a href="{{ URL }}">{{ URL }}</a>',
        duplicateKey: 'HYPERLINK',
        duplicateKeyField: null,
        epicId: null,
        // Without an epic the role has nothing to describe.
        epicLinkRole: null,
        fields: { severity: 'major' },
        rules: [
          { match: 'LABEL', value: 'wontfix', skip: true, workItemType: null, fields: {} },
          { match: 'TYPE', value: 'Bug', skip: false, workItemType: 'issue', fields: { priority: 'high' } },
        ],
      },
      discussions: {
        enabled: false,
        workItemType: null,
        titleTemplate: '[GitHub] {{ SHORT_NAME }} : {{ TITLE }}',
        descriptionTemplate: '<a href="{{ URL }}">{{ URL }}</a>',
        duplicateKey: 'HYPERLINK',
        duplicateKeyField: null,
        epicId: null,
        epicLinkRole: null,
        fields: {},
        rules: [],
      },
      // A setting saved before pull requests existed gets the block off and Renovate as the author.
      pullRequests: {
        enabled: false,
        workItemType: null,
        titleTemplate: '[GitHub] {{ SHORT_NAME }} : Fix the failed checks of {{ TITLE }}',
        descriptionTemplate: '<a href="{{ URL }}">{{ URL }}</a>',
        duplicateKey: 'HYPERLINK',
        duplicateKeyField: null,
        epicId: null,
        epicLinkRole: null,
        fields: {},
        rules: [],
      },
      pullRequestAuthors: 'renovate[bot]',
      // A setting saved before advisories existed gets their block off, named by GHSA ID and severity.
      advisories: {
        enabled: false,
        workItemType: null,
        titleTemplate: '[GitHub] {{ SHORT_NAME }} : {{ GHSA }} ({{ SEVERITY }})',
        descriptionTemplate: '<a href="{{ URL }}">{{ URL }}</a>',
        duplicateKey: 'HYPERLINK',
        duplicateKeyField: null,
        epicId: null,
        epicLinkRole: null,
        fields: {},
        rules: [],
      },
      // A setting saved before notifications existed mails nobody.
      notifications: { users: [], issues: false, discussions: false, pullRequests: false, advisories: false },
    });
  });

  it('mails the chosen users about the kinds turned on', async () => {
    await mount([
      {
        method: 'GET',
        match: /\/content/,
        json: { ...CONTENT, notifications: { users: ['bob'], issues: true, discussions: false, pullRequests: false } },
      },
    ]);
    await vi.waitFor(() => expect(input('notify-issues').checked).toBe(true));
    expect(document.querySelector('.sd-trigger-multi')!.textContent).toContain('Bob Builder (bob)');

    await userEvent.click(input('notify-pullRequests'));
    await userEvent.click(input('notify-advisories'));
    button('Save').click();

    await vi.waitFor(() => expect(toastText()).toContain('successfully saved'));
    expect(savedBody().notifications).toEqual({
      users: ['bob'],
      issues: true,
      discussions: false,
      pullRequests: true,
      advisories: true,
    });
    expect(String(fetchMock.mock.calls.find((c) => /\/users$/.test(String(c[0])))![0])).toBe(
      '/polarion/github/rest/internal/users',
    );
  });

  it('watches the failed pull requests of the authors given', async () => {
    await mount([
      {
        method: 'GET',
        match: /\/content/,
        json: {
          ...CONTENT,
          pullRequests: { ...CONTENT.discussions, enabled: true, workItemType: 'task' },
          pullRequestAuthors: 'dependabot[bot]',
        },
      },
    ]);
    await vi.waitFor(() => expect(input('pull-request-authors').value).toBe('dependabot[bot]'));
    expect(input('pull-requests-enabled').checked).toBe(true);
    expect(document.querySelector('label[for="pull-requests-enabled"]')!.textContent).toBe(
      'Create work items from pull requests',
    );
    await userEvent.fill(input('pull-request-authors'), ' renovate[bot], dependabot[bot] ');

    button('Save').click();

    await vi.waitFor(() => expect(toastText()).toContain('successfully saved'));
    expect(savedBody().pullRequestAuthors).toBe('renovate[bot], dependabot[bot]');
    expect(savedBody().pullRequests.enabled).toBe(true);
    expect(savedBody().pullRequests.workItemType).toBe('task');
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
    await userEvent.fill(input('discussions-title'), 'Discussion {{ NUMBER }}');
    await userEvent.fill(
      document.getElementById('discussions-description') as HTMLTextAreaElement,
      '<p>{{ BODY }}</p>',
    );
    await userEvent.fill(input('discussions-epic'), 'EL-2');
    await pick(trigger('discussions-role'), 'relates to');

    // Two rows, the second one stays without a field and is not saved. The first is then removed again.
    // The discussions block is the second one. It has no rule, so its only such button is the one of the block.
    const addFieldValue = () =>
      Array.from(document.querySelectorAll('.item-settings')[1].querySelectorAll<HTMLButtonElement>('.sbb-btn')).find(
        (b) => b.textContent?.trim() === 'Add a field value',
      )!;
    addFieldValue().click();
    await vi.waitFor(() => expect(document.querySelector('[aria-label="Value 1 of discussions"]')).not.toBeNull());
    const fieldTrigger = dropdown('Field 1 of discussions');
    await pick(fieldTrigger, 'Severity (severity)');
    await userEvent.fill(document.querySelector<HTMLInputElement>('[aria-label="Value 1 of discussions"]')!, 'minor');
    addFieldValue().click();
    await vi.waitFor(() => expect(document.querySelector('[aria-label="Value 2 of discussions"]')).not.toBeNull());

    button('Save').click();
    await vi.waitFor(() => expect(savedBody()).toBeDefined());
    expect(savedBody().discussions).toEqual({
      enabled: true,
      workItemType: 'issue',
      titleTemplate: 'Discussion {{ NUMBER }}',
      descriptionTemplate: '<p>{{ BODY }}</p>',
      duplicateKey: 'HYPERLINK',
      duplicateKeyField: null,
      epicId: 'EL-2',
      epicLinkRole: 'relates_to',
      fields: { severity: 'minor' },
      rules: [],
    });
  });

  it('chooses the value of an enumeration from its options, and several for a field that takes several', async () => {
    await mount([
      {
        method: 'GET',
        match: /\/content/,
        json: {
          ...CONTENT,
          issues: { ...CONTENT.issues, fields: { budget: 'internal', categories: 'core', assignee: 'bob' }, rules: [] },
        },
      },
    ]);
    await vi.waitFor(() => expect(dropdown('Value 1 of issues')).not.toBeNull());
    // A saved value shows by the name of its option.
    expect(dropdown('Value 1 of issues').value).toBe('Internal/all');
    const categories = document.querySelector<HTMLElement>('div.sd-trigger[aria-label="Value 2 of issues"]')!;
    expect(categories.textContent).toContain('Core');
    // The assignee chooses from the users of the project.
    expect(document.querySelector('div.sd-trigger[aria-label="Value 3 of issues"]')!.textContent).toContain(
      'Bob Builder',
    );

    await pick(dropdown('Value 1 of issues'), 'External/all');
    mousedown(categories);
    // A closed dropdown keeps its options in the page, hidden: only the open one shows its own.
    const plugin = () =>
      Array.from(document.querySelectorAll<HTMLElement>('.sd-portal .option')).find(
        (o) => o.textContent?.trim() === 'External/Plugin' && o.getClientRects().length > 0,
      );
    await vi.waitFor(() => expect(plugin()).toBeDefined());
    mousedown(plugin()!);
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    button('Save').click();

    await vi.waitFor(() => expect(savedBody()).toBeDefined());
    // The options keep the order of the list, whatever the order of the clicks.
    expect(savedBody().issues.fields).toEqual({ budget: 'external', categories: 'plugin,core', assignee: 'bob' });
  });

  it('gives every control of a field value the control height of 23 pixels, level with each other', async () => {
    await mount([
      {
        method: 'GET',
        match: /\/content/,
        json: { ...CONTENT, issues: { ...CONTENT.issues, fields: { categories: '', budget: 'internal' }, rules: [] } },
      },
    ]);
    await vi.waitFor(() =>
      expect(document.querySelector('div.sd-trigger[aria-label="Value 1 of issues"]')).not.toBeNull(),
    );

    for (const row of Array.from(document.querySelectorAll('.field-row')).slice(0, 2)) {
      const boxes = Array.from(row.querySelectorAll<HTMLElement>('.sd-trigger, .sbb-btn')).map((element) =>
        element.getBoundingClientRect(),
      );
      expect(boxes).toHaveLength(3);
      expect(boxes.map((box) => box.height)).toEqual([23, 23, 23]);
      expect(new Set(boxes.map((box) => box.top)).size).toBe(1);
    }
  });

  it('gives the recipients the control height and the repository dropdown twice the width of RSP', async () => {
    await mount();

    expect(
      document.querySelector<HTMLElement>('div.sd-trigger[aria-label="Recipients"]')!.getBoundingClientRect().height,
    ).toBe(23);
    expect(
      document.querySelector<HTMLElement>('.configurations-pane .searchable-dropdown')!.getBoundingClientRect().width,
    ).toBe(260);
  });

  it('gives every kind of field the control of its kind, in the form generic reads', async () => {
    await mount([
      {
        method: 'GET',
        match: /\/content/,
        json: {
          ...CONTENT,
          issues: {
            ...CONTENT.issues,
            fields: { approved: 'true', estimate: '1.5', dueDate: '2026-10-09', start: '', remaining: '1d', notes: '' },
            rules: [],
          },
        },
      },
    ]);
    await vi.waitFor(() => expect(byLabel('Value 6 of issues')).not.toBeNull());
    const value = (n: number) => byLabel<HTMLInputElement>(`Value ${n} of issues`);

    expect(dropdown('Value 1 of issues').value).toBe('true');
    expect([value(2).type, value(2).value]).toEqual(['number', '1.5']);
    expect([value(3).type, value(3).value]).toEqual(['date', '2026-10-09']);
    expect(value(4).type).toBe('datetime-local');
    expect([value(5).type, value(5).placeholder]).toEqual(['text', 'For example 1d 2h']);
    expect([value(6).tagName, value(6).placeholder]).toEqual(['TEXTAREA', 'HTML']);
    // Every value control takes the width of the column, whatever its kind.
    expect(new Set([2, 3, 4, 5, 6].map((n) => value(n).getBoundingClientRect().width))).toEqual(new Set([240]));

    await pick(dropdown('Value 1 of issues'), 'false');
    // The browser leaves out the seconds when they are zero; generic needs them.
    await userEvent.fill(value(4), '2026-10-09T14:30');
    await userEvent.fill(value(6), '<b>Imported</b>');
    button('Save').click();

    await vi.waitFor(() => expect(savedBody()).toBeDefined());
    expect(savedBody().issues.fields).toEqual({
      approved: 'false',
      estimate: '1.5',
      dueDate: '2026-10-09',
      start: '2026-10-09T14:30:00',
      remaining: '1d',
      notes: '<b>Imported</b>',
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

  it('shows the rules of the saved setting', async () => {
    await mount();

    expect(byLabel<HTMLInputElement>('Value of rule 1 of issues').value).toBe('wontfix');
    expect(input('issues-rule-1-skip').checked).toBe(true);
    // A rule that leaves items out shows neither a type nor field values.
    expect(dropdown('Work item type of rule 1 of issues')).toBeNull();
    await vi.waitFor(() => expect(dropdown('What rule 2 of issues compares').value).toBe('Issue type'));
    await vi.waitFor(() => expect(dropdown('Work item type of rule 2 of issues').value).toBe('Issue'));
    expect(byLabel<HTMLInputElement>('Value 1 of rule 2 of issues').value).toBe('high');
    // The fields of each work item type are read once, however many controls need them.
    expect(fetchMock.mock.calls.filter((c) => /\/workitem-types\/task\/fields/.test(String(c[0])))).toHaveLength(1);
    expect(fetchMock.mock.calls.filter((c) => /\/workitem-types\/issue\/fields/.test(String(c[0])))).toHaveLength(1);
  });

  it('adds, edits, reorders and removes rules', async () => {
    await mount();
    const addRule = () =>
      Array.from(document.querySelectorAll<HTMLButtonElement>('.sbb-btn')).find(
        (b) => b.textContent?.trim() === 'Add a rule',
      )!;

    addRule().click();
    await vi.waitFor(() => expect(byLabel('Value of rule 3 of issues')).not.toBeNull());
    // An issue rule offers the label, the issue type and the author, never the discussion category.
    mousedown(dropdown('What rule 3 of issues compares'));
    await vi.waitFor(() =>
      expect(Array.from(document.querySelectorAll('.sd-portal .option')).map((o) => o.textContent?.trim())).toEqual([
        'Label',
        'Issue type',
        'Author',
      ]),
    );
    await pick(dropdown('What rule 3 of issues compares'), 'Issue type');
    await userEvent.fill(byLabel<HTMLInputElement>('Value of rule 3 of issues'), ' Feature ');
    await pick(dropdown('Work item type of rule 3 of issues'), 'Task');
    // The new rule gets a field value of its own, and one row without a field, which is not saved.
    const addFieldValue = () =>
      Array.from(document.querySelectorAll<HTMLButtonElement>('.rule:last-of-type .sbb-btn')).find(
        (b) => b.textContent?.trim() === 'Add a field value',
      )!;
    addFieldValue().click();
    await vi.waitFor(() => expect(dropdown('Field 1 of rule 3 of issues')).not.toBeNull());
    await pick(dropdown('Field 1 of rule 3 of issues'), 'priority');
    await userEvent.fill(byLabel<HTMLInputElement>('Value 1 of rule 3 of issues'), 'low');
    addFieldValue().click();

    // The first and the last rule cannot leave the list.
    expect(byLabel<HTMLButtonElement>('Move rule 1 of issues up').disabled).toBe(true);
    expect(byLabel<HTMLButtonElement>('Move rule 3 of issues down').disabled).toBe(true);
    byLabel<HTMLButtonElement>('Move rule 3 of issues up').click();
    await vi.waitFor(() => expect(byLabel<HTMLInputElement>('Value of rule 2 of issues').value).toBe(' Feature '));
    byLabel<HTMLButtonElement>('Move rule 1 of issues down').click();
    await vi.waitFor(() => expect(byLabel<HTMLInputElement>('Value of rule 2 of issues').value).toBe('wontfix'));
    byLabel<HTMLButtonElement>('Remove rule 3 of issues').click();
    await vi.waitFor(() => expect(byLabel('Value of rule 3 of issues')).toBeNull());
    // Turning the remaining "wontfix" rule into an importing one keeps it without a type: the server refuses that.
    await userEvent.click(input('issues-rule-2-skip'));

    button('Save').click();

    await vi.waitFor(() => expect(savedBody()).toBeDefined());
    expect(savedBody().issues.rules).toEqual([
      { match: 'TYPE', value: 'Feature', skip: false, workItemType: 'task', fields: { priority: 'low' } },
      { match: 'LABEL', value: 'wontfix', skip: false, workItemType: null, fields: {} },
    ]);
  });

  it('offers the category for a rule of the discussions', async () => {
    await mount();
    await userEvent.click(input('discussions-enabled'));
    await vi.waitFor(() => expect(document.getElementById('discussions-title')).not.toBeNull());
    Array.from(document.querySelectorAll<HTMLButtonElement>('.sbb-btn'))
      .filter((b) => b.textContent?.trim() === 'Add a rule')[1]
      .click();
    await vi.waitFor(() => expect(dropdown('What rule 1 of discussions compares')).not.toBeNull());

    await pick(dropdown('What rule 1 of discussions compares'), 'Category');
    await userEvent.fill(byLabel<HTMLInputElement>('Value of rule 1 of discussions'), 'Q&A');
    await userEvent.click(input('discussions-rule-1-skip'));
    button('Save').click();

    await vi.waitFor(() => expect(savedBody()).toBeDefined());
    expect(savedBody().discussions.rules).toEqual([
      { match: 'CATEGORY', value: 'Q&A', skip: true, workItemType: null, fields: {} },
    ]);
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
