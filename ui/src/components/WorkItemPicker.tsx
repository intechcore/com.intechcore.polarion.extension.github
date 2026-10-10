import { type KeyboardEvent, useEffect, useState } from 'react';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import type { WorkItemRef } from '../types';

// A search waits for a pause in typing, so it does not ask the server for every letter.
const SEARCH_DELAY = 250;

interface Props {
  /** The id of the control, for its label. */
  id: string;
  /** The ID of the chosen work item, or empty. */
  value: string;
  onChange: (value: string) => void;
  /** Finds work items by the start of their ID or the words of their title, or one by its exact ID. */
  find: (query: string, id?: string) => Promise<WorkItemRef[]>;
  placeholder?: string;
}

/** The icon of the type, the ID and the title: the way Polarion names a work item. */
function WorkItemName({ item }: Readonly<{ item: WorkItemRef }>) {
  return (
    <>
      {item.typeIcon && <img className="wip-icon" src={item.typeIcon} alt={item.typeName ?? ''} />}
      <span className="wip-id">{item.id}</span>
      {item.title && <span className="wip-title">{item.title}</span>}
    </>
  );
}

/**
 * Chooses a work item of the project. It shows the chosen one with the icon of its type and its title,
 * and finds another by its ID or the words of its title. Enter takes typed text as an ID when the
 * search finds nothing, so an ID the search cannot see still goes in.
 */
export default function WorkItemPicker({ id, value, onChange, find, placeholder }: Readonly<Props>) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<WorkItemRef[]>([]);
  const [active, setActive] = useState(0);
  // The chosen work item as the server knows it; null while unknown or when the server has none.
  const [chosen, setChosen] = useState<WorkItemRef | null>(null);
  const [missing, setMissing] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setChosen(null);
    setMissing(false);
    if (value) {
      find('', value)
        .then((found) => {
          if (cancelled) return;
          setChosen(found[0] ?? null);
          setMissing(found.length === 0);
        })
        .catch(() => {
          if (!cancelled) setMissing(true);
        });
    }
    return () => {
      cancelled = true;
    };
  }, [find, value]);

  useEffect(() => {
    if (!open || !query.trim()) {
      setResults([]);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(() => {
      find(query)
        .then((found) => {
          if (cancelled) return;
          setResults(found);
          setActive(0);
        })
        .catch(() => {
          if (!cancelled) setResults([]);
        });
    }, SEARCH_DELAY);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [find, open, query]);

  const close = () => {
    setOpen(false);
    setQuery('');
  };

  const choose = (workItemId: string) => {
    onChange(workItemId);
    close();
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((index) => Math.min(index + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((index) => Math.max(index - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (results[active]) {
        choose(results[active].id);
      } else if (query.trim()) {
        choose(query.trim());
      }
    } else if (e.key === 'Escape') {
      close();
    }
  };

  if (!open) {
    return (
      <div className="work-item-picker">
        <button type="button" id={id} className="wip-display" onClick={() => setOpen(true)}>
          {chosen && <WorkItemName item={chosen} />}
          {!chosen && value && (
            <>
              <span className="wip-id">{value}</span>
              {missing && <span className="wip-missing">not found</span>}
            </>
          )}
          {!value && <span className="wip-placeholder">{placeholder}</span>}
        </button>
        {value && (
          <button type="button" className="wip-clear" aria-label="Remove the work item" onClick={() => onChange('')}>
            <FontAwesomeIcon icon={faXmark} />
          </button>
        )}
      </div>
    );
  }
  return (
    <div className="work-item-picker">
      <input
        id={id}
        type="text"
        role="combobox"
        aria-expanded={results.length > 0}
        aria-controls={`${id}-results`}
        placeholder="Search by ID or title"
        autoFocus
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={onKeyDown}
        onBlur={close}
      />
      {results.length > 0 && (
        <ul className="wip-results" id={`${id}-results`} role="listbox">
          {results.map((item, index) => (
            <li
              key={item.id}
              role="option"
              aria-selected={index === active}
              className={index === active ? 'active' : undefined}
              // mousedown, not click: the input would lose its focus first and close the list.
              onMouseDown={(e) => {
                e.preventDefault();
                choose(item.id);
              }}
            >
              <WorkItemName item={item} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
