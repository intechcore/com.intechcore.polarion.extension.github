import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ConfigurationButtons,
  ConfigurationsPane,
  type ConfigurationsPaneHandle,
  PageLayout,
  RevisionsTable,
  getProjectIdFromScope,
  getScope,
} from '@sbb-polarion/react-sbb-polarion';
import { toast } from 'sonner';
import ErrorNotice from '../components/ErrorNotice';
import type { FieldRow } from '../components/FieldValues';
import ItemSettingsForm, { type ItemForm } from '../components/ItemSettingsForm';
import useSettings from '../services/settings';
import type { ItemSettings, ProjectField, ProjectOption, RepositorySettings, Revision } from '../types';

const DEFAULT_TITLE = '[GitHub] {{ SHORT_NAME }} : {{ TITLE }}';
const DEFAULT_DESCRIPTION = '<a href="{{ URL }}">{{ URL }}</a>';

const EMPTY_ITEM: ItemForm = {
  enabled: false,
  workItemType: '',
  titleTemplate: DEFAULT_TITLE,
  descriptionTemplate: DEFAULT_DESCRIPTION,
  duplicateKey: 'HYPERLINK',
  duplicateKeyField: '',
  epicId: '',
  epicLinkRole: '',
  fields: [],
  rules: [],
};

const toRows = (fields: Record<string, string> | null): FieldRow[] =>
  Object.entries(fields ?? {}).map(([id, value]) => ({ id, value }));

const toFields = (rows: FieldRow[]): Record<string, string> =>
  Object.fromEntries(rows.filter((row) => row.id).map((row) => [row.id, row.value]));

function toForm(settings: ItemSettings | null): ItemForm {
  if (!settings) {
    return EMPTY_ITEM;
  }
  return {
    enabled: settings.enabled,
    workItemType: settings.workItemType ?? '',
    titleTemplate: settings.titleTemplate ?? '',
    descriptionTemplate: settings.descriptionTemplate ?? '',
    duplicateKey: settings.duplicateKey ?? 'HYPERLINK',
    duplicateKeyField: settings.duplicateKeyField ?? '',
    epicId: settings.epicId ?? '',
    epicLinkRole: settings.epicLinkRole ?? '',
    fields: toRows(settings.fields),
    rules: (settings.rules ?? []).map((rule) => ({
      match: rule.match,
      value: rule.value ?? '',
      skip: rule.skip,
      workItemType: rule.workItemType ?? '',
      fields: toRows(rule.fields),
    })),
  };
}

function toSettings(form: ItemForm): ItemSettings {
  return {
    enabled: form.enabled,
    workItemType: form.workItemType || null,
    titleTemplate: form.titleTemplate,
    descriptionTemplate: form.descriptionTemplate,
    duplicateKey: form.duplicateKey,
    duplicateKeyField: form.duplicateKey === 'CUSTOM_FIELD' ? form.duplicateKeyField || null : null,
    epicId: form.epicId.trim() || null,
    epicLinkRole: form.epicId.trim() ? form.epicLinkRole || null : null,
    fields: toFields(form.fields),
    rules: form.rules.map((rule) => ({
      match: rule.match,
      value: rule.value.trim(),
      skip: rule.skip,
      // A rule that leaves items out carries nothing else.
      workItemType: rule.skip ? null : rule.workItemType || null,
      fields: rule.skip ? {} : toFields(rule.fields),
    })),
  };
}

/**
 * The repositories a project imports from. One named setting holds one repository, with one block
 * for its issues and one for its discussions.
 */
export default function Repositories() {
  const settings = useSettings();
  const scope = getScope();
  const projectId = getProjectIdFromScope(scope);
  const paneRef = useRef<ConfigurationsPaneHandle>(null);

  const [workItemTypes, setWorkItemTypes] = useState<ProjectOption[]>([]);
  const [linkRoles, setLinkRoles] = useState<ProjectOption[]>([]);
  const [loadingError, setLoadingError] = useState('');

  const [repository, setRepository] = useState('');
  const [shortName, setShortName] = useState('');
  const [issues, setIssues] = useState<ItemForm>(EMPTY_ITEM);
  const [discussions, setDiscussions] = useState<ItemForm>(EMPTY_ITEM);

  const [selected, setSelected] = useState<string | null>(null);
  const [editingName, setEditingName] = useState(false);
  const [showRevisions, setShowRevisions] = useState(false);
  const [revisionsToken, setRevisionsToken] = useState(0);

  useEffect(() => {
    if (!projectId) {
      return;
    }
    let cancelled = false;
    Promise.all([settings.loadWorkItemTypes(projectId), settings.loadLinkRoles(projectId)])
      .then(([types, roles]) => {
        if (cancelled) return;
        setWorkItemTypes(types);
        setLinkRoles(roles);
      })
      .catch((e: Error) => {
        if (!cancelled) setLoadingError(e.message);
      });
    return () => {
      cancelled = true;
    };
  }, [settings, projectId]);

  const applySettings = useCallback((model: RepositorySettings) => {
    setRepository(model.repository ?? '');
    setShortName(model.shortName ?? '');
    setIssues(toForm(model.issues));
    setDiscussions(toForm(model.discussions));
  }, []);

  const handleSelectedChange = useCallback((name: string | null) => {
    setSelected(name);
    setRevisionsToken((token) => token + 1);
  }, []);

  // Several controls of the page ask for the fields of the same work item type: one request serves them all.
  const fieldRequests = useRef(new Map<string, Promise<ProjectField[]>>());
  const loadFields = useCallback(
    (workItemType: string) => {
      const key = `${projectId}/${workItemType}`;
      let request = fieldRequests.current.get(key);
      if (!request) {
        request = settings.loadFields(projectId, workItemType);
        fieldRequests.current.set(key, request);
        // A failed request is asked again the next time.
        request.catch(() => fieldRequests.current.delete(key));
      }
      return request;
    },
    [settings, projectId],
  );

  // The handlers take the name: the form they belong to exists only while a setting is selected.
  const handleSave = async (name: string) => {
    try {
      await settings.saveContent(name, scope, {
        repository: repository.trim(),
        shortName: shortName.trim(),
        issues: toSettings(issues),
        discussions: toSettings(discussions),
      });
      await paneRef.current?.reloadNames(name);
      setRevisionsToken((token) => token + 1);
      toast.success('Data successfully saved.');
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  const handleCancel = async (name: string) => {
    try {
      applySettings(await settings.loadContent(name, scope));
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  const handleRevert = async (name: string, revision: Revision) => {
    try {
      applySettings(await settings.loadContent(name, scope, revision.name));
      toast.success(`Reverted to revision ${revision.name}. Save the setting to keep it.`);
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  if (!projectId) {
    return (
      <PageLayout title="Repositories">
        <ErrorNotice>Repositories are configured in a project. Open this page from a project.</ErrorNotice>
      </PageLayout>
    );
  }

  return (
    <PageLayout title="Repositories">
      {loadingError && <ErrorNotice>The project options did not load: {loadingError}</ErrorNotice>}

      <ConfigurationsPane
        ref={paneRef}
        scope={scope}
        service={settings}
        cookieKey="selected-configuration-repositories"
        label="repository"
        onContentLoaded={applySettings}
        onSelectedChange={handleSelectedChange}
        onEditingNameChange={setEditingName}
      />

      {selected && (
        <div className={editingName ? 'repository-form dimmed' : 'repository-form'}>
          <h2>Repository</h2>
          <table className="settings-table">
            <tbody>
              <tr>
                <td>
                  <label htmlFor="repository">GitHub repository:</label>
                </td>
                <td>
                  <input
                    id="repository"
                    type="text"
                    placeholder="owner/name"
                    value={repository}
                    onChange={(e) => setRepository(e.target.value)}
                  />
                </td>
              </tr>
              <tr>
                <td>
                  <label htmlFor="short-name">Short name:</label>
                </td>
                <td>
                  <input id="short-name" type="text" value={shortName} onChange={(e) => setShortName(e.target.value)} />
                </td>
              </tr>
            </tbody>
          </table>

          <h2>Issues</h2>
          <ItemSettingsForm
            kind="issues"
            value={issues}
            onChange={setIssues}
            workItemTypes={workItemTypes}
            linkRoles={linkRoles}
            loadFields={loadFields}
          />

          <h2>Discussions</h2>
          <ItemSettingsForm
            kind="discussions"
            value={discussions}
            onChange={setDiscussions}
            workItemTypes={workItemTypes}
            linkRoles={linkRoles}
            loadFields={loadFields}
          />

          <ConfigurationButtons
            onSave={() => void handleSave(selected)}
            onCancel={() => void handleCancel(selected)}
            onToggleRevisions={() => setShowRevisions((shown) => !shown)}
            revisionsShown={showRevisions}
          />

          {showRevisions && (
            <RevisionsTable
              name={selected}
              scope={scope}
              reloadToken={revisionsToken}
              loadRevisions={settings.loadRevisions}
              onRevert={(revision) => void handleRevert(selected, revision)}
            />
          )}

          <div className="quick-help">
            <h3>Templates</h3>
            <p>
              The title and the description take these placeholders: <code>{'{{ SHORT_NAME }}'}</code>,{' '}
              <code>{'{{ REPOSITORY }}'}</code>, <code>{'{{ NUMBER }}'}</code>, <code>{'{{ TITLE }}'}</code>,{' '}
              <code>{'{{ AUTHOR }}'}</code>, <code>{'{{ URL }}'}</code>, <code>{'{{ LABELS }}'}</code>,{' '}
              <code>{'{{ TYPE }}'}</code>, <code>{'{{ CATEGORY }}'}</code>. The description also takes{' '}
              <code>{'{{ BODY }}'}</code>. The description is HTML, and every value is escaped. A name ignores case,
              underscores and the spaces inside the braces.
            </p>
            <h3>Rules</h3>
            <p>
              A rule applies to the items that carry a label, an issue type or a discussion category. It gives them
              another work item type and field values, or leaves them out of the import. The first matching rule
              applies. An item that matches no rule gets the work item type and the field values above. GitHub has issue
              types only in organizations that use them.
            </p>
          </div>
        </div>
      )}
    </PageLayout>
  );
}
