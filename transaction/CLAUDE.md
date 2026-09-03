# CLAUDE.md — transaction

`iu-java-transaction` / module `iu.util.transaction` / package `edu.iu.transaction`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

A portable, self-contained JTA transaction manager for applications running outside a Jakarta EE
container. `dao` and `jdbc/pool` are its primary consumers.

Single flat module — no api/impl split, no SPI. `jakarta.transaction` and `java.transaction.xa` are
`requires transitive`, so consumers inherit the JTA API.

## Design

`IuTransactionManager` implements four JTA interfaces at once:

```java
public final class IuTransactionManager
        implements TransactionManager, UserTransaction,
                   TransactionSynchronizationRegistry, AutoCloseable
```

This is deliberate — it lets a single instance be injected wherever any of the four is expected,
which is what makes it drop-in for code written against a container. It is `AutoCloseable` because it
owns a `ScheduledThreadPoolExecutor` (thread group `iu-java-transaction`) used to force rollback
before enlisted resource timeouts expire. Default timeout is 2 minutes.

Active transactions are held per-thread in a `ThreadLocal<Deque<IuTransaction>>`, so suspend/resume
nests naturally. `clearThreadState()` exists for pooled threads that may carry state between tasks —
call it when handing a thread back.

`IuTransaction` supports distributed branches with heuristic commit/rollback and join logic, and is
documented as thread-safe for high-volume parallel workloads. `IuXid` (package-private) is the `Xid`
implementation, using format ID `63225`.

## Observation

The manager exposes `visit(Function<IuTransaction, Optional<V>>)` and
`subscribe() -> IuAsynchronousSubscription<IuTransaction>`, backed by `edu.iu.IuVisitor` and
`IuAsynchronousSubject` from `base`. Use these for monitoring rather than adding logging inside the
transaction paths.

## Testing notes

Tests are self-contained — no database is required here, unlike `dao` and `jdbc/pool`. They do
exercise timing and the rollback scheduler, so avoid making them dependent on wall-clock slack;
`src/test/resources/META-INF/iu-test.properties` carries a `debug` flag for local diagnosis.
