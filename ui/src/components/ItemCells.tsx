import type { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import {
  faArrowsRotate,
  faBan,
  faCircleCheck,
  faCircleDot,
  faCirclePlus,
  faCodePullRequest,
  faComments,
  faLink,
  faShieldHalved,
  faTriangleExclamation,
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { KIND_LABELS, STATUS_LABELS } from '../services/itemFilters';
import type { ImportEntry, ImportStatus, ItemKind } from '../types';

const KIND_ICONS: Record<ItemKind, IconDefinition> = {
  ISSUE: faCircleDot,
  DISCUSSION: faComments,
  PULL_REQUEST: faCodePullRequest,
  ADVISORY: faShieldHalved,
};

const STATUS_ICONS: Record<ImportStatus, IconDefinition> = {
  NEW: faCirclePlus,
  CREATED: faCircleCheck,
  EXISTS: faLink,
  OUTDATED: faArrowsRotate,
  UPDATED: faCircleCheck,
  SKIPPED: faBan,
  FAILED: faTriangleExclamation,
};

/** The item: an icon of its kind, its number as the link to GitHub, its title, and the failed checks of a pull request. */
export function ItemCell({ entry }: Readonly<{ entry: ImportEntry }>) {
  const kind = KIND_LABELS[entry.kind];
  return (
    <div className="item-cell">
      <FontAwesomeIcon icon={KIND_ICONS[entry.kind]} className={`kind-icon kind-${entry.kind}`} title={kind} />
      {entry.url ? (
        <a
          className="item-number"
          href={entry.url}
          target="_blank"
          rel="noopener noreferrer"
          title={`${kind} on GitHub`}
        >
          {itemNumber(entry)}
        </a>
      ) : (
        <span className="item-number">{itemNumber(entry)}</span>
      )}
      <span className="item-title">
        {entry.title}
        {entry.failedChecks && <span className="item-checks">Failed checks: {entry.failedChecks}</span>}
      </span>
    </div>
  );
}

/** The number of an item, or the GHSA ID of a security advisory, which has no number. */
export const itemNumber = (entry: ImportEntry): string => entry.ghsaId ?? `#${entry.number}`;

/** Dark text on a light label and white text on a dark one, as GitHub picks it. */
export function labelTextColor(color: string): string {
  const red = Number.parseInt(color.slice(0, 2), 16);
  const green = Number.parseInt(color.slice(2, 4), 16);
  const blue = Number.parseInt(color.slice(4, 6), 16);
  return (red * 299 + green * 587 + blue * 114) / 1000 > 150 ? '#1f2328' : '#ffffff';
}

/** The labels in the colors GitHub gives them. A label without a color is gray. */
export function LabelsCell({ entry }: Readonly<{ entry: ImportEntry }>) {
  return (
    <div className="labels-cell">
      {(entry.labels ?? []).map((label) => {
        const color = entry.labelColors?.[label];
        return (
          <span
            key={label}
            className="label-chip"
            style={color ? { backgroundColor: `#${color}`, color: labelTextColor(color) } : undefined}
          >
            {label}
          </span>
        );
      })}
    </div>
  );
}

/** What the import does with the item, with an icon, and the reason of a rule or a failure. */
export function StateCell({ entry }: Readonly<{ entry: ImportEntry }>) {
  return (
    <span className={`state state-${entry.status}`}>
      <FontAwesomeIcon icon={STATUS_ICONS[entry.status]} className="state-icon" />
      {STATUS_LABELS[entry.status]}
      {entry.message ? `: ${entry.message}` : ''}
    </span>
  );
}

/** The work item with the icon of its type and a link. An item without a work item shows nothing. */
export function WorkItemCell({ entry, projectId }: Readonly<{ entry: ImportEntry; projectId: string }>) {
  if (!entry.workItemId) {
    return null;
  }
  const typeName = entry.workItemTypeName ?? entry.workItemType;
  return (
    <span className="work-item-cell">
      {entry.workItemTypeIcon && (
        <img className="type-icon" src={entry.workItemTypeIcon} alt={typeName ?? ''} title={typeName ?? undefined} />
      )}
      <a
        href={`/polarion/#/project/${encodeURIComponent(projectId)}/workitem?id=${encodeURIComponent(entry.workItemId)}`}
        target="_top"
      >
        {entry.workItemId}
      </a>
    </span>
  );
}

/** The status of the work item with its icon, as Polarion shows it. */
export function StatusCell({ entry }: Readonly<{ entry: ImportEntry }>) {
  if (!entry.workItemStatus) {
    return null;
  }
  return (
    <span className="work-item-cell">
      {entry.workItemStatusIcon && <img className="type-icon" src={entry.workItemStatusIcon} alt="" />}
      {entry.workItemStatus}
    </span>
  );
}
