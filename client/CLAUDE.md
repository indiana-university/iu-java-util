# CLAUDE.md — client

`iu-java-client` / module `iu.util.client` / package `edu.iu.client`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

Functional-programming adapters over Jakarta JSON Processing and the JDK HTTP client, plus the
HashiCorp Vault integration that backs `config`. Compiled with `--release 11`.

`jakarta.json` and `java.net.http` are `requires transitive`, so anything depending on this module
also gets them. Adding a new transitive requirement here propagates widely — do it deliberately.

## What lives here

### JSON

`IuJson` is the entry point: parsing, serialization, and the `IuJsonBuilder` fluent construction
helper. `IuJsonAdapter<T>` is the bidirectional `JsonValue` ↔ Java binding used throughout the
repository — `config`, `crypt`, `el`, `session`, and `oidc` all register or consume adapters.
`IuJsonPropertyNameFormat` controls naming strategy (for example camelCase vs. snake_case) when
binding an interface.

When a module needs its configuration or wire format expressed as JSON, it defines an
`IuJsonAdapter` rather than hand-writing serialization.

### HTTP

`IuHttp` wraps a cached `HttpClient` with allowlisting, logging, and exception handling.
Proxy behavior is driven by `iu.http.proxy`, `iu.https.proxy`, and `iu.http.no.proxy`.
`HttpResponseHandler` and `HttpResponseValidator` compose response handling; `HttpException` carries
the failed response for diagnostics.

### Vault

`IuVault` reads a HashiCorp Vault K/V v2 secrets engine. Configuration comes from
`iu.vault.endpoint`, `iu.vault.secrets`, and either `iu.vault.token` (development) or the AppRole
triple `iu.vault.loginEndpoint` / `iu.vault.roleId` / `iu.vault.secretId` (CI/CD). `IuVault.RUNTIME`
is the ambient instance. `IuVaultSecret`, `IuVaultKeyedValue`, and `IuVaultMetadata` model the
responses.

### Remote invocation

`RemoteInvocationHandler` and the surrounding `RemoteInvocation*` types implement dynamic-proxy RPC
over HTTP, including transport of remote stack traces (`RemoteInvocationStackTraceElementDetail`,
`ThrowableRemoteInvocationFailure`) so a server-side failure surfaces with usable diagnostics on the
client.

## Testing notes

Tests here mock the HTTP layer rather than opening sockets. Remember that `iu-java-test` fails a test
on any unexpected log record, and `IuHttp` logs every request — expect or allow those records
explicitly.
