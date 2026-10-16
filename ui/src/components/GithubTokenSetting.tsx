import { useEffect, useState } from 'react';
import { faKey } from '@fortawesome/free-solid-svg-icons';
import {
  isGithubTokenRemembered,
  readGithubToken,
  removeGithubToken,
  saveGithubToken,
  tokenHint,
} from '../services/githubToken';
import useSettings from '../services/settings';
import type { GithubTokenInfo } from '../types';
import ButtonIcon from './ButtonIcon';
import ErrorNotice from './ErrorNotice';

const NEW_TOKEN_URL = 'https://github.com/settings/personal-access-tokens/new';

interface Props {
  /** Called after the token changed, so the page reads GitHub with the new one. */
  onChange: () => void;
}

function describe(token: string, info: GithubTokenInfo | null): string {
  const name = ['Your GitHub token', tokenHint(token)].filter((part) => part).join(' ');
  if (!info) return `${name} is kept in this browser.`;
  const left =
    info.remaining !== null && info.limit !== null
      ? `, ${info.remaining} of ${info.limit} requests left this hour`
      : '';
  return `${name} (${info.login ?? 'unknown user'}${left}) is kept in this browser.`;
}

/**
 * The GitHub token of the user, kept in this browser. With it GitHub allows 5000 requests per hour
 * for the user instead of the limit the server shares. A token is checked with GitHub before it is
 * kept, and again when the page opens.
 */
export default function GithubTokenSetting({ onChange }: Readonly<Props>) {
  const { checkGithubToken } = useSettings();
  const [token, setToken] = useState(() => readGithubToken());
  const [info, setInfo] = useState<GithubTokenInfo | null>(null);
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState('');
  const [remember, setRemember] = useState(() => isGithubTokenRemembered());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) return;
    let active = true;
    checkGithubToken(token)
      .then((checked) => active && setInfo(checked))
      .catch((e: Error) => active && setError(e.message));
    return () => {
      active = false;
    };
    // Checked once when the page opens; a saved token comes with its check.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const close = () => {
    setEditing(false);
    setValue('');
    setError('');
  };

  const save = async () => {
    const candidate = value.trim();
    setBusy(true);
    setError('');
    try {
      const checked = await checkGithubToken(candidate);
      saveGithubToken(candidate, remember);
      setToken(candidate);
      setInfo(checked);
      close();
      onChange();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const remove = () => {
    removeGithubToken();
    setToken(null);
    setInfo(null);
    close();
    onChange();
  };

  if (!editing) {
    return (
      <div className="github-token">
        <span className="github-token-state">
          {token ? describe(token, info) : 'GitHub is read with the token of the server, or without one.'}
        </span>
        <button
          type="button"
          className="sbb-btn sbb-btn--control"
          title="Your own GitHub token: 5000 requests per hour for you. It stays in this browser."
          onClick={() => {
            setError('');
            setEditing(true);
          }}
        >
          <ButtonIcon icon={faKey} />
          {token ? 'Change token' : 'Use my GitHub token'}
        </button>
        {token && (
          <button type="button" className="sbb-btn sbb-btn--control" onClick={remove}>
            <span className="button-image sbb-icon-delete" aria-hidden="true" />
            Remove token
          </button>
        )}
        {error && <ErrorNotice>Your GitHub token: {error}</ErrorNotice>}
      </div>
    );
  }
  return (
    <div className="github-token github-token-editing">
      <label htmlFor="github-token">GitHub token:</label>
      <input
        id="github-token"
        type="password"
        autoComplete="off"
        spellCheck={false}
        value={value}
        autoFocus
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && value.trim() && !busy) void save();
          if (e.key === 'Escape') close();
        }}
      />
      <label className="github-token-remember">
        <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
        Remember in this browser
      </label>
      <button type="button" className="sbb-btn sbb-btn--control" disabled={busy} onClick={close}>
        <span className="button-image sbb-icon-cancel" aria-hidden="true" />
        <span>Cancel</span>
      </button>
      <button
        type="button"
        className="sbb-btn sbb-btn--control"
        disabled={busy || !value.trim()}
        onClick={() => void save()}
      >
        <span className="button-image sbb-icon-save" aria-hidden="true" />
        Save
      </button>
      {busy && <span>Checking the token with GitHub...</span>}
      <span className="github-token-help">
        A{' '}
        <a href={NEW_TOKEN_URL} target="_blank" rel="noopener noreferrer">
          fine-grained token
        </a>{' '}
        with read access to public repositories is enough. GitHub checks it before the page keeps it. The token stays in
        this browser, and the server uses it for your requests only. Without Remember it goes when the tab closes.
      </span>
      {error && <ErrorNotice>{error}</ErrorNotice>}
    </div>
  );
}
