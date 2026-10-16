import { useEffect, useRef, useState } from 'react';
import { faLock, faLockOpen } from '@fortawesome/free-solid-svg-icons';
import { Modal } from '@sbb-polarion/react-sbb-polarion';
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
  /** True in the iframe of a widget, which takes the height of its page. */
  widget?: boolean;
}

// The dialog takes at most 85% of the viewport height (RSP's .rsp-modal), and a widget's viewport is
// its iframe, as high as the page in it.
const DIALOG_HEIGHT_SHARE = 0.85;
const DIALOG_MARGIN = 16;

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
 * The GitHub token of the user: a button that opens a dialog, as Authorize does in Swagger. With a token
 * GitHub allows 5000 requests per hour for the user instead of the limit the server shares. A token is
 * checked with GitHub before it is kept, and again when the page opens. A print shows no button.
 */
export default function GithubTokenSetting({ onChange, widget = false }: Readonly<Props>) {
  const { checkGithubToken, forgetGithubToken } = useSettings();
  const [token, setToken] = useState(() => readGithubToken());
  const [info, setInfo] = useState<GithubTokenInfo | null>(null);
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState('');
  const [remember, setRemember] = useState(() => isGithubTokenRemembered());
  const [busy, setBusy] = useState(false);
  // A failed check when the page opens shows beside the button, a failed save in the dialog.
  const [checkError, setCheckError] = useState('');
  const [error, setError] = useState('');
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!token) return;
    let active = true;
    checkGithubToken(token)
      .then((checked) => active && setInfo(checked))
      .catch((e: Error) => active && setCheckError(e.message));
    return () => {
      active = false;
    };
    // Checked once when the page opens; a saved token comes with its check.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // The dialog focuses its content when it opens; this effect runs after it and moves on to the field.
  useEffect(() => {
    if (!open) return undefined;
    input.current?.focus();
    // In a widget the page grows to the height the dialog needs, and the iframe follows it.
    const dialog = input.current?.closest('dialog');
    if (!widget || !dialog) return undefined;
    const needed = Array.from(dialog.children).reduce((height, part) => height + part.scrollHeight, 0);
    document.body.style.minHeight = `${Math.ceil(needed / DIALOG_HEIGHT_SHARE) + DIALOG_MARGIN}px`;
    return () => {
      document.body.style.minHeight = '';
    };
  }, [open, widget]);

  const close = () => {
    setOpen(false);
    setValue('');
    setError('');
  };

  const save = async () => {
    const candidate = value.trim();
    if (!candidate || busy) return;
    setBusy(true);
    setError('');
    try {
      const checked = await checkGithubToken(candidate);
      saveGithubToken(candidate, remember);
      setToken(candidate);
      setInfo(checked);
      setCheckError('');
      close();
      onChange();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    removeGithubToken();
    setToken(null);
    setInfo(null);
    setCheckError('');
    close();
    onChange();
    try {
      await forgetGithubToken();
    } catch (e) {
      setCheckError(`the server could not forget it: ${(e as Error).message}`);
    }
  };

  const hint = token ? tokenHint(token) : '';
  return (
    <div className="github-token">
      <button
        type="button"
        className="sbb-btn sbb-btn--control"
        title={
          token
            ? describe(token, info)
            : 'GitHub is read with the token of the server, or without one. Your own token gives you 5000 requests per hour.'
        }
        onClick={() => {
          setError('');
          setOpen(true);
        }}
      >
        <ButtonIcon icon={token ? faLock : faLockOpen} />
        {hint ? `GitHub token ${hint}` : 'GitHub token'}
      </button>
      {checkError && <ErrorNotice>Your GitHub token: {checkError}</ErrorNotice>}
      <Modal
        open={open}
        title="GitHub token"
        okText="Save"
        okDisabled={busy || !value.trim()}
        onOk={() => void save()}
        onCancel={close}
      >
        <div className="modal__container github-token-dialog">
          <p className="github-token-state">
            {token ? describe(token, info) : 'GitHub is read with the token of the server, or without one.'}
          </p>
          {token && (
            <p>
              <button type="button" className="sbb-btn sbb-btn--control" onClick={() => void remove()}>
                <span className="button-image sbb-icon-delete" aria-hidden="true" />
                Remove token
              </button>
            </p>
          )}
          <label htmlFor="github-token">{token ? 'New token:' : 'Token:'}</label>
          <input
            id="github-token"
            ref={input}
            type="password"
            autoComplete="off"
            spellCheck={false}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void save();
            }}
          />
          <label className="github-token-remember">
            <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
            Remember in this browser
          </label>
          {busy && <p>Checking the token with GitHub...</p>}
          <p className="github-token-help">
            A{' '}
            <a href={NEW_TOKEN_URL} target="_blank" rel="noopener noreferrer">
              fine-grained token
            </a>{' '}
            with read access to public repositories is enough. GitHub checks it before the page keeps it. Without
            Remember it goes when the tab closes. The server keeps it in memory for your PDF exports, until you remove
            it, GitHub refuses it or Polarion restarts.
          </p>
          {error && <ErrorNotice>{error}</ErrorNotice>}
        </div>
      </Modal>
    </div>
  );
}
