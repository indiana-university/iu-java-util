# CLAUDE.md — jwt

`iu-java-jwt-api`, `iu-java-jwt-impl` / modules `iu.util.jwt.api`, `iu.util.jwt.impl`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

JSON Web Token issuance and verification, layered directly on `crypt`'s JOSE primitives. Consumed by `oidc` and `session`.

Both modules compile with `--release 11`. `api` re-exports `iu.util` and `iu.util.crypt` transitively.

## Layout

| Module | module-info highlights |
|---|---|
| `api` | `exports edu.iu.jwt`, `exports iu.jwt.spi`, `uses iu.jwt.spi.IuJwtSpi` |
| `impl` | exports **nothing** — `provides IuJwtSpi with iu.jwt.JwtSpi` only |

Note that `iu.jwt.spi` is exported unqualified here, unlike `crypt` which restricts its SPI export to the implementation module. The implementation module exports no packages at all, so `iu.jwt.Jwt` and `iu.jwt.JwtBuilder` are reachable only through the SPI.

`impl` additionally requires `iu.util.config`, so token verification can resolve issuer keys from configuration.

## API surface (`edu.iu.jwt`)

- `WebToken` — the token facade. Static entry points: `builder()`, `verify(jwt, issuerKey)`, and `decryptAndVerify(jwt, issuerKey, audienceKey)` for nested JWE-in-JWS tokens.
- `WebTokenBuilder` — claim construction, signing, and encryption.
- `IuAuthorizationDetails` — base interface for RFC 9396 `authorization_details` claims. Extend it per authorization type; `WebToken` binds the claim to the supplied interface.
- `IuCallerAttributes` — caller identity claims.

## Editing notes

Adding a claim accessor means touching `WebToken` (interface), `WebTokenBuilder` (interface), and `iu.jwt.Jwt` / `iu.jwt.JwtBuilder` (implementation) together. Because the two modules are separated by a service boundary, a missing implementation compiles and only fails when exercised — cover new claims in `impl` tests, not just `api` tests.
