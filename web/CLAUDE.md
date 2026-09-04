# CLAUDE.md — web

`iu-java-web-api` / module `iu.util.web` / package `edu.iu.web`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

Integration interfaces for embedding IU utility services in a web container, defined over `com.sun.net.httpserver` (`requires transitive jdk.httpserver`) rather than Jakarta Servlet. This keeps the contract usable from a plain JDK HTTP server as well as from a servlet container adapter.

## Scope

`web/pom.xml` declares a single module, `api`. The module has no dependency on any other module here — it is pure interface declaration, with no `impl` and no `src/test`.

## The three contracts

```java
Subject authenticate(HttpExchange exchange);                    // IuWebAuthenticator
void handleError(String nodeId, String requestNumber, Throwable error, HttpExchange exchange, ...);
```

- `IuWebAuthenticator` — authenticates an incoming request into a `javax.security.auth.Subject`. Its Javadoc states that **only one** implementing resource may be present in a container environment; treat that as a hard constraint when wiring an application.
- `IuWebErrorHandler` — receives the node ID and request number alongside the throwable, so error pages can carry correlation identifiers matching what `logging` emits.
- `IuWebContext` — descriptive metadata for the deployed context path: application, environment, module, runtime, component, and the support-contact triple (pre-text, URL, label) used to render error pages.

## Editing notes

Because there is no implementation module and no tests here, a change to these interfaces has no in-repository consumer to catch it — the breakage appears in downstream applications. Treat any signature change as a breaking API change and keep new methods `default`.
