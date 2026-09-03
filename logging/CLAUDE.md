# CLAUDE.md — logging

`iu-java-logging`, `iu-java-logging-impl` / modules `iu.util.logging`, `iu.util.logging.impl`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

A `java.util.logging` implementation that adds thread-bound context, process tracing, file
publication with rotation, and event subscription — installed at JVM startup, before the application
class path exists.

## Build order is inverted

`logging/pom.xml` builds **`impl` before `api`**. This is the opposite of every other pair in the
repository and it is required: `logging/api` copies `iu-java-logging-impl` (classifier `bundle`, an
assembly produced by `impl`) into its own jar at `META-INF/component/` during `prepare-package`.

The implementation therefore ships *inside* the API artifact and is loaded into an isolated
`ModuleLayer` through `iu.util.type.base`. It is never on the application module path, which is what
lets logging initialize before anything else and keeps its dependencies invisible to the application.

`iu.util.logging.impl` requires `iu.util.client` and `iu.util.crypt`; because of the isolation those
never leak to consumers of `iu-java-logging`.

## Installation

```
-Djava.util.logging.config.class=iu.logging.boot.IuLoggingBootstrap
-Djava.util.logging.manager=iu.logging.boot.IuLogManager      # optional, restricts reconfiguration
```

`iu-java-base`, `iu-java-type-base`, and `iu-java-logging` must be on the module path (`-p`).
`new IuLoggingBootstrap(true)` may be invoked reflectively to reconfigure after startup.

System properties read at bootstrap:

| Property | Meaning |
|---|---|
| `iu.config` | folder containing `logging.properties` |
| `iu.logging.file.path` | root log folder |
| `iu.logging.file.maxSize` | maximum bytes per log file |
| `iu.logging.file.nLimit` | number of backup files retained |

## The reflective boundary

`iu.logging.boot.IuLoggingBootstrap` cannot reference implementation types directly — they live in a
different module layer. It crosses the boundary by name:

```java
.getMethod("initializeContext", String.class, boolean.class, String.class, ...)
```

Consequently **renaming or changing the signature of a `Bootstrap` method silently breaks
initialization at runtime with no compile error.** `iu.logging.Bootstrap` and
`iu.logging.internal.IuLoggingProxy` are the two ends of this bridge; change them together, and keep
the reflected signatures in sync with `IuLoggingBootstrap`.

## Runtime model

`Bootstrap` exposes: `configure`, `initialize`, `initializeContext`, `getEnvironment`,
`getActiveContext`, `follow`/`fork`/`join` (context propagation across threads and requests),
`subscribe` (a `Stream` of typed log events), `trace`, and `destroy`.

- `IuLogContext` (API) — thread-bound context applied to every record.
- `IuLogEvent` (API) — a fully serialized record with context attributes applied.
- `iu.logging.internal` — `IuLogHandler`, `LogFilePublisher` (rotation), `ProcessLogger` (process
  trace boundaries), `LogEnvironmentImpl`.

The API package is both `exports` and `opens` because context binding is reflective.

## Testing notes

`logging/api` has an integration test, `IuLoggingBootstrapIT`, running under failsafe at `verify` —
it exercises real bootstrap and module-layer loading and will not run under `mvn test`.

`impl` tests set `iu.util.test.platformLoggers=edu.iu.crypt` in
`src/test/resources/META-INF/iu-test.properties`. Testing a logging implementation under
`IuTestLogger`'s strict log assertions is inherently awkward; prefer asserting on published
`IuLogEvent` instances over asserting on emitted records.
