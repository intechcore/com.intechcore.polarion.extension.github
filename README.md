# GitHub Integration for Polarion ALM

[![CI](https://github.com/intechcore/com.intechcore.polarion.extension.github/actions/workflows/ci.yml/badge.svg)](https://github.com/intechcore/com.intechcore.polarion.extension.github/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/intechcore/com.intechcore.polarion.extension.github/badge)](https://scorecard.dev/viewer/?uri=github.com/intechcore/com.intechcore.polarion.extension.github)
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/15218/badge)](https://www.bestpractices.dev/projects/15218)
[![Release](https://img.shields.io/github/v/release/intechcore/com.intechcore.polarion.extension.github)](https://github.com/intechcore/com.intechcore.polarion.extension.github/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.intechcore.polarion.extensions/com.intechcore.polarion.extension.github)](https://central.sonatype.com/artifact/com.intechcore.polarion.extensions/com.intechcore.polarion.extension.github)
[![Java 21+](https://img.shields.io/badge/java-21+-blue.svg)](https://openjdk.org/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=coverage)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=bugs)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)

This Polarion extension shows the open issues and discussions of GitHub repositories in a project.
A user selects some of them and creates work items from them.

## Polarion configuration

Open the administration of a project, then `GitHub` / `Repositories`.

1. Select `Add new` and give the setting a name.
2. Enter the repository as `owner/name` and a short name. The short name goes into the titles.
3. Turn on issues, discussions or both, and select the work item type for each.
4. Adjust the title and the description templates when needed.
5. Select where the work item keeps the GitHub URL: a hyperlink, or a custom field of the type String.
   The import finds its own work items by that URL, so do not change it after the first import.
6. To link every created work item to an epic, enter the ID of the epic and select the link role.
7. Add field values that every created work item gets.
8. Add rules for the items that need another work item type, see below.
9. Select `Save`.

The templates take the placeholders `{{ SHORT_NAME }}`, `{{ REPOSITORY }}`, `{{ NUMBER }}`, `{{ TITLE }}`,
`{{ AUTHOR }}`, `{{ URL }}`, `{{ LABELS }}`, `{{ TYPE }}`, `{{ CATEGORY }}` and `{{ CHECKS }}`, the failed checks
of a pull request. The description also takes `{{ BODY }}`.
The description is HTML. `{{ BODY }}` is the rich text GitHub shows for the Markdown of the item, so
place it outside a paragraph, for example in a `<div>`. The import escapes every other value. GitHub
renders the HTML, and the import keeps only safe elements and attributes of it. An uploaded image keeps
its stable GitHub URL. A placeholder name ignores case,
underscores and the spaces inside the braces: `{{ SHORT_NAME }}` and `{{shortName}}` are the same.
`Save` refuses a template with an unknown placeholder, `{{ BODY }}` in a title, or a placeholder of
the earlier form `{title}`. The import checks a setting the same way before it reads GitHub, so a
setting saved before a check existed shows its reason on the GitHub page.

### Pull requests with failed checks

The block `Pull requests` lists the open pull requests of the watched authors whose checks failed.
An example is an update that Renovate could not merge because the build broke. A work item from it
asks a person to finish the update.

1. Enter the watched authors, separated by commas. A new setting watches `renovate[bot]`.
2. Turn on `Create work items from pull requests` and fill the block as for issues.

A failed check is a check run that ended with `failure`, `timed_out`, `action_required` or
`startup_failure`. A cancelled run does not count. Each pull request of a watched author costs one
GitHub request for its checks, once per five minutes. A rule on a pull request can compare a label or
the author.

### Rules

A rule applies to the items that carry a given label, issue type or discussion category, or to the
items of an author, for example `renovate[bot]`.

| A rule can | How |
|---|---|
| create another work item type | Select the type in the rule. |
| set other field values | Add field values to the rule. They are added to the field values of the block and replace them. |
| leave the items out | Select `Do not import`. |

The import checks the rules from the top and applies the first one that matches. An item that
matches no rule gets the work item type and the field values of the block. The comparison ignores
case. GitHub has issue types only in organizations that use them: elsewhere a rule on the issue
type matches nothing.

A work item is not created again when its issue gets another label later.

## GitHub token

A token is optional. Without one the extension reads public repositories anonymously, and GitHub
allows 60 requests per hour for the whole Polarion server. One repository costs at least one request
per list it reads: issues, discussions, pull requests, and the checks of each watched pull request.
A project with several repositories uses up the hour quickly. A token raises the limit to 5000
requests per hour.

### Minimal requirements

| Repositories | Token | Permissions |
|---|---|---|
| Public only | Fine-grained personal access token, `Repository access`: `Public repositories` | None |
| Public only | Classic personal access token | No scope |
| Private | Fine-grained personal access token, `Repository access`: `Only select repositories` | Repository permissions, read-only: `Issues`; `Discussions` for discussions; `Pull requests` and `Checks` for pull requests. `Metadata` is added by GitHub. |

- The extension only reads. It needs no write permission, and no permission on an organization or an account.
- The limit of 5000 requests belongs to the user of the token, across all their tools. A token of
  a dedicated machine user keeps the extension apart.
- An organization may require approval of fine-grained tokens for its private repositories.
- A token expires as set on GitHub. Renew it before then: an expired token fails every request.
- A GitHub App is not supported: its installation tokens expire after one hour.

### Configure the token

1. Create the token on GitHub (`Settings` ➙ `Developer settings` ➙ `Personal access tokens`).
2. Store it as a secret in the secrets manager of the Polarion installation, for example under the name `github-token`.
3. Name that secret in `polarion.properties`:
   ```properties
   com.intechcore.polarion.extension.github.token.secret=github-token
   ```
4. Restart Polarion. The About page lists the property `token.secret`.

The properties file and the About page carry the name of the secret, never the token. The server
reads the secret for each request, so a renewed token works without a restart. The token goes only to
`api.github.com`: the extension follows no redirect to another host.

A secret that is missing or empty, and a token GitHub refuses (wrong, expired or revoked), show
their reason above the list of the GitHub page, per repository. Without the property nothing
changes: the extension stays anonymous.

## Build

This extension can be produced using Maven:

```bash
mvn clean package
```

The build also compiles the React user interface in `ui/` and bundles it into the extension under `webapp/github-app`. No separate Node installation is required.

## Installation to Polarion

The released jar is published to
[Maven Central](https://central.sonatype.com/artifact/com.intechcore.polarion.extensions/com.intechcore.polarion.extension.github)
and attached to every [GitHub release](https://github.com/intechcore/com.intechcore.polarion.extension.github/releases).

To install this extension, copy `com.intechcore.polarion.extension.github-<version>.jar` to `<polarion_home>/polarion/extensions/com.intechcore.polarion.extension.github/eclipse/plugins`. The Maven build can do it for you:

```bash
mvn clean install -P local-install-into-polarion
```

The `POLARION_HOME` environment variable must point to the Polarion installation folder.

Changes take effect only after a restart of Polarion.

### Verify

GitHub releases carry a signed provenance bundle, from the first release on. Verify a downloaded jar
with `gh attestation verify <file> --repo intechcore/com.intechcore.polarion.extension.github`.

## Issues and discussions

Open the topic `GitHub` in the navigation of a project. The administration of a project has the same
page under `GitHub` / `Issues and Discussions`.

### Add the topic to a project

A project shows the topic `GitHub` only when a view of the project lists it. A project
administrator adds it:

1. Open the project and select ⚙ (Actions) ➙ 🔧 Administration in the navigation.
2. Select `Portal` ➙ `Topics`, then select `Edit` for the view that users open.
3. Insert the topic into the topics configuration:
   ```xml
   …
   <topic id="github"/>
   …
   ```
4. Select 💾 `Save`. The topic appears in the navigation of the project for the users of that view.

The topic shows the page to every user who can read the project. Creating or updating a work item
needs the permission to create or modify work items in the project.

### The page

The page lists the open issues and discussions of all repository settings of the project, and the
pull requests with failed checks. A pull request shows the names of its failed checks under its title:

- the short name of the repository, the item with its number as the link to GitHub, its issue type or
  discussion category, its labels in their GitHub colors and its GitHub assignees;
- what the import does with it, with an icon: `New`, `Has a work item`, `Out of date` or `Left out`
  by a rule;
- the work item with the icon of its type and a link, its status with its icon and its assignees in
  Polarion. An item without a work item leaves these columns empty.

The gear in the last header cell opens the table settings. They show or hide each column and move it
up or down. The browser keeps the layout for the next visit, per user. `Reset columns` returns to the
default.

The eye at the end of a row hides an item on the page, for every user of the project. Use it for an
item that stays open on purpose, such as the Dependency Dashboard of Renovate. `Show hidden items` in the
table settings lists the hidden items again, and their eye shows them. Hiding changes only the page: the import still sees the
item. The project keeps the hidden URLs in the setting `hidden-items`.

Filter the list by repository, kind, GitHub type, work item type, GitHub assignee, Polarion assignee
and state, or search the title, the number, the work item and the labels.

To create work items:

1. Select the new items. The box in the table head selects all new items the filters show.
2. Select `Create work items`. The work items are created in your name and with your permissions.
3. The list shows the ID of each created work item, or the reason of a failure.

To update work items:

1. An item whose work item no longer shows what the settings and GitHub say is `Out of date`, with
   what differs: the title, the description, the type, a field value or the epic link.
2. Select the outdated items and select `Update work items`. The update changes what differs and
   nothing else, in your name. The hyperlink or custom field that keeps the GitHub URL never changes.

A work item that a rule would leave out today stays as it is.

A repository that cannot be read shows its reason above the list, and the others stay readable.

GitHub allows 60 requests per hour without a token, for the whole Polarion server, and 5000 with one,
see [GitHub token](#github-token). The server keeps a list it read from GitHub for 5 minutes and
serves every page and user from it. The page shows when the lists were read.

- `Refresh` reads the list again: the GitHub items from that cache, the work items from Polarion.
- `Update from GitHub` reads GitHub again, for every repository of the project. A list read within
  the last minute stays, so a second click costs nothing. One repository costs one request per 100 open issues and one per 100
discussions.

## Live Report widget

The widget **GitHub Items** (category *Reports*) shows the table of the topic `GitHub` in a Live
Report page of a project, with its filters.

1. Open a Live Report page of the project and edit it.
2. Add the widget **GitHub Items**.
3. Adjust the widget settings when needed, and save the page.

| Setting | What it does |
|---|---|
| Repositories | The repository settings of the project to show, picked from a list. None picked shows all. |
| Kinds, States | The filters the table opens with. The reader can change them. Empty shows all. |
| Columns | The columns of the widget, in this order. Empty shows the columns each reader chose on the topic. |
| Hide filters | Shows the table only, without the toolbar and the filters. |
| Allow creating work items | Shows the selection and `Create work items` / `Update work items`. Off by default: the widget is a report. |

The widget sizes itself to the table. The work items open in Polarion, as from the topic. A page
outside a project shows that the table needs a project.

A PDF export or a print of the page (for example with the PDF Exporter) shows the table as it opens
with the settings of the widget: without hidden items, filtered by repositories, kinds and states, in
the columns of the widget, or in all columns. The server reads it when the document is made.

## REST API

This extension provides a REST API. Its OpenAPI specification can be obtained [here](docs/openapi.json).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the build, the tests and the pull request rules.

## Disclaimer

This software is provided "as is", without warranty of any kind, as the [LICENSE](LICENSE) states.
Use it at your own risk. Intechcore GmbH is not liable for damage from its use, as far as the law
allows. It is published free of charge, outside of any commercial offering, with no obligation to
support it. Security reports are welcome, see [SECURITY.md](SECURITY.md).

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
