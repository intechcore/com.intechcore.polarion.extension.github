import { useCallback, useEffect, useState } from 'react';
import { faPlus, faRotateLeft, faTrashCan } from '@fortawesome/free-solid-svg-icons';
import {
  ConfigurationButtons,
  PageLayout,
  SearchableSelect,
  getProjectIdFromScope,
  getScope,
} from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';
import ButtonIcon from '../components/ButtonIcon';
import ErrorNotice from '../components/ErrorNotice';
import useSettings from '../services/settings';
import type { ProjectOption, UserLink } from '../types';

/**
 * The Polarion users of GitHub logins. The import assigns the work item of an issue or pull request
 * to the users of its GitHub assignees. The global mapping serves every project; a project may keep
 * its own, which replaces the global one.
 */
export default function UserMapping() {
  const settings = useSettings();
  const scope = getScope();
  const inProject = Boolean(getProjectIdFromScope(scope));

  const [users, setUsers] = useState<ProjectOption[]>([]);
  const [rows, setRows] = useState<UserLink[]>([]);
  const [inherited, setInherited] = useState(false);
  const [loadingError, setLoadingError] = useState('');

  const load = useCallback(async () => {
    try {
      const content = await settings.loadUserMapping(scope);
      setRows(content.users);
      setInherited(content.inherited);
      setLoadingError('');
    } catch (e) {
      setLoadingError((e as Error).message);
    }
  }, [settings, scope]);

  useEffect(() => {
    void load();
    settings
      .loadUsers()
      .then(setUsers)
      .catch(() => setUsers([]));
  }, [settings, load]);

  const setRow = (index: number, change: Partial<UserLink>) =>
    setRows(rows.map((row, i) => (i === index ? { ...row, ...change } : row)));

  const handleSave = async () => {
    try {
      // A row left empty is no entry.
      const filled = rows
        .map((row) => ({ githubLogin: row.githubLogin.trim(), polarionUser: row.polarionUser }))
        .filter((row) => row.githubLogin || row.polarionUser);
      const content = await settings.saveUserMapping(scope, filled);
      setRows(content.users);
      setInherited(content.inherited);
      toast.success('Data successfully saved.');
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  const handleReset = async () => {
    try {
      const content = await settings.resetUserMapping(scope);
      setRows(content.users);
      setInherited(content.inherited);
      toast.success('The project uses the global mapping again.');
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  const options = users.map((user) => ({ id: user.id, name: `${user.name} (${user.id})` }));

  return (
    <PageLayout title="User Mapping">
      {loadingError && <ErrorNotice>The user mapping did not load: {loadingError}</ErrorNotice>}
      <p className="block-hint">
        The import assigns the work item of an issue or a pull request to the Polarion users of its GitHub assignees. An
        update adds and removes these users as the assignees change on GitHub. A user assigned in Polarion by hand
        stays, unless the repository setting names an assignee.
      </p>
      {inProject && (
        <p className="block-hint">
          {inherited
            ? 'This project uses the global mapping. A save keeps a mapping of its own for the project.'
            : 'This project keeps a mapping of its own, in place of the global one.'}
        </p>
      )}

      <table className="user-mapping">
        <thead>
          <tr>
            <th>GitHub login</th>
            <th>Polarion user</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 && (
            <tr>
              <td colSpan={3} className="user-mapping-empty">
                No GitHub login is mapped.
              </td>
            </tr>
          )}
          {rows.map((row, index) => (
            // The rows have no identity of their own, and they never reorder.
            <tr key={index}>
              <td>
                <input
                  type="text"
                  aria-label={`GitHub login ${index + 1}`}
                  value={row.githubLogin}
                  onChange={(e) => setRow(index, { githubLogin: e.target.value })}
                />
              </td>
              <td>
                <SearchableSelect
                  ariaLabel={`Polarion user ${index + 1}`}
                  value={row.polarionUser}
                  onChange={(polarionUser) => setRow(index, { polarionUser })}
                  options={options}
                  allowEmpty
                />
              </td>
              <td>
                <button
                  type="button"
                  className="sbb-btn sbb-btn--control"
                  onClick={() => setRows(rows.filter((_, i) => i !== index))}
                >
                  <ButtonIcon icon={faTrashCan} />
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <button
        type="button"
        className="sbb-btn sbb-btn--control"
        onClick={() => setRows([...rows, { githubLogin: '', polarionUser: '' }])}
      >
        <ButtonIcon icon={faPlus} />
        Add a user
      </button>
      {inProject && !inherited && (
        <button type="button" className="sbb-btn sbb-btn--control" onClick={() => void handleReset()}>
          <ButtonIcon icon={faRotateLeft} />
          Use the global mapping
        </button>
      )}

      <ConfigurationButtons onSave={() => void handleSave()} onCancel={() => void load()} />
    </PageLayout>
  );
}
