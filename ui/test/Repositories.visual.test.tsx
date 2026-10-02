import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { appButton, captureApp } from './captureApp';
import { CONTENT, SCOPE, repositoriesRoutes } from './fixtures/repositories';
import { type Route, installFetchMock, jsonResponse } from './mockFetch';

// Docker-only full-page snapshots of the Repositories page (hivemodule.xml, extender `repositories`),
// one per state an administrator can meet.

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

const triggers = () =>
  Array.from(document.querySelectorAll<HTMLInputElement>('.sd-trigger')).map((trigger) => trigger.value);

function open(overrides: Route[] = [], scope = SCOPE) {
  installFetchMock(repositoriesRoutes(overrides));
  window.history.replaceState({}, '', `?feature=repositories&embedded=true&scope=${encodeURIComponent(scope)}`);
  render(<App />);
}

/** Waits for the saved setting of the fixture, with its dropdowns filled. */
async function loaded() {
  await vi.waitFor(() => expect(triggers()).toContain('Severity (severity)'));
  await vi.waitFor(() => expect(triggers()).toContain('has parent'));
}

describe.skipIf(!__PIXEL_REFERENCES__)('Repositories page visual', () => {
  it('a setting with issues and discussions', async () => {
    open([
      {
        method: 'GET',
        match: /\/settings\/repositories\/names\/[^/]+\/content/,
        json: {
          ...CONTENT,
          discussions: {
            ...CONTENT.discussions,
            enabled: true,
            workItemType: 'issue',
            duplicateKey: 'CUSTOM_FIELD',
            duplicateKeyField: 'githubUrl',
          },
        },
      },
    ]);

    await vi.waitFor(() => expect(document.getElementById('discussions-key-field')).not.toBeNull());
    await vi.waitFor(() => expect(triggers()).toContain('GitHub URL (githubUrl)'));
    await captureApp('repositories-loaded');
  });

  it('a setting with the discussions turned off', async () => {
    open();

    await loaded();
    await captureApp('repositories-issues-only');
  });

  it('the revisions of a setting', async () => {
    open();
    await loaded();

    appButton('Revisions').click();

    await vi.waitFor(() => expect(document.querySelectorAll('.revert-to-revision-button')).toHaveLength(2));
    await captureApp('repositories-revisions');
  });

  it('the name of a new setting being entered', async () => {
    open();
    await loaded();

    appButton('Add new').click();

    // The form of the selected setting stays visible, dimmed, until the name is saved or cancelled.
    await vi.waitFor(() => expect(document.querySelector('.repository-form.dimmed')).not.toBeNull());
    await captureApp('repositories-add-new');
  });

  it('a project without a setting', async () => {
    open([{ method: 'GET', match: /\/settings\/repositories\/names\?/, json: [] }]);

    await vi.waitFor(() => expect(appButton('Add new')).toBeDefined());
    await captureApp('repositories-empty');
  });

  it('the project options that did not load', async () => {
    open([{ method: 'GET', match: /\/link-roles$/, respond: () => jsonResponse({ message: 'No such project' }, 404) }]);

    await vi.waitFor(() => expect(document.querySelector('.alert-error')).not.toBeNull());
    await vi.waitFor(() =>
      expect((document.getElementById('repository') as HTMLInputElement)?.value).toBe('acme/tool'),
    );
    await captureApp('repositories-options-error');
  });

  it('the page opened outside a project', async () => {
    open([], '');

    await vi.waitFor(() => expect(document.querySelector('.alert-error')).not.toBeNull());
    await captureApp('repositories-no-project');
  });
});
