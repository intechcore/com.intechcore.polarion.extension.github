# GitHub Integration for Polarion ALM

[![CI](https://github.com/intechcore/com.intechcore.polarion.extension.github/actions/workflows/ci.yml/badge.svg)](https://github.com/intechcore/com.intechcore.polarion.extension.github/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/intechcore/com.intechcore.polarion.extension.github/badge)](https://scorecard.dev/viewer/?uri=github.com/intechcore/com.intechcore.polarion.extension.github)
[![Release](https://img.shields.io/github/v/release/intechcore/com.intechcore.polarion.extension.github)](https://github.com/intechcore/com.intechcore.polarion.extension.github/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.intechcore.polarion.extensions/com.intechcore.polarion.extension.github)](https://central.sonatype.com/artifact/com.intechcore.polarion.extensions/com.intechcore.polarion.extension.github)
[![Java 21+](https://img.shields.io/badge/java-21+-blue.svg)](https://openjdk.org/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=intechcore_com.intechcore.polarion.extension.github&metric=coverage)](https://sonarcloud.io/summary/new_code?id=intechcore_com.intechcore.polarion.extension.github)

This Polarion extension creates work items from the open issues and discussions of GitHub
repositories. It runs as a scheduled job or by hand from the administration pages.


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
8. Select `Save`.

The templates take the placeholders `{shortName}`, `{repository}`, `{number}`, `{title}`,
`{author}` and `{url}`. The description also takes `{body}`. The description is HTML, and the
import escapes every value.

## Build

This extension can be produced using Maven:

```bash
mvn clean package
```

The build also compiles the React user interface in `ui/` and bundles it into the extension under `webapp/github-app`. No separate Node installation is required.

## Installation to Polarion

To install this extension, copy `com.intechcore.polarion.extension.github-<version>.jar` to `<polarion_home>/polarion/extensions/com.intechcore.polarion.extension.github/eclipse/plugins`. The Maven build can do it for you:

```bash
mvn clean install -P local-install-into-polarion
```

The `POLARION_HOME` environment variable must point to the Polarion installation folder.

Changes take effect only after a restart of Polarion.

## Manual import

Open the administration of a project, then `GitHub` / `Import`.

1. Select the repository setting.
2. Select `Read from GitHub`. The page lists the open issues and discussions. An item that has a
   work item already shows its ID.
3. Clear the items you do not want. Every new item starts selected.
4. Select `Create work items`. The list shows the ID of each created work item, or the reason of
   a failure.

## Scheduled import

The job `github_import.job` imports the repositories configured in a project. Add it in the global
`Administration` / `Scheduler`, with the scope of the project:

```xml
<job id="github_import.job" cronExpression="0 0 * * * ?" name="GitHub import" scope="project:myproject">
    <dryRun>false</dryRun>
</job>
```

| Parameter | Meaning |
|---|---|
| `repositories` | Comma-separated names of the repository settings to import. Without it the job imports every enabled one. |
| `dryRun` | `true` to log what the import would do, without creating work items. |

GitHub allows 60 requests per hour without a token, for the whole Polarion server. One repository
costs one request per 100 open issues and one per 100 discussions. Schedule the job with that in mind.

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
