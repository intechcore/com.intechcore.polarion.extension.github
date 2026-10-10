import { COLUMNS, type ColumnId, type ColumnLayout } from './columns';
import { type ItemFilters, NO_FILTERS } from './itemFilters';

/**
 * What the URL asks of the page of the GitHub items. The topic and the administration entry pass
 * nothing; the Live Report widget passes its settings (GithubItemsWidgetRenderer).
 */
export interface PageOptions {
  /** The page runs in the iframe of the widget: no title, no breadcrumb, and it posts its height. */
  widget: boolean;
  /** The filters the page opens with. */
  filters: ItemFilters;
  /** The columns the widget fixes, or null to keep the layout of the viewer. */
  layout: ColumnLayout | null;
  /** Only the table: no toolbar and no filters. */
  hideFilters: boolean;
  /** Selection, Create and Update, and the hide buttons. The topic always allows them. */
  allowCreate: boolean;
  /** A line below the table says how long reading the items took. */
  showTime: boolean;
}

const list = (params: URLSearchParams, name: string): string[] =>
  (params.get(name) ?? '')
    .split(',')
    .map((value) => value.trim())
    .filter((value) => value);

const isColumn = (id: string): id is ColumnId => COLUMNS.some((column) => column.id === id);

export function readPageOptions(search: string = window.location.search): PageOptions {
  const params = new URLSearchParams(search);
  const widget = params.get('widget') === 'true';
  const columns = list(params, 'columns').filter(isColumn);
  const preset = { settings: list(params, 'settings'), kinds: list(params, 'kinds'), states: list(params, 'states') };
  const presetAny = Object.values(preset).some((values) => values.length > 0);
  return {
    widget,
    // Without a preset the page keeps NO_FILTERS itself: Clear filters compares with it.
    filters: presetAny ? { ...NO_FILTERS, ...preset } : NO_FILTERS,
    // The chosen columns show in their order, and every other column is hidden.
    layout:
      columns.length > 0
        ? {
            order: [...columns, ...COLUMNS.map((column) => column.id).filter((id) => !columns.includes(id))],
            hidden: COLUMNS.map((column) => column.id).filter((id) => !columns.includes(id)),
          }
        : null,
    hideFilters: widget && params.get('hideFilters') === 'true',
    allowCreate: !widget || params.get('allowCreate') === 'true',
    showTime: widget && params.get('showTime') === 'true',
  };
}
