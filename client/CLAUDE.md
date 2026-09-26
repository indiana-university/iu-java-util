# CLAUDE.md — client

`iu-java-client` / module `iu.util.client` / package `edu.iu.client`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

Functional-programming adapters over Jakarta JSON Processing and the JDK HTTP client, plus the HashiCorp Vault integration that backs `config`. Compiled with `--release 11`.

`jakarta.json` and `java.net.http` are `requires transitive`, so anything depending on this module also gets them. Adding a new transitive requirement here propagates widely — do it deliberately.

## What lives here

### JSON

`IuJson` is the entry point: parsing, serialization, and the `IuJsonBuilder` fluent construction helper. `IuJsonAdapter<T>` is the bidirectional `JsonValue` ↔ Java binding used throughout the repository — `config`, `crypt`, `el`, `session`, and `oidc` all register or consume adapters. `IuJsonPropertyNameFormat` controls naming strategy (for example camelCase vs. snake_case) when binding an interface.

`IuJsonSerializationOptions` carries the Java → JSON options — the property name format, whether a readable property with a null value is included as `JsonValue.NULL` rather than omitted, and whether an enum value is written as an object rather than as text — and is taken as a `Supplier`, so an adapter reads a fresh snapshot per conversion and observes a reconfiguration without being rebuilt. The same supplier propagates into nested value types, so one change reaches a whole captured adapter tree. Nulls are omitted by default, because Java draws no distinction between null and undefined; opt in only for a consumer that does, notably JavaScript UI code. Two things this deliberately does not touch: `IuJson.add` always omits nulls, which is what the JOSE writers in `crypt/impl`, `logging/impl`, and Vault merge patch (where a JSON null *deletes* a key) rely on; and a value wrapped by `IuJson.wrap` still serializes as its source `JsonObject` verbatim, which is what lets hierarchical data handled through a stub interface keep properties the stub doesn't declare.

`isEnumAsObject` exists for a consumer with no decoded metadata for the enum type — a REST client or a UI, which receives `"ACTIVE"` and has nowhere to get a display label or anything else the constant carries. It writes a `name` property holding `Enum.name()`, then the constant's JavaBeans properties, converted the same way a business object's are; properties inherited from `Object` and `Enum` are skipped, so `declaringClass` never appears. Introspection uses the enum type rather than the value's class, so a constant with a class body keeps the shape of its enum while still answering its own overrides. Reading is the asymmetric half and does not consult the options at all: `EnumJsonAdapter` accepts either form always, taking `name` from an object and ignoring every other property, which is what lets a value written as an object be read by a consumer that leaves the option off. Note the text form is still `Enum.toString()` while `name` is `Enum.name()`, so only the object form is guaranteed to convert back — and an enum that declares its own `name` property supplies that value instead, which is only sound when it answers the constant name.

When a module needs its configuration or wire format expressed as JSON, it defines an `IuJsonAdapter` rather than hand-writing serialization.

### HTTP

`IuHttp` wraps a cached `HttpClient` with allowlisting, logging, and exception handling. Proxy behavior is driven by `iu.http.proxy`, `iu.https.proxy`, and `iu.http.no.proxy`. `HttpResponseHandler` and `HttpResponseValidator` compose response handling; `HttpException` carries the failed response for diagnostics.

### Vault

`IuVault` reads a HashiCorp Vault K/V v2 secrets engine. Configuration comes from `iu.vault.endpoint`, `iu.vault.secrets`, and either `iu.vault.token` (development) or the AppRole triple `iu.vault.loginEndpoint` / `iu.vault.roleId` / `iu.vault.secretId` (CI/CD). `IuVault.RUNTIME` is the ambient instance. `IuVaultSecret`, `IuVaultKeyedValue`, and `IuVaultMetadata` model the responses.

### Remote invocation

`RemoteInvocationHandler` and the surrounding `RemoteInvocation*` types implement dynamic-proxy RPC over HTTP, including transport of remote stack traces (`RemoteInvocationStackTraceElementDetail`, `ThrowableRemoteInvocationFailure`) so a server-side failure surfaces with usable diagnostics on the client.

## Testing notes

Tests here mock the HTTP layer rather than opening sockets. Remember that `iu-java-test` fails a test on any unexpected log record, and `IuHttp` logs every request — expect or allow those records explicitly.
