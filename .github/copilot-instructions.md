# Copilot instructions

`com.intechcore.polarion.extension.github` is a Polarion ALM extension: a Java backend on the
`ch.sbb.polarion.extension.generic` parent, and a React/Vite administration UI served from the
`github-app` webapp. It targets Polarion 2606, Tomcat 11 and Jakarta EE 11.

`CLAUDE.md` holds the full picture of the architecture.

## Style of a review comment

Be direct and concise. No preamble, no praise. One idea per sentence. Never use an em-dash.

## Java, `src/main/java/**`

- Everything is on `jakarta.*`. A `javax.*` EE import fails the build through
  `polarion-compatibility-maven-plugin`. Flag one even when the code compiles locally.
- `Require-Bundle` is generated from the `maven-jar-plugin.*` properties of the generic parent. Flag
  any attempt to hardcode it in `META-INF/MANIFEST.MF`.
- `docs/openapi.json` is regenerated on every build and CI fails when the build changes it. A
  changed REST signature has to arrive with the regenerated file.
- A GitHub response is external input. Flag text from it that reaches a work item as raw HTML.

## Java tests, `src/test/java/**`

- Do not assert log output. A behavior is pinned through what it changes, not through the logger.

## UI tests, `ui/test/**` and `ui/e2e/**`

- The visual references in `ui/test/expected` are pixel-locked to the pinned Playwright Docker
  image. Regenerate them with `npm run test:update:docker`, never by hand.
- Coverage is gated at 90% on all four metrics.

## Build, `pom.xml`

- Build-log noise counts as an error here. The filters in `ui/.npmrc` and `vite.config.js` must
  stay narrow.
- Each suite has its own skip flag: `-DskipTests` for Java, `-DskipJsTests` for the UI unit tests,
  `-DskipE2eTests` for Playwright.

## Workflows, `.github/workflows/**`

- Every action is pinned by full commit SHA with a version comment, never a floating tag.
- Pass `github.head_ref` and `github.base_ref` through `env`, never inline in a `run` block.
- A checkout carries no credential: `persist-credentials: false`, and a token reaches the one step
  that pushes.

## Documentation, `**/*.md`

- No em-dashes. American spelling. Active voice, one idea per sentence.
- `README.md` is converted to the About page shown inside Polarion. It carries what an administrator
  needs and nothing that concerns only maintainers.
