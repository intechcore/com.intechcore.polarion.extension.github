import { useState } from 'react';
import { faKey } from '@fortawesome/free-solid-svg-icons';
import {
  isGithubTokenRemembered,
  readGithubToken,
  removeGithubToken,
  saveGithubToken,
  tokenHint,
} from '../services/githubToken';
import ButtonIcon from './ButtonIcon';
import ErrorNotice from './ErrorNotice';

const NEW_TOKEN_URL = 'https://github.com/settings/personal-access-tokens/new';

interface Props {
  /** Called after the token changed, so the page reads GitHub with the new one. */
  onChange: () => void;
}

/**
 * The GitHub token of the user, kept in this browser. With it GitHub allows 5000 requests per hour
 * for the user instead of the limit the server shares.
 */
export default function GithubTokenSetting({ onChange }: Readonly<Props>) {
  const [token, setToken] = useState(() => readGithubToken());
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState('');
  const [remember, setRemember] = useState(() => isGithubTokenRemembered());
  const [error, setError] = useState('');

  const close = () => {
    setEditing(false);
    setValue('');
    setError('');
  };

  const save = () => {
    try {
      saveGithubToken(value.trim(), remember);
    } catch (e) {
      setError((e as Error).message);
      return;
    }
    setToken(value.trim());
    close();
    onChange();
  };

  const remove = () => {
    removeGithubToken();
    setToken(null);
    close();
    onChange();
  };

  if (!editing) {
    return (
      <div className="github-token">
        <span className="github-token-state">
          {token
            ? `GitHub is read with your token ${tokenHint(token)}`.trim() + ', kept in this browser.'
            : 'GitHub is read with the token of the server, or without one.'}
        </span>
        <button
          type="button"
          className="sbb-btn sbb-btn--control"
          title="Your own GitHub token: 5000 requests per hour for you. It stays in this browser."
          onClick={() => setEditing(true)}
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
          if (e.key === 'Enter' && value.trim()) save();
          if (e.key === 'Escape') close();
        }}
      />
      <label className="github-token-remember">
        <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
        Remember in this browser
      </label>
      <button type="button" className="sbb-btn sbb-btn--control" onClick={close}>
        <span className="button-image sbb-icon-cancel" aria-hidden="true" />
        <span>Cancel</span>
      </button>
      <button type="button" className="sbb-btn sbb-btn--control" disabled={!value.trim()} onClick={save}>
        <span className="button-image sbb-icon-save" aria-hidden="true" />
        Save
      </button>
      <span className="github-token-help">
        A{' '}
        <a href={NEW_TOKEN_URL} target="_blank" rel="noopener noreferrer">
          fine-grained token
        </a>{' '}
        with read access to public repositories is enough. The token stays in this browser, and the server uses it for
        your requests only. Without Remember it goes when the tab closes.
      </span>
      {error && <ErrorNotice>{error}</ErrorNotice>}
    </div>
  );
}
