# CLAUDE.md — client

`iu-java-client` / module `iu.util.client` / package `edu.iu.client`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

Functional-programming adapters over Jakarta JSON Processing and the JDK HTTP client, plus the HashiCorp Vault integration that backs `config`. Compiled with `--release 11`.

`jakarta.json` and `java.net.http` are `requires transitive`, so anything depending on this module also gets them. Adding a new transitive requirement here propagates widely — do it deliberately.

## What lives here

### JSON

`IuJson` is the entry point: parsing, serialization, and the `IuJsonBuilder` fluent construction helper. `IuJsonAdapter<T>` is the bidirectional `JsonValue` ↔ Java binding used throughout the repository — `config`, `crypt`, `el`, `session`, and `oidc` all register or consume adapters. `IuJsonPropertyNameFormat` controls naming strategy (for example camelCase vs. snake_case) when binding an interface.

`IuJsonSerializationOptions` carries the Java → JSON options — the property name format, plus whether a readable property with a null value is included as `JsonValue.NULL` rather than omitted — and is taken as a `Supplier`, so an adapter reads a fresh snapshot per conversion and observes a reconfiguration without being rebuilt. The same supplier propagates into nested value types, so one change reaches a whole captured adapter tree. Nulls are omitted by default, because Java draws no distinction between null and undefined; opt in only for a consumer that does, notably JavaScript UI code. Two things this deliberately does not touch: `IuJson.add` always omits nulls, which is what the JOSE writers in `crypt/impl`, `logging/impl`, and Vault merge patch (where a JSON null *deletes* a key) rely on; and a value wrapped by `IuJson.wrap` still serializes as its source `JsonObject` verbatim, which is what lets hierarchical data handled through a stub interface keep properties the stub doesn't declare.

When a module needs its configuration or wire format expressed as JSON, it defines an `IuJsonAdapter` rather than hand-writing serialization.

### HTTP

`IuHttp` wraps a cached `HttpClient` with allowlisting, logging, and exception handling. Proxy behavior is driven by `iu.http.proxy`, `iu.https.proxy`, and `iu.http.no.proxy`. `HttpResponseHandler` and `HttpResponseValidator` compose response handling; `HttpException` carries the failed response for diagnostics.

### Vault

`IuVault` reads a HashiCorp Vault K/V v2 secrets engine. Configuration comes from `iu.vault.endpoint`, `iu.vault.secrets`, and either `iu.vault.token` (development) or the AppRole triple `iu.vault.loginEndpoint` / `iu.vault.roleId` / `iu.vault.secretId` (CI/CD). `IuVault.RUNTIME` is the ambient instance. `IuVaultSecret`, `IuVaultKeyedValue`, and `IuVaultMetadata` model the responses.

### Remote invocation

`RemoteInvocationHandler` and the surrounding `RemoteInvocation*` types implement dynamic-proxy RPC over HTTP, including transport of remote stack traces (`RemoteInvocationStackTraceElementDetail`, `ThrowableRemoteInvocationFailure`) so a server-side failure surfaces with usable diagnostics on the client.

## Testing notes

Tests here mock the HTTP layer rather than opening sockets. Remember that `iu-java-test` fails a test on any unexpected log record, and `IuHttp` logs every request — expect or allow those records explicitly.
