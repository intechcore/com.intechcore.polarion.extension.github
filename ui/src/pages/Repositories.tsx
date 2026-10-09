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
import CopySetting from '../components/CopySetting';
import ErrorNotice from '../components/ErrorNotice';
import type { FieldRow } from '../components/FieldValues';
import ItemSettingsForm, { type ItemForm } from '../components/ItemSettingsForm';
import NotificationsForm from '../components/NotificationsForm';
import useSettings from '../services/settings';
import type {
  ItemSettings,
  NotificationSettings,
  ProjectField,
  ProjectOption,
  RepositorySettings,
  Revision,
} from '../types';

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

const DEFAULT_AUTHORS = 'renovate[bot]';
const NO_NOTIFICATIONS: NotificationSettings = {
  users: [],
  issues: false,
  discussions: false,
  pullRequests: false,
  advisories: false,
};
// Under embargo an advisory is secret: by default the work item names it, and the link leads to it.
const EMPTY_ADVISORIES: ItemForm = {
  ...EMPTY_ITEM,
  titleTemplate: '[GitHub] {{ SHORT_NAME }} : {{ GHSA }} ({{ SEVERITY }})',
};
const EMPTY_PULL_REQUESTS: ItemForm = {
  ...EMPTY_ITEM,
  titleTemplate: '[GitHub] {{ SHORT_NAME }} : Fix the failed checks of {{ TITLE }}',
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
 * for its issues, one for its discussions and one for its pull requests with failed checks.
 */
export default function Repositories() {
  const settings = useSettings();
  const scope = getScope();
  const projectId = getProjectIdFromScope(scope);
  const paneRef = useRef<ConfigurationsPaneHandle>(null);

  const [workItemTypes, setWorkItemTypes] = useState<ProjectOption[]>([]);
  const [linkRoles, setLinkRoles] = useState<ProjectOption[]>([]);
  const [users, setUsers] = useState<ProjectOption[]>([]);
  const [loadingError, setLoadingError] = useState('');

  const [repository, setRepository] = useState('');
  const [shortName, setShortName] = useState('');
  const [issues, setIssues] = useState<ItemForm>(EMPTY_ITEM);
  const [discussions, setDiscussions] = useState<ItemForm>(EMPTY_ITEM);
  const [pullRequests, setPullRequests] = useState<ItemForm>(EMPTY_PULL_REQUESTS);
  const [authors, setAuthors] = useState(DEFAULT_AUTHORS);
  const [advisories, setAdvisories] = useState<ItemForm>(EMPTY_ADVISORIES);
  const [notifications, setNotifications] = useState<NotificationSettings>(NO_NOTIFICATIONS);

  const [selected, setSelected] = useState<string | null>(null);
  const [editingName, setEditingName] = useState(false);
  const [copying, setCopying] = useState(false);
  const [showRevisions, setShowRevisions] = useState(false);
  const [revisionsToken, setRevisionsToken] = useState(0);

  useEffect(() => {
    if (!projectId) {
      return;
    }
    let cancelled = false;
    Promise.all([settings.loadWorkItemTypes(projectId), settings.loadLinkRoles(projectId), settings.loadUsers()])
      .then(([types, roles, polarionUsers]) => {
        if (cancelled) return;
        setWorkItemTypes(types);
        setLinkRoles(roles);
        setUsers(polarionUsers);
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
    setPullRequests(model.pullRequests ? toForm(model.pullRequests) : EMPTY_PULL_REQUESTS);
    setAuthors(model.pullRequestAuthors ?? DEFAULT_AUTHORS);
    setAdvisories(model.advisories ? toForm(model.advisories) : EMPTY_ADVISORIES);
    setNotifications({ ...NO_NOTIFICATIONS, ...model.notifications, users: model.notifications?.users ?? [] });
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
        pullRequests: toSettings(pullRequests),
        pullRequestAuthors: authors.trim(),
        advisories: toSettings(advisories),
        notifications,
      });
      await paneRef.current?.reloadNames(name);
      setRevisionsToken((token) => token + 1);
      toast.success('Data successfully saved.');
    } catch (e) {
      toast.error((e as Error).message);
    }
  };

  // The copy takes the saved setting, not the edits on the form, and opens it.
  const handleCopy = async (name: string, newName: string) => {
    const names = await settings.loadConfigurationNames(scope);
    if (names.some((setting) => setting.scope === scope && setting.name === newName)) {
      throw new Error('A repository with this name already exists');
    }
    await settings.saveContent(newName, scope, await settings.loadContent(name, scope));
    await paneRef.current?.reloadNames(newName);
    toast.success(`Copied ${name} as ${newName}.`);
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
      {selected && !editingName && <CopySetting name={selected} onCopy={handleCopy} onEditingChange={setCopying} />}

      {selected && (
        <div className={editingName || copying ? 'repository-form dimmed' : 'repository-form'}>
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

          <h2>Pull requests</h2>
          <p className="block-hint">
            Lists the open pull requests of the watched authors whose checks failed, for example an update Renovate
            could not merge. Each such pull request costs one GitHub request per five minutes for its checks.
          </p>
          <table className="settings-table">
            <tbody>
              <tr>
                <td>
                  <label htmlFor="pull-request-authors">Watched authors:</label>
                </td>
                <td>
                  <input
                    id="pull-request-authors"
                    type="text"
                    placeholder="renovate[bot], dependabot[bot]"
                    value={authors}
                    onChange={(e) => setAuthors(e.target.value)}
                  />
                </td>
              </tr>
            </tbody>
          </table>
          <ItemSettingsForm
            kind="pull requests"
            value={pullRequests}
            onChange={setPullRequests}
            workItemTypes={workItemTypes}
            linkRoles={linkRoles}
            loadFields={loadFields}
          />

          <h2>Security advisories</h2>
          <p className="block-hint">
            Lists the security advisories of the repository in triage, drafted or published. GitHub shows those in
            triage and the drafts only to a GitHub token of an administrator or security manager of the repository, with
            the permission to read its advisories.{' '}
            <strong>
              Until it is published an advisory is under embargo: everyone who reads the project sees what the templates
              put into the work item.
            </strong>
          </p>
          <ItemSettingsForm
            kind="security advisories"
            value={advisories}
            onChange={setAdvisories}
            workItemTypes={workItemTypes}
            linkRoles={linkRoles}
            loadFields={loadFields}
          />

          <h2>Notifications</h2>
          <p className="block-hint">
            The job <code>github_watch.job</code> of the Polarion scheduler mails the recipients the items that are new
            since its last check, every 15 minutes by default. New issues and discussions are the ones without a work
            item.
          </p>
          <NotificationsForm value={notifications} onChange={setNotifications} users={users} />

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
              <code>{'{{ TYPE }}'}</code>, <code>{'{{ CATEGORY }}'}</code>, <code>{'{{ CHECKS }}'}</code> (the failed
              checks of a pull request), <code>{'{{ GHSA }}'}</code>, <code>{'{{ SEVERITY }}'}</code>,{' '}
              <code>{'{{ CVSS }}'}</code>, <code>{'{{ CWE }}'}</code> (of a security advisory). The description also
              takes <code>{'{{ BODY }}'}</code>. The description is HTML. <code>{'{{ BODY }}'}</code> is the rich text
              GitHub shows for the Markdown of the item, so place it outside a paragraph. Every other value is escaped.
              A name ignores case, underscores and the spaces inside the braces.
            </p>
            <h3>Rules</h3>
            <p>
              A rule applies to the items that carry a label, an issue type or a discussion category, or to the items of
              an author. It gives them another work item type and field values, or leaves them out of the import. The
              first matching rule applies. An item that matches no rule gets the work item type and the field values
              above. GitHub has issue types only in organizations that use them.
            </p>
          </div>
        </div>
      )}
    </PageLayout>
  );
}
