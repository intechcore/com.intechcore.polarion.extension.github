import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
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
