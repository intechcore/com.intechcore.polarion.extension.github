import { useEffect, useState } from 'react';
import { faPlus, faTrashCan } from '@fortawesome/free-solid-svg-icons';
import { SearchableSelect } from '@sbb-polarion/react-sbb-polarion';
import type { ProjectField } from '../types';
import ButtonIcon from './ButtonIcon';

/** One field value of the created work items. */
export interface FieldRow {
  id: string;
  value: string;
}

// Polarion names a built-in field by its ID, so the ID is added only where it says something new.
export const fieldOption = (field: ProjectField) => ({
  id: field.id,
  name: field.name === field.id ? field.id : `${field.name} (${field.id})`,
});

/** Reads the fields of a work item type, again whenever the type changes. Without a type there are none. */
export function useFields(
  workItemType: string,
  loadFields: (workItemType: string) => Promise<ProjectField[]>,
): ProjectField[] {
  const [fields, setFields] = useState<ProjectField[]>([]);
  useEffect(() => {
    let cancelled = false;
    if (!workItemType) {
      setFields([]);
      return;
    }
    loadFields(workItemType)
      .then((list) => {
        if (!cancelled) setFields(list);
      })
      .catch(() => {
        if (!cancelled) setFields([]);
      });
    return () => {
      cancelled = true;
    };
  }, [loadFields, workItemType]);
  return fields;
}

/**
 * The value of one field: a choice of its options for an enumeration, several for one that takes
 * several values, and text for any other field. Several values travel as "a,b", as Polarion reads them.
 */
function FieldValue({
  field,
  label,
  value,
  onChange,
}: Readonly<{ field: ProjectField | undefined; label: string; value: string; onChange: (value: string) => void }>) {
  if (!field?.options) {
    return <input type="text" aria-label={label} value={value} onChange={(e) => onChange(e.target.value)} />;
  }
  const choices = field.options.map((option) => ({
    id: option.id,
    name: option.name,
    iconURL: option.iconUrl ?? undefined,
  }));
  if (field.multi) {
    const values = value
      .split(',')
      .map((part) => part.trim())
      .filter((part) => part);
    return (
      <SearchableSelect
        multiple
        ariaLabel={label}
        value={values}
        onChange={(chosen) => onChange(chosen.join(','))}
        options={choices}
        placeholder="None"
      />
    );
  }
  return <SearchableSelect ariaLabel={label} value={value} onChange={onChange} options={choices} allowEmpty />;
}

interface FieldValuesProps {
  /** Whose field values these are, for the labels of the controls: `issues`, or `rule 1 of issues`. */
  owner: string;
  workItemType: string;
  rows: FieldRow[];
  onChange: (rows: FieldRow[]) => void;
  loadFields: (workItemType: string) => Promise<ProjectField[]>;
}

/**
 * The field values every created work item gets: one row per field, chosen from the fields of the
 * work item type.
 */
export default function FieldValues({ owner, workItemType, rows, onChange, loadFields }: Readonly<FieldValuesProps>) {
  const fields = useFields(workItemType, loadFields);
  const options = fields.map(fieldOption);
  const setRow = (index: number, change: Partial<FieldRow>) =>
    onChange(rows.map((row, i) => (i === index ? { ...row, ...change } : row)));

  return (
    <>
      {rows.map((row, index) => (
        // The rows have no identity of their own, and they never reorder.
        <div className="field-row" key={index}>
          <SearchableSelect
            ariaLabel={`Field ${index + 1} of ${owner}`}
            value={row.id}
            onChange={(id) => setRow(index, { id })}
            options={options}
            allowEmpty
          />
          <FieldValue
            field={fields.find((field) => field.id === row.id)}
            label={`Value ${index + 1} of ${owner}`}
            value={row.value}
            onChange={(value) => setRow(index, { value })}
          />
          <button
            type="button"
            className="sbb-btn sbb-btn--control"
            onClick={() => onChange(rows.filter((_, i) => i !== index))}
          >
            <ButtonIcon icon={faTrashCan} />
            Remove
          </button>
        </div>
      ))}
      <button
        type="button"
        className="sbb-btn sbb-btn--control"
        onClick={() => onChange([...rows, { id: '', value: '' }])}
      >
        <ButtonIcon icon={faPlus} />
        Add a field value
      </button>
    </>
  );
}
