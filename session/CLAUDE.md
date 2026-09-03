# CLAUDE.md — session

`iu-java-session-api`, `iu-java-session-impl` / modules `iu.util.session`, `iu.util.session.impl`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

Container-agnostic session management. Session state is carried in an encrypted JWT rather than held
server-side, so `session` sits on top of `crypt` and `jwt` and is in turn consumed by `oidc` and
`saml` to carry authentication state across redirects.

`api` compiles at the inherited level; `impl` overrides `release` to **17**.

## Layout

| Module | Contents |
|---|---|
| `api` | `edu.iu.session` — `IuSession`, `IuSessionHandler` |
| `impl` | `iu.session` — `Session`, `SessionHandler`, `SessionDetail`, `SessionDetailAttributes`; `iu.session.config` — `IuSessionConfiguration` |

Like `pki`, this pair uses direct instantiation rather than a `ServiceLoader` SPI: `iu.util.session.impl`
exports `iu.session` and `iu.session.config` (the latter also `opens` for configuration binding).

## Model

`IuSessionHandler` is the lifecycle boundary, expressed in terms of `java.net.HttpCookie` so it works
with any HTTP stack:

```java
IuSession create();
IuSession activate(Iterable<HttpCookie> cookies);   // decrypt and verify from request cookies
String store(IuSession session);                    // re-encrypt, returns the Set-Cookie value
void remove(Iterable<HttpCookie> cookies);
```

`IuSession` holds typed **details** rather than a string-keyed attribute map: `getDetail(Class<T>)`
returns an interface-shaped view backed by the session's JSON, created on demand. Modules layered on
top define their own detail interfaces (`oidc` and `saml` each store their authentication state this
way) instead of sharing a namespace of attribute keys. `setStrict(boolean)` controls whether unknown
detail data is tolerated; `isChanged()` drives whether `store` needs to re-issue the cookie.

`iu.session.config.IuSessionConfiguration` supplies the signing/encryption `WebKey`, the JWE
`Encryption` algorithm, and TTLs — defaulting to 15 minutes inactive and 12 hours maximum.

## Testing notes

`src/test/resources/META-INF/iu-test.properties` sets `iu.util.test.platformLoggers=edu.iu.crypt` and
exports `project.build.testOutputDirectory` for tests that need to resolve paths on disk.
