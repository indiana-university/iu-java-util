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

`api` also re-exports `jakarta.json.bind`, since `WebToken.jsonb()` returns a `Jsonb`. `impl` requires `iu.util.crypt.impl` for `CryptJsonAdapters.config()`, which the token Jsonb builds on; both it and the JSON-B API are `provided`. `impl` no longer requires `iu.util.config`.

## Claim conversion

Claims convert by one `Jsonb`, `TokenJsonb` in `impl`, exposed as `WebToken.jsonb()` for modules that carry values in tokens of their own (session, oidc): `CryptJsonAdapters.config()` (snake_case names, base64url binary, JOSE values and crypt components) plus a NumericDate adapter, so **every** `Instant`, nested ones included, is seconds since the epoch; a fraction is dropped on read. It is created on first use, including the first builder, parse, or verify, and that seals registration through `WebToken.registerAdapter`, `registerSerializer`, and `registerDeserializer`.

`Jwt` holds its claims as `IuJsonProperties`. `JwtBuilder` collects claims once each (a null value leaves a claim as it is; a different value fails) and builds from their JSON, so a built token reads exactly as the same token parsed. A single `aud` string reads as an audience of one.

Configuration binding is separate: `IuConfig.jsonb()` uses ISO-8601 dates and resolves stored references, neither of which applies to tokens.

## API surface (`edu.iu.jwt`)

- `WebToken` — the token facade. Static entry points: `builder()`, `verify(jwt, issuerKey)`, and `decryptAndVerify(jwt, issuerKey, audienceKey)` for nested JWE-in-JWS tokens; `jsonb()` and the `register*` methods for claim conversion.
- `WebTokenBuilder` — claim construction, signing, and encryption.
- `IuAuthorizationDetails` — base interface for RFC 9396 `authorization_details` claims. Extend it per authorization type; `WebToken` binds the claim to the supplied interface.
- `IuCallerAttributes` — caller identity claims.

## Editing notes

Adding a claim accessor means touching `WebToken` (interface), `WebTokenBuilder` (interface), and `iu.jwt.Jwt` / `iu.jwt.JwtBuilder` (implementation) together. Because the two modules are separated by a service boundary, a missing implementation compiles and only fails when exercised — cover new claims in `impl` tests, not just `api` tests.
