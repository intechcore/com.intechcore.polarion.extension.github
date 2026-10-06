/** The columns of the items table a user can hide and move. The selection column always shows. */
export const COLUMNS = [
  { id: 'repository', label: 'Repository' },
  { id: 'item', label: 'Item' },
  { id: 'githubType', label: 'GitHub type' },
  { id: 'labels', label: 'Labels' },
  { id: 'assignees', label: 'GitHub assignees' },
  { id: 'state', label: 'State' },
  { id: 'workItem', label: 'Work item' },
  { id: 'status', label: 'Status' },
  { id: 'workItemAssignees', label: 'Polarion assignees' },
] as const;

export type ColumnId = (typeof COLUMNS)[number]['id'];

/** The order of all columns, and the ones hidden. */
export interface ColumnLayout {
  order: ColumnId[];
  hidden: ColumnId[];
}

export const DEFAULT_LAYOUT: ColumnLayout = { order: COLUMNS.map((column) => column.id), hidden: [] };

export const COLUMN_LABELS = Object.fromEntries(COLUMNS.map((column) => [column.id, column.label])) as Record<
  ColumnId,
  string
>;

// The layout is a convenience of one user in one browser, so the browser keeps it.
const STORAGE_KEY = 'github-items-columns';

const known = (id: unknown): id is ColumnId => COLUMNS.some((column) => column.id === id);

/**
 * The layout the user saved, or the default. Unknown columns drop out, and a column added since
 * the save shows at the end.
 */
export function loadLayout(): ColumnLayout {
  try {
    const saved = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? 'null') as Partial<ColumnLayout> | null;
    if (!saved || !Array.isArray(saved.order)) return DEFAULT_LAYOUT;
    const order = [...new Set(saved.order.filter(known))];
    const missing = DEFAULT_LAYOUT.order.filter((id) => !order.includes(id));
    const hidden = Array.isArray(saved.hidden) ? saved.hidden.filter(known) : [];
    return { order: [...order, ...missing], hidden };
  } catch {
    return DEFAULT_LAYOUT;
  }
}

export function saveLayout(layout: ColumnLayout): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(layout));
  } catch {
    // Without storage the layout lasts until the page closes.
  }
}

/** Moves a column one place up (-1) or down (+1). */
export function moveColumn(layout: ColumnLayout, id: ColumnId, offset: -1 | 1): ColumnLayout {
  const from = layout.order.indexOf(id);
  const to = from + offset;
  if (from < 0 || to < 0 || to >= layout.order.length) return layout;
  const order = [...layout.order];
  [order[from], order[to]] = [order[to], order[from]];
  return { ...layout, order };
}

/** Shows or hides a column. The last visible column stays. */
export function toggleColumn(layout: ColumnLayout, id: ColumnId): ColumnLayout {
  if (layout.hidden.includes(id)) {
    return { ...layout, hidden: layout.hidden.filter((hidden) => hidden !== id) };
  }
  if (layout.order.length - layout.hidden.length <= 1) return layout;
  return { ...layout, hidden: [...layout.hidden, id] };
}

/** The columns that show, in their order. */
export const visibleColumns = (layout: ColumnLayout): ColumnId[] =>
  layout.order.filter((id) => !layout.hidden.includes(id));
