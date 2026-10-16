/**
 * The GitHub token of the user, kept in this browser only. Every REST call sends it in the header
 * `X-GitHub-Token` (useRemote); the server uses it for that request and stores it nowhere.
 *
 * Remembered, it lives in localStorage until the user removes it. Otherwise it lives in sessionStorage
 * and goes with the tab. Storage may be blocked (a private window, cleared site data), so every access
 * is guarded and the page then works with the token of the server.
 */
const KEY = 'github.token';

/** The name of the header that carries the token. Matches RequestToken.HEADER on the server. */
export const TOKEN_HEADER = 'X-GitHub-Token';

function read(storage: () => Storage): string | null {
  try {
    return storage().getItem(KEY);
  } catch {
    return null;
  }
}

function remove(storage: () => Storage): void {
  try {
    storage().removeItem(KEY);
  } catch {
    // Nothing kept, nothing to remove.
  }
}

/** The token of the user, or null to read GitHub with the token of the server. */
export function readGithubToken(): string | null {
  return read(() => window.sessionStorage) ?? read(() => window.localStorage);
}

/** Whether the token stays after the tab closes. */
export function isGithubTokenRemembered(): boolean {
  return read(() => window.localStorage) !== null;
}

/**
 * Keeps the token of the user in this browser.
 *
 * @throws Error when the browser keeps nothing for this page
 */
export function saveGithubToken(token: string, remember: boolean): void {
  removeGithubToken();
  try {
    (remember ? window.localStorage : window.sessionStorage).setItem(KEY, token);
  } catch {
    throw new Error('This browser keeps no data for the page, so it cannot keep the token.');
  }
}

export function removeGithubToken(): void {
  remove(() => window.sessionStorage);
  remove(() => window.localStorage);
}

/** The last four characters, to tell tokens apart. A short value shows none: they would be most of it. */
export function tokenHint(token: string): string {
  return token.length > 16 ? `…${token.slice(-4)}` : '';
}
