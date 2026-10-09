import { useState } from 'react';
import { faCopy } from '@fortawesome/free-solid-svg-icons';
import ButtonIcon from './ButtonIcon';
import ErrorNotice from './ErrorNotice';

// The names the generic settings accept, as RSP's ConfigurationsPane checks them.
const INVALID_CHARS = /[^a-zA-Z0-9\-_ ]+/;
const NAME_MAX_LENGTH = 40;

interface Props {
  /** The selected setting, the one to copy. */
  name: string;
  /** Copies the saved content of the setting under the new name. */
  onCopy: (name: string, newName: string) => Promise<void>;
  /** Called when the name editor opens or closes, so the form can be dimmed. */
  onEditingChange: (editing: boolean) => void;
}

/**
 * Copies a repository setting under a new name, to edit the copy afterwards. RSP's ConfigurationsPane
 * offers no copy, so this row sits below it and follows its look.
 */
export default function CopySetting({ name, onCopy, onEditingChange }: Readonly<Props>) {
  const [newName, setNewName] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const open = () => {
    setNewName(`${name} copy`.slice(0, NAME_MAX_LENGTH));
    setError('');
    onEditingChange(true);
  };

  const close = () => {
    setNewName(null);
    setError('');
    onEditingChange(false);
  };

  const submit = async (value: string) => {
    if (INVALID_CHARS.test(value)) {
      setError('Only alphanumeric characters, hyphens and spaces are allowed');
      return;
    }
    setBusy(true);
    try {
      await onCopy(name, value);
      close();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  if (newName === null) {
    return (
      <div className="config-row copy-setting">
        <button
          type="button"
          className="sbb-btn sbb-btn--control"
          title="Copies the saved setting under a new name, to edit the copy"
          onClick={open}
        >
          <ButtonIcon icon={faCopy} />
          Copy
        </button>
      </div>
    );
  }
  return (
    <div className="config-row config-edit-row copy-setting">
      <label htmlFor="copy-name">Copy {name} as:</label>
      <input
        id="copy-name"
        type="text"
        maxLength={NAME_MAX_LENGTH}
        value={newName}
        autoFocus
        onChange={(e) => setNewName(e.target.value)}
      />
      <button type="button" className="sbb-btn sbb-btn--control" disabled={busy} onClick={close}>
        <span className="button-image sbb-icon-cancel" aria-hidden="true" />
        <span>Cancel</span>
      </button>
      <button
        type="button"
        className="sbb-btn sbb-btn--control"
        disabled={busy || newName.trim().length === 0}
        onClick={() => void submit(newName.trim())}
      >
        <span className="button-image sbb-icon-save" aria-hidden="true" />
        Copy
      </button>
      {error && <ErrorNotice>{error}</ErrorNotice>}
    </div>
  );
}
