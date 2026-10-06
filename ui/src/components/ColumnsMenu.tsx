import { useState } from 'react';
import { faArrowDown, faArrowUp } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  COLUMN_LABELS,
  type ColumnLayout,
  DEFAULT_LAYOUT,
  moveColumn,
  toggleColumn,
  visibleColumns,
} from '../services/columns';

/** A button that opens the list of the table columns: show or hide each one, and move it. */
export default function ColumnsMenu({
  layout,
  onChange,
}: Readonly<{ layout: ColumnLayout; onChange: (layout: ColumnLayout) => void }>) {
  const [open, setOpen] = useState(false);
  const lastVisible = visibleColumns(layout).length <= 1;

  return (
    <div className="columns-menu">
      <button
        type="button"
        className="sbb-btn sbb-btn--control"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        Columns
      </button>
      {open && (
        <div className="columns-panel" role="group" aria-label="Columns">
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
