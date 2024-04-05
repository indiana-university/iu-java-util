/*
 * Copyright © 2024 Indiana University
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
package edu.iu;

import java.lang.reflect.Array;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Simplifies building efficient {@link Object#equals(Object)},
 * {@link Object#hashCode()}, and {@link Comparable#compareTo(Object)} methods
 * on plain Java objects.
 * 
 * <p>
 * The use of this utility is preferred, following the examples below, over
 * other methods of generating these methods. When following these examples, the
 * implements can be expected to follow the expected contracts in a null-safe
 * and type-safe manner without undue object creation.
 * </p>
 * 
 * <dl>
 * <dt>Top level object:</dt>
 * <dd>
 * 
 * <pre>
 * &#064;Override
 * public int hashCode() {
 * 	return ObjectUtil.hashCode(val1, val2);
 * }
 * 
 * &#064;Override
 * public boolean equals(Object obj) {
 * 	if (!ObjectUtil.typeCheck(this, obj))
 * 		return false;
 * 	MyClass other = (MyClass) obj;
 * 	return ObjectUtil.equals(this.val1, other.val1) &amp;&amp; ObjectUtil.equals(this.val2, other.val2);
 * }
 * 
 * &#064;Override
 * public int compareTo(T o) {
 * 	Integer rv = ObjectUtil.compareNullCheck(this, o);
 * 	if (rv != null)
 * 		return rv;
 * 
 * 	rv = ObjectUtil.compareTo(this.val1, o.val1);
 * 	if (rv != 0)
 * 		return rv;
 * 
 * 	return ObjectUtil.compareTo(this.val2, o.val2);
 * }
 * </pre>
 * 
 * </dd>
 * 
 * <dt>Subclass object:</dt>
 * <dd>
 * 
 * <pre>
 * &#064;Override
 * public int hashCode() {
 * 	return ObjectUtil.hashCodeSuper(super.hashCode(), val1, val2);
 * }
 * 
 * &#064;Override
 * public boolean equals(Object obj) {
 * 	if (!ObjectUtil.typeCheck(this, obj))
 * 		return false;
 * 	MyClass other = (MyClass) obj;
 * 	return super.equals(obj) &amp;&amp; ObjectUtil.equals(this.val1, other.val1) &amp;&amp; ObjectUtil.equals(this.val2, other.val2);
 * }
 * 
 * &#064;Override
 * public int compareTo(T o) {
 * 	Integer rv = ObjectUtil.compareNullCheck(this, o);
 * 	if (rv != null)
 * 		return rv;
 * 
 * 	rv = ObjectUtil.compareTo(this.val1, o.val1);
 * 	if (rv != 0)
 * 		return rv;
 * 
 * 	rv = ObjectUtil.compareTo(this.val2, o.val2);
 * 	if (rv != 0)
 * 		return rv;
 * 
 * 	return super.compareTo(o);
 * }
 * </pre>
 * 
 * </dd>
 * </dl>
 * 
 * @since 4.0
 */
public final class IuObject {

	/**
	 * Determines if a name is relative to a package provided by the JDK or JEE
	 * platform.
	 * 
	 * @param name type name
	 * @return {@code true} if a platform type; else false
	 */
	public static boolean isPlatformName(String name) {
		return name.startsWith("jakarta.") // JEE and related
				// JDK packages:
				|| name.startsWith("sun.") //
				|| name.startsWith("com.sun.") //
				|| name.startsWith("java.") //
				|| name.startsWith("javax.") //
				|| name.startsWith("jdk.") //
				|| name.startsWith("netscape.javascript.") //
				|| name.startsWith("org.ietf.jgss.") //
				|| name.startsWith("org.w3c.dom.") //
				|| name.startsWith("org.xml.sax.");
	}

	/**
	 * Asserts that a class is in a module that is named and part of a package that
	 * is not open.
	 * 
	 * @param classToCheck {@link Class}
	 * @throws IllegalStateException if the class is in an open module and/or
	 *                               package
	 */
	public static void assertNotOpen(Class<?> classToCheck) throws IllegalStateException {
		final var module = classToCheck.getModule();
		if (module.isOpen(classToCheck.getPackageName()))
			throw new IllegalStateException("Must be in a named module and not open");
	}

	/**
	 * Enforces that either a current or new value is non-null, and that both
	 * non-null values are equal.
	 * 
	 * @param <T>     value type
	 * @param current current value
	 * @param value   value to set or enforce as already set
	 * @return value
	 * @throws IllegalArgumentException if already set to the same value
	 */
	public static <T> T once(T current, T value) {
		return once(current, value, () -> "requires a single non-null value");
	}

	/**
	 * Enforces that either a current or new value is non-null, and that both
	 * non-null values are equal.
	 * 
	 * @param <T>             value type
	 * @param current         current value
	 * @param value           value to set or enforce as already set
	 * @param messageSupplier provides a message for
	 *                        {@link IllegalArgumentException} if current was
	 *                        already set to a different value
	 * @return value
	 * @throws IllegalArgumentException if already set to the same value
	 */
	public static <T> T once(T current, T value, Supplier<String> messageSupplier) {
		return Objects.requireNonNull(first(current, value), messageSupplier);
	}

	/**
	 * Enforces that a value is either not already set or is already set to the same
	 * value.
	 * 
	 * @param <T>     value type
	 * @param current current value
	 * @param value   value to set or enforce as already set
	 * @return value
	 * @throws IllegalArgumentException if already set to the same value
	 */
	public static <T> T first(T current, T value) {
		return first(current, value, () -> "already set to a different value");
	}

	/**
	 * Enforces that a value is either not already set or is already set to the same
	 * value.
	 * 
	 * @param <T>             value type
	 * @param current         current value
	 * @param value           value to set or enforce as already set
	 * @param messageSupplier provides a message for
	 *                        {@link IllegalArgumentException} if current was
	 *                        already set to a different value
	 * @return value
	 * @throws IllegalArgumentException if already set to the same value
	 */
	public static <T> T first(T current, T value, Supplier<String> messageSupplier) {
		if (current == null)
			return value;
		else if (value == null)
			return current;
		else if (!current.equals(value))
			throw new IllegalArgumentException(messageSupplier.get());
		else
			return value;
	}

	/**
	 * Determines if either or both objects are null, then if both non-null if both
	 * are {@link #equals(Object, Object)}.
	 * 
	 * <p>
	 * This method is the boolean equivalent of {@link #first(Object, Object)}
	 * </p>
	 * 
	 * @param a an object
	 * @param b another object
	 * @return true if either object is null or if both are equal; else false
	 */
	public static boolean represents(Object a, Object b) {
		return a == null || b == null || IuObject.equals(a, b);
	}

	/**
	 * Require value to be an instance of a specific type or null.
	 * 
	 * @param <T>   required type
	 * @param type  required type
	 * @param value value
	 * @return typed value
	 * @throws IllegalArgumentException if the types don't match
	 */
	public static <T> T requireType(Class<T> type, Object value) {
		try {
			return convert(value, type::cast);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("expected " + type, e);
		}
	}

	/**
	 * Require a condition to be true for a value.
	 * 
	 * @param <T>       value type
	 * @param value     value
	 * @param condition condition to verify
	 * @return value
	 * @throws IllegalArgumentException if the types don't match
	 */
	public static <T> T require(T value, Predicate<T> condition) {
		return require(value, condition, () -> null);
	}

	/**
	 * Require a condition to be true for a value if non-null.
	 * 
	 * @param <T>             value type
	 * @param value           value
	 * @param condition       condition to verify
	 * @param messageSupplier provides a message for
	 *                        {@link IllegalArgumentException}
	 * @return value
	 * @throws IllegalArgumentException if the types don't match
	 */
	public static <T> T require(T value, Predicate<T> condition, Supplier<String> messageSupplier) {
		if (value != null //
				&& !condition.test(value))
			throw new IllegalArgumentException(messageSupplier.get());
		return value;
	}

	/**
	 * Passes a value through a conversion function if non-null.
	 * 
	 * @param <S>                source type
	 * @param <T>                result type
	 * @param value              value
	 * @param conversionFunction conversion function
	 * @return converted value
	 */
	public static <S, T> T convert(S value, Function<S, T> conversionFunction) {
		if (value == null)
			return null;
		else
			return conversionFunction.apply(value);
	}

	/**
	 * Perform identity and and null check on two objects, returning a valid value
	 * for {@link Comparable#compareTo(Object)} if any of the checks result in a
	 * conclusive result.
	 * 
	 * @param o1 any object
	 * @param o2 any object
	 * @return 0 if o1 == o2, -1 if o1 is null, 1 if o2 is null; otherwise, return
	 *         null indicating that compareTo should continue to inspect each
	 *         object's specific data.
	 */
	public static Integer compareNullCheck(Object o1, Object o2) {
		if (o1 == o2)
			return 0;
		if (o1 == null)
			return -1;
		if (o2 == null)
			return 1;
		return null;
	}

	/**
	 * Compares two objects with null checks (see
	 * {@link #compareNullCheck(Object, Object)}) and also consistent sort order
	 * based for objects that don't implement {@link Comparable}.
	 * 
	 * @param o1 any object
	 * @param o2 any object
	 * @return Valid {@link Comparator} return value enforcing consistent sort order
	 *         within the same JVM instance.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static int compareTo(Object o1, Object o2) {
		Integer rv = compareNullCheck(o1, o2);
		if (rv != null)
			return rv;

		Comparable v1;
		Comparable v2;
		if ((o1.getClass() != o2.getClass()) || !(o1 instanceof Comparable)) {
			v1 = (Comparable) Integer.valueOf(o1.hashCode());
			v2 = (Comparable) Integer.valueOf(o2.hashCode());
		} else {
			v1 = (Comparable) o1;
			v2 = (Comparable) o2;
		}

		return v1.compareTo(v2);
	}

	/**
	 * Generates a hash code for a top-level object based on related values (i.e.
	 * field, bean property values, etc).
	 * 
	 * @param oa related values
	 * @return hash code
	 */
	public static int hashCode(Object... oa) {
		return hashCodeSuper(1, oa);
	}

	/**
	 * Generate a hash code for a subclass object based on its parent class' hash
	 * code and related values.
	 * 
	 * @param superHashCode parent class hash code
	 * @param oa            related values
	 * @return hash code
	 */
	public static int hashCodeSuper(int superHashCode, Object... oa) {
		final int prime = 31;
		int result = superHashCode;
		for (Object o : oa) {
			int hash;
			if (o == null)
				hash = 0;
			else if (o instanceof boolean[])
				hash = Arrays.hashCode((boolean[]) o);
			else if (o instanceof byte[])
				hash = Arrays.hashCode((byte[]) o);
			else if (o instanceof char[])
				hash = Arrays.hashCode((char[]) o);
			else if (o instanceof double[])
				hash = Arrays.hashCode((double[]) o);
			else if (o instanceof float[])
				hash = Arrays.hashCode((float[]) o);
			else if (o instanceof int[])
				hash = Arrays.hashCode((int[]) o);
			else if (o instanceof long[])
				hash = Arrays.hashCode((long[]) o);
			else if (o instanceof short[])
				hash = Arrays.hashCode((short[]) o);
			else if (o.getClass().isArray()) {
				int l = Array.getLength(o);
				int h = o.getClass().getComponentType().hashCode();
				for (int i = 0; i < l; i++)
					h = prime * h + hashCode(Array.get(o, i));
				hash = h;
			} else
				hash = o.hashCode();
			result = prime * result + hash;
		}
		return result;
	}

	/**
	 * Determine if two objects are both non-null instances of the same class. This
	 * method is useful as a null and type safety check when implementing equals. If
	 * this returns true, and the type of one of the objects is known, then it is
	 * safe to cast the other object to the same type.
	 * 
	 * @param <T> object type
	 * @param o1  any object
	 * @param o2  any object
	 * @return True if both objects are not null and instances of the same class.
	 */
	public static <T> boolean typeCheck(T o1, T o2) {
		return typeCheck(o1, o2, null);
	}

	/**
	 * Determine if two objects are both instances of a specific class, or
	 * subclasses of that class. This method is useful as a null and type safety
	 * check when implementing equals. If this returns true, then it is safe to cast
	 * the both objects to the type provided.
	 * 
	 * @param <T>  object type
	 * 
	 * @param o1   any object
	 * @param o2   any object
	 * @param type the type to check, may be null for the behavior outlined in
	 *             {@link #typeCheck(Object, Object)}.
	 * @return True if both objects are not null and instances of the given type, or
	 *         are the same class if type is null.
	 */
	public static <T> boolean typeCheck(T o1, T o2, Class<?> type) {
		if (type != null)
			return type.isInstance(o1) && type.isInstance(o2);
		if (o1 == o2)
			return true;
		if (o1 == null || o2 == null)
			return false;
		return o1.getClass() == o2.getClass();
	}

	/**
	 * Determine if two objects are equal, checking first for identity and null.
	 * 
	 * @param o1 any object
	 * @param o2 any object
	 * @return true if o1 and o2 refer to the same object, are both null, or if
	 *         o1.equals(o2) returns true. Otherwise, return false.
	 */
	public static boolean equals(Object o1, Object o2) {
		if (o1 == o2)
			return true;
		if (o1 == null || o2 == null)
			return false;

		if (o1.getClass() != o2.getClass())
			if (o1 instanceof Set && o2 instanceof Set && !(o1 instanceof SortedSet) && !(o2 instanceof SortedSet)) {
				Set<?> s1 = (Set<?>) o1;
				Set<?> s2 = (Set<?>) o2;
				if (s1.size() != s2.size())
					return false;
				return s1.containsAll(s2);
			} else if (o1 instanceof Iterable && o2 instanceof Iterable) {
				Iterator<?> i1 = ((Iterable<?>) o1).iterator();
				Iterator<?> i2 = ((Iterable<?>) o2).iterator();
				while (i1.hasNext()) {
					if (!i2.hasNext())
						return false;
					if (!equals(i1.next(), i2.next()))
						return false;
				}
				if (i2.hasNext())
					return false;
				return true;
			} else if ((o1 instanceof Map && o2 instanceof Map)) {
				Map<?, ?> m1 = (Map<?, ?>) o1;
				Map<?, ?> m2 = (Map<?, ?>) o2;
				if (!equals(m1.keySet(), m2.keySet()))
					return false;
				for (Object k : m1.keySet())
					if (!equals(m1.get(k), m2.get(k)))
						return false;
				return true;
			} else
				return false;

		if (o1 instanceof boolean[])
			return Arrays.equals((boolean[]) o1, (boolean[]) o2);
		if (o1 instanceof byte[])
			return Arrays.equals((byte[]) o1, (byte[]) o2);
		if (o1 instanceof char[])
			return Arrays.equals((char[]) o1, (char[]) o2);
		if (o1 instanceof double[])
			return Arrays.equals((double[]) o1, (double[]) o2);
		if (o1 instanceof float[])
			return Arrays.equals((float[]) o1, (float[]) o2);
		if (o1 instanceof int[])
			return Arrays.equals((int[]) o1, (int[]) o2);
		if (o1 instanceof long[])
			return Arrays.equals((long[]) o1, (long[]) o2);
		if (o1 instanceof short[])
			return Arrays.equals((short[]) o1, (short[]) o2);
		if (o1.getClass().isArray()) {
			int l1 = Array.getLength(o1);
			int l2 = Array.getLength(o2);
			if (l1 != l2)
				return false;
			for (int i = 0; i < l1; i++)
				if (!equals(Array.get(o1, i), Array.get(o2, i)))
					return false;
			return true;
		}

		return o1.equals(o2);
	}

	/**
	 * Waits until a condition is met or a timeout interval expires.
	 * 
	 * @param lock      object to synchronize on
	 * @param condition condition to wait for
	 * @param timeout   timeout interval
	 * 
	 * @throws InterruptedException if the current thread is interrupted while
	 *                              waiting for the condition to be met
	 * @throws TimeoutException     if the timeout interval expires before the
	 *                              condition is met
	 * 
	 */
	public static void waitFor(Object lock, BooleanSupplier condition, Duration timeout)
			throws InterruptedException, TimeoutException {
		waitFor(lock, condition, Instant.now().plus(timeout));
	}

	/**
	 * Waits until a condition is met or a timeout interval expires.
	 * 
	 * @param lock           object to synchronize on
	 * @param condition      condition to wait for
	 * @param timeout        timeout interval
	 * @param timeoutFactory creates a timeout exception to be thrown if the
	 *                       condition is not met before the expiration time
	 * 
	 * @throws InterruptedException if the current thread is interrupted while
	 *                              waiting for the condition to be met
	 * @throws TimeoutException     if the timeout interval expires before the
	 *                              condition is met
	 * 
	 */
	public static void waitFor(Object lock, BooleanSupplier condition, Duration timeout,
			Supplier<TimeoutException> timeoutFactory) throws InterruptedException, TimeoutException {
		waitFor(lock, condition, Instant.now().plus(timeout), timeoutFactory);
	}

	/**
	 * Waits until a condition is met or a timeout interval expires.
	 * 
	 * @param lock      object to synchronize on to receive status change
	 *                  notifications
	 * @param condition condition to wait for
	 * @param expires   timeout interval expiration time
	 * 
	 * @throws InterruptedException if the current thread is interrupted while
	 *                              waiting for the condition to be met
	 * @throws TimeoutException     if the timeout interval expires before the
	 *                              condition is met
	 */
	public static void waitFor(Object lock, BooleanSupplier condition, Instant expires)
			throws InterruptedException, TimeoutException {
		final var init = Instant.now();
		waitFor(lock, condition, expires, () -> {
			StringBuilder sb = new StringBuilder("Timed out in ");
			sb.append(Duration.between(init, expires));
			return new TimeoutException(sb.toString());
		});
	}

	/**
	 * Waits until a condition is met or a timeout interval expires.
	 * 
	 * @param lock           object to synchronize on to receive status change
	 *                       notifications
	 * @param condition      condition to wait for
	 * @param expires        timeout interval expiration time
	 * @param timeoutFactory creates a timeout exception to be thrown if the
	 *                       condition is not met before the expiration time
	 * 
	 * @throws InterruptedException if the current thread is interrupted while
	 *                              waiting for the condition to be met
	 * @throws TimeoutException     if the timeout interval expires before the
	 *                              condition is met
	 */
	public static void waitFor(Object lock, BooleanSupplier condition, Instant expires,
			Supplier<TimeoutException> timeoutFactory) throws InterruptedException, TimeoutException {
		synchronized (lock) {
			while (!condition.getAsBoolean()) {
				final var now = Instant.now();
				if (now.isBefore(expires)) {
					final var waitFor = Duration.between(now, expires);
					lock.wait(waitFor.toMillis(), waitFor.toNanosPart() % 1_000_000);
				} else
					throw timeoutFactory.get();
			}
		}
	}

	private IuObject() {
	};

}
