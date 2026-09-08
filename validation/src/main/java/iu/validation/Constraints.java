/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.validation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import edu.iu.IuException;
import jakarta.validation.Constraint;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

/**
 * The rule for each built-in Jakarta Validation constraint annotation.
 *
 * <p>
 * Every rule but {@link NotNull}, {@link NotEmpty}, {@link NotBlank}, and
 * {@link Null} treats a null value as valid, per the specification: absence is
 * {@link NotNull}'s concern alone, so a property can be optional and still be
 * constrained when present.
 * </p>
 */
final class Constraints {

	/**
	 * Matches an email address: a dotted local part of unreserved and permitted
	 * special characters, then a domain of hyphen-separated labels.
	 *
	 * <p>
	 * Deliberately stricter than {@code something@something} and more permissive
	 * than a full RFC 5322 parse, which admits comments, quoted local parts, and
	 * address literals that no caller of this library wants to accept.
	 * </p>
	 */
	private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("" //
			+ "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*" //
			+ "@" //
			+ "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?" //
			+ "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*");

	/**
	 * Compiled regular expressions, keyed by flag mask and pattern.
	 *
	 * <p>
	 * Unbounded by design, and safe: every key originates in an annotation
	 * attribute, so the key space is fixed by the application's source code.
	 * </p>
	 */
	private static final Map<String, java.util.regex.Pattern> PATTERNS = new ConcurrentHashMap<>();

	/** Rule for each supported constraint annotation type. */
	private static final Map<Class<? extends Annotation>, ConstraintRule> RULES = rules();

	/**
	 * Determines whether an annotation type is a constraint this class can
	 * evaluate.
	 *
	 * @param annotationType annotation type
	 * @return true if a rule is registered for {@code annotationType}; else false
	 */
	static boolean isConstraint(Class<? extends Annotation> annotationType) {
		return RULES.containsKey(annotationType);
	}

	/**
	 * Determines whether an annotation type declares itself a constraint that this
	 * class has no rule for.
	 *
	 * <p>
	 * A custom constraint is meta-annotated {@link Constraint}. Detecting these
	 * rather than ignoring them is deliberate: a constraint that silently does
	 * nothing is worse than one that refuses to run.
	 * </p>
	 *
	 * @param annotationType annotation type
	 * @return true if {@code annotationType} is meta-annotated {@link Constraint}
	 *         but is not {@link #isConstraint(Class) registered}; else false
	 */
	static boolean isUnsupportedConstraint(Class<? extends Annotation> annotationType) {
		return !isConstraint(annotationType) //
				&& annotationType.isAnnotationPresent(Constraint.class);
	}

	/**
	 * Gets the rule for a constraint annotation type.
	 *
	 * @param annotationType annotation type
	 * @return rule; null if none is registered
	 */
	static ConstraintRule rule(Class<? extends Annotation> annotationType) {
		return RULES.get(annotationType);
	}

	/**
	 * Determines whether a constraint declaration applies to the default validation
	 * group.
	 *
	 * <p>
	 * An empty {@code groups()} means {@link Default}, so an unqualified constraint
	 * applies. A declaration that names only other groups does not.
	 * </p>
	 *
	 * @param constraint constraint annotation
	 * @return true if the declaration applies to {@link Default}; else false
	 */
	static boolean appliesToDefaultGroup(Annotation constraint) {
		final var groups = (Class<?>[]) attribute(constraint, "groups");
		if (groups.length == 0)
			return true;

		for (final var group : groups)
			if (group == Default.class)
				return true;

		return false;
	}

	/**
	 * Unwraps a repeatable constraint container, such as {@code @Pattern.List}.
	 *
	 * <p>
	 * Detected structurally rather than by name: a container declares
	 * {@code value()} returning an array of an annotation type that is
	 * {@link Repeatable} by the container. This covers every built-in {@code List}
	 * container and any custom repeatable constraint, and returns every element
	 * rather than only the first.
	 * </p>
	 *
	 * @param annotation annotation that may be a repeatable container
	 * @return contained annotations, in declaration order; null if
	 *         {@code annotation} is not a repeatable container
	 */
	static Annotation[] unwrapRepeatable(Annotation annotation) {
		final Method value;
		try {
			value = annotation.annotationType().getMethod("value");
		} catch (NoSuchMethodException e) {
			return null;
		}

		final var returnType = value.getReturnType();
		if (!returnType.isArray() //
				|| !returnType.getComponentType().isAnnotation())
			return null;

		final var repeatable = returnType.getComponentType().getAnnotation(Repeatable.class);
		if (repeatable == null //
				|| repeatable.value() != annotation.annotationType())
			return null;

		return IuException.uncheckedInvocation(() -> (Annotation[]) value.invoke(annotation));
	}

	/**
	 * Reads one attribute of an annotation.
	 *
	 * @param annotation    annotation
	 * @param attributeName name of an attribute the annotation declares
	 * @return attribute value
	 */
	static Object attribute(Annotation annotation, String attributeName) {
		return IuException
				.uncheckedInvocation(() -> annotation.annotationType().getMethod(attributeName).invoke(annotation));
	}

	/**
	 * Wraps a rule so that a null value is valid.
	 *
	 * @param test rule for a non-null value
	 * @return null-tolerant rule
	 */
	private static ConstraintRule optional(BiPredicate<Annotation, Object> test) {
		return (constraint, value) -> value == null || test.test(constraint, value);
	}

	/**
	 * Compiles and caches a regular expression.
	 *
	 * @param regexp regular expression
	 * @param flags  {@link Pattern.Flag} values to combine
	 * @return compiled pattern
	 */
	private static java.util.regex.Pattern pattern(String regexp, Pattern.Flag[] flags) {
		var mask = 0;
		for (final var flag : flags)
			mask |= flag.getValue();

		final var mode = mask;
		return PATTERNS.computeIfAbsent(mask + ":" + regexp,
				key -> java.util.regex.Pattern.compile(regexp, mode));
	}

	/**
	 * Builds the rule registry.
	 *
	 * @return unmodifiable rule map
	 */
	private static Map<Class<? extends Annotation>, ConstraintRule> rules() {
		return Map.ofEntries( //
				Map.entry(Null.class, (ConstraintRule) (c, v) -> v == null), //
				Map.entry(NotNull.class, (ConstraintRule) (c, v) -> v != null), //
				Map.entry(NotBlank.class,
						(ConstraintRule) (c, v) -> v != null && !Values.text(v).toString().isBlank()), //
				Map.entry(NotEmpty.class, (ConstraintRule) (c, v) -> v != null && Values.size(v) > 0), //

				Map.entry(AssertTrue.class, optional((c, v) -> Values.bool(v))), //
				Map.entry(AssertFalse.class, optional((c, v) -> !Values.bool(v))), //

				Map.entry(Size.class, optional((c, v) -> {
					final var size = Values.size(v);
					return size >= ((Size) c).min() && size <= ((Size) c).max();
				})), //
				Map.entry(Pattern.class, optional((c, v) -> {
					final var pattern = (Pattern) c;
					return pattern(pattern.regexp(), pattern.flags()).matcher(Values.text(v)).matches();
				})), //
				Map.entry(Email.class, optional((c, v) -> {
					final var text = Values.text(v);
					if (text.length() == 0)
						return true;
					if (!EMAIL.matcher(text).matches())
						return false;

					final var email = (Email) c;
					return ".*".equals(email.regexp()) //
							|| pattern(email.regexp(), email.flags()).matcher(text).matches();
				})), //

				Map.entry(Min.class,
						optional((c, v) -> Values.number(v).compareTo(BigDecimal.valueOf(((Min) c).value())) >= 0)), //
				Map.entry(Max.class,
						optional((c, v) -> Values.number(v).compareTo(BigDecimal.valueOf(((Max) c).value())) <= 0)), //
				Map.entry(DecimalMin.class, optional((c, v) -> {
					final var min = (DecimalMin) c;
					final var comparison = Values.number(v).compareTo(new BigDecimal(min.value()));
					return min.inclusive() ? comparison >= 0 : comparison > 0;
				})), //
				Map.entry(DecimalMax.class, optional((c, v) -> {
					final var max = (DecimalMax) c;
					final var comparison = Values.number(v).compareTo(new BigDecimal(max.value()));
					return max.inclusive() ? comparison <= 0 : comparison < 0;
				})), //
				Map.entry(Digits.class, optional((c, v) -> {
					final var digits = (Digits) c;
					final var number = Values.number(v).stripTrailingZeros();
					final var fraction = Math.max(number.scale(), 0);
					final var integer = number.precision() - number.scale();
					return integer <= digits.integer() && fraction <= digits.fraction();
				})), //

				Map.entry(Positive.class, optional((c, v) -> Values.number(v).signum() > 0)), //
				Map.entry(PositiveOrZero.class, optional((c, v) -> Values.number(v).signum() >= 0)), //
				Map.entry(Negative.class, optional((c, v) -> Values.number(v).signum() < 0)), //
				Map.entry(NegativeOrZero.class, optional((c, v) -> Values.number(v).signum() <= 0)), //

				Map.entry(Past.class, optional((c, v) -> Values.compareToNow(v) < 0)), //
				Map.entry(PastOrPresent.class, optional((c, v) -> Values.compareToNow(v) <= 0)), //
				Map.entry(Future.class, optional((c, v) -> Values.compareToNow(v) > 0)), //
				Map.entry(FutureOrPresent.class, optional((c, v) -> Values.compareToNow(v) >= 0)));
	}

	private Constraints() {
	}

}
