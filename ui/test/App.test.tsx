import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from 'vitest-browser-react';
import App from '../src/App';
import { findFeature } from '../src/features';
import { installFetchMock } from './mockFetch';

// The feature router: which page a URL opens. REST is mocked at the global fetch boundary.

const origUrl = window.location.pathname + window.location.search;

function setUrl(query: string) {
  window.history.replaceState({}, '', query);
}

function mockAbout() {
  installFetchMock([
    { method: 'GET', match: /\/version$/, json: { bundleName: 'GitHub Integration', bundleVersion: '1.0.0' } },
    { method: 'GET', match: /\/configuration-properties$/, json: { properties: [], obsoleteProperties: [] } },
    { method: 'GET', match: /\/configuration-status/, json: [] },
    { method: 'GET', match: /\/readme$/, respond: () => new Response('<h1>Readme</h1>', { status: 200 }) },
  ]);
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', origUrl);
});

describe('feature router', () => {
  it('matches a known feature and answers nothing for the rest', () => {
    expect(findFeature('about')?.id).toBe('about');
    expect(findFeature('unknown')).toBeUndefined();
    expect(findFeature(null)).toBeUndefined();
  });

  it('renders the About page for ?feature=about', async () => {
    setUrl('?feature=about&embedded=true');
    mockAbout();

    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('article.markdown-body')).not.toBeNull());
    expect(document.querySelector('.app.standard-admin-page.feature-about')).not.toBeNull();
  });

  it('falls back to the About page when no feature is named', async () => {
    setUrl('?');
    mockAbout();

    render(<App />);

    await vi.waitFor(() => expect(document.querySelector('article.markdown-body')).not.toBeNull());
    expect(document.querySelector('.app.feature-about')).not.toBeNull();
  });
});
