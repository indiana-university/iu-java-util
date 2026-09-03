# CLAUDE.md — site

`iu-java-site` / packaging `pom`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

Reporting aggregation only. This module produces no artifact of its own — `maven.install.skip` and
`maven.deploy.skip` are both `true`. It exists to build the two published reports:

- **Aggregate Javadoc** — `maven-javadoc-plugin:aggregate` with `includeDependencySources=true`,
  bound to `package`.
- **Aggregate coverage** — `jacoco-maven-plugin:report-aggregate`, bound to `package`.

CI publishes the output to GitHub Pages:

- <https://indiana-university.github.io/iu-java-util/develop/site/apidocs/>
- <https://indiana-university.github.io/iu-java-util/develop/site/jacoco-aggregate/>

```bash
mvn -pl site package     # requires the rest of the reactor to be built/installed first
```

## The dependency list is a manual allowlist

Aggregation covers exactly the modules listed in `<dependencies>` — currently `base`, `client`,
`test`, `crypt` (api and impl), the `type` family, `jwt-api`, `session` (api and impl), `config`,
`saml-api`, `pki` (api and impl), and `redis`.

**Adding a module to the root POM does not add it to the published documentation or the aggregate
coverage report.** Whenever a new published module is created, add it here too, or it silently
disappears from both. Several existing modules — `el`, `dao`, `oidc`, `jdbc`, `transaction`,
`logging`, `web` — are not currently in this list.
