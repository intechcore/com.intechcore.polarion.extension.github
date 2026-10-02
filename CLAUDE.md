# CLAUDE.md

Guidance for working in this repository.

## What this is

`com.intechcore.polarion.extension.github` - a Polarion ALM extension that creates work items from
the open issues and discussions of GitHub repositories, by a scheduled job or by hand. It builds on
the SBB `ch.sbb.polarion.extension.generic` framework (parent POM) and targets
**Polarion 2606 / Tomcat 11 / Jakarta EE 11**. The repository follows the layout of
`com.intechcore.polarion.extension.timesheet`.

Status: in development. The About page, the GitHub client, the repository settings and the import
with its REST endpoint exist. The job and the administration pages are not written yet.

## Build & verify

```bash
mvn clean package            # do NOT pass -s .mvn/settings.xml locally, it is for CI only
mvn clean install -P local-install-into-polarion   # needs POLARION_HOME, then restart Polarion
```

- The React UI build is **inherited from the generic parent** (its `ui-build-react-app` profile,
  activated by the presence of `ui/package.json`): it installs Node, runs `npm ci` + `npm run build`
  in `ui/`, and copies `ui/dist` to `src/main/resources/webapp/github-app/`. This pom carries no UI
  build of its own.
- **Tests** run on `mvn test`/`package`: Java via surefire (JUnit 5 + AssertJ + Mockito, inherited
  from the parent), and the frontend suite through the parent's test-phase execution.
  `-DskipJsTests=true` skips only the JS tests. Frontend tests live in `ui/test/**` and run in
  **Vitest browser mode** (a real Chromium via Playwright), with an istanbul **90%** gate on all
  four metrics, plus visual-regression references in `ui/test/expected/` (regenerate only with
  `npm run test:update:docker`). Playwright **e2e** (`ui/e2e/*.spec.js`, REST mocked via
  `page.route`) also runs in the build; skip it with `-DskipE2eTests=true`.

UI dev loop (hot reload against a running Polarion):

```bash
cd ui
cp .env.local.template .env.local   # set VITE_BASE_URL and (optional) VITE_BEARER_TOKEN
npm run dev                          # http://localhost:5173/?feature=about
npx tsc --noEmit                     # type-check (vite build does NOT type-check)
```

## CI

GitHub Actions runs four workflows, the same as in the timesheet repository.

- `ci.yml`: `lint` (actionlint, zizmor), `build` (`mvn -s .mvn/settings.xml clean verify`, the
  `docs/openapi.json` check, SonarCloud), `pre-commit`, `conventional-commits`.
- `scorecard.yml`: OpenSSF Scorecard, weekly and on every push to `main`.
- `bump-version.yml`: dispatched by hand with `patch`, `minor` or `major`. It sets the release
  version, cuts the `CHANGELOG.md` section, commits, tags `v<version>` and pushes with `PAT_TOKEN`.
- `release.yml`: runs on a `v*` tag. It publishes to Maven Central under
  `com.intechcore.polarion.extensions`, attests the files, creates the GitHub release in one call,
  and returns `main` to the next `-SNAPSHOT`.

The Polarion artifacts come from the Intechcore Nexus through the secrets `NEXUS_URL`,
`NEXUS_USERNAME` and `NEXUS_PASSWORD`.

## Architecture

**Java** (`src/main/java/.../github/`)
- `GithubAppServlet` - a `GenericUiServlet` subclass serving the `github-app` webapp context, which
  holds the Vite bundle, the administration-menu icons and the generated `html/about.html`.
- `rest/GithubRestApplication` - the REST application. It registers the repository settings, the import
  controllers and the GitHub error mapper.
- `client/GithubClient` - reads the open issues and discussions of a public repository from the
  GitHub REST API, without a token. Anonymous access allows 60 requests per hour.
- `settings/RepositorySettings` - named settings under the feature `repositories`, one setting per
  repository, in the scope of a project only. `RepositorySettingsModel.validate()` runs before
  every save. The REST endpoints come from generic: `/settings/repositories/...`.
- `service/ImportService` - creates one work item per GitHub item, in one write transaction per
  work item. `TemplateRenderer` fills the `{name}` placeholders and escapes every value in HTML.
- `rest/controller/ImportInternalController` (`@Hidden`, `/internal`) and `ImportApiController`
  (`@Secured`, `/api`) - `POST /projects/{projectId}/repositories/{name}/import?dryRun=`. A dry run
  is the preview. `GithubClientExceptionMapper` answers a GitHub failure with 502, or 429 for the
  rate limit.
- `META-INF/hivemodule.xml` - the administration entries. Each opens the SPA at `?feature=<id>`.

**React** (`ui/`) - Vite + React 19 + TypeScript SPA on `@sbb-polarion/react-sbb-polarion` (RSP),
served from the `github-app` webapp. One app, page selected by `?feature=`; an unknown or missing
feature falls back to About.
- `services/useRemote.ts` - REST hook. **The UI always calls `/internal/*` (in-session); external
  callers use `/api/*` with a bearer token.**

Webapp contexts must be declared in `src/main/resources/plugin.xml` - adding a
`webapp/<name>/WEB-INF/web.xml` is not enough. An administration URL must point at a real file
(`.../ui/app/index.html?...`), not the directory.

## Conventions & gotchas

- **Jakarta, not javax.** `src/` must stay free of `javax.*` EE imports. `web.xml` descriptors use
  the Jakarta `web-app_6_1.xsd`.
- **Don't hardcode `Require-Bundle`** in `META-INF/MANIFEST.MF`. The generic parent supplies the
  list. Add a bundle through `maven-jar-plugin.Require-Bundle` in the pom.
- **No new runtime dependency without need.** The GitHub client uses `java.net.http` and the
  Jackson that Polarion ships. `polarion-compatibility-maven-plugin` checks every nested jar.
- **Built SPA is git-ignored** (`webapp/github-app/app/`), along with `ui/node*`, `ui/dist`,
  `ui/.env*.local`. `ui/package-lock.json` IS committed.
- **Build-log noise is treated as an error.** `ui/.npmrc` and the `customLogger` in
  `vite.config.js` filter known noise. Keep both filters narrow.
- **OpenAPI**: `docs/openapi.json` is regenerated on build, and CI fails when the build changes it.
- **Commits**: a pre-commit hook requires `user.email` to match
  `firstname.lastname@intechcore.com`.

- **The Lucene index does not hold hyperlinks.** `tracker-hivemodule.xml` of
  `com.polarion.alm.tracker` lists the indexed work item fields, and `hyperlinks` is not one of
  them. The import finds its work items with an `SQL:(...)` query on `STRUCT_WORKITEM_HYPERLINKS`
  or `CF_WORKITEM`. Every value in that SQL passes `sqlLiteral`, which allows no quote.
- **Mockito: re-stubbing with `when(...)` runs the old answer once.** Use `doAnswer(...).when(...)`
  to replace an answer that has a side effect.

## Review focus

- A GitHub response is external input. Text from it reaches a work item only through the template
  code, never as raw HTML.
- An import must not create a second work item for one issue or discussion.
- Every action in a workflow is pinned by commit SHA.
