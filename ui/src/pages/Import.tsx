import { useEffect, useState } from 'react';
import { PageLayout, SearchableSelect, getProjectIdFromScope, getScope } from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';
import ErrorNotice from '../components/ErrorNotice';
import useSettings from '../services/settings';
import type { ImportEntry, ImportStatus, SettingName } from '../types';

const STATUS_LABELS: Record<ImportStatus, string> = {
  NEW: 'New',
  CREATED: 'Created',
  EXISTS: 'Exists',
  SKIPPED: 'Left out',
  FAILED: 'Failed',
};

const KIND_LABELS = { ISSUE: 'Issue', DISCUSSION: 'Discussion' };

/**
 * The manual import: reads the open issues and discussions of a configured repository, shows which
 * of them have a work item already, and creates work items for the selected ones.
 */
export default function Import() {
  const settings = useSettings();
  const scope = getScope();
  const projectId = getProjectIdFromScope(scope);

  const [names, setNames] = useState<SettingName[]>([]);
  const [name, setName] = useState('');
  const [entries, setEntries] = useState<ImportEntry[] | null>(null);
  const [checked, setChecked] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!projectId) {
      return;
    }
    let cancelled = false;
    settings
      .loadConfigurationNames(scope)
      .then((list) => {
        if (cancelled) return;
        setNames(list);
        setName(list.length > 0 ? list[0].name : '');
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message);
      });
    return () => {
      cancelled = true;
    };
  }, [settings, scope, projectId]);

  const selectName = (value: string) => {
    setName(value);
    setEntries(null);
    setChecked(new Set());
  };

  /** Reads the items from GitHub, without creating anything. Every new item starts selected. */
  const load = async () => {
    setBusy(true);
    setError('');
    try {
      const result = await settings.runImport(projectId, name, true);
      setEntries(result.entries);
      setChecked(new Set(result.entries.filter((e) => e.status === 'NEW' && e.url).map((e) => e.url as string)));
    } catch (e) {
      setEntries(null);
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  /** Creates the work items of the selected items, and shows their outcome in place. */
  const importSelected = async () => {
    setBusy(true);
    setError('');
    try {
      const result = await settings.runImport(projectId, name, false, Array.from(checked));
      const outcome = new Map(result.entries.map((entry) => [entry.url, entry]));
      setEntries((current) => (current ?? []).map((entry) => outcome.get(entry.url) ?? entry));
      setChecked(new Set());
      const created = result.entries.filter((entry) => entry.status === 'CREATED').length;
      const failed = result.entries.filter((entry) => entry.status === 'FAILED').length;
      if (failed > 0) {
        toast.error(`${created} work item(s) created, ${failed} failed.`);
      } else {
        toast.success(`${created} work item(s) created.`);
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const toggle = (url: string) =>
    setChecked((current) => {
      const next = new Set(current);
      if (!next.delete(url)) {
        next.add(url);
      }
      return next;
    });

  if (!projectId) {
    return (
      <PageLayout title="Import">
        <ErrorNotice>The import runs in a project. Open this page from a project.</ErrorNotice>
      </PageLayout>
    );
  }

  const count = (status: ImportStatus) => (entries ?? []).filter((entry) => entry.status === status).length;

  return (
    <PageLayout title="Import">
      {error && <ErrorNotice>{error}</ErrorNotice>}

      {names.length === 0 ? (
        <p>This project has no repository setting. Create one on the Repositories page.</p>
      ) : (
        <div className="import-controls">
          <label htmlFor="import-repository">Repository:</label>
          <SearchableSelect
            id="import-repository"
            value={name}
            onChange={selectName}
            options={names.map((setting) => ({ id: setting.name, name: setting.name }))}
          />
          <button type="button" className="sbb-btn sbb-btn--control" disabled={busy} onClick={() => void load()}>
            Read from GitHub
          </button>
          <button
            type="button"
            className="sbb-btn sbb-btn--control"
            disabled={busy || checked.size === 0}
            onClick={() => void importSelected()}
          >
            Create work items
          </button>
        </div>
      )}

      {busy && <p>Working...</p>}

      {entries && (
        <>
          <p className="import-summary">
            {entries.length} open item(s): {count('NEW')} new, {count('EXISTS') + count('CREATED')} with a work item,{' '}
            {count('SKIPPED')} left out, {count('FAILED')} failed.
          </p>
          {entries.length > 0 && (
            <table className="import-table">
              <thead>
                <tr>
                  <th aria-label="Selected" />
                  <th>Kind</th>
                  <th>Number</th>
                  <th>Title</th>
                  <th>Status</th>
                  <th>Work item</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => (
                  <tr key={`${entry.kind}-${entry.number}`}>
                    <td>
                      {entry.status === 'NEW' && entry.url && (
                        <input
                          type="checkbox"
                          aria-label={`Select ${entry.url}`}
                          checked={checked.has(entry.url)}
                          onChange={() => toggle(entry.url as string)}
                        />
                      )}
                    </td>
                    <td>{KIND_LABELS[entry.kind]}</td>
                    <td>{entry.number}</td>
                    <td>
                      {entry.url ? (
                        <a href={entry.url} target="_blank" rel="noopener noreferrer">
                          {entry.title}
                        </a>
                      ) : (
                        entry.title
                      )}
                    </td>
                    <td className={`status-${entry.status}`}>
                      {STATUS_LABELS[entry.status]}
                      {entry.message ? `: ${entry.message}` : ''}
                    </td>
                    <td>
                      {entry.workItemId && (
                        <a
                          href={`/polarion/#/project/${encodeURIComponent(projectId)}/workitem?id=${encodeURIComponent(entry.workItemId)}`}
                          target="_top"
                        >
                          {entry.workItemId}
                        </a>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </PageLayout>
  );
}
