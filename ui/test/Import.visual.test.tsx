import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import { page } from 'vitest/browser';
import App from '../src/App';
import { SCOPE, importRoutes } from './fixtures/import';
import { installFetchMock } from './mockFetch';
import { settleBeforeCapture, settleLayout } from './visualHelpers';

// Docker-only full-page snapshot of the Import page (hivemodule.xml, extender `import`): the
// controls and the list after an import, which shows every status at once.

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

const button = (label: string) =>
  Array.from(document.querySelectorAll<HTMLButtonElement>('.sbb-btn')).find((b) => b.textContent?.trim() === label)!;

describe.skipIf(!__PIXEL_REFERENCES__)('Import page visual', () => {
  it('the list after an import', async () => {
    installFetchMock(importRoutes());
    window.history.replaceState({}, '', `?feature=import&embedded=true&scope=${encodeURIComponent(SCOPE)}`);
    render(<App />);

    await vi.waitFor(() => expect(button('Read from GitHub')).toBeDefined());
    button('Read from GitHub').click();
    await vi.waitFor(() => expect(document.querySelectorAll('.import-table tbody tr')).toHaveLength(4));
    button('Create work items').click();
    await vi.waitFor(() => expect(document.querySelector('.status-CREATED')).not.toBeNull());
    // The toast is transient and sits outside the page flow, so it is taken out of the capture.
    document.querySelectorAll('[data-sonner-toaster]').forEach((toaster) => toaster.remove());

    const app = document.querySelector('.app') as HTMLElement;
    await settleLayout();
    await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
    await settleBeforeCapture();
    await expect(page.elementLocator(app)).toMatchScreenshot('import-done');
  });
});
