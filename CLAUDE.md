# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`iu-java-util` is a multi-module Maven aggregate of independent JPMS (Java Platform Module System) utility libraries published under `edu.iu.util`. Every published module is a **named Java module** with a `module-info.java`; nothing here is intended to be consumed from the unnamed module.

Modules are versioned and released together (`7.1.1-SNAPSHOT` at the root, inherited by all children via `${project.version}` / `${iu-java-util.version}`).

## Build

Requires **JDK 25+** and **Maven 3.9+** (enforced by `maven-enforcer-plugin`). Compiler `release` is 17 by default, overridden to 11 in the widest-reach modules and 21 in `crypt/cli`.

```bash
mvn clean verify                       # full build: compile, test, javadoc, 100% coverage gate
mvn clean verify -DskipTests           # compile + javadoc only (also skips JaCoCo and Liquibase)
mvn verify -Dmaven.javadoc.skip        # tests + coverage, no javadoc
mvn -pl base -am verify                # one module and everything it depends on
mvn -pl crypt/impl verify              # one leaf module (deps must already be installed)
```

### Running tests

```bash
mvn -pl type/impl test                                   # unit tests only, no coverage gate
mvn -pl base test -Dtest=IuObjectTest                    # single test class
mvn -pl base test -Dtest=IuObjectTest#testRequire        # single test method
mvn test -Dtest=IuObjectTest -Dsurefire.failIfNoSpecifiedTests=false   # across the reactor
mvn -pl type/bundle verify                               # includes *IT.java via failsafe
```

Prefer the `test` phase while iterating: `verify` runs the JaCoCo gate, which fails on anything below full coverage and will mask the failure you are actually chasing.

### Two gates that fail builds routinely

1. **100% JaCoCo coverage.** `coverage-check` is bound to `verify` with `haltOnFailure`, requiring `INSTRUCTION` and `BRANCH` covered ratio of `1.000` and zero missed classes, per bundle (module). New code needs tests for every branch, including defensive ones. Reports land in `<module>/target/site/jacoco/index.html`.
2. **Javadoc with `failOnWarnings`.** `show=package`, so every package-private and public element needs complete Javadoc — `@param`, `@return`, `@throws` included. A missing tag fails the build.

### Profile quirk

The reactor's module list lives in a profile named `default` with `activeByDefault`, alongside a second `activeByDefault` profile named `scan` that redundantly lists `logging`. Because `activeByDefault` profiles deactivate as soon as any profile is selected explicitly, running `mvn -P<anything> ...` yields an empty reactor. Use `-pl` to scope a build, not `-P`.

### CI

`.github/workflows/develop.yml` and `main.yml` run in a container with a `postgres` service (`-c max_prepared_transactions=10`) and publish javadoc plus aggregate coverage to GitHub Pages. Modules with database tests read `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` from the environment — see `dao/` and `jdbc/`.

## Architecture

### Naming convention

Three parallel namespaces, consistently applied:

| Namespace | Meaning |
|---|---|
| `iu.util.*` | JPMS module name (`iu.util.crypt`, `iu.util.crypt.impl`) |
| `edu.iu.*` | **Public API** package, exported and supported |
| `iu.*` | **Implementation** package, exported only where the module system requires it |

The `base` module is the exception: its module name is `iu.util` and its package is plain `edu.iu`.

### The api/impl SPI split

Most functional areas are a directory containing an `api` and an `impl` child module:

```
crypt/api   -> module iu.util.crypt       exports edu.iu.crypt, exports iu.crypt.spi to impl
                                          uses iu.crypt.spi.IuCryptSpi
crypt/impl  -> module iu.util.crypt.impl  provides iu.crypt.spi.IuCryptSpi with iu.crypt.CryptSpi
```

The API module declares the SPI interface, restricts its export to the implementation module, and resolves it through `ServiceLoader`. Callers depend only on the `api` module at compile time and add the `impl` module to the runtime module path. `dao`, `jwt`, `redis`, and `type` follow the same shape. When an API and its implementation are loaded into a **non-system** `ModuleLayer`, the API module exposes an explicit initialization stub (for example `edu.iu.crypt.Init.init()`) that must be invoked while the implementation's `ClassLoader` is the thread context class loader.

### Dependency layering

```
base (iu.util)                        nothing beyond java.logging
 |- client            jakarta.json + java.net.http adapters, IuVault, IuHttp
 |   `- config        IuConfig — configuration interfaces bound to Vault secrets
 |- crypt -> jwt -> oidc
 |   |- session -> oidc, saml
 |   `- pki
 |- type/base -> type/api -> type/impl -> type/bundle -> type/loader
 |   `- logging       logging/impl is embedded in logging/api as an isolated component
 |- transaction, jdbc/pool, jdbc/monitor, dao
 |- el, web, redis
 `- test              JUnit 5 + Mockito support, consumed at test scope everywhere
```

`base` is the root of everything and deliberately depends on nothing but `java.logging`.

### Isolated module layers

Two modules ship their implementation as an assembly with a `bundle` classifier that is embedded in another artifact and loaded into a private `ModuleLayer` at runtime, rather than being placed on the application module path:

- `type/impl` is embedded in `type/bundle` (`iu-java-type`), which provides `IuTypeSpi`.
- `logging/impl` is copied into `logging/api` at `META-INF/component/` and loaded through `iu.util.type.base`.

Because of this, **submodule order in the parent POM is load-bearing**: `logging/pom.xml` builds `impl` before `api`, and `type/pom.xml` builds its test fixture components before `impl`.

## Testing conventions

Tests are plain classpath tests — there are no `module-info.java` files under `src/test`.

### `iu-java-test` changes how logging behaves in tests

Adding `iu-java-test` at test scope registers `edu.iu.test.IuTestSessionListener` (a JUnit `LauncherSessionListener`) and `IuTestExtension` through `ServiceLoader`. This installs `IuTestLogger`, which makes **any unexpected `Logger.log` call fail the test**. Expected log events must be declared, in order, and must match logger name exactly, level exactly, message as a regular expression, and thrown exception class exactly:

```java
IuTestLogger.expect("edu.iu.example.Service", Level.FINE, "started \\w+");
IuTestLogger.allow("edu.iu.example.Service", Level.FINER);  // optional, any number of times
```

Platform loggers (`org.junit`, `org.mockito`, `org.apiguardian`, `net.bytebuddy`, and similar) are exempt. Additional exemptions go in `src/test/resources/META-INF/iu-test.properties` under `iu.util.test.platformLoggers`.

### Build-time values in tests

`src/test/resources` is filtered, and `IuTest.properties()` / `IuTest.getProperty(key)` read `META-INF/iu-test.properties` from the classpath. This is how POM properties reach a test:

```properties
project.version=${project.version}
jakarta.json-api.version=${jakarta.json-api.version}
```

### Integration tests

`*IT.java` classes run under failsafe during `verify`, not surefire. They exist in `logging/api`, `redis/impl`, `type/bundle`, and `type/loader`. Integration tests that need external services skip themselves when configuration is absent — see `LettuceConnectionIT` gating on `redis.host`.

### Mocking

`IuTest.mockWithDefaults(Class)` produces a Mockito mock that still runs `default` interface methods. Use it when testing an interface whose behavior lives in its default methods.

## Conventions to follow when editing

- **BSD 3-Clause header.** Every `.java` file and `pom.xml` carries the full license header. Copy it from a sibling file when creating a new one.
- **Checked-exception handling.** Do not write `try`/`catch` boilerplate to convert exceptions. Use `edu.iu.IuException` with the `Unsafe*` functional interfaces: `IuException.unchecked(() -> ...)`, `IuException.checked(IOException.class, () -> ...)`.
- **Argument and state checks.** Use `edu.iu.IuObject`: `require`, `requireType`, `once` (assign a value only once), `first`, `assertNotOpen`, and the `hashCode`/`equals`/`compareTo` helpers rather than hand-rolled equivalents.
- **Environment access.** Only `edu.iu.IuRuntimeEnvironment` reads system properties and environment variables, and only for bootstrapping. Everything else goes through `edu.iu.config.IuConfig` backed by `edu.iu.client.IuVault`.
- **Configuration interfaces.** Implementation modules expose an `iu.<area>.config` package that is both `exports` and `opens` (for reflective JSON binding), containing interfaces registered with `IuConfig.registerInterface`. Follow that pattern rather than introducing new config mechanisms.
- **Optional dependencies.** Heavy or environment-specific dependencies are declared `requires static` in `module-info.java` and `provided`/`optional` in the POM — see `saml/impl` (OpenSAML), `redis/impl` (Lettuce), and `el` (commons-text).
