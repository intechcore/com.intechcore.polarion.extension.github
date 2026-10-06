import { useEffect, useRef, useState } from 'react';
import { faArrowDown, faArrowUp, faGear } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  COLUMN_LABELS,
  type ColumnLayout,
  DEFAULT_LAYOUT,
  moveColumn,
  toggleColumn,
  visibleColumns,
} from '../services/columns';

interface TableSettingsProps {
  layout: ColumnLayout;
  onChange: (layout: ColumnLayout) => void;
  showHidden: boolean;
  onShowHiddenChange: (showHidden: boolean) => void;
  hiddenCount: number;
}

/**
 * The settings of the items table behind a gear in its header: whether the hidden items show, and
 * which columns show in which order.
 */
export default function TableSettings({
  layout,
  onChange,
  showHidden,
  onShowHiddenChange,
  hiddenCount,
}: Readonly<TableSettingsProps>) {
  const [open, setOpen] = useState(false);
  const lastVisible = visibleColumns(layout).length <= 1;
  const root = useRef<HTMLDivElement>(null);

  // A click outside closes the settings, as a menu does.
  useEffect(() => {
    if (!open) return undefined;
    const close = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);

  return (
    <div className="table-settings" ref={root}>
      <button
        type="button"
        className="table-settings-button"
        aria-label="Table settings"
        title="Table settings"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <FontAwesomeIcon icon={faGear} />
      </button>
      {open && (
        <div className="table-settings-panel" role="group" aria-label="Table settings">
          <label className="table-settings-hidden">
            <input type="checkbox" checked={showHidden} onChange={(e) => onShowHiddenChange(e.target.checked)} />
            Show hidden items ({hiddenCount})
          </label>
          <div className="table-settings-title">Columns</div>
          {layout.order.map((id, index) => {
            const shown = !layout.hidden.includes(id);
            return (
              <div key={id} className="columns-row">
                <label>
                  <input
                    type="checkbox"
                    checked={shown}
                    disabled={shown && lastVisible}
                    onChange={() => onChange(toggleColumn(layout, id))}
                  />
                  {COLUMN_LABELS[id]}
                </label>
                <button
                  type="button"
                  className="columns-move"
                  aria-label={`Move ${COLUMN_LABELS[id]} up`}
                  disabled={index === 0}
                  onClick={() => onChange(moveColumn(layout, id, -1))}
                >
                  <FontAwesomeIcon icon={faArrowUp} />
                </button>
                <button
                  type="button"
                  className="columns-move"
                  aria-label={`Move ${COLUMN_LABELS[id]} down`}
                  disabled={index === layout.order.length - 1}
                  onClick={() => onChange(moveColumn(layout, id, 1))}
                >
                  <FontAwesomeIcon icon={faArrowDown} />
                </button>
              </div>
            );
          })}
          <button type="button" className="sbb-btn sbb-btn--control" onClick={() => onChange(DEFAULT_LAYOUT)}>
            Reset columns
          </button>
        </div>
      )}
    </div>
  );
}
