/*
 * Copyright © 2023 Indiana University
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

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

	private IuObject() {
	};

}
