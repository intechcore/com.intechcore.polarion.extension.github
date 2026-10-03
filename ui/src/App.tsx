import { BreadcrumbInjector, Toaster } from '@sbb-polarion/react-sbb-polarion';
import { findFeature } from './features';
import About from './pages/About';

/**
 * Top-level feature router. One index.html / bundle; the page is chosen by the `feature` query
 * parameter, which `hivemodule.xml` sets for every administration entry and the GitHub topic. A URL with no (or an
 * unknown) feature falls back to the About page.
 */
export default function App() {
  const feature = new URLSearchParams(window.location.search).get('feature');
  const match = findFeature(feature);
  const Page = match ? match.component : About;

  return (
    // `.app` supplies the page shell (RSP's PageLayout.css) and `standard-admin-page` the --sbb-*
    // control tokens and Polarion-styled controls. The `feature-<id>` class lets one page opt into a
    // layout the others must not get.
    <div className={`app standard-admin-page feature-${match ? match.id : 'about'}`}>
      {/* Fixes the app-header breadcrumb when the page opens as the GitHub topic of a project. */}
      <BreadcrumbInjector marker="github" title="GitHub" icon="/polarion/github-app/ui/images/menu/30x30/_parent.svg" />
      <Toaster />
      <Page />
    </div>
  );
}
