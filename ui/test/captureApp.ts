import { expect } from 'vitest';
import { page } from 'vitest/browser';
import { settleBeforeCapture, settleLayout } from './visualHelpers';

/**
 * Captures the whole rendered app at its natural height and compares it with the committed reference
 * `test/expected/<Page>/<name>.png`. Call it once the page shows the state to pin.
 */
export async function captureApp(name: string): Promise<void> {
  // A toast is transient and sits outside the page flow, so it is taken out of the capture.
  document.querySelectorAll('[data-sonner-toaster]').forEach((toaster) => toaster.remove());
  const app = document.querySelector('.app') as HTMLElement;
  await settleLayout();
  await page.viewport(1280, Math.ceil(app.scrollHeight) + 40);
  await settleBeforeCapture();
  await expect(page.elementLocator(app)).toMatchScreenshot(name);
}

/** The button of the app with exactly this label. */
export function appButton(label: string): HTMLButtonElement {
  const found = Array.from(document.querySelectorAll<HTMLButtonElement>('.sbb-btn')).find(
    (button) => button.textContent?.trim() === label,
  );
  if (!found) throw new Error(`button "${label}" not found`);
  return found;
}
