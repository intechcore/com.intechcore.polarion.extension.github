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

The extension is under development. This version installs the administration entry and the REST
API base only.

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
