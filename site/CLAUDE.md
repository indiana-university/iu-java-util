# CLAUDE.md — site

`iu-java-site` / packaging `pom`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

Reporting aggregation only. This module produces no artifact of its own — `maven.install.skip` and `maven.deploy.skip` are both `true`. It exists to build the two published reports:

- **Aggregate Javadoc** — `maven-javadoc-plugin:aggregate` with `includeDependencySources=true`, bound to `package`, producing one unified modular API reference over every published module.
- **Aggregate coverage** — `jacoco-maven-plugin:report-aggregate`, bound to `package`.

There is no `maven-site-plugin` here and no assembly; the `<reporting>` section exists so the same two reports are available to `mvn site` if it is ever run, but the build path is the two `package`-bound executions.

## How the output reaches GitHub Pages

The `publish_docs` step in the CI workflows is a script supplied by the build container (`esas-build/support/publish_docs`), not by this repository. It does not use Maven, `mvn site`, or an assembly. It checks out the `github_pages` branch, wipes `docs/<ref>`, then moves every directory matching `apidocs`, `jacoco`, or `jacoco-aggregate` into `docs/<ref>/${path%%target*}`:

```bash
find -type d -regex '.*/\(apidocs\|jacoco\(-aggregate\)?\)'
# base/target/reports/apidocs      -> docs/<ref>/base/apidocs
# base/target/site/jacoco          -> docs/<ref>/base/jacoco
# site/target/reports/apidocs      -> docs/<ref>/site/apidocs
# site/target/site/jacoco-aggregate -> docs/<ref>/site/jacoco-aggregate
```

Two consequences worth knowing:

- **The intermediate directory does not matter.** Truncating at `target` means `target/reports/apidocs` and the pre-3.11 `target/site/apidocs` publish to the same URL. The javadoc plugin's move to `target/reports` was therefore harmless, and there is no reason to pin `reportOutputDirectory`.
- **Every module's own javadoc is published too**, at `<module-path>/apidocs/` — so `crypt/api` lands at `docs/<ref>/crypt/api/apidocs`. The aggregate under `site/apidocs` is the unified entry point, not the only one.

Published locations:

- <https://indiana-university.github.io/iu-java-util/develop/site/apidocs/> — aggregate
- <https://indiana-university.github.io/iu-java-util/develop/base/apidocs/> — one module, as an example

The aggregate coverage report is generated into `site/target/site/jacoco-aggregate` and *is* matched by that `find`, but <https://indiana-university.github.io/iu-java-util/develop/site/jacoco-aggregate/> returns 404 — and so does every per-module `<module>/jacoco/`. Since `publish_docs` rebuilds `docs/<ref>` in a single pass, a published tree holding `apidocs` for every module and `jacoco` for none means no JaCoCo output was on disk when it ran. That is a question about the workflow's step order (`Compile and Build Javadoc` runs `clean verify -DskipTests`; `Verify Test Coverage` runs afterwards and is what produces the JaCoCo directories), not about anything configured here.

## It must build last, in a reactor containing everything it documents

`site` is the final entry in the root POM's module list, and it declares a dependency on every module it documents, so the reactor cannot order it anywhere but last.

That is a hard requirement, not a preference. `maven-javadoc-plugin` unpacks each dependency's sources artifact into `target/distro-javadoc-sources`, then — **only for dependencies that are also projects in the current reactor** — arranges them under `target/reports/apidocs/src/<module-name>` and drives javadoc in multi-module mode (`--module-source-path` plus one `--patch-module` per module). A dependency that is not in the reactor is reported as `no reactor project: <gav>` and contributes nothing but a flat source path entry, which fails immediately because more than one `module-info.java` cannot share a source path:

```
error: too many module declarations found
```

So:

```bash
mvn -pl site -am package     # works
mvn clean verify             # works, site runs last
mvn -pl site package         # fails, every dependency is "no reactor project"
```

The same applies to excluding modules. `mvn verify -pl '!jdbc/pool,!dao/impl'` leaves `site` depending on two modules that are no longer reactor projects, and it fails with `module not found on module source path`. Exclude `site` too.

## The dependency list is a manual allowlist

Aggregation covers exactly the modules listed in `<dependencies>`. All 34 modules that publish a javadoc artifact are currently listed; the only Java modules deliberately left out are the `type/test*` fixtures, which set `maven.javadoc.skip`.

**Adding a module to the root POM does not add it to the published documentation or the aggregate coverage report.** Whenever a new published module is created, add it here too, or it silently disappears from both.

`dependencySourceIncludes` restricts source unpacking to `${project.groupId}:*` so third-party artifacts are never fetched or documented.

## Optional dependencies have to be repeated here

The trailing block of non-`edu.iu.util` dependencies is not decoration. Modules that declare a dependency `requires static` scope it `provided` or `optional` in their own POM, so it does not reach `site` transitively — but javadoc still has to resolve every `requires` in the aggregate module graph, `static` included. Missing one surfaces as `error: module not found: <name>`.

Currently needed: Jakarta Persistence and Jakarta Transactions (`dao/api`, `jdbc/pool`, `transaction`), Jakarta CDI (not used directly — `jakarta.transaction` declares `requires static jakarta.cdi`), Commons Text (`el`), Commons Pool 2 and Lettuce (`redis/impl`), and OpenSAML (`saml/impl`, with the same BouncyCastle exclusion and Shibboleth repository the module itself uses).
