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
package edu.iu.type;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import edu.iu.IuIterable;
import edu.iu.IuObject;

/**
 * Hash key for use with generic types.
 * 
 * <p>
 * Java controls instances of {@link Class}, but instance control for other
 * implementations of the {@link Type} interface is not clearly defined. The JDK
 * typically, but is not required to, produces a new instance each time a
 * generic type is referred to. This class inspects the generic type to
 * determine strict equality as:
 * </p>
 * 
 * <ul>
 * <li>Same instances are equal. Recursive base case: all leaf nodes are
 * {@link Class}; and {@link Class} is strictly a leaf node.</li>
 * <li>Both {@code instanceof} matching exactly one of:
 * <ul>
 * <li>{@link ParameterizedType}
 * <ul>
 * <li>Recursively checks raw type and all type arguments</li>
 * </ul>
 * </li>
 * <li>{@link TypeVariable}
 * <ul>
 * <li>Checks variable name</li>
 * <li>Recursively checks bounds</li>
 * </ul>
 * </li>
 * <li>{@link WildcardType}
 * <ul>
 * <li>Recursively check upper and lower bounds</li>
 * </ul>
 * </li>
 * <li>{@link GenericArrayType}
 * <ul>
 * <li>Recursively check component type</li>
 * </ul>
 * </li>
 * </ul>
 * </li>
 * </ul>
 */
public class IuTypeKey {

	private static final Map<Type, IuTypeKey> KEY_CACHE = new WeakHashMap<>();
	private static final ThreadLocal<IdentityHashMap<IuTypeKey, IuTypeKey>> LOOP = new ThreadLocal<>();

	/**
	 * Distinguishes between kinds of generic types.
	 * 
	 * <p>
	 * {@link Kind} loosely dictates internal structure for {@link IuTypeKey}.
	 * </p>
	 */
	public static enum Kind {
		/**
		 * {@link Class}
		 */
		CLASS,

		/**
		 * {@link ParameterizedType}
		 */
		PARAMETERIZED,

		/**
		 * {@link TypeVariable}
		 */
		VARIABLE,

		/**
		 * {@link WildcardType}
		 */
		WILDCARD,

		/**
		 * {@link GenericArrayType}
		 */
		ARRAY
	}

	/**
	 * Refers to a type in the generic hierarchy of a <strong>reference
	 * class</strong> by {@link IuType#erase() erasure}.
	 * 
	 * @param referenceClass       <strong>reference class</strong>
	 * @param inheritedTypeErasure {@link IuType#erase() erasure}
	 * @return Generic type extended or implemented by the <strong>reference
	 *         class</strong> with {@link IuType#erase() erasure}
	 *         {@code == inheritedTypeErasure}
	 */
	public static Type referTo(final Class<?> referenceClass, final Class<?> inheritedTypeErasure) {
		if (referenceClass == inheritedTypeErasure)
			return referenceClass;

		var classToCheck = referenceClass;
		while (classToCheck != null) {
			final var rawInterfaces = classToCheck.getInterfaces();
			final var length = rawInterfaces.length;
			if (length > 0)
				for (var i = 0; i < length; i++)
					if (rawInterfaces[i] == inheritedTypeErasure)
						return classToCheck.getGenericInterfaces()[i];

			final var rawSuperclass = classToCheck.getSuperclass();
			if (inheritedTypeErasure == rawSuperclass)
				return classToCheck.getGenericSuperclass();
			else
				classToCheck = rawSuperclass;
		}

		throw new IllegalArgumentException(
				inheritedTypeErasure.getSimpleName() + " is not assignable from " + referenceClass.getSimpleName());
	}

	/**
	 * Gets a hash key for a generic type.
	 * 
	 * @param type generic type
	 * @return unique hash key
	 */
	public static IuTypeKey of(final Type type) {
		IuTypeKey key = KEY_CACHE.get(type);
		if (key != null)
			return key;

		if (type instanceof Class rawClass)
			return new IuTypeKey(Kind.CLASS, type, rawClass, null, null);

		if (type instanceof ParameterizedType parameterizedType)
			return new IuTypeKey(Kind.PARAMETERIZED, type, null, null, () -> {
				final var actualTypeArguments = parameterizedType.getActualTypeArguments();
				final var length = actualTypeArguments.length;
				final var children = new IuTypeKey[length + 1];
				children[0] = of(parameterizedType.getRawType());
				for (var i = 0; i < length; i++)
					children[i + 1] = of(actualTypeArguments[i]);
				return children;
			});

		if (type instanceof WildcardType wildcardType)
			return new IuTypeKey(Kind.WILDCARD, type, null, null, () -> {
				final var upperBounds = wildcardType.getUpperBounds();
				final var upperBoundLimit = upperBounds.length;
				final var lowerBounds = wildcardType.getLowerBounds();
				final var lowerBoundLimit = lowerBounds.length;
				final var length = upperBoundLimit + lowerBoundLimit;
				final var children = new IuTypeKey[length];
				for (var i = 0; i < upperBoundLimit; i++)
					children[i] = of(upperBounds[i]);
				for (var i = 0; i < lowerBoundLimit; i++)
					children[upperBoundLimit + i] = of(lowerBounds[i]);
				return children;
			});

		if (type instanceof TypeVariable<?> typeVariable)
			return new IuTypeKey(Kind.VARIABLE, type, null, typeVariable.getName(), () -> {
				final var bounds = typeVariable.getBounds();
				final var length = bounds.length;
				final var children = new IuTypeKey[length];
				for (var i = 0; i < length; i++)
					children[i] = of(bounds[i]);
				return children;
			});

		if (type instanceof GenericArrayType genericArrayType)
			return new IuTypeKey(Kind.ARRAY, type, null, null,
					() -> new IuTypeKey[] { of(genericArrayType.getGenericComponentType()) });

		throw new UnsupportedOperationException("Not supported in this version: " + type.getClass().getSimpleName());
	}

	private final Kind kind;
	private final Class<?> raw;
	private final String name;
	private final IuTypeKey[] children;

	private IuTypeKey(Kind kind, Type type, Class<?> raw, String name, Supplier<IuTypeKey[]> children) {
		synchronized (KEY_CACHE) {
			KEY_CACHE.put(type, this);
		}

		this.kind = kind;
		this.raw = raw;
		this.name = name;

		if (children == null)
			this.children = null;
		else
			this.children = children.get();
	}

	private <T> T preventLoop(Supplier<T> supplier, T baseCase) {
		final var loop = LOOP.get();
		try {
			if (loop == null)
				LOOP.set(new IdentityHashMap<>(Map.of(this, this)));
			else if (loop.containsKey(this))
				return baseCase;
			else
				loop.put(this, this);

			return supplier.get();

		} finally {
			if (loop == null)
				LOOP.remove();
			else
				loop.remove(this);
		}
	}

	@Override
	public int hashCode() {
		return preventLoop(() -> IuObject.hashCode(kind, name, raw, children), 0);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;

		IuTypeKey other = (IuTypeKey) obj;
		return kind == other.kind //
				&& raw == other.raw //
				&& IuObject.equals(name, other.name) //
				&& IuObject.equals(children, other.children);
	}

	@Override
	public String toString() {
		if (kind == Kind.CLASS)
			return "CLASS " + raw.getSimpleName();

		final var sb = new StringBuilder(kind.name());
		if (name != null)
			sb.append(' ').append(name);
		
		final var childrenToString = preventLoop(() -> IuIterable.print(IuIterable.iter(children)), "");
		if (!childrenToString.isEmpty())
			sb.append(' ').append(childrenToString);
		
		return sb.toString();
	}

}
