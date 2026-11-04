IU Java Client
--------------

Overview
========

`iu-java-client` (module `iu.util.client`, package `edu.iu.client`) converts between Java and JSON, calls HTTP services, reads secrets from HashiCorp Vault, and invokes Java interfaces remotely over HTTP. It is a named module, compiled for Java 17, and exports only `edu.iu.client`; everything under `iu.client` is internal.

| Requirement | Scope | Purpose |
|---|---|---|
| `jakarta.json` API and an implementation, such as Parsson | required at runtime | every JSON conversion; `requires transitive`, so a consumer reads it too |
| `jakarta.json.bind` API | optional | the JSON-B provider, and JSON-B annotations honored by the IU conversions |
| `iu.util` (`iu-java-base`) | required | shared utilities |

Both JSON APIs are `provided` dependencies: the application supplies them.

The module holds two ways to convert, sharing one set of built-in conversions and one property model:

- **The IU conversions**: `IuJsonAdapter`, `IuJson`, and `IuJsonProperties`, driven by `IuJsonSerializationOptions`. They need only JSON-P.
- **A JSON-B provider**: `jakarta.json.bind.Jsonb`, configured by `JsonbConfig`. It needs the JSON-B API.

Converting with the IU conversions
==================================

### `IuJsonAdapter`

An `IuJsonAdapter<T>` converts one Java type both ways, as a tree (`toJson`, `fromJson`) or streaming (`write`, `read`). Get one for a type:

| Factory | Converts |
|---|---|
| `IuJsonAdapter.of(type)` | a built-in type, generics included, such as `List<URI>`; fails for a type with no built-in conversion |
| `IuJsonAdapter.adapt(type, options)` | any type: built-in types, business objects, enums, and every nested value, by the options supplied |
| `IuJsonAdapter.adapt(type, options, valueAdapter)` | the same, with nested value types resolved by a function of the caller's |
| `IuJsonAdapter.from(Class, options, valueAdapter)` | a business object type |
| `IuJsonAdapter.from(fromJson, toJson)` | by functions of the caller's |
| `IuJsonAdapter.basic()` | JSON to `String`, `BigDecimal`, `Boolean`, `List`, and `Map`, and back |

`adapt` takes a `Supplier<IuJsonSerializationOptions>` and reads it once per conversion, so a captured adapter observes a configuration change without being rebuilt; the same supplier reaches every nested value. A supplier that answers null reads as `IuJsonSerializationOptions.DEFAULT`.

### `IuJsonSerializationOptions`

| Option | Default | Effect |
|---|---|---|
| `getPropertyNameFormat()` | `IDENTITY` | property names as-is, `LOWER_CASE_WITH_UNDERSCORES`, or `UPPER_CASE_WITH_UNDERSCORES`; read and written |
| `isIncludeNullProperties()` | false | write a null property as JSON null rather than omit it |
| `isEnumAsObject()` | false | write an enum as an object holding `name` and its properties |
| `getBinaryDataStrategy()` | `BYTE` | `byte[]` as an array of signed bytes, `BASE_64`, or `BASE_64_URL`; read and written |
| `isEnumToString()` | false | write enum text by `toString()` rather than `name()` |
| `isEmptyOptionalPresent()` | false | write an empty `Optional` as null even when nulls are omitted |
| `isLegacyProperties()` | false | discover properties as before 7.1; read and written |
| `isLegacyDates()` | false | convert dates as before 7.1; read and written |

`DEFAULT`, `INCLUDE_NULLS`, `ENUM_AS_OBJECT`, `LEGACY`, and `of(...)` supply common sets. The last four options exist to restore pre-7.1 behavior; see [Upgrading to 7.1](#upgrading-to-71).

### Business objects

A type that isn't a built-in, primitive, or array type converts as a business object. A class of the application's own that extends a class with a built-in conversion, such as `ArrayList`, or implements `Map`, `Collection`, `Iterable`, `Iterator`, `Enumeration`, `Stream`, or `CharSequence`, converts as that type instead, parameterized as the class declares; a `Collection` or `Map` class reads into a new instance, created by its no-arg constructor. Its properties are discovered as JSON-B discovers them:

- public fields and public accessors, including those inherited from non-platform superclasses and interfaces; `isX()` reads a `boolean`;
- a getter or setter that isn't public closes that direction, so its field isn't used in its place;
- in lexicographic order, unless `@JsonbPropertyOrder` lists some first;
- with property types resolved against the concrete type, so `Holder extends Base<String>` reads `T value` as a `String`;
- a record's components through their accessors.

A null property, or an empty optional, is omitted unless nulls are included or the property is declared nillable. Reading creates the object with its creator, if it declares one, else its no-arg constructor, then sets each property the JSON names; an unknown property is ignored. A value declared as `Object`, or another broad type such as `Serializable` or `Comparable`, converts by its runtime type.

### Built-in types

| Java | JSON |
|---|---|
| `String`, `CharSequence`, `Character` | string |
| `Boolean`, number types, `BigDecimal`, `BigInteger` | boolean, number; read exactly, so `1.5` isn't an `int` |
| `byte[]` | by the binary data strategy |
| enum | string by `name()`; reads an object's `name` too |
| `Date`, `Instant` | ISO date and time in UTC, such as `2026-09-27T12:34:00Z`; a `Date` at midnight UTC as a date, such as `2026-09-27Z` |
| `Calendar`, `GregorianCalendar` | ISO date and time in its own zone; at midnight, as a date with its offset |
| `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetTime`, `OffsetDateTime`, `ZonedDateTime` | by the ISO formatter for the type, seconds included |
| `Duration`, `Period`, `ZoneId`, `ZoneOffset`, `TimeZone`, `URI`, `URL`, `UUID`, `Pattern` | string |
| `Optional`, `OptionalInt`, `OptionalLong`, `OptionalDouble` | the value, or null |
| arrays, `Collection`, `List`, `Set`, `Queue`, `Deque`, `Iterable`, `Iterator`, `Enumeration`, `Stream`, `EnumSet` | array |
| `Map`, `EnumMap`, `Properties` | object; keys by the key type's text |
| `JsonValue` and its subtypes | as-is |
| `IuJsonProperties` | the object it indexes |

Built-in conversions are strict: a number doesn't read from a string, text doesn't read from a number or array, and a scalar doesn't read as a one-item list. A failure names what was expected and found. A date read without a time is at midnight, and without a zone or offset is in UTC. A platform class with no conversion of its own, such as a JDK-internal list, converts as the nearest type that has one.

The conversion for a type that reads from a JSON array is an `IuJsonArrayAdapter`, from `IuJsonArrayAdapter.of(type)`. Beyond converting, it takes the items out of a value (`iterator`) and puts items into a new one (`collect`), for code that reads or writes the items itself. Accepting a single value where an array is expected, which the built-ins don't, is one line: `IuJsonArrayAdapter.of(type).collect(List.of(item))`. Inside a JSON-B call, as in a `JsonbDeserializer`, `of(type)` converts items as the call does, so the items can be any type the instance converts; `of(type, valueAdapter)` takes the item conversions explicitly. A collection or array it collects is new and, for a collection, mutable; an `Iterable` is the items given; a `Stream`, `Iterator`, or `Enumeration` is a single-use view of them.

### `IuJson`

`IuJson` parses and writes JSON, builds values (`object()`, `array()`, `string(...)`, `number(...)`), and reads and adds properties by adapter (`get`, `add`). `IuJson.add` always omits a null value, whatever the options; Vault's merge patch, where JSON null deletes a key, relies on that.

`IuJson.wrap(object, Interface.class)` wraps a JSON object in an interface: each getter reads its property when first called, and keeps the value. A wrapped value writes back as its source object, unknown properties included, so data handled through a stub interface keeps what the stub doesn't declare. A default method answers for a property the object doesn't have.

### `IuJsonProperties`

An `IuJsonProperties` indexes the properties of one JSON object and converts each only when it is read. It resolves a property from values already read, then raw JSON captured from an object or parser, then a parser still inside the object: a read pulls forward, converting the property asked for straight from the parser, and captures the properties it passes over raw.

- `IuJsonProperties.of(object)`, `read(parser)`, and `builder()` create an index; `of(object, jsonb)`, `read(parser, jsonb)`, and `builder(jsonb)` create one that converts as a JSON-B instance does when no call is in progress; `with(name, value)` and `Builder.copy()` copy one, keeping how it converts.
- `get(name, type)` converts a property. One not in the object converts nothing: it is null, a primitive's default, or an empty optional.
- `requireOnly(names)` rejects a property not expected; otherwise unknown properties are kept and written back unchanged.
- `toJsonObject()` and `write(generator)` write the object; `write(generator, context)` writes it from a JSON-B serializer, through the call's context, under any provider. A property set to a Java null is written only where the call in progress writes null properties; JSON null, as read or set by `putJson`, is written back as-is.
- `detach()` releases a parser before whatever controls it moves on. Within a deserialization by the IU JSON-B provider, the provider detaches it.

An index created without conversions of its own converts as the IU JSON-B call in progress when it converts; else the call in progress when it was created; else as the `Jsonb` given to `builder(jsonb)`, if any, its adapters included; else by the IU defaults. So a value put as one type and read as another, such as a `BigInteger` read as `byte[]`, converts by the application's JSON-B configuration outside any call. An index is a built-in value type in both the IU conversions and the JSON-B provider, with nothing to register.

The JSON-B provider
===================

### Selecting the provider

The module provides `jakarta.json.bind.spi.JsonbProvider` as `iu.client.jsonb.IuJsonbProvider`, through its module declaration; there is no `META-INF/services` entry, as the module runs only as a named module.

- With this module on the module path and no other provider, `JsonbBuilder.create()` uses it.
- With another provider as a named module in the same layer, which one `JsonbBuilder.create()` finds is undefined; name this one with `JsonbBuilder.newBuilder("iu.client.jsonb.IuJsonbProvider")`.
- A provider that fails to construct breaks `JsonbProvider.provider()` for every caller.

### Configuration

| `JsonbConfig` property | Support |
|---|---|
| `FORMATTING`, `ENCODING`, `NULL_VALUES` | as specified |
| `PROPERTY_NAMING_STRATEGY` | every standard strategy, including `CASE_INSENSITIVE`, and `PropertyNamingStrategy` instances |
| `PROPERTY_ORDER_STRATEGY` | `LEXICOGRAPHICAL` by default, `REVERSE`, `ANY` |
| `PROPERTY_VISIBILITY_STRATEGY` | as specified; `@JsonbVisibility` on a class or package overrides it |
| `BINARY_DATA_STRATEGY` | `BYTE` by default, `BASE_64`, `BASE_64_URL` |
| `DATE_FORMAT`, `LOCALE` | a `DateTimeFormatter` pattern or `TIME_IN_MILLIS`, for every date type; `LOCALE` a `Locale` or language tag |
| `STRICT_IJSON` | see [Strict I-JSON](#strict-i-json) |
| `CREATOR_PARAMETERS_REQUIRED` | a creator parameter not read fails |
| `SERIALIZERS`, `DESERIALIZERS`, `ADAPTERS` | see [Components](#components) |
| `IuJsonAdapter.SERIALIZATION_OPTIONS` | a `Supplier<IuJsonSerializationOptions>` read once per call, so JSON-B converts with the same options as the IU conversions; must agree with `PROPERTY_NAMING_STRATEGY`, `NULL_VALUES`, and `BINARY_DATA_STRATEGY` when those are also set |
| `IuJsonAdapter.BASE64_URL_UNPADDED` | `Boolean`: true to write `BASE_64_URL` without padding, as JOSE requires, alongside `withBinaryDataStrategy(BinaryDataStrategy.BASE_64_URL)`; reading accepts either form |

An unknown property is ignored.

### Annotations

| Annotation | Support |
|---|---|
| `@JsonbProperty` | names a property on its field, getter, setter, or creator parameter; an accessor's name wins over its field's |
| `@JsonbTransient` | on a field, excludes the property; on a getter or setter, that direction; combined with another JSON-B annotation on the property, fails |
| `@JsonbNillable`, `@JsonbProperty(nillable)` | on a property, class, or package |
| `@JsonbPropertyOrder`, `@JsonbVisibility` | on a class; `@JsonbVisibility` on a package too |
| `@JsonbDateFormat`, `@JsonbNumberFormat` | on a property, creator parameter, class, or package; see [Formats](#formats) |
| `@JsonbTypeAdapter`, `@JsonbTypeSerializer`, `@JsonbTypeDeserializer` | on a type or property; see [Components](#components) |
| `@JsonbCreator` | on one constructor or static factory method; see [Creators](#creators) |
| `@JsonbTypeInfo`, `@JsonbSubtype` | see [Polymorphism](#polymorphism) |

An annotation on a private field applies to its property, as the field behind a getter and setter. JSON-B annotations used as meta-annotations, on an annotation of the application's own, are not supported.

### Components

Serializers, deserializers, and adapters form one chain per direction for each type: reading merges deserializers and adapters, and writing merges serializers and adapters.

- A component applies to the type it is registered for and every subtype. The chain runs from the most specific registration to the least.
- Between components neither more specific than the other, a component a type declares by annotation runs first, then a serializer or deserializer before an adapter, then configured order.
- A component a property declares, on its accessor or field, heads that property's chain.
- Writing selects components by the value's runtime type, so the order is the same wherever the value is declared.
- Each adapter applies at most once to a value; the adapted value converts through its own type's chain, less the adapters already applied.

A serializer or deserializer passes a value down its chain by giving the same value, or the parser at the same position, back to its context: the next component not already in progress runs, and then the built-in conversion. What an adapter or deserializer returns is checked against the type being read.

A component registered for a broad type, such as `Object`, `Comparable`, or `Serializable`, leaves scalar values alone: text, numbers, and booleans, declared or read. Reading skips an adapter whose adapted type doesn't accept the JSON value's shape, and uses it only if nothing else runs, so adapters can accept several formats.

A component's types come from its type arguments. A lambda, a raw implementation, or one whose type argument is a type variable of its own has none, and registers through `IuJsonAdapter.typedSerializer(type, ...)`, `typedDeserializer(type, ...)`, or `typedAdapter(original, adapted, ...)`.

A deserializer reads from a view of the parser bounded to its value: `hasNext()` is false at the value's end, and whatever it leaves unread is skipped when it returns. After `DeserializationContext.deserialize` returns, the parser sits on the value's last event. Null reaches components like any other value; an undefined value, from a missing property in tree mode, reaches only adapters.

### Tree and streaming

`Jsonb.fromJson` and `toJson` stream. A component that converts a `JsonValue`, or an `IuJsonProperties` index converting in a call, converts as a tree by the same chains; either way, nested values share one call's options, property path, and cycle checks. A failure names the property path, such as `Root.items[2].name` or `Root.byName["k"]`, and the location read.

### Formats

A date format is a `DateTimeFormatter` pattern, `DEFAULT_FORMAT`, or `TIME_IN_MILLIS`, which writes a number and reads a number or numeric text. A declared format wins over `DATE_FORMAT`, even when it declares `DEFAULT_FORMAT`; a locale not declared is `LOCALE`, else the default locale. A `Date` or `Instant` formats in UTC, a `Calendar` in its own zone; a date read without a time is at midnight, and without a zone in UTC. `@JsonbNumberFormat` writes a number as text in its `DecimalFormat` pattern, and reads that text or a JSON number.

A format replaces the built-in conversion, so a configured or declared component still runs first. It applies to the property's own type, not the items of a collection.

### Strict I-JSON

`STRICT_IJSON` writes only an object or array at the top level, `byte[]` as base64url whatever else is configured, and `Date`, `Calendar`, `Instant`, `LocalDate`, and `LocalDateTime` as an ISO date and time with an offset and seconds, in UTC where the type has no zone. A configured or declared date format still wins. Numbers aren't restricted; the specification leaves them out of strict I-JSON.

### Creators

An object is created by the one constructor or static factory method declared `@JsonbCreator`; a record by its canonical constructor; otherwise by its no-arg constructor.

- A parameter reads the name `@JsonbProperty` declares, else its Java name, named as properties are, which requires compiling with `-parameters`.
- A parameter's own annotations declare its formats and components.
- Reading holds the values until the object is read through, then creates the instance and sets the other properties; a property that names a creator parameter goes to the creator only.
- A parameter not read is null, a primitive's default, or an empty optional, unless `CREATOR_PARAMETERS_REQUIRED`.

### Polymorphism

`@JsonbTypeInfo(key, @JsonbSubtype(alias, type)...)` declares the key a JSON object names its subtype by.

- **Writing**: an instance writes, before its properties, a key and alias for each type in its type information chain, outermost first. A subtype not listed writes no key. A value declared as a polymorphic type writes its runtime type's properties.
- **Reading**: the declared type's own type information picks the subtype, else its nearest supertype's. When the key comes first, as written, the subtype reads the rest straight from the parser; otherwise the object is read through first. An object without the key reads as the declared type.
- An unknown alias, an alias naming a type that isn't a subtype of the declared type, type information inherited from two unrelated types, a key declared twice in a chain, and a property sharing a key's name all fail.

JSON-B annotations in the IU conversions
========================================

When the JSON-B API is present at runtime, the IU conversions honor the same annotations: names, transient and nillable properties, order, visibility, formats, creators, polymorphism, and components. A component runs through an internal JSON-B provider created on first use, with the IU conversion's options, so it sees a real JSON-B context. Presence is decided once: the JSON-B annotations apply when `iu.util.client` reads the `jakarta.json.bind` module. Without it, none apply, and a type declaring them can't load anyway. `isLegacyProperties()` turns them off per conversion.

Upgrading to 7.1
================

The IU conversions adopt JSON-B's defaults in 7.1. Each change can be restored per option, or all at once with `IuJsonSerializationOptions.LEGACY`:

| Behavior | 7.1 | Before | Restore with |
|---|---|---|---|
| `byte[]` | array of signed bytes | base64 text | `getBinaryDataStrategy()` answering `BASE_64` |
| enum text | `name()` | `toString()` | `isEnumToString()` |
| empty `Optional` property | omitted with nulls | written as null | `isEmptyOptionalPresent()` |
| property discovery | JSON-B's: public fields and accessors, JSON-B annotations | public accessors only | `isLegacyProperties()` |
| `LocalTime`, `LocalDateTime`, `OffsetTime`, `OffsetDateTime`, `ZonedDateTime` | ISO formatter: seconds always, fraction digits as needed | `toString()` | `isLegacyDates()` |
| `Calendar` | in its own zone, read in the zone written | as `Date` in UTC, read in the default zone | `isLegacyDates()` |

Not restorable: built-in conversions are strict; a `Date` writes with a `Z` offset rather than a `[UTC]` zone, and a date read without an offset is in UTC; a value declared `Object` converts by its runtime type.

Removed in 7.1: `IuVault.of(Properties, Function<Type, IuJsonAdapter<?>>)`; use `IuVault.of(Properties, Jsonb)`.

The minimum runtime is Java 17: the module is compiled for Java 17, and binds records through the JDK's own record API.

Deviations from standard behavior
=================================

Where the specification is silent or Yasson differs, the provider behaves as follows:

- **Component precedence**: the most specific component wins across serializers, deserializers, and adapters; Yasson checks a serializer or deserializer for any supertype before an adapter for the exact type.
- **Map keys** convert by the key type's built-in text conversion; components never see keys, and a null key fails.
- **`SerializationContext.serialize(key, null, generator)`** omits the key when nulls are omitted, as a property would be; `serialize(null, generator)` without a key writes null.
- **Built-in conversions are strict**, where Yasson reads a number from a string.
- **Dates**: a `Date` or `Instant` writes with a `Z` offset, not `Z[UTC]`; a `Date` at midnight UTC, or a `Calendar` at midnight in its zone, writes as an ISO date.
- **Strict I-JSON** writes a `Calendar` with its offset, dropping a region zone, and doesn't restrict numbers.
- **`BASE_64_URL`** writes padded, as `Base64.getUrlEncoder()` does; `IuJsonAdapter.BASE64_URL_UNPADDED` writes it unpadded.
- **Lambdas and unresolved type variables** aren't inferred as component types, where Yasson binds an unresolved variable to `Object`; register them with the typed factories.
- **Number formats** read a JSON number as well as formatted text.
- **`TIME_IN_MILLIS`** doesn't apply to `LocalTime` or `OffsetTime`, which have no date.
- **A property of `IuJsonProperties`** absent from its object converts nothing, so no component sees it.

HTTP
====

`IuHttp` sends requests through a cached `HttpClient`, logging each one. A URI must be relative to an entry of `iu.http.allowedUri`, a comma-separated list read at startup; a URI that isn't `https` must be relative to `iu.http.allowedInsecureUri`. `iu.http.proxy` and `iu.https.proxy` name proxy servers, and `iu.http.no.proxy` lists domains that bypass them.

- `IuHttp.get(uri, handler)` and `send(uri, requestConsumer, handler)` send a request and handle the response; `send(exceptionClass, ...)` lets the request consumer throw a checked exception of its own.
- `HttpResponseHandler` and `HttpResponseValidator` compose handling: `IuHttp.OK`, `expectStatus(code)`, `checkHeaders(...)`, and `validate(deserializer, validators...)`; `READ_JSON`, `READ_JSON_OBJECT`, `READ_UTF8`, and `NO_CONTENT` handle common responses.
- A response that fails validation throws `HttpException`, which carries the response for diagnostics.

Vault
=====

`IuVault` reads a HashiCorp Vault K/V version 2 secrets engine. `IuVault.RUNTIME` is configured from the runtime environment, converting values by the IU defaults; `IuVault.of(properties, jsonb)` from properties of the caller's, converting values as a JSON-B instance does. Secret metadata converts by Vault's own format either way.

| Property | Purpose |
|---|---|
| `iu.vault.endpoint` | K/V engine endpoint; Vault isn't configured without it |
| `iu.vault.secrets` | comma-separated secret names |
| `iu.vault.token` | a token, for development |
| `iu.vault.loginEndpoint` | login endpoint, without a token |
| `iu.vault.roleId`, `iu.vault.secretId` | AppRole authentication |
| `iu.vault.kubeRole`, `iu.vault.tokenPath` | Kubernetes authentication, instead of AppRole; the token path defaults to `/var/run/secrets/tokens/vault-jwt` |
| `iu.vault.cubbyhole` | true for a cubbyhole engine, which keeps no versions or metadata |
| `iu.vault.cacheTtl` | how long values are cached, as an ISO duration |

`IuVaultSecret`, `IuVaultKeyedValue`, and `IuVaultMetadata` model the responses.

Remote invocation
=================

`RemoteInvocationHandler` invokes a Java interface by HTTP POST. A subclass supplies the URI for each method and adds authorization to each request; it may also customize serialization, cache keys, and context forwarding. Results are resolved through a refresh-ahead cache, whose configuration is read for each invocation, and a handler must be closed when no longer needed. A remote error response becomes a `RemoteInvocationException`, carrying the remote failure and its stack trace as `RemoteInvocationFailure` and `RemoteInvocationStackTraceElementDetail`; a failure before a response is received remains an `HttpException`.
