import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import useSettings, { errorMessage } from '../src/services/settings';
import useRemote from '../src/services/useRemote';
import { installFetchMock } from './mockFetch';

// The data layer: how requests are addressed. The hook is exercised through a probe component,
// which is how it runs in the app.

/** Renders a hook and exposes its latest value. */
function probe<T>(useHook: () => T) {
  const seen: { current: T | undefined } = { current: undefined };
  function Probe() {
    seen.current = useHook();
    return <div data-testid="probe" />;
  }
  render(<Probe />);
  return seen;
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe('useRemote', () => {
  it('addresses the session endpoints under the extension REST path', async () => {
    const fetchMock = installFetchMock([{ method: 'GET', match: /\/version$/, json: {} }]);
    const remote = probe(useRemote);

    await vi.waitFor(() => expect(remote.current).toBeDefined());
    const response = await remote.current!.sendRequest({ method: 'GET', url: '/version' });

    expect(response.ok).toBe(true);
    // Without a dev token the app talks to /internal, which Polarion authenticates by session.
    expect(String(fetchMock.mock.calls[0][0])).toBe('/polarion/github/rest/internal/version');
  });

  it('addresses the token endpoints when a development token is set', async () => {
    vi.stubEnv('VITE_BEARER_TOKEN', 'dev-token');
    const fetchMock = installFetchMock([{ method: 'GET', match: /\/version$/, json: {} }]);
    const remote = probe(useRemote);
    await vi.waitFor(() => expect(remote.current).toBeDefined());

    await remote.current!.sendRequest({ method: 'GET', url: '/version' });

    expect(String(fetchMock.mock.calls[0][0])).toBe('/polarion/github/rest/api/version');
    expect(fetchMock.mock.calls[0][1]!.headers).toEqual({ Authorization: 'Bearer dev-token' });
  });

  it('sets the content type only when one is given', async () => {
    const fetchMock = installFetchMock([{ method: 'PUT', match: /./, json: {} }]);
    const remote = probe(useRemote);
    await vi.waitFor(() => expect(remote.current).toBeDefined());

    await remote.current!.sendRequest({ method: 'PUT', url: '/x', body: '{}', contentType: 'application/json' });
    expect((fetchMock.mock.calls[0][1]!.headers as Record<string, string>)['Content-Type']).toBe('application/json');

    await remote.current!.sendRequest({ method: 'PUT', url: '/x' });
    expect(fetchMock.mock.calls[1][1]!.headers).toEqual({});
  });

  it('answers a network failure as a 503 response rather than rejecting', async () => {
    // Callers branch on response.ok; a rejected promise would surface as an unhandled error instead.
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new Error('offline'))),
    );
    const remote = probe(useRemote);
    await vi.waitFor(() => expect(remote.current).toBeDefined());

    const response = await remote.current!.sendRequest({ method: 'GET', url: '/version' });

    expect(response.status).toBe(503);
    expect((await response.json()).message).toContain('Be sure Polarion is started');
  });
});

describe('useSettings', () => {
  const SCOPE = 'project/elibrary/';

  async function settings() {
    const seen = probe(useSettings);
    await vi.waitFor(() => expect(seen.current).toBeDefined());
    return seen.current!;
  }

  it('creates a setting without a body, so the server stores its defaults', async () => {
    const fetchMock = installFetchMock([
      { method: 'PUT', match: /./, respond: () => new Response(null, { status: 204 }) },
    ]);

    await (await settings()).createConfiguration('my tool', SCOPE);

    expect(String(fetchMock.mock.calls[0][0])).toBe(
      '/polarion/github/rest/internal/settings/repositories/names/my%20tool/content?scope=project%2Felibrary%2F',
    );
    expect(fetchMock.mock.calls[0][1]!.body).toBeUndefined();
  });

  it('renames and deletes a setting', async () => {
    const fetchMock = installFetchMock([
      { method: 'POST', match: /./, respond: () => new Response(null, { status: 204 }) },
      { method: 'DELETE', match: /./, respond: () => new Response(null, { status: 204 }) },
    ]);
    const service = await settings();

    await service.renameConfiguration('tool', SCOPE, 'other');
    await service.deleteConfiguration('other', SCOPE);

    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'POST', body: 'other' });
    expect(String(fetchMock.mock.calls[1][0])).toContain('/names/other?scope=');
    expect(fetchMock.mock.calls[1][1]!.method).toBe('DELETE');
  });

  it('rejects with the message of the server', async () => {
    installFetchMock([
      { method: 'DELETE', match: /./, respond: () => new Response('{"message":"Setting not found"}', { status: 404 }) },
    ]);

    await expect((await settings()).deleteConfiguration('gone', SCOPE)).rejects.toThrow('Setting not found');
  });

  it('reads the reason of a failure from whatever the server sends', async () => {
    expect(await errorMessage(new Response('{"other":1}', { status: 400 }))).toBe('HTTP 400');
    expect(await errorMessage(new Response('plain text', { status: 500 }))).toBe('plain text');
    expect(await errorMessage(new Response('', { status: 503 }))).toBe('HTTP 503');
  });
});
