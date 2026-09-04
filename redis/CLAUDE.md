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

`edu.iu.redis.IuRedisConfiguration` — host, port, password, optional username, optional `X509Certificate` trust anchor, SSL toggle, and key expiration (default 15 minutes). Bound through `IuConfig`, which is why the package is `opens`.

## Integration test

`LettuceConnectionIT` needs a real Redis instance and **skips itself** when configuration is absent — it gates on `IuRuntimeEnvironment.envOptional("redis.host")`. To run it:

```bash
export redis.host=... redis.port=... redis.password=... redis.cacert=/path/to/ca.pem
mvn -pl redis/impl verify
```

`redis.cacert` is a filesystem path to the CA certificate, read at test setup. Since the test skips silently without these, a green `mvn verify` does not mean the Lettuce binding was exercised — run it explicitly after changing `LettuceConnection`.
