# CLAUDE.md — el

`iu-java-el` / module `iu.util.el` / package `edu.iu.util.el`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

A compact expression and template language over Jakarta JSON values, for rendering thin views from
module or class-path resources. It is unrelated to Jakarta Expression Language — no Jakarta EL
dependency, different syntax, JSON-only value model.

Note the package name: `edu.iu.util.el`, not `edu.iu.el`. It is the one module that does not follow
the `edu.iu.<area>` convention.

`org.apache.commons.text` is `requires static` — optional at runtime, used for escaping.

## Entry points

```java
JsonValue eval(String expr);
JsonValue eval(JsonValue context, String expr);
JsonValue eval(JsonValue context, String expr, Function<String, String> readResource);
JsonValue eval(JsonValue context, String expr, Function<String, String> readResource,
               Map<String, ?> inlineTemplateCache, Map<String, ?> templateCache);
```

`El` is a static facade; `ElContext` is the per-evaluation stack frame, `ElTemplate` a parsed
template, `ElUtils` the shared token and selection helpers. Evaluation is an explicit
`Deque<ElContext>` loop rather than recursion — keep it that way so deeply nested templates cannot
overflow the stack.

## Security constraint on `readResource`

`El` performs **no** validation of resource names. The `readResource` function is the entire trust
boundary, and its Javadoc requires it to map strictly to a least-privilege set of preloaded template
resources. A `readResource` that concatenates the name onto a filesystem path or a URL is a path
traversal. Do not add filesystem or network resolution inside this module; keep it in the caller,
behind an allowlist.

## Language summary

Expressions begin with a context symbol and take path elements and postfix operations:

- `$` current context, `root` original context, `_` previous result, `p.` parent template context.
- `i`, `head`, `tail` — index and position while iterating an array, or the property name when
  introspecting an object with `&`.
- Dot-separated path elements select members or array indexes; a leading `/` makes the element a JSON
  Pointer (`$./items/0/name`). A failed selection attaches a suppressed diagnostic positioned at the
  operation after the failure.
- `'` quotes remaining text, `*` comments, `@` returns raw (unescaped) text. Atomic results are
  HTML-escaped by default.
- `?` / `!` conditional evaluation, combinable as `condition?ifTrue!ifFalse`. Missing values, JSON
  null, false, and numbers with integer value zero are falsey.
- `=` compares, `#` formats numbers with a `DecimalFormat` pattern and ISO instants with a
  `SimpleDateFormat` pattern.
- `&` marks a JSON object so a following template applies once per property.
- `<` applies a template by resource path; `` <`...` `` applies an inline template. Templates embed
  expressions in braces (`{$.name}`); `\{` escapes a literal brace.

## Implementation notes

`DecimalFormat` and `SimpleDateFormat` are held in `ThreadLocal`s because neither is thread-safe.
Parsed templates are cached in `IuCacheMap` instances with a 5-minute TTL; callers can substitute
their own maps through the five-argument `eval` when they need different lifetimes.

Any syntax change here needs test coverage for both the accepting and rejecting branch of every
operator — the coverage gate applies, and the parser is dense with branches.
