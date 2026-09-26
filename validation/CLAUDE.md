# CLAUDE.md — validation

`iu-java-validation` / module `iu.util.validation` / package `edu.iu.validation`

Read the repository root `CLAUDE.md` first for build commands and shared conventions.

## Role

Deep bean validation over the [Jakarta Validation](https://jakarta.ee/specifications/bean-validation/3.1/) constraint vocabulary. One pass walks a business object graph and collects **every** constraint failure into an `IuValidationResult`; nothing short-circuits.

This is **not** a `jakarta.validation.Validator` implementation. The constraint annotations are consumed as a vocabulary only — there is no `ValidatorFactory`, no `Validation.byProvider`, no `validation.xml`, and no provider discovery. `jakarta.validation-api` is `requires transitive`, because the annotations are inseparable from the validator; adding any other transitive requirement here propagates widely, so do it deliberately.

Origin: this replaces `iu.sis.IuValidator` in `ess-sis-api`, which was a partial reimplementation covering three of the twenty-two built-in constraints and was marked `@Deprecated ... promote as iu-java-validation`. It is also the metadata source for the intended JSON Schema and OpenAPI generation described in `../CLAUDE.md`'s downstream notes — which is why `IuConstraintViolation.attributes()` exposes every annotation attribute rather than only the ones a message needs.

## Public API

`IuValidator` is a static entry point. Every method comes in a `validate` form returning an `IuValidationResult` and a `require` form throwing `IuValidationException` instead: `(Class, Object)` and `(Object)` validate a whole object, `(AnnotatedElement, Object)` and `(AnnotatedElement, String, Object)` validate one value against one annotation target — see below.

Migrating a call site from the old `IuValidator.validate(Type.class, value)` means `require`, not `validate` — the old method threw.

`IuValidationException extends edu.iu.IuBadRequestException`, so a web boundary already mapping that exception to `400 BAD REQUEST` needs no change, and `getMessage()` is the redacted report so violations reach a log record even where nothing inspects `getResult()`.

## Standalone annotation targets

`validate(AnnotatedElement, Object)` validates a value the caller already holds against the constraints on the target that declares it, for callers with no bean to walk from — the motivating case is a REST dispatcher that converts one parameter at a time and has the `Parameter` in hand at the moment it has the value.

`Class` → the existing bean walk. `Field` → its own name. `Method` → the bean property it reads if it is a getter (`getEmplid` → `emplid`, `isActive` → `active`), else its own name. `Parameter` → `Parameter.getName()`. Everything else — `Constructor`, `Package`, `Module`, `AnnotatedType` — throws `IllegalArgumentException`. A `Constructor` is refused deliberately: it has no value to validate, and validating an invocation is a separate Jakarta feature this module does not implement.

**`Parameter.getName()` is `arg0` unless the declaring code was compiled with `-parameters`, which this build does not set.** Use the `validate(AnnotatedElement, String, Object)` overload wherever a better name exists — for a REST parameter that name lives in the JAX-RS annotation (`@QueryParam("message")`), not in the bytecode. For a `Class` target the supplied name roots every path of the bean walk instead, so a request entity validated as `"body"` reports `body.emplid`.

**Only the target's own annotations apply.** A bean walk unions constraints across the hierarchy because they are additive there; validating one target does not, because the caller named that target specifically. So a field and its getter can carry different rules and each entry point sees only its own.

**`Class` is itself an `AnnotatedElement`, and the overloads are deliberately arranged so that does not matter.** For `validate(Foo.class, foo)` the compiler picks `validate(Class<T>, T)` as more specific; where inference cannot match `T` it picks the `AnnotatedElement` overload, which delegates to the same walk. Both routes behave identically, so nobody has to think about which one bound.

`ConstraintCollector` is the shared accumulator: `BeanModel` creates one per property and merges every declaration found in the hierarchy, `ElementModel` creates one per target and calls `element(...)` once. Put new constraint-discovery logic there rather than in either caller, or the two will drift the way the four property walks described above already have.

## Things that are easy to get wrong

**Constraints are unioned across the type hierarchy, not resolved to the nearest declaration.** Jakarta constraints are additive: a rule on an interface method and another on the implementing method both apply. This is also what makes the model correct for a **dynamic proxy**, whose own methods carry no annotations at all — the production DTOs here are proxies of getter interfaces, so taking the nearest declaration would validate nothing. Identical declarations failing at the same path are deduplicated when the result is assembled, not when the model is built.

**Property discovery deliberately mirrors `iu.client.JsonSerializer.serialize`** — `Introspector` at every level of the hierarchy, super-interfaces included, since `Introspector` walks superclasses but not super-interfaces. Keep the two in step: a generated schema must describe exactly the properties that are validated, and `JsonSerializer` is what decides what actually goes on the wire. The same walk exists a third and fourth time in `iu.dao.DaoUtils` and in `ess-sis-api`'s `OpenAPIUtils`; those already disagree with each other.

**A property with no constraint and no cascade is not modeled at all**, so the walk never invokes a getter it has no rule for. Add a fixture property expecting it to be read and nothing will happen unless it carries a rule.

**Array element constraints are intentionally unsupported.** `@NotEmpty String[] getWords()` is, per the JLS, *simultaneously* a declaration annotation on the accessor and a `TYPE_USE` annotation on the component, and no syntax distinguishes the two intents — reading the component would apply every dual-target constraint twice, once to the array and once to each element. Element constraints belong on a type argument: `List<@NotBlank String>`. `@Valid` is unaffected and still cascades into array elements.

**The walk is an explicit breadth-first queue, not recursion**, so a deep graph costs heap rather than stack and needs no depth limit. A bean is validated at most once per pass by instance identity, which terminates cycles and means an object reachable by two paths is reported under whichever path is reached first — properties are walked in name order, so that choice is deterministic.

**`Optional` is transparent to the path.** Its contents are its single container element and keep the property's own path, so a caller sees `birthDate`, not `birthDate[0]`. Nested containers (`Optional<List<X>>`, `List<List<X>>`) are not descended.

**Only the default group is evaluated.** A constraint declaring no `groups()` belongs to `Default` and is kept; one naming only other groups is filtered out *when the model is built*, so it costs nothing per validation. Explicit group selection is not implemented.

**A custom `@Constraint(validatedBy = ...)` annotation throws `UnsupportedOperationException`** when the model is built, rather than being ignored. A constraint that silently does nothing is the worse failure. Adding support means registering a rule in `Constraints`; that registry is the seam.

**Invalid values are redacted from `report()` by default.** Validated input is untrusted and frequently PII. `report(true)` opts a rendered report into including values; `IuConstraintViolation.invalidValue()` is always available programmatically.

**Constraints refuse incompatible value types** rather than passing them. `Values` throws `UnsupportedOperationException`, and `ValidationWalk.evaluate` restates it with the offending path — `@Pattern` on an `Integer` property is a declaration error, and quietly reporting such a property as valid would hide it.

## Messages

`src/main/resources/iu/validation/ValidationMessages.properties` holds the default templates, keyed exactly as the specification names them so each annotation's default `message` attribute resolves without redeclaration. Interpolation implements the required `{param}` substitution only.

The specification's reference templates for `DecimalMin`/`DecimalMax` use an EL expression for the inclusive/exclusive wording; this module splits them into `.message` and `.exclusive.message` keys instead, which is the only reason it needs no Jakarta EL provider. If EL interpolation is ever wanted, `edu.iu.util.el.El.eval` is the natural implementation — but it would pull `iu.util.el` → `iu.util.client` → `jakarta.json` into the dependency chain, which this module deliberately avoids so that it stays usable from DTO modules carrying only `iu-java-base`.

## `iu.util.type` is optional

`requires static iu.util.type`. Nothing uses it yet. The reflective introspector in `BeanModel` is the default and authoritative one; an `IuType`-backed backend would add hierarchy-resolved generics.

Note that `requires static` alone is not sufficient protection: `edu.iu.type.spi.TypeImplementation.PROVIDER` is an eager `static final` `ServiceLoader...iterator().next()`, so the module can resolve while the *provider* is absent and the first touch throws `ExceptionInInitializerError`. Any activation probe must be guarded against `Throwable`, not just checked for class presence. Also beware that `TypeTemplate.isNative()` makes `properties()` return **silently empty** for a named module that does not `opens` its packages to `iu.util.type.impl` — a validator built on that would report a correctly annotated bean as valid.

## Testing

`Beans.of(Class, Map)` builds a fixture from a dynamic proxy over a getter interface, which is both what production objects look like and the reason the annotation-union design exists.

Fixture interfaces must hold **only properties that are valid when absent**, one property per concern. A property-level `@NotNull` on a shared fixture fails in every test that does not set it, which reads as a code failure and is not.
