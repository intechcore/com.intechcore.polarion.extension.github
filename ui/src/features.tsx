import type { ComponentType } from 'react';
import About from './pages/About';
import Items from './pages/Items';
import Repositories from './pages/Repositories';
import UserMapping from './pages/UserMapping';

/**
 * A single navigable page of the app. The `id` is what appears in the URL as `?feature=<id>`: the
 * administration entries in `hivemodule.xml` open these pages. Keep the ids stable - they live
 * outside this app.
 */
export interface Feature {
  id: string;
  label: string;
  description: string;
  component: ComponentType;
}

export const FEATURES: Feature[] = [
  {
    id: 'repositories',
    label: 'Repositories',
    description: 'The GitHub repositories a project imports from.',
    component: Repositories,
  },
  {
    id: 'items',
    label: 'Issues and discussions',
    description: 'The open GitHub items of all repositories of a project. A user creates work items from them.',
    component: Items,
  },
  {
    id: 'user-mapping',
    label: 'User mapping',
    description: 'The Polarion users of GitHub logins. The import assigns work items to them.',
    component: UserMapping,
  },
  {
    id: 'about',
    label: 'About',
    description: 'Extension version and general information.',
    component: About,
  },
];

export function findFeature(id: string | null): Feature | undefined {
  return FEATURES.find((f) => f.id === id);
}
