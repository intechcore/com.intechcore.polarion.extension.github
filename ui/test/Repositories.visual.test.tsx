import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import App from '../src/App';
import { CONTENT, SCOPE, repositoriesRoutes } from './fixtures/repositories';
import { installFetchMock } from './mockFetch';
import { settleBeforeCapture, settleLayout } from './visualHelpers';

// Docker-only full-page snapshot of the Repositories page (hivemodule.xml, extender `repositories`):
// the setting selector, the repository block, both item blocks open, and the toolbar.

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

describe.skipIf(!__PIXEL_REFERENCES__)('Repositories page visual', () => {
  it('a setting with issues and discussions', async () => {
    installFetchMock(
      repositoriesRoutes([
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
      ]),
    );
    window.history.replaceState({}, '', `?feature=repositories&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
    render(<App />);

    await vi.waitFor(() => expect(document.getElementById('discussions-key-field')).not.toBeNull());
    await vi.waitFor(() =>
      expect(
        Array.from(document.querySelectorAll<HTMLInputElement>('.sd-trigger')).map((trigger) => trigger.value),
      ).toContain('GitHub URL (githubUrl)'),
    );
    const app = document.querySelector('.app') as HTMLElement;
    await settleLayout();
    await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
    await settleBeforeCapture();
    await expect(page.elementLocator(app)).toMatchScreenshot('repositories-loaded');
  });
});
