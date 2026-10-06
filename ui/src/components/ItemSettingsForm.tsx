import { SearchableSelect } from '@sbb-polarion/react-sbb-polarion';
import type { DuplicateKey, ProjectField, ProjectOption } from '../types';
import FieldValues, { type FieldRow, fieldOption, useFields } from './FieldValues';
import RulesEditor, { type RuleForm } from './RulesEditor';

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
  rules: RuleForm[];
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
  const set = (change: Partial<ItemForm>) => onChange({ ...value, ...change });
  // The kind names the block in the labels; the element IDs take it without spaces.
  const id = kind.replace(/\s+/g, '-');
  // Only the custom fields of the type String can keep the URL of a GitHub item.
  const urlKeyOptions = useFields(value.workItemType, loadFields)
    .filter((field) => field.urlKey)
    .map(fieldOption);

  return (
    <div className="item-settings">
      <div className="item-settings-enabled">
        <input
          id={`${id}-enabled`}
          type="checkbox"
          checked={value.enabled}
          onChange={(e) => set({ enabled: e.target.checked })}
        />
        <label htmlFor={`${id}-enabled`}>Create work items from {kind}</label>
      </div>

      {value.enabled && (
        <table className="settings-table">
          <tbody>
            <tr>
              <td>
                <label htmlFor={`${id}-type`}>Work item type:</label>
              </td>
              <td>
                <SearchableSelect
                  id={`${id}-type`}
                  value={value.workItemType}
                  onChange={(workItemType) => set({ workItemType })}
                  options={workItemTypes}
                  allowEmpty
                />
              </td>
            </tr>
            <tr>
              <td>
                <label htmlFor={`${id}-title`}>Title:</label>
              </td>
              <td>
                <input
                  id={`${id}-title`}
                  type="text"
                  value={value.titleTemplate}
                  onChange={(e) => set({ titleTemplate: e.target.value })}
                />
              </td>
            </tr>
            <tr>
              <td>
                <label htmlFor={`${id}-description`}>Description (HTML):</label>
              </td>
              <td>
                <textarea
                  id={`${id}-description`}
                  rows={3}
                  value={value.descriptionTemplate}
                  onChange={(e) => set({ descriptionTemplate: e.target.value })}
                />
              </td>
            </tr>
            <tr>
              <td>
                <label htmlFor={`${id}-key`}>Keep the GitHub URL in:</label>
              </td>
              <td>
                <SearchableSelect
                  id={`${id}-key`}
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
                  <label htmlFor={`${id}-key-field`}>Custom field:</label>
                </td>
                <td>
                  <SearchableSelect
                    id={`${id}-key-field`}
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
                <label htmlFor={`${id}-epic`}>Link to the work item:</label>
              </td>
              <td>
                <input
                  id={`${id}-epic`}
                  type="text"
                  placeholder="The ID of an epic, optional"
                  value={value.epicId}
                  onChange={(e) => set({ epicId: e.target.value })}
                />
              </td>
            </tr>
            <tr>
              <td>
                <label htmlFor={`${id}-role`}>Link role:</label>
              </td>
              <td>
                <SearchableSelect
                  id={`${id}-role`}
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
                <FieldValues
                  owner={kind}
                  workItemType={value.workItemType}
                  rows={value.fields}
                  onChange={(fields) => set({ fields })}
                  loadFields={loadFields}
                />
              </td>
            </tr>
            <tr>
              <td>Rules:</td>
              <td>
                <RulesEditor
                  kind={kind}
                  rules={value.rules}
                  onChange={(rules) => set({ rules })}
                  workItemTypes={workItemTypes}
                  loadFields={loadFields}
                />
              </td>
            </tr>
          </tbody>
        </table>
      )}
    </div>
  );
}
