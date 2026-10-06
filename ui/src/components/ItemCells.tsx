import type { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import {
  faArrowsRotate,
  faBan,
  faCircleCheck,
  faCircleDot,
  faCirclePlus,
  faComments,
  faLink,
  faTriangleExclamation,
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { KIND_LABELS, STATUS_LABELS } from '../services/itemFilters';
import type { ImportEntry, ImportStatus, ItemKind } from '../types';

const KIND_ICONS: Record<ItemKind, IconDefinition> = { ISSUE: faCircleDot, DISCUSSION: faComments };

const STATUS_ICONS: Record<ImportStatus, IconDefinition> = {
  NEW: faCirclePlus,
  CREATED: faCircleCheck,
  EXISTS: faLink,
  OUTDATED: faArrowsRotate,
  UPDATED: faCircleCheck,
  SKIPPED: faBan,
  FAILED: faTriangleExclamation,
};

/** The item: an icon of its kind, its number as the link to GitHub, and its title. */
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
          #{entry.number}
        </a>
      ) : (
        <span className="item-number">#{entry.number}</span>
      )}
      <span className="item-title">{entry.title}</span>
    </div>
  );
}

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

/**
 * The work item with the icon of its type, or for a new item the icon and the name of the type the
 * import would create.
 */
export function WorkItemCell({ entry, projectId }: Readonly<{ entry: ImportEntry; projectId: string }>) {
  const typeName = entry.workItemTypeName ?? entry.workItemType;
  const icon = entry.workItemTypeIcon ? (
    <img className="type-icon" src={entry.workItemTypeIcon} alt={typeName ?? ''} title={typeName ?? undefined} />
  ) : null;
  if (entry.workItemId) {
    return (
      <span className="work-item-cell">
        {icon}
        <a
          href={`/polarion/#/project/${encodeURIComponent(projectId)}/workitem?id=${encodeURIComponent(entry.workItemId)}`}
          target="_top"
        >
          {entry.workItemId}
        </a>
      </span>
    );
  }
  if (!typeName) {
    return null;
  }
  return (
    <span className="work-item-cell work-item-planned" title="The type of the work item the import would create">
      {icon}
      {typeName}
    </span>
  );
}
