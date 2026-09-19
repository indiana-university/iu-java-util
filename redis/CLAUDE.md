# CLAUDE.md — redis

`iu-java-redis`, `iu-java-redis-impl` / modules `iu.util.redis`, `iu.util.redis.impl`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

A library-agnostic Redis abstraction. `IuRedis extends IuDataStore` (from `base`) and `AutoCloseable`, so Redis is interchangeable with the in-memory store for callers that only need key/value semantics.

Compiled with `--release 11`.

## Layout

Standard SPI pair: `api` `uses edu.iu.redis.spi.IuRedisSpi`; `impl` `provides` it with `iu.redis.spi.RedisSpi`. Both `edu.iu.redis` and `iu.redis.spi` are `exports` **and** `opens` for configuration binding.

## Everything in impl is an optional dependency

```java
requires static org.apache.commons.pool2;
requires static lettuce.core;
requires static iu.util.client;
```

Lettuce, commons-pool2, and even `iu.util.client` are `requires static`. The current binding (`iu.redis.lettuce.LettuceConnection`) is one possible driver, not the only one — the SPI exists precisely so another client library can be substituted. Do not let Lettuce types appear in `edu.iu.redis` signatures, and do not promote any of these to a hard `requires`.

## Configuration

`edu.iu.redis.IuRedisConfiguration` — host, port, password, optional username, optional `X509Certificate` trust anchor, SSL toggle, key expiration (default 15 minutes), and key prefix (default `iu`). Bound through `IuConfig`, which is why the package is `opens`.

## Key format

Every key this binding writes is `<prefix>:<segment>:{<name>}`.

- **`<prefix>`** is `IuRedisConfiguration.getKeyPrefix()`, default `iu`, read once when the connection is created. It is what makes an instance shareable: it scopes a listing to one store and an incident to one application. A deployment sharing an instance between applications — or between a session store and a cache in one application — gives each its own.
- **`<segment>`** is `:d:` for a value and `:m:` for its write time, so a listing can scan for one without the other.
- **`<name>`** is the caller's key, Base64 URL-encoded — the same string `IuDataStoreEntry.getName()` returns and the same string the logs print, so a log line names the exact Redis key. It is unpadded and its alphabet contains no character `SCAN MATCH` reads as a wildcard.
- **`{ }`** is a Redis Cluster hash tag. It keeps a value and its write time in one slot, so the two-key `DEL` in `put` stays legal if this is ever pointed at a cluster; inert on a standalone instance.

The codec is `RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)` — text keys, because this class names them and they should stay legible in `redis-cli`; **binary values, because a caller's value must not be decoded at all.** Do not "simplify" this back to a plain `StringCodec`: it replaces every byte sequence that is not valid UTF-8 with U+FFFD, which silently corrupts any value that is not already text. That was the previous behavior, and it applied to keys too, which is why a listing could not name what it found.

## Write times are stored, because Redis has none

Redis records no per-key modification time. `OBJECT IDLETIME` reports last *access*, which every `get` resets, and `PTTL` only reveals a write time to a reader that already knows the interval the key was written with — which a caller passing its own `ttl` to `put` does not share. So `IuDataStore.lastModified(byte[])` is answered from the `:m:` key: `put` writes the value, then the write time under the same expiration, and `put(key, null)` deletes both in one `DEL`.

- **A write costs two commands, not one.** The order is deliberate: a write time lost between them reads as unknown, and an older one left in place reads as staler than the value is. Either understates freshness, which costs a caller a re-read; the reverse order would overstate it, which would have a caller keep data it should replace. Pipelining the pair through `connection.async()` would recover the round trip, but Lettuce applies its command timeout to the sync API only, so an async write needs `TimeoutOptions` enabled before it can replace a sync one.
- **`lastModified` returning null does not mean the key is absent.** It means no write time is available. Check with `get`.

## `list()` walks the whole keyspace

`MATCH` filters *after* the scan, not before, so listing one store costs a pass over every key in the database — not just this store's. `SCAN_COUNT` is set to 1000 because Lettuce would otherwise send no `COUNT` and take Redis's default of ten, turning that pass into thousands of round trips. It is non-blocking, so it is safe against a live instance, but it is an operator's view of the store and does not belong on a request path.

`SCAN` promises only that a key present throughout is returned *at least* once, so a key can arrive on two pages; `list()` collapses those by name. Keys written or deleted while it runs may or may not appear at all — hence the "point-in-time approximation" wording on `IuDataStore.list()`.

Cheap listing would need a secondary index (a `SET` per namespace, `SADD` on every put), which drifts as TTL'd keys expire without touching it. Don't add one without a concrete need.

Entries resolve lazily: `list()` reads names only, and `getData()`/`getModified()` each cost a round trip when called, every time they are called. A caller that needs a value twice holds it.

## Integration test

`LettuceConnectionIT` needs a real Redis instance and **skips itself** when configuration is absent — it gates on `IuRuntimeEnvironment.envOptional("redis.host")`. To run it:

```bash
export redis.host=... redis.port=... redis.password=... redis.cacert=/path/to/ca.pem
mvn -pl redis/impl verify
```

`redis.cacert` is a filesystem path to the CA certificate, read at test setup. Since the test skips silently without these, a green `mvn verify` does not mean the Lettuce binding was exercised — run it explicitly after changing `LettuceConnection`.

`testLastModified` brackets the stored write time with the local clock, so it holds only against a Redis whose host clock agrees with the test runner's. That is deliberate: a write time is only comparable across nodes if their clocks are.

The unit tests mock Lettuce, so they prove the commands this binding *sends*; only the IT proves Redis answers them. `testListFindsWhatWasPutAndNotTheWriteTimes` in particular exercises the `SCAN` cursor against a real keyspace, which no mock can.

`iu-test.properties` exempts `io.netty` as a platform logger: Lettuce's codec classes pull Netty in, and Netty announces its logging framework at `FINE` the first time it is touched — which, because the codec is a static field, happens during class initialization in whichever test loads `LettuceConnection` first rather than in one that declared an interest in log output.
