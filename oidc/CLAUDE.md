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
| `api` | `edu.iu.oidc` — the claim contract, and whatever is identical for every RP and OP endpoint: `IuOidcClaims`, `IuOidcAddress`, `IuOidcActor`, `IuOidcProviderMetadata`, `IuOidcAuthorization`, `IuOidcPrincipal`, `IuOidcTokenResponse` |
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

`IuOidcProviderReference` is not a structural interface — an integration declares it explicitly. Only `getConfiguration()` has no default; everything else either refuses by name (`throw new UnsupportedOperationException("Missing client source")`) or defaults safely. `isProduction()` defaults to `true` so a forgotten binding closes the token-exchange backdoor rather than opening it.

There is deliberately no `adaptJson` on it. That pattern did not work out on `IuOidcClientReference` and is not repeated here: an OP integration depends on `IuConfig` independently and configures both the JSON its endpoints answer with and the claim adapters a stored grant round-trips through.

### The SPIs a deployment binds

| SPI | Operation |
|---|---|
| `IuOidcClientSource` | `client(clientId)` — read a relying party's registration |
| `IuOidcClaimsSource` | `admitted(scope, usage)` — name the claims a scope of the deployment's own releases; `claims(principalName, admittedClaims, builder)` — write them onto a token; `claims(principalName, admittedClaims)` — render them as a document |
| `IuOidcIdentitySource` | `hasRole(principalName, roles...)` — decide entitlement |
| `IuOidcAuthorizationDetailsSource` | `authorize(details, principalName)` — decide what RFC 9396 details release |

`IuOidcClaimsSource` answers for one principal and must answer for the one it was asked about: an unsigned UserInfo document names `principalName` back as `sub`, and nothing written onto a token contradicts the `sub` already on it. The provider does not check — it cannot parse what it publishes — so a source answering for somebody else is caught by the relying party, which refuses a response whose `sub` disagrees with the ID token it holds.

**`iu.oidc.provider` never reads a claim.** It names what a grant admits and the source does the rest: `claims(principalName, admittedClaims, builder)` writes typed claims onto a token being issued, and `claims(principalName, admittedClaims)` renders the document an *unsigned* UserInfo response publishes. `IuOidcClaims` is implementation-facing — it states which claim a property is and what type it serializes as, so a source can apply those types — and nothing in `provider` imports it. A signed UserInfo response is a JWT and takes the builder form like any other token, which is why `iss` and `aud` are no longer arguments to `claims()`: the provider writes both itself. Redeclaring `toString()` on an interface obliges no implementation to override it — it documents, it does not enforce.

Disclosure is decided in two halves. The §5.4 claim sets are the provider's, mapped in `OidcClaimScopes` from the granted scope; every other scope is the deployment's, and `admitted(scope, usage)` names what those release. A source only ever sees the scopes OIDC does not define — `OidcClaimScopes.additional(scope)` is what splits them — so an implementation never reasons about `profile` or `email`. The `usage` says where the result is bound: `USERINFO` is the widest disclosure, `ID_TOKEN` is narrower because the client keeps it, and `ACCESS_TOKEN` is narrower still. An access token gets **none** of the §5.4 sets — RFC 9068 defines no claim describing the end user — so only what a deployment names for a scope of its own reaches one, which is how a resource server sees a claim without a UserInfo request of its own.

A source writes before the provider sets the claims it derives — `at_hash`, `act`, `roles`, `auth_time`, `authorization_details` — so nothing a deployment names displaces them. With nothing admitted the source is not asked to write at all.

**An additional scope must be registered on an `IuOidcClientResource.getScope()`**, or `OidcAuthorizeEndpoint` refuses the request as `invalid_scope` before a claims source is consulted at all. Discovery advertises provider capability — `scopes_supported` always names `openid` and `offline_access`, and `claims_supported` is derived from it — while that registration decides who may actually ask.

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

A refresh must authenticate the way the code was redeemed. `OidcGrant.getTokenEndpointAuthMethod()` records it, answered at redemption through a view over the read-only grant (`OidcTokenEndpoint.authenticatedBy`) and carried through every rotation because a refresh token is filed from the grant it redeemed. A grant recording no method is refused. This is what keeps a confidential line from being continued with no credential through an endpoint that also registers a public record.

### Impersonation is RFC 8693 token exchange, at the token endpoint

Answering for somebody else is a second token request, not a parameter on the first. A client presents an access token this provider issued as `actor_token`, names the principal it wants instead as `subject_token` under the provider-defined `https://iu.edu/oauth/token-type/principal-name` type, and gets back tokens whose `sub` is that principal and whose `act` claim is the one that authenticated. The subject is *asserted* — nobody holds a token for the party being impersonated — so what authorizes it is `IuOidcClientEndpoint.getBackdoorRoles()` held by the actor, outside production only, and only for a client that authenticated — a public client is refused, since the `actor_token` would be the only credential in the request.

Four things keep an exchange narrower than what it descends from, and each is load-bearing rather than incidental:

- The `actor_token` must name this issuer in its `aud`, so a token issued for an external API resource cannot be exchanged — the token endpoint is a resource server for it here, exactly as `OidcUserinfoEndpoint` is.
- Granted scope is intersected with the `actor_token`'s own, so exchanging never gains authority.
- `offline_access` is dropped, so no refresh token descends from an exchange.
- An `actor_token` that already carries `act` is refused, and so is one whose subject is what the exchange asked for. The first would drop a link from the delegation chain `IuOidcActor` has no room to nest; the second would make an exchange a way to renew a token past the age its authentication was good for.

`auth_time` is the subtle one. An exchanged token's subject never authenticated, so neither token carries a top-level `auth_time` — the actor's own time rides inside `act` as a NumericDate, which is both true and the only age worth measuring. `respond()` and `idToken()` both branch on `Redeemed.impersonated()` for this. Access tokens now carry `auth_time` generally (RFC 9068 §2.2.1) because an exchange has nowhere else to read the actor's authentication back from.

`Redeemed` is what every grant that answers for an end user reaches `respond()` through — `null` means the tokens answer for the client itself, as `client_credentials` does. It exists so a token exchange, which has no stored grant at all, takes the same path as a code or refresh redemption; its `grant()` member is only for re-filing a refresh token, which an exchange never issues.

### Supporting types

`OidcProviderUtils` holds the request-shaping logic every endpoint shares — scope splitting, resource validation and matching, audience derivation, error URIs. Put shared logic there rather than reaching across endpoints. `OidcClaimScopes` owns the scope vocabulary OIDC defines: `admitted(scope)` maps a granted scope to the §5.4 claim sets, deny-by-default and with `sub` coming from `openid` rather than unconditionally, and `additional(scope)` names what is left over for the claims source. `OidcJose` signs and encrypts an already-serialized document and is deliberately unaware of what it is securing. `ClientAuthenticator` verifies a presented credential against one registration and refuses a replayed assertion through `IuDataStore`.

## The relying party (`iu.oidc.client`)

```java
IuStatefulRedirect init(String delegatingPrincipal, ...);
IuStatefulRedirect authorize(IuRequestAttributes attributes, String code, String state);
IuOidcPrincipal getAuthorizedPrincipal(IuRequestAttributes attributes);
```

`init` produces the redirect to the provider; `authorize` consumes the callback's `code` and `state`; `getAuthorizedPrincipal` reads the resulting identity back from the session. State is held in `OidcPreAuthSession` before the redirect and `OidcPostAuthSession` after — both are `session` details, not server-side storage, so they must stay small and serializable.

### Grant types

Each grant is its own class implementing the shared `AuthorizationGrant` contract, so adding a grant means adding a class rather than branching an existing one:

`ClientCredentialsGrant`, `PasswordGrant`, `RefreshTokenGrant`, `JwtBearerGrant`, `OnBehalfOfGrant`, `OidcTokenExchangeGrant`, `OidcTokenGrant`.

`OidcTokenExchangeGrant` is the second half of a two-step flow: authorize the end user normally, then present the access token they are the subject of as the RFC 8693 `actor_token` and name who to answer for instead. `OidcTokenGrant.isAuthTimeRequired()` exists for it — an exchanged ID token dates the actor inside `act`, never its own subject, so a maximum age is enforced against whatever is found in either place and required only where one must be.

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

**A nested bean claim does not get the JWT `Instant` treatment.** `iu.jwt.Jwt.adapt` answers the RFC 7519 NumericDate adapter only when the type *is* `Instant`, and recurses through itself only for `IuAuthorizationDetails`; anything else, a bean interface like `IuOidcActor` included, falls through to `IuConfig.adaptJson`, whose `Instant` adapter is `Instant::parse` — ISO-8601 text. So an `Instant` property on a nested claim serializes as a string no other implementation will read as a date. That is why `IuOidcActor.getAuthTime()` is a `Long`, matching how top-level `auth_time` is already written. Round-trip tests pass either way, since the same adapter reads it back; only the wire is wrong. Declare a NumericDate as `Long` unless you are writing it at the top level.
