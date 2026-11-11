# CLAUDE.md — jwt

`iu-java-jwt-api`, `iu-java-jwt-impl` / modules `iu.util.jwt.api`, `iu.util.jwt.impl`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

JSON Web Token issuance and verification, layered directly on `crypt`'s JOSE primitives. Consumed by `oidc` and `session`.

Both modules compile with `--release 17`. `api` re-exports `iu.util` and `iu.util.crypt` transitively.

## Layout

| Module | module-info highlights |
|---|---|
| `api` | `exports edu.iu.jwt`, `exports iu.jwt.spi`, `uses iu.jwt.spi.IuJwtSpi` |
| `impl` | exports **nothing** — `provides IuJwtSpi with iu.jwt.JwtSpi` only |

Note that `iu.jwt.spi` is exported unqualified here, unlike `crypt` which restricts its SPI export to the implementation module. The implementation module exports no packages at all, so `iu.jwt.Jwt` and `iu.jwt.JwtBuilder` are reachable only through the SPI.

`api` also re-exports `jakarta.json.bind`, since `WebToken.jsonb()` returns a `Jsonb`. It deliberately does not read `jakarta.json`: `WebToken.of(Object)` takes its source untyped, and `impl` checks the type at runtime. `impl` requires `iu.util.crypt` for `edu.iu.crypt.Init.jsonbConfig()`, which the token Jsonb builds on; both it and the JSON-B API are `provided`, and `iu.util.crypt.impl`, which supplies that configuration at runtime, is a test dependency. `impl` no longer requires `iu.util.config`.

## Claim conversion

Claims convert by one `Jsonb`, `TokenJsonb` in `impl`, exposed as `WebToken.jsonb()` for modules that carry values in tokens of their own (session, oidc): `edu.iu.crypt.Init.jsonbConfig()` (snake_case names, base64url binary, JOSE values and crypt components) plus a NumericDate adapter, so **every** `Instant`, nested ones included, is seconds since the epoch; a fraction is dropped on read. It is created on first use, including the first builder, parse, or verify, and that seals registration through `WebToken.registerAdapter`, `registerSerializer`, and `registerDeserializer`.

`Jwt` holds its claims as `IuJsonProperties`. `JwtBuilder` collects claims once each (a null value leaves a claim as it is; a different value fails) and builds from their JSON, so a built token reads exactly as the same token parsed.

A claim holding a single value reads as an array of one when `getClaim` asks for an array or `Iterable` type, so `aud`, `roles`, and similar claims need no shape check by the caller. `Jwt.isMultiValued` decides which types wrap: `byte[]` (base64url text) and `JsonArray` (converts as itself) never do, and JSON null reads as null. Deciding reads the claim's JSON through `claims.toJsonObject()`, which caches no type for the claim, so a later scalar read of the same claim (`Map`, `Object`) still converts; an array or `Iterable` read converts that JSON through a fresh index bound to `TokenJsonb` on every call. It must not read the claim as `JsonValue` through `claims.get`, nor fall through to `claims.get(name, type)`: `IuJsonProperties.fit` checks only the erased type, and a cached `JsonArray` already *is* a `List` (and a cached `JsonObject` a `Map`), so `List<String>` would come back holding `JsonString`s. An empty `aud` array reads as no audience: `getAudience` returns null.

`WebToken.of(source)` creates a token from claims that weren't received as a signed token — a `JsonObject` or JSON object text, both read by `TokenJsonb` like every other `Jwt`; anything else, `IuJsonProperties` included, is `IllegalArgumentException`. Because every `Jwt` is bound to `TokenJsonb`, converting through a fresh `TokenJsonb` index never departs from how the token reads otherwise. It never verifies a signature, but the point in time claims are validated as for every `Jwt`: claims that are present must be valid. `oidc/client` reads UserInfo claims through it, so they convert exactly as ID token claims do.

Configuration binding is separate: `IuConfig.jsonb()` binds with ISO-8601 dates and resolves stored references, neither of which applies to tokens.

## API surface (`edu.iu.jwt`)

- `WebToken` — the token facade. Static entry points: `builder()`, `verify(jwt, issuerKey)`, and `decryptAndVerify(jwt, issuerKey, audienceKey)` for nested JWE-in-JWS tokens; `of(source)` for unsigned claims, which verifies nothing; `jsonb()` and the `register*` methods for claim conversion.
- `WebTokenBuilder` — claim construction, signing, and encryption.
- `IuAuthorizationDetails` — base interface for RFC 9396 `authorization_details` claims. Extend it per authorization type; `WebToken` binds the claim to the supplied interface.
- `IuCallerAttributes` — caller identity claims.

## Editing notes

Adding a claim accessor means touching `WebToken` (interface), `WebTokenBuilder` (interface), and `iu.jwt.Jwt` / `iu.jwt.JwtBuilder` (implementation) together. Because the two modules are separated by a service boundary, a missing implementation compiles and only fails when exercised — cover new claims in `impl` tests, not just `api` tests.
