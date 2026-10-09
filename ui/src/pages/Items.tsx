import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  faArrowsRotate,
  faCloudArrowDown,
  faFilterCircleXmark,
  faPlus,
  faRotateRight,
} from '@fortawesome/free-solid-svg-icons';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  PageLayout,
  SearchableSelect,
  getProjectIdFromScope,
  getScope,
  useOfferForPdfExport,
} from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';
import ButtonIcon from '../components/ButtonIcon';
import ErrorNotice from '../components/ErrorNotice';
import { ItemCell, LabelsCell, StateCell, StatusCell, WorkItemCell, itemNumber } from '../components/ItemCells';
import TableSettings from '../components/TableSettings';
import {
  COLUMN_LABELS,
  type ColumnId,
  type ColumnLayout,
  loadLayout,
  saveLayout,
  visibleColumns,
} from '../services/columns';
import {
  type ItemFilters,
  KIND_LABELS,
  NO_FILTERS,
  STATUS_LABELS,
  applyFilters,
  optionsOf,
} from '../services/itemFilters';
import { readPageOptions } from '../services/pageOptions';
import useSettings from '../services/settings';
import useIframeAutoHeight from '../services/useIframeAutoHeight';
import type { ImportEntry, ImportStatus, ItemKind, RepositoryState } from '../types';

const join = (values: string[] | null | undefined) => (values ?? []).join(', ');

function readTime(repositories: RepositoryState[]): string | null {
  const times = repositories.map((state) => state.readAt).filter((time): time is string => !!time);
  if (times.length === 0) return null;
  const oldest = times.sort()[0];
  // The 24-hour clock, whatever the locale of the browser would pick: 18:48, not 06:48 PM.
  return new Date(oldest).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
}

/**
 * Offers the widget to pdf-exporter's "Export to PDF" button of the report, which may then export it alone, as
 * the server renders it. Rendered by the widget only: the topic is no widget of a report.
 */
function PdfExportOffer() {
  useOfferForPdfExport('GitHub Items');
  return null;
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
  const [options] = useState(() => readPageOptions());
  const [filters, setFilters] = useState<ItemFilters>(options.filters);
  const [checked, setChecked] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  // A widget that fixes its columns neither reads nor changes the layout the viewer keeps.
  const [layout, setLayout] = useState<ColumnLayout>(() => options.layout ?? loadLayout());
  const [showHidden, setShowHidden] = useState(false);

  const changeLayout = (next: ColumnLayout) => {
    setLayout(next);
    if (!options.layout) {
      saveLayout(next);
    }
  };
  useIframeAutoHeight(options.widget);
  const title = options.widget ? undefined : 'GitHub';

  const load = useCallback(
    async (refresh = false) => {
      setBusy(true);
      setError('');
      try {
        // A widget reads only the repositories it names, in their order.
        const items = await settings.loadItems(projectId, refresh, options.widget ? options.filters.settings : []);
        setRepositories(items.repositories);
        setEntries(items.entries);
        setChecked(new Set());
      } catch (e) {
        setError((e as Error).message);
      } finally {
        setBusy(false);
      }
    },
    [settings, projectId, options],
  );

  useEffect(() => {
    if (projectId) {
      void load();
    }
  }, [load, projectId]);

  const visible = useMemo(
    () =>
      applyFilters(
        (entries ?? []).filter((entry) => showHidden || !entry.hidden),
        filters,
      ),
    [entries, filters, showHidden],
  );
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

  /** Hides an item on the page of the project, or shows a hidden one again, for every user. */
  const toggleHidden = async (entry: ImportEntry) => {
    setError('');
    try {
      const hidden = new Set(await settings.hideItems(projectId, [entry.url as string], !entry.hidden));
      setEntries((current) =>
        (current ?? []).map((other) => ({ ...other, hidden: !!other.url && hidden.has(other.url) })),
      );
      setChecked((current) => new Set([...current].filter((selected) => !hidden.has(selected))));
    } catch (e) {
      setError((e as Error).message);
    }
  };

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
      <PageLayout title={title}>
        <ErrorNotice>The GitHub items belong to a project. Open this page from a project.</ErrorNotice>
      </PageLayout>
    );
  }

  const all = entries ?? [];
  // The filter offers the short names, as the table shows them, and filters by the setting.
  const shortNames = new Map(all.map((entry) => [entry.setting ?? '', entry.shortName ?? '']));
  const time = readTime(repositories);
  const hiddenCount = all.filter((entry) => entry.hidden).length;
  const columns = visibleColumns(layout);
  const cell = (id: ColumnId, entry: ImportEntry): ReactNode => {
    switch (id) {
      case 'repository':
        return (
          <td key={id} title={entry.repository ?? undefined}>
            {entry.shortName || entry.setting}
          </td>
        );
      case 'item':
        return (
          <td key={id}>
            <ItemCell entry={entry} />
          </td>
        );
      case 'githubType':
        return <td key={id}>{entry.githubType}</td>;
      case 'labels':
        return (
          <td key={id}>
            <LabelsCell entry={entry} />
          </td>
        );
      case 'assignees':
        return <td key={id}>{join(entry.assignees)}</td>;
      case 'state':
        return (
          <td key={id}>
            <StateCell entry={entry} />
          </td>
        );
      case 'workItem':
        return (
          <td key={id}>
            <WorkItemCell entry={entry} projectId={projectId} />
          </td>
        );
      case 'status':
        return (
          <td key={id}>
            <StatusCell entry={entry} />
          </td>
        );
      case 'workItemAssignees':
        return <td key={id}>{join(entry.workItemAssignees)}</td>;
    }
  };
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
    <PageLayout title={title}>
      {options.widget && <PdfExportOffer />}
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
          {(!options.hideFilters || options.allowCreate) && (
            <div className="items-toolbar">
              {!options.hideFilters && (
                <>
                  <button
                    type="button"
                    className="sbb-btn sbb-btn--control"
                    disabled={busy}
                    title="Reads the list again: GitHub items from the cache of the server, work items from Polarion"
                    onClick={() => void load()}
                  >
                    <ButtonIcon icon={faRotateRight} />
                    Refresh
                  </button>
                  <button
                    type="button"
                    className="sbb-btn sbb-btn--control"
                    disabled={busy}
                    title="Reads GitHub again instead of the lists of the last five minutes. Each repository costs requests of the hourly GitHub limit."
                    onClick={() => void load(true)}
                  >
                    <ButtonIcon icon={faCloudArrowDown} />
                    Update from GitHub
                  </button>
                </>
              )}
              {options.allowCreate && (
                <>
                  <button
                    type="button"
                    className="sbb-btn sbb-btn--control"
                    disabled={busy || toCreate === 0}
                    onClick={() => void apply(false)}
                  >
                    <ButtonIcon icon={faPlus} />
                    Create work items{toCreate > 0 ? ` (${toCreate})` : ''}
                  </button>
                  <button
                    type="button"
                    className="sbb-btn sbb-btn--control"
                    disabled={busy || toUpdate === 0}
                    title="Makes the selected outdated work items show what the settings and GitHub say now. The URL they keep stays."
                    onClick={() => void apply(true)}
                  >
                    <ButtonIcon icon={faArrowsRotate} />
                    Update work items{toUpdate > 0 ? ` (${toUpdate})` : ''}
                  </button>
                </>
              )}
              {busy && <span>Working...</span>}
              {!options.hideFilters && time && (
                <span className="items-read-at">
                  Read from GitHub at {time}. The server keeps the lists for 5 minutes, and Update from GitHub reads
                  them again.
                </span>
              )}
            </div>
          )}

          {!options.hideFilters && (
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
                <ButtonIcon icon={faFilterCircleXmark} />
                Clear filters
              </button>
            </div>
          )}

          {entries !== null && (
            <p className="items-summary">
              {visible.length} of {all.length} open item(s) shown.
              {hiddenCount > 0 && !showHidden ? ` ${hiddenCount} hidden.` : ''}
            </p>
          )}

          {/* The header stays while filters or hiding leave no row: it carries the table settings. */}
          {all.length > 0 && (
            <table className="items-table">
              <thead>
                <tr>
                  {options.allowCreate && (
                    <th>
                      <input
                        type="checkbox"
                        aria-label="Select all new and outdated items shown"
                        checked={allSelected}
                        disabled={selectable.length === 0}
                        onChange={toggleAll}
                      />
                    </th>
                  )}
                  {columns.map((id) => (
                    <th key={id}>{COLUMN_LABELS[id]}</th>
                  ))}
                  <th className="table-settings-cell">
                    <TableSettings
                      layout={layout}
                      onChange={changeLayout}
                      showHidden={showHidden}
                      onShowHiddenChange={setShowHidden}
                      hiddenCount={hiddenCount}
                    />
                  </th>
                </tr>
              </thead>
              <tbody>
                {visible.length === 0 && (
                  <tr className="items-empty-row">
                    <td className="items-empty" colSpan={columns.length + (options.allowCreate ? 2 : 1)}>
                      No item matches the filters.
                    </td>
                  </tr>
                )}
                {visible.map((entry) => (
                  <tr
                    key={`${entry.setting}-${entry.kind}-${entry.ghsaId ?? entry.number}`}
                    className={entry.hidden ? 'item-hidden' : undefined}
                  >
                    {options.allowCreate && (
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
                    )}
                    {columns.map((id) => cell(id, entry))}
                    <td>
                      {/* Hiding changes what every user of the project sees: a report widget leaves it. */}
                      {options.allowCreate && entry.url && (
                        <button
                          type="button"
                          className="item-hide"
                          aria-label={`${entry.hidden ? 'Show' : 'Hide'} ${itemNumber(entry)}`}
                          title={
                            entry.hidden
                              ? 'Shows the item again, for every user of the project'
                              : 'Hides the item on this page, for every user of the project'
                          }
                          disabled={busy}
                          onClick={() => void toggleHidden(entry)}
                        >
                          <FontAwesomeIcon icon={entry.hidden ? faEye : faEyeSlash} />
                        </button>
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
