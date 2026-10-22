import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { captureApp } from './captureApp';
import { MAPPING, PROJECT_SCOPE, userMappingRoutes } from './fixtures/userMapping';
import { type Route, installFetchMock } from './mockFetch';

// Docker-only full-page snapshots of the User Mapping page (hivemodule.xml, extender `user-mapping`),
// one per state an administrator can meet.

const origUrl = window.location.pathname + window.location.search;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

const triggers = () =>
  Array.from(document.querySelectorAll<HTMLInputElement>('.sd-trigger')).map((trigger) => trigger.value);

function open(overrides: Route[] = [], scope = '') {
  installFetchMock(userMappingRoutes(overrides));
  window.history.replaceState({}, '', `?feature=user-mapping&embedded=true&scope=${encodeURIComponent(scope)}`);
  render(<App />);
}

describe.skipIf(!__PIXEL_REFERENCES__)('User Mapping page visual', () => {
  it('the global mapping', async () => {
    open();
    await vi.waitFor(() => expect(triggers()).toEqual(['Alice Jones (aj)', 'Bob King (bk)']));
    await captureApp('user-mapping-global');
  });

  it('a project on the global mapping', async () => {
    open(
      [{ method: 'GET', match: /\/user-mapping\?scope=project/, json: { ...MAPPING, inherited: true } }],
      PROJECT_SCOPE,
    );
    await vi.waitFor(() => expect(document.body.textContent).toContain('This project uses the global mapping.'));
    await vi.waitFor(() => expect(triggers()).toEqual(['Alice Jones (aj)', 'Bob King (bk)']));
    await captureApp('user-mapping-inherited');
  });

  it('a project with a mapping of its own', async () => {
    open([], PROJECT_SCOPE);
    await vi.waitFor(() => expect(document.body.textContent).toContain('This project keeps a mapping of its own'));
    await vi.waitFor(() => expect(triggers()).toEqual(['Alice Jones (aj)', 'Bob King (bk)']));
    await captureApp('user-mapping-own');
  });

  it('an empty mapping', async () => {
    open([{ method: 'GET', match: /\/user-mapping/, json: { users: [], inherited: false } }]);
    await vi.waitFor(() => expect(document.querySelector('.user-mapping thead')).not.toBeNull());
    await captureApp('user-mapping-empty');
  });

  it('a mapping that does not load', async () => {
    open([{ method: 'GET', match: /\/user-mapping/, status: 500, json: { message: 'repository locked' } }]);
    await vi.waitFor(() => expect(document.querySelector('.alert-error')).not.toBeNull());
    await captureApp('user-mapping-error');
  });
});
