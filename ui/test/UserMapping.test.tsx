import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { userEvent } from 'vitest/browser';
import App from '../src/App';
import { MAPPING, PROJECT_SCOPE, userMappingRoutes } from './fixtures/userMapping';
import { type FetchMock, type Route, installFetchMock, jsonResponse } from './mockFetch';

// Behavior tests for the User Mapping page (hivemodule.xml, extender `user-mapping`).

const origUrl = window.location.pathname + window.location.search;

const byLabel = <T extends HTMLElement>(label: string) => document.querySelector<T>(`[aria-label="${label}"]`)!;
const dropdown = (label: string) =>
  document.querySelector<HTMLInputElement>(`input.sd-trigger[aria-label="${label}"]`)!;
const findButton = (label: string) =>
  Array.from(document.querySelectorAll<HTMLButtonElement>('.sbb-btn')).find(
    (b) => (b.textContent ?? '').trim() === label,
  );
const button = (label: string): HTMLButtonElement => {
  const found = findButton(label);
  if (!found) throw new Error(`button "${label}" not found`);
  return found;
};
const logins = () =>
  Array.from(document.querySelectorAll<HTMLInputElement>('input[aria-label^="GitHub login"]')).map((i) => i.value);
const toastText = () =>
  Array.from(document.querySelectorAll('[data-sonner-toast]'))
    .map((t) => t.textContent)
    .join();
const mousedown = (el: Element) =>
  el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, composed: true }));

async function pick(trigger: HTMLElement, text: string) {
  mousedown(trigger);
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

async function mount(overrides: Route[] = [], scope = '') {
  fetchMock = installFetchMock(userMappingRoutes(overrides));
  window.history.replaceState({}, '', `?feature=user-mapping&embedded=true&scope=${encodeURIComponent(scope)}`);
  render(<App />);
  await vi.waitFor(() => expect(document.querySelector('.feature-user-mapping')).not.toBeNull());
}

const calls = (method: string) =>
  fetchMock.mock.calls.filter((c) => (c[1]?.method ?? 'GET') === method && /\/user-mapping/.test(String(c[0])));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

describe('User Mapping page', () => {
  it('shows the global mapping and saves the edited rows without the empty ones', async () => {
    await mount([
      {
        method: 'PUT',
        match: /\/user-mapping\?scope=$/,
        respond: (_url, init) => jsonResponse({ ...JSON.parse(String(init!.body)), inherited: false }),
      },
    ]);
    await vi.waitFor(() => expect(logins()).toEqual(['alice', 'bob-gh']));
    await vi.waitFor(() => expect(dropdown('Polarion user 2').value).toBe('Bob King (bk)'));
    expect(document.body.textContent).not.toContain('This project');
    expect(findButton('Use the global mapping')).toBeUndefined();

    button('Remove').click();
    await vi.waitFor(() => expect(logins()).toEqual(['bob-gh']));
    button('Add a user').click();
    await vi.waitFor(() => expect(logins()).toEqual(['bob-gh', '']));
    await userEvent.fill(byLabel<HTMLInputElement>('GitHub login 2'), ' carol ');
    await pick(dropdown('Polarion user 2'), 'Alice Jones (aj)');
    button('Add a user').click();
    await vi.waitFor(() => expect(logins()).toEqual(['bob-gh', ' carol ', '']));

    button('Save').click();

    await vi.waitFor(() => expect(toastText()).toContain('Data successfully saved.'));
    expect(JSON.parse(String(calls('PUT')[0][1]!.body))).toEqual({
      users: [
        { githubLogin: 'bob-gh', polarionUser: 'bk' },
        { githubLogin: 'carol', polarionUser: 'aj' },
      ],
    });
    expect(logins()).toEqual(['bob-gh', 'carol']);
  });

  it('lets a project keep a mapping of its own and go back to the global one', async () => {
    await mount(
      [
        { method: 'GET', match: /\/user-mapping\?scope=project/, json: { ...MAPPING, inherited: true } },
        {
          method: 'PUT',
          match: /\/user-mapping\?scope=project/,
          respond: (_url, init) => jsonResponse({ ...JSON.parse(String(init!.body)), inherited: false }),
        },
        { method: 'DELETE', match: /\/user-mapping\?scope=project/, json: { ...MAPPING, inherited: true } },
      ],
      PROJECT_SCOPE,
    );
    await vi.waitFor(() => expect(document.body.textContent).toContain('This project uses the global mapping.'));
    expect(findButton('Use the global mapping')).toBeUndefined();

    button('Save').click();
    await vi.waitFor(() => expect(document.body.textContent).toContain('This project keeps a mapping of its own'));

    button('Use the global mapping').click();

    await vi.waitFor(() => expect(toastText()).toContain('The project uses the global mapping again.'));
    expect(calls('DELETE')[0][0]).toContain(`scope=${encodeURIComponent(PROJECT_SCOPE)}`);
    expect(document.body.textContent).toContain('This project uses the global mapping.');
  });

  it('reports a mapping that does not load, and a save or reset that fails', async () => {
    await mount([
      { method: 'GET', match: /\/user-mapping/, status: 500, json: { message: 'repository locked' } },
      { method: 'PUT', match: /\/user-mapping/, status: 400, json: { message: "The GitHub login 'x' is given twice" } },
      { method: 'GET', match: /\/users$/, status: 500, json: {} },
    ]);
    await vi.waitFor(() =>
      expect(document.querySelector('.alert-error')?.textContent).toBe(
        'The user mapping did not load: repository locked',
      ),
    );

    button('Save').click();

    await vi.waitFor(() => expect(toastText()).toContain("The GitHub login 'x' is given twice"));
  });

  it('reloads the saved mapping on Cancel', async () => {
    await mount();
    await vi.waitFor(() => expect(logins()).toEqual(['alice', 'bob-gh']));
    await userEvent.fill(byLabel<HTMLInputElement>('GitHub login 1'), 'edited');

    button('Cancel').click();

    await vi.waitFor(() => expect(logins()).toEqual(['alice', 'bob-gh']));
    expect(calls('GET')).toHaveLength(2);
  });

  it('reports a failed reset', async () => {
    await mount(
      [
        { method: 'GET', match: /\/user-mapping\?scope=project/, json: MAPPING },
        { method: 'DELETE', match: /\/user-mapping/, status: 500, json: { message: 'locked' } },
      ],
      PROJECT_SCOPE,
    );
    await vi.waitFor(() => expect(findButton('Use the global mapping')).toBeDefined());

    button('Use the global mapping').click();

    await vi.waitFor(() => expect(toastText()).toContain('locked'));
  });
});
