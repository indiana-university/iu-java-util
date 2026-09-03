# CLAUDE.md — oidc

`iu-java-oidc-api`, `iu-java-oidc-client` / modules `iu.util.oidc`, `iu.util.oidc.client`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

OpenID Connect and OAuth 2.0 relying-party support: discovery, the authorization code flow, and the
non-interactive grant types. Built on `jwt` for token verification and `session` for carrying
authentication state across the browser redirect.

Compiled with `--release 11`.

## Layout

Note the naming: the second module is `client`, not `impl`.

| Module | Contents |
|---|---|
| `api` | `edu.iu.oidc` — `IuOidcAuthorization`, `IuOidcPrincipal`, `IuOidcProviderMetadata`, `IuOidcTokenResponse` |
| `client` | `iu.oidc.client` (grants and session details), `iu.oidc.client.config` (exported and `opens`) |

## The authorization code flow

```java
IuStatefulRedirect init(String delegatingPrincipal, String impersonatedPrincipalName, ...);
IuStatefulRedirect authorize(IuRequestAttributes attributes, String code, String state);
IuOidcPrincipal getAuthorizedPrincipal(IuRequestAttributes attributes);
```

`init` produces the redirect to the provider; `authorize` consumes the callback's `code` and `state`;
`getAuthorizedPrincipal` reads the resulting identity back from the session. State is held in
`OidcPreAuthSession` before the redirect and `OidcPostAuthSession` after — both are `session` details,
not server-side storage, so they must stay small and serializable.

## Grant types

Each grant is its own class implementing the shared `AuthorizationGrant` contract, so adding a grant
means adding a class rather than branching an existing one:

`ClientCredentialsGrant`, `PasswordGrant`, `RefreshTokenGrant`, `JwtBearerGrant`,
`OnBehalfOfGrant`, `OidcTokenGrant`.

## Provider discovery

`OidcProviders.getMetadata(config)` resolves `.well-known` discovery documents over `IuHttp`, binding
the JSON to `IuOidcProviderMetadata` through an `IuJsonAdapter`. Results are cached per issuer URI in
a static `Map` guarded by synchronization, with a refresh interval. Configuration may supply metadata
inline instead of an issuer, which bypasses the fetch — check `IuOidcProvider.getMetadata()` before
assuming a network call happens.

## Configuration (`iu.oidc.client.config`)

- `IuOidcClient` — client credentials and policy: `clientId`/`clientSecret`, basic-auth toggle,
  assertion JWK and TTL (default 2 minutes), decryption JWKs, token TTL (default 15 minutes), max age
  (default 12 hours), and the claim used as the principal name.
- `IuOidcProvider` — issuer, metadata URI or inline metadata.
- `IuOidcClientReference` — links a client to its provider.

These are bound through `IuConfig.registerInterface`, which is why the package is `opens`. When
adding a setting, prefer a `default` method on the interface so existing deployments keep working.
