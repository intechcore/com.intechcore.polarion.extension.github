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

The templates take the placeholders `{shortName}`, `{repository}`, `{number}`, `{title}`,
`{author}`, `{url}`, `{labels}`, `{type}` and `{category}`. The description also takes `{body}`.
The description is HTML, and the import escapes every value.

### Rules

A rule applies to the items that carry a given label, issue type or discussion category.

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

The page lists the open issues and discussions of all repository settings of the project:

- the repository, the item with a link to GitHub, its issue type or discussion category, its labels
  and its GitHub assignees;
- what the import does with it: `New`, `Has a work item` or `Left out` by a rule;
- the work item, with a link, its type, status and assignees in Polarion. For a new item the type is
  the one the rules choose.

Filter the list by repository, kind, GitHub type, work item type, GitHub assignee, Polarion assignee
and state, or search the title, the number, the work item and the labels.

To create work items:

1. Select the new items. The box in the table head selects all new items the filters show.
2. Select `Create work items`. The work items are created in your name and with your permissions.
3. The list shows the ID of each created work item, or the reason of a failure.

A repository that cannot be read shows its reason above the list, and the others stay readable.

GitHub allows 60 requests per hour without a token, for the whole Polarion server. The server keeps a
list it read from GitHub for 5 minutes and serves every page and user from it. The page shows when
the lists were read.

- `Refresh` reads the list again: the GitHub items from that cache, the work items from Polarion.
- `Update from GitHub` reads GitHub again, for every repository of the project. A list read within
  the last minute stays, so a second click costs nothing. One repository costs one request per 100 open issues and one per 100
discussions.

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
