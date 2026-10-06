import { useCallback, useEffect, useMemo, useState } from 'react';
import { PageLayout, SearchableSelect, getProjectIdFromScope, getScope } from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';
import ErrorNotice from '../components/ErrorNotice';
import { ItemCell, LabelsCell, StateCell, WorkItemCell } from '../components/ItemCells';
import {
  type ItemFilters,
  KIND_LABELS,
  NO_FILTERS,
  STATUS_LABELS,
  applyFilters,
  optionsOf,
} from '../services/itemFilters';
import useSettings from '../services/settings';
import type { ImportEntry, ImportStatus, ItemKind, RepositoryState } from '../types';

const join = (values: string[] | null | undefined) => (values ?? []).join(', ');

function readTime(repositories: RepositoryState[]): string | null {
  const times = repositories.map((state) => state.readAt).filter((time): time is string => !!time);
  if (times.length === 0) return null;
  const oldest = times.sort()[0];
  return new Date(oldest).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

/**
 * The open issues and discussions of all repositories of a project: the GitHub topic of the project,
 * and the same page in its administration. A user filters them, selects some and creates their work
 * items in their own name.
 */
export default function Items() {
  const settings = useSettings();
  const scope = getScope();
  const projectId = getProjectIdFromScope(scope);

  const [repositories, setRepositories] = useState<RepositoryState[]>([]);
  const [entries, setEntries] = useState<ImportEntry[] | null>(null);
  const [filters, setFilters] = useState<ItemFilters>(NO_FILTERS);
  const [checked, setChecked] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(
    async (refresh = false) => {
      setBusy(true);
      setError('');
      try {
        const items = await settings.loadItems(projectId, refresh);
        setRepositories(items.repositories);
        setEntries(items.entries);
        setChecked(new Set());
      } catch (e) {
        setError((e as Error).message);
      } finally {
        setBusy(false);
      }
    },
    [settings, projectId],
  );

  useEffect(() => {
    if (projectId) {
      void load();
    }
  }, [load, projectId]);

  const visible = useMemo(() => applyFilters(entries ?? [], filters), [entries, filters]);
  const selectable = visible.filter((entry) => (entry.status === 'NEW' || entry.status === 'OUTDATED') && entry.url);
  const count = (status: ImportStatus) =>
    (entries ?? []).filter((entry) => entry.status === status && entry.url && checked.has(entry.url)).length;
  const toCreate = count('NEW');
  const toUpdate = count('OUTDATED');
  const allSelected = selectable.length > 0 && selectable.every((entry) => checked.has(entry.url as string));

  const setFilter = (change: Partial<ItemFilters>) => setFilters((current) => ({ ...current, ...change }));
  const toggle = (url: string) =>
    setChecked((current) => {
      const next = new Set(current);
      if (!next.delete(url)) {
        next.add(url);
      }
      return next;
    });
  const toggleAll = () =>
    setChecked((current) => {
      const next = new Set(current);
      selectable.forEach((entry) => (allSelected ? next.delete(entry.url as string) : next.add(entry.url as string)));
      return next;
    });

  /**
   * Creates the work items of the selected new items, or updates those of the selected outdated ones:
   * one request per repository setting.
   */
  const apply = async (update: boolean) => {
    const wanted: ImportStatus = update ? 'OUTDATED' : 'NEW';
    const bySetting = new Map<string, string[]>();
    (entries ?? [])
      .filter((entry) => entry.url && checked.has(entry.url) && entry.setting && entry.status === wanted)
      .forEach((entry) =>
        bySetting.set(entry.setting as string, [
          ...(bySetting.get(entry.setting as string) ?? []),
          entry.url as string,
        ]),
      );
    setBusy(true);
    setError('');
    const outcome = new Map<string, ImportEntry>();
    const failures: string[] = [];
    for (const [setting, urls] of bySetting) {
      try {
        const result = update
          ? await settings.runUpdate(projectId, setting, urls)
          : await settings.runImport(projectId, setting, false, urls);
        result.entries.forEach((entry) => outcome.set(entry.url as string, entry));
      } catch (e) {
        failures.push(`${setting}: ${(e as Error).message}`);
      }
    }
    // An update answers for every item of its repositories: only the selected ones change here.
    const selected = new Set([...bySetting.values()].flat());
    setEntries((current) =>
      (current ?? []).map((entry) =>
        selected.has(entry.url as string) ? (outcome.get(entry.url as string) ?? entry) : entry,
      ),
    );
    setChecked((current) => new Set([...current].filter((url) => !selected.has(url))));
    setBusy(false);
    if (failures.length > 0) {
      setError(failures.join(' '));
    }
    const done = [...outcome.values()].filter(
      (entry) => selected.has(entry.url as string) && entry.status === (update ? 'UPDATED' : 'CREATED'),
    ).length;
    const failed = [...outcome.values()].filter(
      (entry) => selected.has(entry.url as string) && entry.status === 'FAILED',
    ).length;
    const verb = update ? 'updated' : 'created';
    if (failed > 0 || failures.length > 0) {
      toast.error(`${done} work item(s) ${verb}, ${failed} failed.`);
    } else {
      toast.success(`${done} work item(s) ${verb}.`);
    }
  };

  if (!projectId) {
    return (
      <PageLayout title="GitHub">
        <ErrorNotice>The GitHub items belong to a project. Open this page from a project.</ErrorNotice>
      </PageLayout>
    );
  }

  const all = entries ?? [];
  // The filter offers the short names, as the table shows them, and filters by the setting.
  const shortNames = new Map(all.map((entry) => [entry.setting ?? '', entry.shortName ?? '']));
  const time = readTime(repositories);
  const filterSelect = (
    label: string,
    key: Exclude<keyof ItemFilters, 'text'>,
    options: { id: string; name: string }[],
  ) => (
    <label className="item-filter">
      <span>{label}</span>
      <SearchableSelect
        multiple
        ariaLabel={label}
        value={filters[key]}
        onChange={(value) => setFilter({ [key]: value })}
        options={options}
        placeholder="All"
      />
    </label>
  );

  return (
    <PageLayout title="GitHub">
      {error && <ErrorNotice>{error}</ErrorNotice>}
      {repositories
        .filter((state) => state.error)
        .map((state) => (
          <ErrorNotice key={state.setting}>
            {state.setting}
            {state.repository ? ` (${state.repository})` : ''}: {state.error}
          </ErrorNotice>
        ))}

      {entries !== null && repositories.length === 0 ? (
        <p>This project has no repository setting. An administrator creates one on the Repositories page.</p>
      ) : (
        <>
          <div className="items-toolbar">
            <button
              type="button"
              className="sbb-btn sbb-btn--control"
              disabled={busy}
              title="Reads the list again: GitHub items from the cache of the server, work items from Polarion"
              onClick={() => void load()}
            >
              Refresh
            </button>
            <button
              type="button"
              className="sbb-btn sbb-btn--control"
              disabled={busy}
              title="Reads GitHub again instead of the lists of the last five minutes. Each repository costs requests of the hourly GitHub limit."
              onClick={() => void load(true)}
            >
              Update from GitHub
            </button>
            <button
              type="button"
              className="sbb-btn sbb-btn--control"
              disabled={busy || toCreate === 0}
              onClick={() => void apply(false)}
            >
              Create work items{toCreate > 0 ? ` (${toCreate})` : ''}
            </button>
            <button
              type="button"
              className="sbb-btn sbb-btn--control"
              disabled={busy || toUpdate === 0}
              title="Makes the selected outdated work items show what the settings and GitHub say now. The URL they keep stays."
              onClick={() => void apply(true)}
            >
              Update work items{toUpdate > 0 ? ` (${toUpdate})` : ''}
            </button>
            {busy && <span>Working...</span>}
            {time && (
              <span className="items-read-at">
                Read from GitHub at {time}. The server keeps the lists for 5 minutes, and Update from GitHub reads them
                again.
              </span>
            )}
          </div>

          <div className="item-filters">
            {filterSelect(
              'Repository',
              'settings',
              optionsOf(all, 'settings', (setting) => shortNames.get(setting) || setting),
            )}
            {filterSelect(
              'Kind',
              'kinds',
              optionsOf(all, 'kinds', (kind) => KIND_LABELS[kind as ItemKind]),
            )}
            {filterSelect('GitHub type', 'githubTypes', optionsOf(all, 'githubTypes'))}
            {filterSelect('Work item type', 'workItemTypes', optionsOf(all, 'workItemTypes'))}
            {filterSelect('GitHub assignee', 'assignees', optionsOf(all, 'assignees', undefined, 'Unassigned'))}
            {filterSelect(
              'Polarion assignee',
              'workItemAssignees',
              optionsOf(all, 'workItemAssignees', undefined, 'Unassigned'),
            )}
            {filterSelect(
              'State',
              'states',
              optionsOf(all, 'states', (state) => STATUS_LABELS[state as ImportStatus]),
            )}
            <label className="item-filter">
              <span>Search</span>
              <input
                type="text"
                aria-label="Search"
                placeholder="Title, number, label"
                value={filters.text}
                onChange={(e) => setFilter({ text: e.target.value })}
              />
            </label>
            <button
              type="button"
              className="sbb-btn sbb-btn--control"
              disabled={filters === NO_FILTERS}
              onClick={() => setFilters(NO_FILTERS)}
            >
              Clear filters
            </button>
          </div>

          {entries !== null && (
            <p className="items-summary">
              {visible.length} of {all.length} open item(s) shown.
            </p>
          )}

          {visible.length > 0 && (
            <table className="items-table">
              <thead>
                <tr>
                  <th>
                    <input
                      type="checkbox"
                      aria-label="Select all new and outdated items shown"
                      checked={allSelected}
                      disabled={selectable.length === 0}
                      onChange={toggleAll}
                    />
                  </th>
                  <th>Repository</th>
                  <th>Item</th>
                  <th>GitHub type</th>
                  <th>Labels</th>
                  <th>GitHub assignees</th>
                  <th>State</th>
                  <th>Work item</th>
                  <th>Status</th>
                  <th>Polarion assignees</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((entry) => (
                  <tr key={`${entry.setting}-${entry.kind}-${entry.number}`}>
                    <td>
                      {(entry.status === 'NEW' || entry.status === 'OUTDATED') && entry.url && (
                        <input
                          type="checkbox"
                          aria-label={`Select ${entry.url}`}
                          checked={checked.has(entry.url)}
                          onChange={() => toggle(entry.url as string)}
                        />
                      )}
                    </td>
                    <td title={entry.repository ?? undefined}>{entry.shortName || entry.setting}</td>
                    <td>
                      <ItemCell entry={entry} />
                    </td>
                    <td>{entry.githubType}</td>
                    <td>
                      <LabelsCell entry={entry} />
                    </td>
                    <td>{join(entry.assignees)}</td>
                    <td>
                      <StateCell entry={entry} />
                    </td>
                    <td>
                      <WorkItemCell entry={entry} projectId={projectId} />
                    </td>
                    <td>{entry.workItemStatus}</td>
                    <td>{join(entry.workItemAssignees)}</td>
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
