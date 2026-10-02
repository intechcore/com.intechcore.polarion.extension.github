import { useEffect, useState } from 'react';
import { SearchableSelect } from '@sbb-polarion/react-sbb-polarion';
import type { DuplicateKey, ProjectField, ProjectOption } from '../types';

/** One field value of the created work items. */
export interface FieldRow {
  id: string;
  value: string;
}

/** The form state of one kind of GitHub item: ItemSettings with the field values as an ordered list. */
export interface ItemForm {
  enabled: boolean;
  workItemType: string;
  titleTemplate: string;
  descriptionTemplate: string;
  duplicateKey: DuplicateKey;
  duplicateKeyField: string;
  epicId: string;
  epicLinkRole: string;
  fields: FieldRow[];
}

const DUPLICATE_KEYS = [
  { id: 'HYPERLINK', name: 'A hyperlink of the work item' },
  { id: 'CUSTOM_FIELD', name: 'A custom field' },
];

interface ItemSettingsFormProps {
  /** The prefix of the control ids, and what the labels call the items: `issues` or `discussions`. */
  kind: string;
  value: ItemForm;
  onChange: (value: ItemForm) => void;
  workItemTypes: ProjectOption[];
  linkRoles: ProjectOption[];
  loadFields: (workItemType: string) => Promise<ProjectField[]>;
}

/**
 * The settings of one kind of GitHub item: whether to import it, the work item it becomes, and what
 * the work item carries.
 */
export default function ItemSettingsForm({
  kind,
  value,
  onChange,
  workItemTypes,
  linkRoles,
  loadFields,
}: Readonly<ItemSettingsFormProps>) {
  const [fields, setFields] = useState<ProjectField[]>([]);
  const set = (change: Partial<ItemForm>) => onChange({ ...value, ...change });
  const setRow = (index: number, change: Partial<FieldRow>) =>
    set({ fields: value.fields.map((row, i) => (i === index ? { ...row, ...change } : row)) });

  // The fields belong to a work item type, so they are read again when the type changes.
  useEffect(() => {
    let cancelled = false;
    if (!value.workItemType) {
      setFields([]);
      return;
    }
    loadFields(value.workItemType)
      .then((list) => {
        if (!cancelled) setFields(list);
      })
      .catch(() => {
        if (!cancelled) setFields([]);
      });
    return () => {
      cancelled = true;
    };
  }, [loadFields, value.workItemType]);

  // Polarion names a built-in field by its ID, so the ID is added only where it says something new.
  const option = (field: ProjectField) => ({
    id: field.id,
    name: field.name === field.id ? field.id : `${field.name} (${field.id})`,
  });
  const fieldOptions = fields.map(option);
  const urlKeyOptions = fields.filter((field) => field.urlKey).map(option);

  return (
    <div className="item-settings">
      <div className="item-settings-enabled">
        <input
          id={`${kind}-enabled`}
          type="checkbox"
          checked={value.enabled}
          onChange={(e) => set({ enabled: e.target.checked })}
        />
        <label htmlFor={`${kind}-enabled`}>Create work items from {kind}</label>
      </div>

      {value.enabled && (
        <table className="settings-table">
          <tbody>
            <tr>
              <td>
                <label htmlFor={`${kind}-type`}>Work item type:</label>
              </td>
              <td>
                <SearchableSelect
                  id={`${kind}-type`}
                  value={value.workItemType}
                  onChange={(workItemType) => set({ workItemType })}
                  options={workItemTypes}
                  allowEmpty
                />
              </td>
            </tr>
            <tr>
              <td>
                <label htmlFor={`${kind}-title`}>Title:</label>
              </td>
              <td>
                <input
                  id={`${kind}-title`}
                  type="text"
                  value={value.titleTemplate}
                  onChange={(e) => set({ titleTemplate: e.target.value })}
                />
              </td>
            </tr>
            <tr>
              <td>
                <label htmlFor={`${kind}-description`}>Description (HTML):</label>
              </td>
              <td>
                <textarea
                  id={`${kind}-description`}
                  rows={3}
                  value={value.descriptionTemplate}
                  onChange={(e) => set({ descriptionTemplate: e.target.value })}
                />
              </td>
            </tr>
            <tr>
              <td>
                <label htmlFor={`${kind}-key`}>Keep the GitHub URL in:</label>
              </td>
              <td>
                <SearchableSelect
                  id={`${kind}-key`}
                  value={value.duplicateKey}
                  onChange={(duplicateKey) => set({ duplicateKey: duplicateKey as DuplicateKey })}
                  options={DUPLICATE_KEYS}
                  searchable={false}
                />
              </td>
            </tr>
            {value.duplicateKey === 'CUSTOM_FIELD' && (
              <tr>
                <td>
                  <label htmlFor={`${kind}-key-field`}>Custom field:</label>
                </td>
                <td>
                  <SearchableSelect
                    id={`${kind}-key-field`}
                    value={value.duplicateKeyField}
                    onChange={(duplicateKeyField) => set({ duplicateKeyField })}
                    options={urlKeyOptions}
                    placeholder="A custom field of the type String"
                    allowEmpty
                  />
                </td>
              </tr>
            )}
            <tr>
              <td>
                <label htmlFor={`${kind}-epic`}>Link to the work item:</label>
              </td>
              <td>
                <input
                  id={`${kind}-epic`}
                  type="text"
                  placeholder="The ID of an epic, optional"
                  value={value.epicId}
                  onChange={(e) => set({ epicId: e.target.value })}
                />
              </td>
            </tr>
            <tr>
              <td>
                <label htmlFor={`${kind}-role`}>Link role:</label>
              </td>
              <td>
                <SearchableSelect
                  id={`${kind}-role`}
                  value={value.epicLinkRole}
                  onChange={(epicLinkRole) => set({ epicLinkRole })}
                  options={linkRoles}
                  allowEmpty
                />
              </td>
            </tr>
            <tr>
              <td>Field values:</td>
              <td>
                {value.fields.map((row, index) => (
                  // The rows have no identity of their own, and they never reorder.
                  <div className="field-row" key={index}>
                    <SearchableSelect
                      ariaLabel={`Field ${index + 1} of ${kind}`}
                      value={row.id}
                      onChange={(id) => setRow(index, { id })}
                      options={fieldOptions}
                      allowEmpty
                    />
                    <input
                      type="text"
                      aria-label={`Value ${index + 1} of ${kind}`}
                      value={row.value}
                      onChange={(e) => setRow(index, { value: e.target.value })}
                    />
                    <button
                      type="button"
                      className="sbb-btn sbb-btn--control"
                      onClick={() => set({ fields: value.fields.filter((_, i) => i !== index) })}
                    >
                      Remove
                    </button>
                  </div>
                ))}
                <button
                  type="button"
                  className="sbb-btn sbb-btn--control"
                  onClick={() => set({ fields: [...value.fields, { id: '', value: '' }] })}
                >
                  Add a field value
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      )}
    </div>
  );
}
