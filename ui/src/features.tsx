import type { ComponentType } from 'react';
import About from './pages/About';

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
    id: 'about',
    label: 'About',
    description: 'Extension version and general information.',
    component: About,
  },
];

export function findFeature(id: string | null): Feature | undefined {
  return FEATURES.find((f) => f.id === id);
}
