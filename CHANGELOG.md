# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- The extension skeleton: the administration entry with the About page, and the REST API base.
- A client that reads the open issues and discussions of a public GitHub repository.
- Repository settings per project: the repository, the short name, and how issues and discussions
  become work items. The settings REST endpoints store and validate them.
- The import: one work item per open issue or discussion, with the title and description from the
  templates, the configured field values and the link to the epic. A work item keeps the GitHub
  URL, and the import skips an item that a work item holds already.
- The REST endpoint `POST /projects/{projectId}/repositories/{name}/import`, with a dry run and an
  optional list of URLs.
- The administration page `Repositories`, in the administration of a project. It edits the
  repository settings: the work item type, the templates, where the GitHub URL is kept, the epic
  link and the field values, for issues and discussions each.
- The topic `GitHub` in the navigation of a project, and the same page in its administration. It
  lists the open issues and discussions of all repositories of the project with the state of their
  work items, filters them by repository, kind, GitHub type, work item type, assignee and state, and
  creates work items for the selected ones in the name of the user.
- A cache of five minutes for the lists read from GitHub, shared by all pages and users. The button
  `Update from GitHub` reads GitHub again, at most once a minute per repository.
- Rules in the repository settings. A rule gives the issues with a label or an issue type, and the
  discussions with a label or a category, another work item type and field values, or leaves them
  out of the import. The first matching rule applies.
- The placeholders `{labels}`, `{type}` and `{category}` for the title and description templates.
