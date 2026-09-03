# CLAUDE.md — type

Modules `iu.util.type`, `iu.util.type.base`, `iu.util.type.impl`, `iu.util.type.bundle`,
`iu.util.type.loader`, plus five test-fixture artifacts.

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

The most structurally complex area in the repository. Two related capabilities:

1. **Type introspection** — a uniform, cached facade over Java Reflection, Java Beans, Jakarta
   Interceptors, and Jakarta Annotations.
2. **Component isolation** — loading versioned application components into private `ModuleLayer`s
   without contaminating the application class path.

`logging` depends on `type/base` to load its own implementation this way.

## Build order matters

`type/pom.xml` builds in this order, and it is not alphabetical by accident:

```
api  testlegacy  testruntime  testcomponent  testweb  testresources  base  impl  bundle  loader
```

`impl` consumes the four test-fixture artifacts at `generate-test-resources` — copying
`testlegacy`, `testruntime`, `testcomponent`, and the `testweb` WAR into `target/dependency/`, and
unpacking the `deps` classifier of several of them. `bundle` then consumes `impl`'s `bundle`
classifier at `prepare-package`. Reordering these breaks the build in ways that look like missing
test resources.

## Module roles

| Module | Artifact | Role |
|---|---|---|
| `api` | `iu-java-type-api` | `edu.iu.type`, `edu.iu.type.spi`; `uses IuTypeSpi` |
| `base` | `iu-java-type-base` | `edu.iu.type.base` — class loading primitives, Java 11, depends only on `iu.util` |
| `impl` | `iu-java-type-impl` | `iu.type` — the introspection engine; `provides IuTypeSpi with iu.type.TypeSpi` |
| `bundle` | **`iu-java-type`** | Embeds `impl`'s bundle assembly; `provides IuTypeSpi with iu.type.bundle.TypeBundleSpi` |
| `loader` | `iu-java-type-loader` | `edu.iu.type.loader` — `IuComponentLoader` |

Note the artifact naming trap: the *bundle* module publishes `iu-java-type`, while `api` publishes
`iu-java-type-api`. Applications depend on `iu-java-type`.

`impl` declares `opens iu.type to iu.util` — required for the reflective paths in `base`.

## `type/base` — the isolation primitives

These are the pieces `logging`, `bundle`, and `loader` all build on:

- `ModularClassLoader` — a closeable `ClassLoader` owning an application-defined `ModuleLayer`.
- `FilteringClassLoader` — blocks delegation to anything unrelated to the base platform. Allows
  `IuObject.isPlatformName` names **except** `javax.` and `jakarta.`, plus an explicit allowlist.
  Allowlisting is exact per package: allowing `edu.iu` does **not** allow `edu.iu.type`.
- `CloseableModuleFinder` — an `AutoCloseable` `ModuleFinder`.
- `TemporaryFile` — temporary file creation with fail-safe delete when initialization fails.

## `type/api` — the facade model

`IuType` is stereotyped as a **hash key**: it has a 1:1 relationship with a `Class`, and separate 1:1
relationships for that class referenced through a specific `TypeVariable`, `ParameterizedType`,
`GenericArrayType`, or `WildcardType`. That is what makes `WeakHashMap`-keyed extensions on `IuType`
correct. Preserve those identity semantics when touching `TypeFactory`, `TypeKey`, or `TypeReference`.

The facade hierarchy (`IuAnnotatedElement`, `IuDeclaredElement`, `IuNamedElement`, `IuExecutable`,
`IuAttribute`, `IuParameterizedElement`) is mirrored one-for-one by `*Base`/`*Facade` classes in
`iu.type`. A new facade method needs the corresponding implementation in the same commit.

`IuComponent` models a named, versioned, isolated component made of one or more jar archives;
`IuResource`, `IuResourceKey`, and `IuResourceReference` model resource injection.

## `type/impl` internals

`ComponentFactory`, `ComponentArchive`, `ComponentEntry`, `ComponentTarget`, `ArchiveSource`, and
`PathEntryScanner` handle archive validation and loading. `BackwardsCompatibility` and
`AnnotationBridge` map legacy `javax.*` annotations onto their `jakarta.*` equivalents — that is what
the `testlegacy` fixture (built against `javax.annotation-api`, `javax.interceptor-api`,
`javax.json-api`) exists to verify.

## Test fixtures

`testcomponent`, `testruntime`, `testweb` (WAR), `testresources`, and `testlegacy` are real
artifacts built to be loaded as isolated components by `impl`, `bundle`, and `loader` tests. They are
`@SuppressWarnings("javadoc")` and exempt from the documentation standards applied elsewhere. Changing
one changes the expectations of tests in three other modules.

## Integration tests

`type/bundle` (`IuComponentIT`, `IuTypeIT`, `TypeBundleSpiIT`) and `type/loader` (`ComponentLoaderIT`)
run under failsafe at `verify`. They exercise real module-layer loading, so they will not pass under
`mvn test` alone.
