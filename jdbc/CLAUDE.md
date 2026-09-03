# CLAUDE.md — jdbc

`iu-java-jdbc-pool`, `iu-java-jdbc-monitor` / modules `iu.util.jdbc.pool`, `iu.util.jdbc.monitor`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

Two independent, unrelated JDBC utilities under one parent. Neither depends on the other, and neither
uses an SPI.

| Module | Package | Purpose |
|---|---|---|
| `pool` | `edu.iu.jdbc.pool`, `iu.jdbc.pool.config` | JTA-aware connection pool and `DataSource` |
| `monitor` | `edu.iu.jdbc.monitor` | Reflective proxies that instrument statement execution |

## pool

`IuDataSource` is a `DataSource` backed by `IuConnectionPool`, wired to the application's transaction
manager through `IuDataSourceIntegration`. Connection dispatch depends on transaction context:

- **No active JTA transaction** — `getConnection()` checks out of the pool and hands the connection
  to the caller, who must close it to return it.
- **Active JTA transaction** — the connection is enlisted and its lifecycle follows the transaction.

Read the `IuDataSource` Javadoc before changing dispatch behavior; the distinction between "caller
closes" and "transaction closes" is the source of most leaks in this area.

`iu.jdbc.pool.config.IuConnectionPoolConfiguration` is the tuning surface, bound through `IuConfig`
(the package is `exports` **and** `opens`). All settings have defaults: login timeout 15s, connection
reuse time 15m, abandoned-connection timeout 30m, shutdown timeout 30s, validation interval 15s. When
adding a setting, give it a `default` method so existing configurations keep binding.

`PooledConnectionHolder` and `IuPooledConnectionFactory` are the extension points for supplying
physical connections.

## monitor

`IuJdbcMonitor` wraps a `Connection` in `InvocationHandler` proxies —
`ConnectionHandler`, `StatementHandler`, `PreparedStatementHandler`, `ResultSetHandler` — that
intercept statement execution and result set scanning. It emits two things for each observed
operation:

1. `FINE`-level records on the logger named `edu.iu.jdbc.monitor.IuJdbcMonitor` (`execute` on
   completion, and related timing events).
2. `IuJdbcObservableEvent` instances published via `edu.iu.IuListener.observe`.

Because this module logs on every statement, any test that exercises a monitored connection must
declare those records with `IuTestLogger.allow` or `expect` — otherwise `iu-java-test` fails the test.

## Database-backed tests

`pool` requires a live PostgreSQL instance. `liquibase-maven-plugin` runs
`src/test/sql/create_test_database.sql` at `generate-test-resources`, reading connection details from
the environment:

```bash
export POSTGRES_HOST=localhost POSTGRES_PORT=5432 POSTGRES_USER=postgres POSTGRES_PASSWORD=...
mvn -pl jdbc/pool verify
```

The Liquibase execution honors `-DskipTests`, so `mvn verify -DskipTests` compiles without a
database. CI provides Postgres as a service container.
