# CLAUDE.md — test

`iu-java-test` / module `iu.util.test` / package `edu.iu.test`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

Shared unit-test support, consumed at `test` scope by nearly every other module. Compiled with
`--release 11`.

This module is unusual: **it changes test behavior everywhere it is on the classpath**, through two
`provides` clauses in `module-info.java`:

```java
provides org.junit.platform.launcher.LauncherSessionListener with edu.iu.test.IuTestSessionListener;
provides org.junit.jupiter.api.extension.Extension with edu.iu.test.IuTestExtension;
```

Changes here affect every downstream module's test run. Verify with a full `mvn verify`, not just
this module's tests.

## `IuTestLogger` — the strict logging assertion

`IuTestSessionListener` installs a `java.util.logging` handler that fails the test on **any**
`Logger.log` call that was not explicitly expected. Expectations are ordered and matched on:

- logger name — exact
- level — exact
- message — regular expression
- thrown exception class — exact, or "no exception thrown"
- an optional `Predicate` on the thrown exception

```java
IuTestLogger.expect("edu.iu.example.Service", Level.FINE, "started \\w+");
IuTestLogger.expect("edu.iu.example.Service", Level.WARNING, "retry .*", IOException.class);
IuTestLogger.allow("edu.iu.example.Service", Level.FINER);   // any number of times, unordered
IuTestLogger.assertExpectedMessages();                        // usually handled by the extension
```

Platform loggers (`org.junit`, `org.mockito`, `org.apiguardian`, `net.bytebuddy`, and similar) are
exempt and log normally. A module can exempt more prefixes through
`iu.util.test.platformLoggers` in its own `src/test/resources/META-INF/iu-test.properties` — several
modules exempt `edu.iu.crypt` this way.

## `IuTest`

- `IuTest.properties()` / `IuTest.getProperty(key)` — loads every `META-INF/iu-test.properties`
  resource on the classpath. Because `src/test/resources` is filtered, this is the supported channel
  for passing POM properties (versions, output directories) into a test.
- `IuTest.mockWithDefaults(Class)` — a Mockito mock that still executes `default` interface methods,
  for testing interfaces whose behavior lives in their defaults.
- `IuTest.rand(enumClass)` — random enum constant, for property-style tests.

`CliTestSupport` drives command-line entry points (used by `crypt/cli`).

## Dependency note

`jakarta.json` is `requires static` here, so JSON-aware test helpers degrade gracefully when a
consuming module does not have Jakarta JSON on its path. Keep it that way — making it a hard
requirement would force Jakarta JSON onto every test classpath in the repository.
