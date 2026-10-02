import { useCallback, useMemo } from 'react';
import type { ImportResult, ProjectField, ProjectOption, RepositorySettings, Revision, SettingName } from '../types';
import useRemote from './useRemote';

/** The named-settings feature id (the backend `{feature}` path, settings.RepositorySettings). */
const FEATURE = 'repositories';

/** The message of a failed response: the `{message}` JSON of generic, else the text, else the status. */
export async function errorMessage(response: Response): Promise<string> {
  const text = await response.text().catch(() => '');
  if (text) {
    try {
      const parsed = JSON.parse(text);
      if (parsed?.message) return parsed.message;
    } catch {
      return text;
    }
  }
  return `HTTP ${response.status}`;
}

export async function jsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
  return (await response.json()) as T;
}

async function okOrThrow(response: Response): Promise<void> {
  if (!response.ok) {
    throw new Error(await errorMessage(response));
  }
}

const settingPath = (name: string, scope: string, suffix = ''): string =>
  `/settings/${FEATURE}/names/${encodeURIComponent(name)}${suffix}?scope=${encodeURIComponent(scope)}`;

const projectPath = (projectId: string, suffix: string): string =>
  `/projects/${encodeURIComponent(projectId)}${suffix}`;

/**
 * REST helpers for the Repositories page: the named-settings endpoints of generic, and the options
 * a project offers. Built on `useRemote`, so it uses `/internal` in Polarion and `/api` in `vite dev`.
 */
export default function useSettings() {
  const { sendRequest } = useRemote();

  const loadConfigurationNames = useCallback(
    (scope: string): Promise<SettingName[]> =>
      sendRequest({ method: 'GET', url: `/settings/${FEATURE}/names?scope=${encodeURIComponent(scope)}` }).then((r) =>
        jsonOrThrow<SettingName[]>(r),
      ),
    [sendRequest],
  );

  const loadContent = useCallback(
    (name: string, scope: string, revision?: string): Promise<RepositorySettings> => {
      let url = settingPath(name, scope, '/content');
      if (revision) {
        url += `&revision=${encodeURIComponent(revision)}`;
      }
      return sendRequest({ method: 'GET', url }).then((r) => jsonOrThrow<RepositorySettings>(r));
    },
    [sendRequest],
  );

  const saveContent = useCallback(
    (name: string, scope: string, content: RepositorySettings): Promise<void> =>
      sendRequest({
        method: 'PUT',
        url: settingPath(name, scope, '/content'),
        contentType: 'application/json',
        body: JSON.stringify(content),
      }).then(okOrThrow),
    [sendRequest],
  );

  /** Creates a named setting. Without a body the backend stores its default values, a draft. */
  const createConfiguration = useCallback(
    (name: string, scope: string): Promise<void> =>
      sendRequest({ method: 'PUT', url: settingPath(name, scope, '/content'), contentType: 'application/json' }).then(
        okOrThrow,
      ),
    [sendRequest],
  );

  const renameConfiguration = useCallback(
    (name: string, scope: string, newName: string): Promise<void> =>
      sendRequest({
        method: 'POST',
        url: settingPath(name, scope),
        contentType: 'application/json',
        body: newName,
      }).then(okOrThrow),
    [sendRequest],
  );

  const deleteConfiguration = useCallback(
    (name: string, scope: string): Promise<void> =>
      sendRequest({ method: 'DELETE', url: settingPath(name, scope) }).then(okOrThrow),
    [sendRequest],
  );

  const loadRevisions = useCallback(
    (name: string, scope: string): Promise<Revision[]> =>
      sendRequest({ method: 'GET', url: settingPath(name, scope, '/revisions') }).then((r) =>
        jsonOrThrow<Revision[]>(r),
      ),
    [sendRequest],
  );

  const loadWorkItemTypes = useCallback(
    (projectId: string): Promise<ProjectOption[]> =>
      sendRequest({ method: 'GET', url: projectPath(projectId, '/workitem-types') }).then((r) =>
        jsonOrThrow<ProjectOption[]>(r),
      ),
    [sendRequest],
  );

  const loadLinkRoles = useCallback(
    (projectId: string): Promise<ProjectOption[]> =>
      sendRequest({ method: 'GET', url: projectPath(projectId, '/link-roles') }).then((r) =>
        jsonOrThrow<ProjectOption[]>(r),
      ),
    [sendRequest],
  );

  const loadFields = useCallback(
    (projectId: string, workItemType: string): Promise<ProjectField[]> =>
      sendRequest({
        method: 'GET',
        url: projectPath(projectId, `/workitem-types/${encodeURIComponent(workItemType)}/fields`),
      }).then((r) => jsonOrThrow<ProjectField[]>(r)),
    [sendRequest],
  );

  /** Runs the import of one repository setting. A dry run only reports what the import would do. */
  const runImport = useCallback(
    (projectId: string, name: string, dryRun: boolean, urls?: string[]): Promise<ImportResult> =>
      sendRequest({
        method: 'POST',
        url: projectPath(projectId, `/repositories/${encodeURIComponent(name)}/import?dryRun=${dryRun}`),
        contentType: 'application/json',
        body: JSON.stringify(urls ? { urls } : {}),
      }).then((r) => jsonOrThrow<ImportResult>(r)),
    [sendRequest],
  );

  return useMemo(
    () => ({
      loadConfigurationNames,
      loadContent,
      saveContent,
      createConfiguration,
      renameConfiguration,
      deleteConfiguration,
      loadRevisions,
      loadWorkItemTypes,
      loadLinkRoles,
      loadFields,
      runImport,
    }),
    [
      loadConfigurationNames,
      loadContent,
      saveContent,
      createConfiguration,
      renameConfiguration,
      deleteConfiguration,
      loadRevisions,
      loadWorkItemTypes,
      loadLinkRoles,
      loadFields,
      runImport,
    ],
  );
}
