# CLAUDE.md — client

`iu-java-client` / module `iu.util.client` / package `edu.iu.client`

Read the repository root `CLAUDE.md` first for build commands and shared conventions. `README.md` in this directory is the developer guide: what every option, annotation, and built-in type does. This file covers what matters when changing the module.

## Role

JSON conversion over Jakarta JSON Processing — the IU conversions and a JSON-B provider sharing one set of built-ins and one property model — plus the JDK HTTP client wrapper, the HashiCorp Vault integration that backs `config`, and remote invocation. Compiles with `--release 17`.

`jakarta.json` and `java.net.http` are `requires transitive`, so anything depending on this module also reads them; `jakarta.json` is a `provided` dependency, so the application supplies the API and an implementation. `jakarta.json.bind` is `requires static` and `provided`: optional at runtime. The module `provides jakarta.json.bind.spi.JsonbProvider with iu.client.jsonb.IuJsonbProvider`; there is no `META-INF/services` entry, since the module's components assert they run as a named module that isn't open. Adding a new transitive requirement here propagates widely — do it deliberately.

Only `edu.iu.client` is exported. Anything an application must name — config property keys, factories — belongs there, typically on `IuJsonAdapter`, even when its implementation lives in `iu.client.jsonb`; a public member of an internal package is invisible outside the module.

## Layout

| Package | Holds |
|---|---|
| `edu.iu.client` | the public API: `IuJsonAdapter`, `IuJson`, `IuJsonProperties`, `IuJsonSerializationOptions`, `IuHttp`, `IuVault`, `RemoteInvocationHandler` |
| `iu.client` | the built-in conversions (`JsonAdapters` and its adapters), the IU business-object paths (`JsonSerializer`, `JsonDeserializer`, `JsonProxy`), the shared property model (`BeanModel`), `BindingMetadata`, `FormatAdapters`, Vault |
| `iu.client.jsonb` | the JSON-B provider: `IuJsonb`, `IuJsonbValueAdapter` (component chains), `IuJsonbAdapter` (business objects), `IuJsonbModel`, the call contexts, and `JsonbMetadata` |

## Invariants to keep

- **JSON-B API isolation.** Only classes in `iu.client.jsonb` refer to JSON-B types. The IU paths reach annotations through `BindingMetadata`; `JsonbPresence` decides once, by whether this module reads `jakarta.json.bind`, whether `BindingMetadata.get()` is `JsonbMetadata` or `NONE`. Public API methods whose signatures name JSON-B types (`IuJsonProperties.deserialize`, `IuJsonAdapter.typedSerializer`) carry `@SuppressWarnings("exports")` and only link when called. A test covers the absent path through `JsonbPresence.metadata`.
- **One property model.** `BeanModel` discovers properties for both paths: JSON-B's rules by default, `BeanModel.legacy` (public accessors, no annotations) when `isLegacyProperties()`. Discovery, names, formats, creators, and type information belong there, not in either path.
- **Strict built-ins.** No coercion between JSON types; exact number reads. Downstream code that relied on leniency is fixed downstream.
- **Options are read per conversion.** Anything an option affects dispatches at conversion time (`OptionsSwitch`, `BinaryJsonAdapter.of(Supplier)`), never at adapter creation, so a captured adapter observes a reconfiguration.
- **Pre-7.1 behavior stays restorable.** A change to an IU default gets an `IuJsonSerializationOptions` setting that restores it, included in `LEGACY`.
- **`IuJson.add` always omits nulls**, whatever the options: the JOSE writers and Vault merge patch, where JSON null deletes a key, rely on it. A value wrapped by `IuJson.wrap` writes back as its source object verbatim.

## JSON-B provider internals

- Components form one chain per direction per type (`IuJsonb.readChain`/`writeChain`), most specific first; see the README for precedence. A property's own components head a per-property `IuJsonbValueAdapter`.
- Every call runs inside `IuSerializationContext` or `IuDeserializationContext`, which also sets `iu.client.ConversionScope` so an `IuJsonProperties` without conversions of its own follows the call.
- A deserializer gets an `IuJsonbBoundedParser` view; when the call holds the source text, `IuJsonProperties` can detach by continuing from the text rather than capturing the rest.
- The JSON-B specification text and Yasson's source are the references for behavior; where this provider deviates, the README's "Deviations from standard behavior" says so, and a new deviation is added there.

## Testing notes

Tests compile with `-parameters` (the `default-testCompile` execution in `pom.xml`), so they can declare creators without `@JsonbProperty` on every parameter. Tests here mock the HTTP layer rather than opening sockets. `iu-java-test` fails a test on any unexpected log record, and `IuHttp` logs every request — expect or allow those records explicitly.

Coverage is 100% instruction and branch. A coverage report showing most classes missed while every test passed means the class files changed under JaCoCo — look for "does not match" in the build log, typically an IDE rebuilding `target/` — and re-run with `clean` rather than chasing gaps.
