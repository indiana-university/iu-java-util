# CLAUDE.md — oidc

`iu-java-oidc-api`, `iu-java-oidc-client`, `iu-java-oidc-config`, `iu-java-oidc-provider` / modules `iu.util.oidc`, `iu.util.oidc.client`, `iu.util.oidc.config`, `iu.util.oidc.provider`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

Both sides of OpenID Connect and OAuth 2.0. The relying party — discovery, the authorization code flow, the non-interactive grant types — and the OpenID Provider: authorization, token, UserInfo, and the JWK Set. Built on `jwt` for token verification, `crypt` for signing and encryption, and `session` for carrying authentication state across the browser redirect.

Compiled with `--release 11`, except `provider`, which is 21 for sealed interfaces and records.

## Layout

Note the naming: the second module is `client`, not `impl`. The four are not an api/impl SPI split — each is its own thing.

| Module | Contents |
|---|---|
| `api` | `edu.iu.oidc` — the claim contract, and whatever is identical for every RP and OP endpoint: `IuOidcClaims`, `IuOidcAddress`, `IuOidcProviderMetadata`, `IuOidcAuthorization`, `IuOidcPrincipal`, `IuOidcTokenResponse` |
| `client` | `iu.oidc.client` (grants and session details), `iu.oidc.client.config` (exported and `opens`) — the relying party |
| `config` | `edu.iu.oidc.config` (exported and `opens`) — the OP's integration layer: what a deployment configures, and the SPIs it binds |
| `provider` | `iu.oidc.provider` — the OP's endpoints and the utilities behind them |

The delineation between `config` and `provider` is deliberate and worth preserving: **`config` holds interfaces a deployment implements over its own backing resources, and none of those implementations may depend on `provider`.** `provider` depends on `config`, never the reverse.

## The provider (`iu.oidc.provider`)

### One reference, not a collaborator per seam

Every endpoint takes a single `IuOidcProviderReference` and reads everything through it, so adding an endpoint doesn't add a constructor argument and two endpoints cannot be wired to disagree about who the provider is.

```java
public OidcTokenEndpoint(IuOidcProviderReference reference)
```

`IuOidcProviderReference` is not a structural interface — an integration declares it explicitly. Only `getConfiguration()` has no default; everything else either refuses by name (`throw new UnsupportedOperationException("Missing client source")`) or defaults safely. `isProduction()` defaults to `true` so a forgotten binding closes the impersonation backdoor rather than opening it.

There is deliberately no `adaptJson` on it. That pattern did not work out on `IuOidcClientReference` and is not repeated here: an OP integration depends on `IuConfig` independently and configures both the JSON its endpoints answer with and the claim adapters a stored grant round-trips through.

### The SPIs a deployment binds

| SPI | Operation |
|---|---|
| `IuOidcClientSource` | `client(clientId)` — read a relying party's registration |
| `IuOidcClaimsSource` | `claims(principalName, admittedClaims, issuer, audience)` — read an end user's claims |
| `IuOidcIdentitySource` | `hasRole(principalName, roles...)` — decide entitlement |
| `IuOidcAuthorizationDetailsSource` | `authorize(details, principalName)` — decide what RFC 9396 details release |

`IuOidcClaimsSource` renders its own document: `toString()` **must** produce the claims document a UserInfo response carries, and `sub` **must** answer `principalName` back. The provider checks `sub` and trusts the rest, because it cannot parse what it publishes — see below. The `issuer`/`audience` pair is non-null only when the response will be signed, which OIDC §5.3.2 requires to carry `iss` and `aud`; every other caller passes null.

`IuOidcIdentitySource` only ever sees roles that need a real lookup — a role naming everyone (`all`) and a role matching the principal by name are settled in the endpoint. Throwing means the principal doesn't resolve (answered as `invalid_request`); returning `false` means it resolves and holds none of these roles.

### No JSON in `provider`

`iu.util.oidc.provider` does **not** require `iu.util.client`, and that is load-bearing rather than incidental. Consequences to respect when adding to this module:

- A response is returned as a typed result for the transport to serialize, never as a rendered document — `OidcAuthorizeResult` carries a `URI`, `OidcTokenResult` carries named fields spelled as the wire spells them.
- Anything the module needs read out of JSON arrives already parsed through the request interface. `OidcAuthorizeRequest.getAuthorizationDetails()` and `OidcTokenRequest.getClientAssertionIssuer()` are both there for that reason — the latter reads an unverified `iss` used only to select which registration to verify against.
- The one exception is `OidcIssuer.publishedJwks()`, which does serialize. A JWK Set is a cryptographic representation rather than a document a deployment has any say in, RFC 7517 fixes its shape, and `WebKey.asJwks` implements it without any JSON dependency.

### Endpoints

| Type | Entry point |
|---|---|
| `OidcAuthorizeEndpoint` | `authorize(OidcAuthorizeRequest)` → `OidcAuthorizeResult` (sealed: `Redirect`, `AuthenticationRequired`) |
| `OidcTokenEndpoint` | `token(OidcTokenRequest)` → `OidcTokenResult` (sealed: `Issued`, `Error`) |
| `OidcUserinfoEndpoint` | `userinfo(String accessToken)` → `OidcUserinfoResult` (sealed: `Json`, `Jwt`) |

The JWK Set has no endpoint class of its own because it takes no request: `OidcIssuer.publishedJwks()` answers the document, and `OidcProviderMetadata` derives the paths all four are served from, so what discovery advertises and what the module actually implements cannot drift.

Errors follow OAuth 2.0 rather than one convention. The authorization endpoint redirects an error to the client once `client_id` and `redirect_uri` check out, and raises `IuBadRequestException` before that point — redirecting to an unverified URI would make it an open redirector. The token endpoint returns `OidcTokenResult.Error` instead of throwing, because RFC 6749 §5.2 defines an error object the client parses.

### The authorization code flow, provider side

`authorize` runs in two passes. The first carries `client_id`, is validated, and is recorded as an `OidcGrant` in a session of its own; the second carries no parameters at all, which is what marks it as a resumption — `state`, `nonce`, and the PKCE challenge never travel through the identity provider or turn up in its logs. The session handler **must** be separate from the one the authentication mechanism uses, or each overwrites the other's cookie.

The completed grant is not what the code names. `GrantStore` signs and encrypts it, files it under a digest of the reference, and hands back the content encryption key as the opaque reference — so the store holds nothing it can read, and presenting a reference spends it.

### Supporting types

`OidcProviderUtils` holds the request-shaping logic every endpoint shares — scope splitting, resource validation and matching, audience derivation, error URIs. Put shared logic there rather than reaching across endpoints. `OidcClaimScopes.admitted(scope)` maps a granted scope to the OIDC §5.4 claim sets, deny-by-default. `OidcJose` signs and encrypts an already-serialized document and is deliberately unaware of what it is securing. `ClientAuthenticator` verifies a presented credential against one registration and refuses a replayed assertion through `IuDataStore`.

## The relying party (`iu.oidc.client`)

```java
IuStatefulRedirect init(String delegatingPrincipal, String impersonatedPrincipalName, ...);
IuStatefulRedirect authorize(IuRequestAttributes attributes, String code, String state);
IuOidcPrincipal getAuthorizedPrincipal(IuRequestAttributes attributes);
```

`init` produces the redirect to the provider; `authorize` consumes the callback's `code` and `state`; `getAuthorizedPrincipal` reads the resulting identity back from the session. State is held in `OidcPreAuthSession` before the redirect and `OidcPostAuthSession` after — both are `session` details, not server-side storage, so they must stay small and serializable.

### Grant types

Each grant is its own class implementing the shared `AuthorizationGrant` contract, so adding a grant means adding a class rather than branching an existing one:

`ClientCredentialsGrant`, `PasswordGrant`, `RefreshTokenGrant`, `JwtBearerGrant`, `OnBehalfOfGrant`, `OidcTokenGrant`.

### Provider discovery

`OidcProviders.getMetadata(config)` resolves `.well-known` discovery documents over `IuHttp`, binding the JSON to `IuOidcProviderMetadata` through an `IuJsonAdapter`. Results are cached per issuer URI in a static `Map` guarded by synchronization, with a refresh interval. Configuration may supply metadata inline instead of an issuer, which bypasses the fetch — check `IuOidcProvider.getMetadata()` before assuming a network call happens.

### Configuration (`iu.oidc.client.config`)

- `IuOidcClient` — client credentials and policy: `clientId`/`clientSecret`, basic-auth toggle, assertion JWK and TTL (default 2 minutes), decryption JWKs, token TTL (default 15 minutes), max age (default 12 hours), and the claim used as the principal name.
- `IuOidcProvider` — issuer, metadata URI or inline metadata.
- `IuOidcClientReference` — links a client to its provider. Its `adaptJson` has not turned out to be a useful pattern and may be deprecated; do not copy it.

These are bound through `IuConfig.registerInterface`, which is why the package is `opens`. When adding a setting, prefer a `default` method on the interface so existing deployments keep working — the same applies to `edu.iu.oidc.config`.

## Naming

`Iu` prefix on a class name when the package is `edu.iu.*`; omit it for package-private classes, and optionally when the package is `iu.*`. That is why `edu.iu.oidc.config.IuOidcClientEndpoint` carries it and `iu.oidc.provider.OidcTokenEndpoint` does not.

## Testing notes

`oidc/client` is one of the modules the root `CLAUDE.md` flags for JaCoCo "do not match with execution data": its tests mock its own classes, Mockito's inline mock maker retransforms them at load time, and coverage collapses to near zero while every test passes. Verify that module's coverage separately.

Two things bite repeatedly in `provider` tests:

- **A Mockito mock answers an unstubbed `Iterable` with an empty one, not `null`.** Any branch that distinguishes "named no parameter" from "named it emptily" — `getResource()`, `getCrl()`, `getRoles()`, `getReleasedAuthorizationDetails()` — needs `null` stubbed explicitly, or the branch is silently uncovered.
- **A mocked `WebKey` cannot be handed to the crypt implementation.** `iu.crypt.JoseBuilder.key` casts every key it receives to `iu.crypt.Jwk`. Anything that signs, verifies, or publishes a key needs a real one; for certificates, generate them with `openssl` through `IuProcess`, following `jwt/impl`'s `JwtTest`. Note that a single self-signed certificate produces an `x5t` header and no `x5c`, while a two-certificate chain produces `x5c` and a null `x5t` — which is exactly what `ClientAuthenticator` branches on.

`MemoryDataStore` in the `provider` test package is the shared `IuDataStore` fixture; use it rather than mocking the store when a test needs a real `GrantStore` round trip.
