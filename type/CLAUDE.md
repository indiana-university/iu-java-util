# CLAUDE.md — type

Modules `iu.util.type`, `iu.util.type.base`, `iu.util.type.impl`, `iu.util.type.bundle`, `iu.util.type.loader`, plus five test-fixture artifacts.

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

The most structurally complex area in the repository. Two related capabilities:

1. **Type introspection** — a uniform, cached facade over Java Reflection, Java Beans, Jakarta Interceptors, and Jakarta Annotations.
2. **Component isolation** — loading versioned application components into private `ModuleLayer`s without contaminating the application class path.

`logging` depends on `type/base` to load its own implementation this way.

## Build order matters

`type/pom.xml` builds in this order, and it is not alphabetical by accident:

```
api  testlegacy  testruntime  testcomponent  testweb  testresources  base  impl  bundle  loader
```

`impl` consumes the four test-fixture artifacts at `generate-test-resources` — copying `testlegacy`, `testruntime`, `testcomponent`, and the `testweb` WAR into `target/dependency/`, and unpacking the `deps` classifier of several of them. `bundle` then consumes `impl`'s `bundle` classifier at `prepare-package`. Reordering these breaks the build in ways that look like missing test resources.

## Module roles

| Module | Artifact | Role |
|---|---|---|
| `api` | `iu-java-type-api` | `edu.iu.type`, `edu.iu.type.spi`; `uses IuTypeSpi` |
| `base` | `iu-java-type-base` | `edu.iu.type.base` — class loading primitives, depends only on `iu.util` |
| `impl` | `iu-java-type-impl` | `iu.type` — the introspection engine; `provides IuTypeSpi with iu.type.TypeSpi` |
| `bundle` | **`iu-java-type`** | Embeds `impl`'s bundle assembly; `provides IuTypeSpi with iu.type.bundle.TypeBundleSpi` |
| `loader` | `iu-java-type-loader` | `edu.iu.type.loader` — `IuComponentLoader` |

Note the artifact naming trap: the *bundle* module publishes `iu-java-type`, while `api` publishes `iu-java-type-api`. Applications depend on `iu-java-type`.

`impl` declares `opens iu.type to iu.util` — required for the reflective paths in `base`.

## `type/base` — the isolation primitives

These are the pieces `logging`, `bundle`, and `loader` all build on:

- `ModularClassLoader` — a closeable `ClassLoader` owning an application-defined `ModuleLayer`.
- `FilteringClassLoader` — blocks delegation to anything unrelated to the base platform. Allows `IuObject.isPlatformName` names **except** `javax.` and `jakarta.`, plus an explicit allowlist. Allowlisting is exact per package: allowing `edu.iu` does **not** allow `edu.iu.type`.
- `CloseableModuleFinder` — an `AutoCloseable` `ModuleFinder`.
- `TemporaryFile` — temporary file creation with fail-safe delete when initialization fails.

## `type/api` — the facade model

`IuType` is stereotyped as a **hash key**: it has a 1:1 relationship with a `Class`, and separate 1:1 relationships for that class referenced through a specific `TypeVariable`, `ParameterizedType`, `GenericArrayType`, or `WildcardType`. That is what makes `WeakHashMap`-keyed extensions on `IuType` correct. Preserve those identity semantics when touching `TypeFactory`, `TypeKey`, or `TypeReference`.

The facade hierarchy (`IuAnnotatedElement`, `IuDeclaredElement`, `IuNamedElement`, `IuExecutable`, `IuAttribute`, `IuParameterizedElement`) is mirrored one-for-one by `*Base`/`*Facade` classes in `iu.type`. A new facade method needs the corresponding implementation in the same commit.

`IuComponent` models a named, versioned, isolated component made of one or more jar archives; `IuResource`, `IuResourceKey`, and `IuResourceReference` model resource injection.

## `type/impl` internals

`ComponentFactory`, `ComponentArchive`, `ComponentEntry`, `ComponentTarget`, `ArchiveSource`, and `PathEntryScanner` handle archive validation and loading. `BackwardsCompatibility` and `AnnotationBridge` map legacy `javax.*` annotations onto their `jakarta.*` equivalents — that is what the `testlegacy` fixture (built against `javax.annotation-api`, `javax.interceptor-api`, `javax.json-api`) exists to verify.

`TypeTemplate.isNative()` treats a type as opaque — no fields, properties, or methods introspected — when it is a platform type, lives in `iu.util.type.impl` itself, or is in a package not open to `iu.util.type.impl`. Unit tests are patched into the impl module, so introspection fixtures must live in `testresources` (on the test classpath, so open), never under `type/impl/src/test`.

Requiring `opens` is deliberate; don't widen it to exported packages. The module exists to introspect non-public members for container deployments. Third-party shared library modules are typically exported but not opened, are large, and are compile-time dependencies rather than injection points, so they must stay out of introspection. Preloading keeps introspection idempotent, and deployment should fail unless all component source is valid, so pulling in library internals would add cost and new failure modes. `opens` is the opt-in; widening the scope would force app developers to filter or opt in some other way. A type that is opaque only because its package is exported but not open is logged once per raw class at `FINE`, to diagnose a contract module that forgot `opens`. The user-facing statement of this rule is in `edu.iu.type`'s `package-info.java`.

`TypeFactory.resolveType` caches in-progress generic templates keyed by `Type` equality, not `IuTypeKey`. `IuTypeKey` matches a type variable by name and bounds only, so it would hand `Leaf.get(K)` the template for another interface's same-named `K` (e.g. in a diamond). Don't use `IuTypeKey` anywhere a variable's declaring class matters.

### Bridge methods

javac emits two kinds of bridge, and introspection must tell them apart. A generic or covariant bridge (`get(Object)` forwarding to the class's own `get(String)`) means the inherited method is overridden, so `TypeTemplate.initializeMethods` records its signature to hide the inherited copy. A visibility bridge, emitted on a public class for each public method inherited from a non-public superclass, forwards to a method that is *not* overridden, so the inherited method must stay. `isOverrideBridge` distinguishes them by whether the class also declares a non-synthetic method with the same name and parameter count. Bridges themselves are never exposed as methods.

When several supertypes provide the same inherited signature, `TypeTemplate.isMoreSpecific` resolves it as Java does: a class method (abstract or not) beats an interface method, and a subtype's beats its supertype's. Supertypes may finish initializing in any order, so the outcome must not depend on which one `initializeMethods` sees first.

### Resource injection and inheritance

`IuType.observe` and `destroy` notify the `InstanceReference` subscribers of the observed type **and of every type in its hierarchy** (least specific first on observe, most specific first on destroy, after `@PreDestroy`). Containers observe on the concrete class, so a reference subscribed to a superclass reaches every subclass instance. Subscriptions belong to the raw type: a parameterized template (e.g. `Base<String>`, the hierarchy entry of `Sub extends Base<String>`) shares its erased template's subscriber set, so subscribing or observing through either form reaches the same references.

`Component` therefore creates **one** `ComponentResourceReference` per `@Resource` field or setter, in a second pass once every class in the component is indexed, subscribed to the most general indexed type that has the attribute:

- Declared by a class indexed in this component or a parent component: referenced by that class only, never again by subclasses.
- Inherited from a class that is not indexed (e.g. a library superclass outside the component): referenced by each indexed subclass with no indexed class between it and the declaring class, subscribed to that subclass.
- Declared by a class in a package not open to `iu.util.type.impl`: not introspected (see `isNative()`), so not visible and not injected.
- Visibility bridge setters, which javac generates on a public subclass of a non-public class and annotates like the original, are skipped; the original is referenced through its declaring class.

## Test fixtures

`testcomponent`, `testruntime`, `testweb` (WAR), `testresources`, and `testlegacy` are real artifacts built to be loaded as isolated components by `impl`, `bundle`, and `loader` tests. They are `@SuppressWarnings("javadoc")` and exempt from the documentation standards applied elsewhere. Changing one changes the expectations of tests in three other modules.

## Integration tests

`type/bundle` (`IuComponentIT`, `IuTypeIT`, `TypeBundleSpiIT`) and `type/loader` (`ComponentLoaderIT`) run under failsafe at `verify`. They exercise real module-layer loading, so they will not pass under `mvn test` alone.
