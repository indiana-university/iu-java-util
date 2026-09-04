# CLAUDE.md — config

`iu-java-config` / module `iu.util.config` / package `edu.iu.config`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

The secure configuration layer. `IuConfig` is the single class in the public API, and it is the sanctioned way for every module above `base` to obtain settings. `edu.iu.IuRuntimeEnvironment` exists only to bootstrap *this* layer; application code should not read the environment directly.

Compiled with `--release 11`. `iu.util.client` and `iu.util.crypt` are `requires transitive`, so consumers get `IuVault` and the JOSE API alongside it.

## The registration model

Configuration is declared once at startup, then sealed:

```java
IuConfig.registerInterface("example.service", MyServiceConfig.class, IuVault.RUNTIME);
IuConfig.registerFactory(SomeType.class, key -> load(key));
IuConfig.seal();                       // no further registration permitted
MyServiceConfig cfg = IuConfig.load(MyServiceConfig.class, "prod");
```

- `registerInterface(prefix, configInterface, vault...)` binds an interface to Vault secrets under a prefix; the overload taking a `Duration` sets a cache TTL.
- `registerFactory(configType, load)` covers types that are not interface-shaped.
- `seal()` is one-way. Any registration attempt afterward fails, which is what makes configuration immutable for the life of the process. Tests that register must not leak state across cases.
- `adaptJson(Class)` / `adaptJson(Type)` produce the `IuJsonAdapter` used to bind a configuration interface, applying recursively to nested non-platform interfaces (`IuObject.isPlatformName` decides what counts as platform).

## The convention this module enforces

Implementation modules across the repository define their configuration as **interfaces** in an `iu.<area>.config` package that is both `exports` and `opens` in `module-info.java` — `opens` is required because binding is reflective. Examples to follow:

- `iu.session.config.IuSessionConfiguration`
- `iu.oidc.client.config.IuOidcClient`, `IuOidcProvider`
- `iu.saml.config.IuSamlServiceProviderMetadata`
- `iu.jdbc.pool.config.IuConnectionPoolConfiguration`
- `edu.iu.redis.IuRedisConfiguration`

When adding configuration to a module, add an interface to that module's `config` package and register it here rather than introducing a new properties file or environment variable.
