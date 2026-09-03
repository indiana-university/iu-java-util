# CLAUDE.md — base

`iu-java-base` / module `iu.util` / package `edu.iu`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

The foundation every other module depends on. Its `module-info.java` requires nothing but
`java.logging`, and it is compiled with `--release 11` so it can be consumed by older runtimes.

**Do not add dependencies to this module.** Anything needing Jakarta JSON, HTTP, or a third-party
library belongs in `client` or higher. Anything that would raise the language level above 11 belongs
elsewhere too.

Surefire runs here with `-Xmx1g` (`argLine` override in `pom.xml`) because several concurrency and
buffering tests allocate aggressively.

## What lives here

Grouped by the problem each family solves, since the flat package listing does not make this obvious:

- **Exception adaptation** — `IuException` plus the `Unsafe*` functional interfaces
  (`UnsafeRunnable`, `UnsafeSupplier`, `UnsafeFunction`, `UnsafeConsumer`, `UnsafeBiFunction`,
  `UnsafeBiConsumer`). These exist so no other module writes exception-translation boilerplate.
  `unchecked(...)` wraps and rethrows; `checked(SomeException.class, ...)` narrows back to a declared
  checked type. `Error` and `IllegalStateException` always pass through unwrapped.
- **Object contracts** — `IuObject`: `require`/`requireType` for validation, `once` for
  write-exactly-once fields on builders, `first` for coalescing, `assertNotOpen` for module-boundary
  checks, `isPlatformName` for distinguishing JDK/framework names from application names, plus
  null-safe `hashCode`/`equals`/`compareTo`/`typeCheck` helpers.
- **Async and concurrency** — `IuAsynchronousPipe`, `IuAsynchronousSubject`,
  `IuAsynchronousSubscription`, `IuTaskController`, `IuUtilityTaskController`,
  `IuParallelWorkloadController`, `IuRateLimitter`.
- **Caching and data** — `IuCachedValue`, `IuCacheMap`, `IuDataStore`, `InMemoryDataStore`,
  `IuEnumerableQueue`, `IuIterable`, `IuVisitor`.
- **HTTP/web primitives with no servlet dependency** — `IuWebUtils`, `IuForwardedHeader`,
  `IuWebAuthenticationChallenge`, `IuStatefulRedirect`, `IuRequestAttributes`.
- **Bootstrap configuration** — `IuRuntimeEnvironment`. This is the *only* sanctioned reader of
  system properties and environment variables in the repository, and its Javadoc is explicit that it
  should be used sparingly, sufficient only to bootstrap the real configuration layer
  (`edu.iu.config.IuConfig`).
- **Standard exceptions** — `IuBadRequestException`, `IuAuthorizationFailedException`,
  `IuNotFoundException`, `IuOutOfServiceException`. Higher modules map these to protocol responses,
  so throw these rather than inventing new types for the same conditions.
- **Observation** — `IuListener` (a `uses` service in `module-info.java`) and `IuObservableEvent`.
  `jdbc/monitor` publishes through this.
- **Misc** — `IdGenerator`, `IuDigest`, `IuText`, `IuStream`, `IuFixedLimitOutputBuffer`,
  `IuProcess`, `IuClassLoaderContext`.

## Editing notes

Everything here is on the hot path of every other module and is held to full branch coverage, so a
new utility method needs its failure branches exercised in the same commit. Prefer extending an
existing class in the right family over adding a new top-level type.
