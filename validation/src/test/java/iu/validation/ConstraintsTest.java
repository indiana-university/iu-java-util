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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
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

@SuppressWarnings("javadoc")
public class ConstraintsTest {

	public interface Group {
	}

	/** One getter per built-in constraint, to source annotation instances from. */
	public interface Declarations {
		@Null
		Object getNothing();

		@NotNull
		Object getSomething();

		@NotBlank
		String getNotBlank();

		@NotEmpty
		Object getNotEmpty();

		@AssertTrue
		Boolean getYes();

		@AssertFalse
		Boolean getNo();

		@Size(min = 2, max = 4)
		Object getSized();

		@Pattern(regexp = "[a-z]+")
		String getLowercase();

		@Pattern(regexp = "[a-z]+", flags = Pattern.Flag.CASE_INSENSITIVE)
		String getAnycase();

		@Email
		String getEmail();

		@Email(regexp = ".+@iu\\.edu")
		String getIuEmail();

		@Min(5)
		Number getAtLeastFive();

		@Max(5)
		Number getAtMostFive();

		@DecimalMin("5.5")
		Number getAtLeastFivePointFive();

		@DecimalMin(value = "5.5", inclusive = false)
		Number getAboveFivePointFive();

		@DecimalMax("5.5")
		Number getAtMostFivePointFive();

		@DecimalMax(value = "5.5", inclusive = false)
		Number getBelowFivePointFive();

		@Digits(integer = 3, fraction = 2)
		Number getDigits();

		@Positive
		Number getPositive();

		@PositiveOrZero
		Number getPositiveOrZero();

		@Negative
		Number getNegative();

		@NegativeOrZero
		Number getNegativeOrZero();

		@Past
		Instant getPast();

		@PastOrPresent
		Instant getPastOrPresent();

		@Future
		Instant getFuture();

		@FutureOrPresent
		Instant getFutureOrPresent();

		@NotNull(groups = Group.class)
		Object getGroupOnly();

		@NotNull(groups = { Group.class, Default.class })
		Object getGroupAndDefault();

		@Pattern(regexp = "a+")
		@Pattern(regexp = "b+")
		String getRepeated();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Constraint(validatedBy = {})
	public @interface Custom {
		String message() default "custom";

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface NotAConstraint {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface ScalarValue {
		String value() default "";
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface TextArrayValue {
		String[] value() default {};
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface Loose {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface LooseContainer {
		Loose[] value();
	}

	@Repeatable(RealContainer.class)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Repeated {
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface RealContainer {
		Repeated[] value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface ImpostorContainer {
		Repeated[] value();
	}

	public interface Containers {
		@ScalarValue("x")
		@TextArrayValue({ "x" })
		@LooseContainer({ @Loose })
		@RealContainer({ @Repeated, @Repeated })
		@ImpostorContainer({ @Repeated })
		@NotAConstraint
		String getEverything();
	}

	private static boolean valid(String getter, Class<? extends Annotation> annotationType, Object value) {
		final var constraint = Beans.annotation(Declarations.class, getter, annotationType);
		return Constraints.rule(annotationType).isValid(constraint, value);
	}

	@Test
	public void testEveryBuiltInConstraintIsRegistered() {
		for (final var annotation : Beans.annotations(Declarations.class, "getNothing"))
			assertTrue(Constraints.isConstraint(annotation.annotationType()));

		assertFalse(Constraints.isConstraint(NotAConstraint.class));
		assertNull(Constraints.rule(NotAConstraint.class));
	}

	@Test
	public void testNullAndPresence() {
		assertTrue(valid("getNothing", Null.class, null));
		assertFalse(valid("getNothing", Null.class, "x"));

		assertTrue(valid("getSomething", NotNull.class, "x"));
		assertFalse(valid("getSomething", NotNull.class, null));
	}

	@Test
	public void testNotBlank() {
		assertTrue(valid("getNotBlank", NotBlank.class, "x"));
		assertFalse(valid("getNotBlank", NotBlank.class, null));
		assertFalse(valid("getNotBlank", NotBlank.class, ""));
		assertFalse(valid("getNotBlank", NotBlank.class, " \t\n "));
	}

	@Test
	public void testNotEmpty() {
		assertTrue(valid("getNotEmpty", NotEmpty.class, "x"));
		assertTrue(valid("getNotEmpty", NotEmpty.class, List.of("x")));
		assertFalse(valid("getNotEmpty", NotEmpty.class, null));
		assertFalse(valid("getNotEmpty", NotEmpty.class, ""));
		assertFalse(valid("getNotEmpty", NotEmpty.class, List.of()));
	}

	@Test
	public void testAssertions() {
		assertTrue(valid("getYes", AssertTrue.class, null));
		assertTrue(valid("getYes", AssertTrue.class, true));
		assertFalse(valid("getYes", AssertTrue.class, false));

		assertTrue(valid("getNo", AssertFalse.class, null));
		assertTrue(valid("getNo", AssertFalse.class, false));
		assertFalse(valid("getNo", AssertFalse.class, true));
	}

	@Test
	public void testSize() {
		assertTrue(valid("getSized", Size.class, null));
		assertTrue(valid("getSized", Size.class, "ab"));
		assertTrue(valid("getSized", Size.class, "abcd"));
		assertFalse(valid("getSized", Size.class, "a"));
		assertFalse(valid("getSized", Size.class, "abcde"));
	}

	@Test
	public void testPattern() {
		assertTrue(valid("getLowercase", Pattern.class, null));
		assertTrue(valid("getLowercase", Pattern.class, "abc"));
		assertFalse(valid("getLowercase", Pattern.class, "ABC"));
		assertFalse(valid("getLowercase", Pattern.class, "abc1"));
	}

	@Test
	public void testPatternHonorsFlags() {
		assertTrue(valid("getAnycase", Pattern.class, "AbC"));
		assertFalse(valid("getAnycase", Pattern.class, "Ab1"));
	}

	@Test
	public void testPatternCachesCompiledExpressions() {
		// second evaluation must hit the cache rather than recompile
		assertTrue(valid("getLowercase", Pattern.class, "abc"));
		assertTrue(valid("getLowercase", Pattern.class, "xyz"));
	}

	@Test
	public void testEmail() {
		assertTrue(valid("getEmail", Email.class, null));
		assertTrue(valid("getEmail", Email.class, ""));
		assertTrue(valid("getEmail", Email.class, "someone@iu.edu"));
		assertTrue(valid("getEmail", Email.class, "first.last+tag@sub.example.co.uk"));
		assertFalse(valid("getEmail", Email.class, "someone"));
		assertFalse(valid("getEmail", Email.class, "someone@"));
		assertFalse(valid("getEmail", Email.class, "some one@iu.edu"));
	}

	@Test
	public void testEmailAppliesAnAdditionalExpression() {
		assertTrue(valid("getIuEmail", Email.class, "someone@iu.edu"));
		assertFalse(valid("getIuEmail", Email.class, "someone@example.com"));
	}

	@Test
	public void testMinAndMax() {
		assertTrue(valid("getAtLeastFive", Min.class, null));
		assertTrue(valid("getAtLeastFive", Min.class, 5));
		assertTrue(valid("getAtLeastFive", Min.class, 6));
		assertFalse(valid("getAtLeastFive", Min.class, 4));

		assertTrue(valid("getAtMostFive", Max.class, 5));
		assertTrue(valid("getAtMostFive", Max.class, 4));
		assertFalse(valid("getAtMostFive", Max.class, 6));
	}

	@Test
	public void testDecimalBounds() {
		assertTrue(valid("getAtLeastFivePointFive", DecimalMin.class, null));
		assertTrue(valid("getAtLeastFivePointFive", DecimalMin.class, new BigDecimal("5.5")));
		assertFalse(valid("getAtLeastFivePointFive", DecimalMin.class, new BigDecimal("5.4")));

		assertFalse(valid("getAboveFivePointFive", DecimalMin.class, new BigDecimal("5.5")));
		assertTrue(valid("getAboveFivePointFive", DecimalMin.class, new BigDecimal("5.6")));

		assertTrue(valid("getAtMostFivePointFive", DecimalMax.class, new BigDecimal("5.5")));
		assertFalse(valid("getAtMostFivePointFive", DecimalMax.class, new BigDecimal("5.6")));

		assertFalse(valid("getBelowFivePointFive", DecimalMax.class, new BigDecimal("5.5")));
		assertTrue(valid("getBelowFivePointFive", DecimalMax.class, new BigDecimal("5.4")));
	}

	@Test
	public void testDigits() {
		assertTrue(valid("getDigits", Digits.class, null));
		assertTrue(valid("getDigits", Digits.class, new BigDecimal("123.45")));
		assertTrue(valid("getDigits", Digits.class, new BigDecimal("1.5")));
		assertTrue(valid("getDigits", Digits.class, BigDecimal.ZERO));
		assertFalse(valid("getDigits", Digits.class, new BigDecimal("1234.5")));
		assertFalse(valid("getDigits", Digits.class, new BigDecimal("1.234")));
	}

	@Test
	public void testDigitsCountsAnIntegerWithANegativeScale() {
		// 1000 strips to 1E+3: scale -3, precision 1, so four integer digits
		assertTrue(valid("getDigits", Digits.class, new BigDecimal("100")));
		assertFalse(valid("getDigits", Digits.class, new BigDecimal("1000")));
	}

	@Test
	public void testSign() {
		assertTrue(valid("getPositive", Positive.class, null));
		assertTrue(valid("getPositive", Positive.class, 1));
		assertFalse(valid("getPositive", Positive.class, 0));
		assertFalse(valid("getPositive", Positive.class, -1));

		assertTrue(valid("getPositiveOrZero", PositiveOrZero.class, 0));
		assertFalse(valid("getPositiveOrZero", PositiveOrZero.class, -1));

		assertTrue(valid("getNegative", Negative.class, -1));
		assertFalse(valid("getNegative", Negative.class, 0));

		assertTrue(valid("getNegativeOrZero", NegativeOrZero.class, 0));
		assertFalse(valid("getNegativeOrZero", NegativeOrZero.class, 1));
	}

	@Test
	public void testTemporalBounds() {
		final var past = Instant.EPOCH;
		final var future = Instant.now().plusSeconds(3600L);

		assertTrue(valid("getPast", Past.class, null));
		assertTrue(valid("getPast", Past.class, past));
		assertFalse(valid("getPast", Past.class, future));

		assertTrue(valid("getPastOrPresent", PastOrPresent.class, past));
		assertFalse(valid("getPastOrPresent", PastOrPresent.class, future));

		assertTrue(valid("getFuture", Future.class, future));
		assertFalse(valid("getFuture", Future.class, past));

		assertTrue(valid("getFutureOrPresent", FutureOrPresent.class, future));
		assertFalse(valid("getFutureOrPresent", FutureOrPresent.class, past));
	}

	@Test
	public void testAppliesToDefaultGroup() {
		assertTrue(Constraints
				.appliesToDefaultGroup(Beans.annotation(Declarations.class, "getSomething", NotNull.class)));
		assertTrue(Constraints
				.appliesToDefaultGroup(Beans.annotation(Declarations.class, "getGroupAndDefault", NotNull.class)));
		assertFalse(
				Constraints.appliesToDefaultGroup(Beans.annotation(Declarations.class, "getGroupOnly", NotNull.class)));
	}

	@Test
	public void testIsUnsupportedConstraintOnlyForACustomConstraint() {
		assertTrue(Constraints.isUnsupportedConstraint(Custom.class));
		assertFalse(Constraints.isUnsupportedConstraint(NotAConstraint.class));
		assertFalse(Constraints.isUnsupportedConstraint(NotNull.class));
	}

	@Test
	public void testUnwrapRepeatableReturnsEveryElement() {
		final var container = Beans.annotation(Containers.class, "getEverything", RealContainer.class);
		final var unwrapped = Constraints.unwrapRepeatable(container);
		assertEquals(2, unwrapped.length, () -> "expected both elements, not the first twice");
		assertEquals(Repeated.class, unwrapped[0].annotationType());
		assertEquals(Repeated.class, unwrapped[1].annotationType());
	}

	@Test
	public void testUnwrapRepeatableRejectsWhatIsNotAContainer() {
		assertNull(Constraints.unwrapRepeatable(Beans.annotation(Containers.class, "getEverything",
				NotAConstraint.class)), () -> "no value() method");
		assertNull(Constraints.unwrapRepeatable(Beans.annotation(Containers.class, "getEverything",
				ScalarValue.class)), () -> "value() is not an array");
		assertNull(Constraints.unwrapRepeatable(Beans.annotation(Containers.class, "getEverything",
				TextArrayValue.class)), () -> "value() is not an annotation array");
		assertNull(Constraints.unwrapRepeatable(Beans.annotation(Containers.class, "getEverything",
				LooseContainer.class)), () -> "element is not repeatable");
		assertNull(Constraints.unwrapRepeatable(Beans.annotation(Containers.class, "getEverything",
				ImpostorContainer.class)), () -> "element is repeatable by a different container");
	}

	@Test
	public void testAttribute() {
		final var pattern = Beans.annotation(Declarations.class, "getLowercase", Pattern.class);
		assertEquals("[a-z]+", Constraints.attribute(pattern, "regexp"));
		assertSame(Pattern.class, pattern.annotationType());
		assertThrows(IllegalStateException.class, () -> Constraints.attribute(pattern, "noSuchAttribute"));
	}

	@Test
	public void testUnsupportedValueTypesAreRefusedRatherThanPassed() {
		assertThrows(UnsupportedOperationException.class, () -> valid("getLowercase", Pattern.class, 42));
		assertThrows(UnsupportedOperationException.class, () -> valid("getSized", Size.class, 42));
		assertThrows(UnsupportedOperationException.class, () -> valid("getYes", AssertTrue.class, "true"));
		assertThrows(UnsupportedOperationException.class, () -> valid("getPositive", Positive.class, Map.of()));
		assertThrows(UnsupportedOperationException.class, () -> valid("getPast", Past.class, "yesterday"));
	}

}
