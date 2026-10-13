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

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import edu.iu.IuException;

/**
 * Adapts a validated value to the shape a constraint rule needs.
 * 
 * <p>
 * Each accessor throws {@link UnsupportedOperationException} for a value the
 * corresponding constraint does not apply to, rather than silently passing.
 * A constraint declared on an incompatible property is a declaration error, and
 * quietly reporting such a property as valid is the worse failure.
 * </p>
 */
final class Values {

	/**
	 * Counts the elements of a sized value.
	 * 
	 * @param value {@link CharSequence}, {@link Collection}, {@link Iterable},
	 *              {@link Map}, or array
	 * @return length, size, or element count
	 * @throws UnsupportedOperationException if {@code value} has no size
	 */
	static int size(Object value) {
		if (value instanceof CharSequence charSequence)
			return charSequence.length();
		if (value instanceof Collection<?> collection)
			return collection.size();
		if (value instanceof Map<?, ?> map)
			return map.size();
		if (value.getClass().isArray())
			return Array.getLength(value);
		if (value instanceof Iterable<?> iterable) {
			var count = 0;
			for (final var i = iterable.iterator(); i.hasNext(); i.next())
				count++;
			return count;
		}
		throw new UnsupportedOperationException(unsupported(value, "a sized value"));
	}

	/**
	 * Adapts a value to text.
	 * 
	 * @param value {@link CharSequence}
	 * @return the value itself
	 * @throws UnsupportedOperationException if {@code value} is not a
	 *                                       {@link CharSequence}
	 */
	static CharSequence text(Object value) {
		if (value instanceof CharSequence charSequence)
			return charSequence;
		throw new UnsupportedOperationException(unsupported(value, "text"));
	}

	/**
	 * Adapts a value to a boolean.
	 * 
	 * @param value {@link Boolean}
	 * @return the value itself
	 * @throws UnsupportedOperationException if {@code value} is not a {@link Boolean}
	 */
	static boolean bool(Object value) {
		if (value instanceof Boolean b)
			return b;
		throw new UnsupportedOperationException(unsupported(value, "a boolean"));
	}

	/**
	 * Adapts a value to an exactly comparable number.
	 * 
	 * <p>
	 * {@code float} and {@code double} are converted through
	 * {@link BigDecimal#valueOf(double)}, so a bound comparison sees the shortest
	 * decimal that round-trips the binary value rather than its exact binary
	 * expansion.
	 * </p>
	 * 
	 * @param value {@link Number} or a {@link CharSequence} holding a decimal
	 *              numeral
	 * @return equivalent {@link BigDecimal}
	 * @throws UnsupportedOperationException if {@code value} is not numeric
	 * @throws NumberFormatException         if {@code value} is text that is not a
	 *                                       decimal numeral
	 */
	static BigDecimal number(Object value) {
		if (value instanceof BigDecimal bigDecimal)
			return bigDecimal;
		if (value instanceof BigInteger bigInteger)
			return new BigDecimal(bigInteger);
		if (value instanceof Double || value instanceof Float)
			return BigDecimal.valueOf(((Number) value).doubleValue());
		if (value instanceof Number number)
			return BigDecimal.valueOf(number.longValue());
		if (value instanceof CharSequence charSequence)
			return new BigDecimal(charSequence.toString());
		throw new UnsupportedOperationException(unsupported(value, "a number"));
	}

	/**
	 * Compares a temporal value to the present instant.
	 * 
	 * <p>
	 * {@link Date} and {@link Calendar} are compared directly. Every
	 * {@code java.time} and {@code java.time.chrono} type the Jakarta Validation
	 * specification lists for {@link jakarta.validation.constraints.Past} declares
	 * a static {@code now()} returning its own type, so the present is obtained
	 * through that method and compared with {@link Comparable}. This keeps the
	 * supported set identical to the specification's without enumerating it.
	 * </p>
	 * 
	 * @param value temporal value
	 * @return negative if {@code value} precedes the present, zero if it is the
	 *         present, positive if it follows
	 * @throws UnsupportedOperationException if {@code value} is not a supported
	 *                                       temporal type
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static int compareToNow(Object value) {
		if (value instanceof Date date)
			return date.compareTo(new Date());
		if (value instanceof Calendar calendar)
			return calendar.compareTo(Calendar.getInstance());

		final var now = now(value.getClass());
		if (now == null || !(value instanceof Comparable))
			throw new UnsupportedOperationException(unsupported(value, "a temporal value"));

		return ((Comparable) value).compareTo(now);
	}

	/**
	 * Invokes a temporal type's static {@code now()} method.
	 * 
	 * @param type temporal type
	 * @return the present, as an instance of {@code type}; null if {@code type}
	 *         declares no static no-argument {@code now()} returning its own type
	 */
	private static Object now(Class<?> type) {
		final Method now;
		try {
			now = type.getMethod("now");
		} catch (NoSuchMethodException e) {
			return null;
		}

		if (!Modifier.isStatic(now.getModifiers()) //
				|| !type.isAssignableFrom(now.getReturnType()))
			return null;

		return IuException.uncheckedInvocation(() -> now.invoke(null));
	}

	/**
	 * Describes a value that a constraint cannot be applied to.
	 * 
	 * @param value    offending value
	 * @param expected description of what the constraint requires
	 * @return message for {@link UnsupportedOperationException}
	 */
	private static String unsupported(Object value, String expected) {
		return value.getClass().getName() + " is not " + expected;
	}

	private Values() {
	}

}
