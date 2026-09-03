# CLAUDE.md — dao

`iu-java-dao-api`, `iu-java-dao-impl` / modules `iu.util.dao`, `iu.util.dao.impl`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

A data-access facade that generates SQL from JPA annotations without running a JPA provider. It reads
`@Table`/`@Column` metadata to build statements against a plain `DataSource`, and integrates with JTA
through `TransactionManager` and `TransactionSynchronizationRegistry`.

This is **not** an ORM. There is no persistence context, no dirty checking, and no lazy loading — the
JPA annotations are used purely as a mapping vocabulary.

## Layout

Standard SPI pair. `api` (`edu.iu.dao`, `edu.iu.dao.spi`) `uses IuDaoSpi`; `impl` (`iu.dao`)
`provides IuDaoSpi with iu.dao.DaoSpi`.

## `IuDao` — the two method families

Obtain an instance with
`IuDao.of(DataSource, TransactionManager, TransactionSynchronizationRegistry)`.

- **`get*` family** returns an unexecuted `ParameterizedSql` that the caller drives **and closes**.
  Some accessors on `ParameterizedSql` close themselves — its Javadoc says which.
- **Everything else** (`loadBean`, `searchBeans`, `updateBean`, `saveBean`, `deleteBean`, and the
  bulk variants) executes immediately and leaves no open resources.

Mixing the two is the usual source of leaked statements here.

## Caching semantics

A DAO holds no per-call state and is thread-safe. Reads through `loadBean` and `searchBeans` are
cached, but **only inside an active transaction and only in that transaction's own
`TransactionSynchronizationRegistry` resources** — cached rows cannot outlive the transaction or leak
across threads. Writes through the facade evict the affected entity type; **direct SQL does not**, so
call `clear()` after modifying rows by other means.

## Entity mapping vocabulary

An entity needs `@Table` plus `@Column` or `@SqlColumn` getters. It may be a class with a no-argument
constructor (populated through setters) or an **interface**, which is materialized as an immutable
view of the row it was read from.

Beyond standard JPA annotations, this module defines:

- `@SqlColumn` — a raw SQL expression for a mapped member.
- `@EffectiveDated` — effective-dated behavior applied to generated queries.
- `@Filtered` / `SqlFilter` — filters automatically applied to generated selects.
- `@Distinct` — marks a selection `DISTINCT`.
- `@SpaceForNull` — normalizes `null` to a single space when binding.

`TableDefinition` and `ColumnDefinition` are immutable snapshots of live database metadata;
`SqlQuery`, `SqlStatement`, and `SqlJoinType` model generated statements. `IuSqlUnchangedException`
signals an update that would change nothing.

## Database-backed tests

Requires a live PostgreSQL instance. `liquibase-maven-plugin` applies
`src/test/sql/db.changelog-master.sql` at `generate-test-resources`:

```bash
export POSTGRES_HOST=localhost POSTGRES_PORT=5432 POSTGRES_USER=postgres POSTGRES_PASSWORD=...
mvn -pl dao/impl verify
```

The Liquibase execution honors `-DskipTests`. CI provides Postgres as a service container started
with `-c max_prepared_transactions=10`, which the XA paths depend on.

Jakarta Persistence, Transactions, and CDI are `provided` scope — the deploying application supplies
them.
