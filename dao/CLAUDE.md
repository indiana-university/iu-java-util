# CLAUDE.md — dao

`iu-java-dao-api`, `iu-java-dao-impl` / modules `iu.util.dao`, `iu.util.dao.impl`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

A data-access facade that generates SQL from JPA annotations without running a JPA provider. It reads `@Table`/`@Column` metadata to build statements against a plain `DataSource`, and integrates with JTA through `TransactionManager` and `TransactionSynchronizationRegistry`.

This is **not** an ORM. There is no persistence context, no dirty checking, and no lazy loading — the JPA annotations are used purely as a mapping vocabulary.

## Layout

Standard SPI pair. `api` (`edu.iu.dao`, `edu.iu.dao.spi`) `uses IuDaoSpi`; `impl` (`iu.dao`) `provides IuDaoSpi with iu.dao.DaoSpi`.

## `IuDao` — the two method families

Obtain an instance with `IuDao.of(DataSource, TransactionManager, TransactionSynchronizationRegistry)`.

- **`get*` family** returns an unexecuted `ParameterizedSql` that the caller drives **and closes**. Some accessors on `ParameterizedSql` close themselves — its Javadoc says which.
- **Everything else** (`loadBean`, `searchBeans`, `exists`, `updateBean`, `saveBean`, `deleteBean`, and the bulk variants) executes immediately and leaves no open resources.

Mixing the two is the usual source of leaked statements here.

## Caching semantics

A DAO holds no per-call state and is thread-safe. Reads through `loadBean` and `searchBeans` are cached, but **only inside an active transaction and only in that transaction's own `TransactionSynchronizationRegistry` resources** — cached rows cannot outlive the transaction or leak across threads. Writes through the facade evict the affected entity type; **direct SQL does not**, so call `clear()` after modifying rows by other means. `exists` caches only its boolean answer, a `false` included, under the same rules: a write to the entity type evicts the answers for that type, and the raw SQL form names no entity, so only `clear()` or expiration evicts it.

## Entity mapping vocabulary

An entity needs `@Table` plus `@Column` or `@SqlColumn` getters. It may be a class with a no-argument constructor (populated through setters) or an **interface**, which is materialized as an immutable view of the row it was read from.

Beyond standard JPA annotations, this module defines:

- `@SqlColumn` — a raw SQL expression for a mapped member.
- `@EffectiveDated` — effective-dated behavior applied to generated queries.
- `@Filtered` / `SqlFilter` — filters automatically applied to generated selects.
- `@Distinct` — marks a selection `DISTINCT`.
- `@SpaceForNull` — normalizes `null` to a single space when binding.

`TableDefinition` and `ColumnDefinition` are immutable snapshots of live database metadata; `SqlQuery`, `SqlStatement`, and `SqlJoinType` model generated statements. `IuSqlUnchangedException` signals an update that would change nothing.

## `IuEntityDao` — keyed CRUD over one entity type

`IuEntityDao<K, I, E>` (in `api`) is a base class for a DAO that stores contract interface `I` as entity `E`, keyed by `K`. It adds no state to `IuDao` and it is still not an ORM: each verb is an explicit read or write. `save(K, I)` applies the input onto the stored row (or a new entity) and writes it back, clearing values the input leaves null and creating the row if absent; `merge(K, I)` is the `PATCH` alternative, applying only non-null values onto an existing row. Either takes an explicit key, the key carried by the input, or (`save` only) a generated one; a key and an input key that disagree throw `IllegalArgumentException`. `save(I)` and `merge(I)` are shortcuts for an input that carries its own key.

- Type arguments are resolved reflectively from the subclass's generic declaration. Entities opt in to behavior through decorator interfaces, and an entity that does not implement one is left untouched by the operation: `SaveableEntity<I>` (applies the input in `save`), `MergeableEntity<I>` (applies the partial input in `merge`) and `SequencedEntity` (takes a child row's position). Key handling uses `IuDao.getPrimaryKeyProperties`, `getBeanKey` and `newBean`, so it follows the entity's own mapping; `key(I)` reads a key from input and `entityKey(E)` from a stored entity, and a composite key overrides `id`, `key`, `entityKey` and `generate`.
- Hooks are `afterLoad` (runs on `load`, on `merge`, and on `save` of an existing row, before `beforeInput`), `beforeInput` (existing entities only, before input is applied), `afterInput` (audit stamping belongs here; runs on every save and merge before the write), `afterSave`, `afterMerge` (exactly one of the two runs); the four input hooks take `(I input, E entity)`, and the sequence number of a `SequencedEntity` child is a plain argument of the collection methods, so a nested `save` or `merge` on the same DAO never inherits it, `beforeDelete` and `afterDelete` (neither runs when no row exists). The protected `search()` and the public `search(Map)` return a deferred `Iterable` that captures `parent()` (through `Search.child()`) when called and runs the query on first iteration; the eager forms, `search(Search)`, `search(Map, Search)` and the protected `searchWhere(where, args, Search)`, take a `Search` record (parent, optional `Predicate` filter, range, built with `Search.child()`, `range`, `filtered` or `filteredRange`, which capture the active `parent()`) and return unmodifiable lists. **No hook may share a name with a public verb**: an overloaded `delete(T)` hook once shadowed `delete(K)` for callers passing an entity.
- Parent and child rows: from the parent DAO's `afterSave` or `afterMerge`, a child DAO calls the public `save(Iterable)` or `merge(Iterable)` with the input's child collection. `parent()` (shared by every DAO on the thread, set while `afterLoad`, `afterSave`, `afterMerge` and the delete hooks run) and the `input` argument of the `beforeInput`, `afterInput`, `afterSave` and `afterMerge` hooks (null when the caller passed none) tell the child what it belongs to. The child opts in to row removal by overriding `search(Search)` (which receives the parent captured by `search()` and returns no rows by default; `search()` is a deferred `Iterable`) and may override `delete()`, which by default deletes each row `search()` returns; `save(Iterable)` deletes stored children the collection omits, while `merge(Iterable)` merges keyed children onto stored rows, resequences the rest of a `SequencedEntity` child set (relying on `saveBean` to skip unchanged rows) (an unknown key is an `IllegalArgumentException`, validated before any write) and saves keyless children as new rows. A `SequencedEntity` child is given its position in the collection, after the input is applied and before `afterInput`, on both `save` and `merge`. Parent binding is strict by default: `bind(parent, entity)` runs for a new row in the default `newEntity` (before the input is applied) and for a stored row in `load`, `save`, `merge` (through `load`) and `delete`, before any hook, and throws `IllegalArgumentException` (deliberately distinct from `EntityNotFoundException`; the message names the parent and the child's `toString`, so an entity class should override `hashCode`, `equals` and `toString` over its primary key values) when the child cannot be bound. A DAO supports a parent by one of: the entity implements `ChildEntity`, which needs only `tryBinding(parent)`; the DAO overrides only `tryBinding(parent, entity)`, which the default `bind` consults for any non-`ChildEntity` entity (the default is true only when no parent is active); or the DAO overrides `bind`. Otherwise a nested save or merge from a parent DAO's hook fails. A subclass overriding `newEntity` must bind for itself. `tryBinding` is the single binding primitive and carries the fail-fast contract: it returns false, never throws, for a parent that cannot be bound, and `bind` (the `ChildEntity` default or the DAO's) converts that false into the `IllegalArgumentException`, so a bad parent is rejected before any hook or write. Implementations MAY set the child's parent reference when it has none (a null parent is accepted and a parent already set is never replaced, so repeating a binding is a no-op), SHOULD otherwise be non-destructive, and should do no work beyond comparing and assigning. The same method filters `search` results, and is called on every candidate row before the range is taken, so an implementation that captures a parent also binds rows that are then discarded, which suits bulk assignment from a link-table search but nothing that needs a row to stay unbound. `withParent(Object, Runnable)` and the parent-taking overloads of the verbs (`load`, `save`, `merge`, `delete`, `save`/`merge(Iterable)`) inject a parent outside a hook; `deleteChildren(parent)` is the bulk-delete override point.
- `search` reads every matching row, keeps those `tryBinding` accepts for the parent and the `Search` filter accepts, sorts by `order()` in memory, then takes the range and runs `afterLoad` for the returned range only, so it suits registration-sized tables. `searchWhere` emits its clauses verbatim and bypasses the read cache.
- Interface, record and `@EffectiveDated` entities are not supported.

## Database-backed tests

Requires a live PostgreSQL instance. `liquibase-maven-plugin` applies `src/test/sql/db.changelog-master.sql` at `generate-test-resources`:

```bash
export POSTGRES_HOST=localhost POSTGRES_PORT=5432 POSTGRES_USER=postgres POSTGRES_PASSWORD=...
mvn -pl dao/impl verify
```

The Liquibase execution honors `-DskipTests`. CI provides Postgres as a service container started with `-c max_prepared_transactions=10`, which the XA paths depend on.

Jakarta Persistence, Transactions, and CDI are `provided` scope — the deploying application supplies them.
