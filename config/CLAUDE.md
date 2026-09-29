# CLAUDE.md — config

`iu-java-config` / module `iu.util.config` / package `edu.iu.config`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

The secure configuration layer. `IuConfig` is the single class in the public API, and it is the sanctioned way for every module above `base` to obtain settings. `edu.iu.IuRuntimeEnvironment` exists only to bootstrap *this* layer; application code should not read the environment directly.

Compiled with `--release 11`. `iu.util.client`, `iu.util.crypt`, and `jakarta.json.bind` are `requires transitive`, so consumers get `IuVault`, the JOSE API, and `Jsonb` alongside it. `iu.util.crypt.impl` is required for `CryptJsonAdapters.config()`, which configuration binding builds on; both it and the JSON-B API are `provided`.

## The registration model

Configuration is declared once at startup, then sealed:

```java
IuConfig.registerInterface("example.service", MyServiceConfig.class, IuVault.RUNTIME);
IuConfig.registerFactory(SomeType.class, key -> load(key));
IuConfig.seal();                       // no further registration permitted
MyServiceConfig cfg = IuConfig.load(MyServiceConfig.class, "prod");
```

- `registerInterface(prefix, configType, vault...)` binds a type to Vault secrets under a prefix; the overload taking a `Duration` sets a cache TTL. Wherever the type is bound, a JSON string refers to a stored value by key; anything else passes down the JSON-B chain to the type's own conversion, so a type with a component of its own, such as `WebKey`, can be stored by reference too.
- `registerFactory(configType, load)` covers types that are not interface-shaped.
- `registerAdapter`, `registerSerializer`, and `registerDeserializer` add JSON-B components for configuration binding; wrap a lambda with `IuJsonAdapter.typedAdapter`, `typedSerializer`, or `typedDeserializer`.
- `jsonb()` is the `Jsonb` that binds configuration: `CryptJsonAdapters.config()` (snake_case names, base64url binary, JOSE values for algorithms and key types, crypt's components for keys and certificates) with JSON-B's defaults otherwise, so ISO-8601 dates and enums by `name()`, plus the registered components. It is created on first use, including the first `load` of a stored value, and creating it seals registration.
- `seal()` is one-way. Any registration attempt afterward fails, which is what makes configuration immutable for the life of the process. Tests that register must not leak state across cases.

Removed in 7.1: `adaptJson(Class)`, `adaptJson(Type)`, and `registerAdapter(Class, IuJsonAdapter)`. Stored configuration follows the formats above, so secrets written in the pre-7.1 format need migrating where those differ: a `WebKey.Algorithm` or `WebEncryption.Encryption` value is its JOSE name (`RSA-OAEP`, `A128CBC-HS256`), not its enum constant name (`RSA_OAEP`, `AES_128_CBC_HMAC_SHA_256`), and `byte[]` is base64url.

## The convention this module enforces

Implementation modules across the repository define their configuration as **interfaces** in an `iu.<area>.config` package that is both `exports` and `opens` in `module-info.java` — `opens` is required because binding is reflective. Examples to follow:

- `iu.session.config.IuSessionConfiguration`
- `iu.oidc.client.config.IuOidcClient`, `IuOidcProvider`
- `iu.saml.config.IuSamlServiceProviderMetadata`
- `iu.jdbc.pool.config.IuConnectionPoolConfiguration`
- `edu.iu.redis.IuRedisConfiguration`

When adding configuration to a module, add an interface to that module's `config` package and register it here rather than introducing a new properties file or environment variable.
