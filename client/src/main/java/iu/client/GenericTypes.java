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
package iu.client;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves and compares generic types.
 *
 * <p>
 * Type variables and wildcards are compared by the erasure of their bounds
 * rather than by their bounds' full generic types, so a recursive bound such as
 * {@code T extends Comparable<T>} can't loop. Owner types are not compared.
 * </p>
 */
public final class GenericTypes {

	private static final Map<Class<?>, Class<?>> BOXES = Map.of( //
			boolean.class, Boolean.class, //
			byte.class, Byte.class, //
			char.class, Character.class, //
			short.class, Short.class, //
			int.class, Integer.class, //
			long.class, Long.class, //
			float.class, Float.class, //
			double.class, Double.class, //
			void.class, Void.class);

	private GenericTypes() {
	}

	/**
	 * Gets the type arguments a type supplies to one of its generic supertypes.
	 *
	 * <p>
	 * Walks the generic superclasses and interfaces of {@code type}, binding each
	 * type variable to the argument supplied for it, so an argument passed
	 * through a sub-interface or a generic superclass resolves to the type it
	 * names. An argument no declaration binds, such as a type parameter of
	 * {@code type} itself when it's raw, is returned as the unbound
	 * {@link TypeVariable}.
	 * </p>
	 *
	 * @param type      class or parameterized type
	 * @param supertype generic class or interface {@code type} extends or
	 *                  implements
	 * @return one argument per type parameter of {@code supertype}; null if
	 *         {@code type} is not a subtype of {@code supertype}
	 */
	public static Type[] typeArguments(Type type, Class<?> supertype) {
		final var raw = JsonAdapters.erase(type);
		if (!supertype.isAssignableFrom(raw))
			return null;

		final Map<TypeVariable<?>, Type> bindings = new HashMap<>();
		bind(type, bindings);
		return find(raw, supertype, bindings);
	}

	/**
	 * Boxes a primitive type.
	 *
	 * @param type type
	 * @return wrapper class for a primitive; otherwise {@code type}
	 */
	public static Type box(Type type) {
		if (type instanceof Class && ((Class<?>) type).isPrimitive())
			return BOXES.get(type);
		else
			return type;
	}

	/**
	 * Determines if a value of one type can be used where another is declared.
	 *
	 * <p>
	 * Classes compare as {@link Class#isAssignableFrom(Class)} does. A
	 * parameterized {@code to} also requires the arguments {@code from} supplies
	 * to its raw type to match its own: an unbound type variable matches any
	 * argument within its bounds, a wildcard matches any argument within its
	 * bounds, and any other argument must be equal, since generic types are
	 * invariant. Arrays are covariant. A primitive {@code from} is boxed.
	 * </p>
	 *
	 * @param to   declared type
	 * @param from type of the value
	 * @return true if a value of type {@code from} is a {@code to}
	 */
	public static boolean isAssignable(Type to, Type from) {
		from = upperBound(box(from));
		if (to.equals(from))
			return true;

		if (to instanceof Class)
			return ((Class<?>) to).isAssignableFrom(JsonAdapters.erase(from));

		if (to instanceof ParameterizedType) {
			final var toArgs = ((ParameterizedType) to).getActualTypeArguments();
			final var fromArgs = typeArguments(from, JsonAdapters.erase(to));
			if (fromArgs == null)
				return false;
			for (var i = 0; i < toArgs.length; i++)
				if (!argumentMatches(toArgs[i], fromArgs[i]))
					return false;
			return true;
		}

		if (to instanceof GenericArrayType) {
			final var fromComponent = componentType(from);
			return fromComponent != null
					&& isAssignable(((GenericArrayType) to).getGenericComponentType(), fromComponent);
		}

		// a variable or wildcard accepts anything within its bounds
		return isWithinBounds(to, from);
	}

	private static boolean argumentMatches(Type pattern, Type argument) {
		if (pattern.equals(argument))
			return true;

		if (pattern instanceof TypeVariable)
			return isWithinBounds(pattern, argument);

		if (pattern instanceof WildcardType) {
			final var wildcard = (WildcardType) pattern;
			for (final var upper : wildcard.getUpperBounds())
				if (!isAssignable(upper, argument))
					return false;

			final var lowerBounds = wildcard.getLowerBounds();
			if (lowerBounds.length == 0)
				return true;

			// ? super L accepts a supertype of L, or ? super M where L extends M
			final Type[] argumentLower;
			if (argument instanceof WildcardType)
				argumentLower = ((WildcardType) argument).getLowerBounds();
			else if (argument instanceof TypeVariable)
				return false;
			else
				argumentLower = new Type[] { argument };
			if (argumentLower.length == 0)
				return false;
			for (final var lower : lowerBounds)
				if (!isAssignable(argumentLower[0], lower))
					return false;
			return true;
		}

		return false;
	}

	private static boolean isWithinBounds(Type variableOrWildcard, Type type) {
		final Type[] bounds;
		if (variableOrWildcard instanceof TypeVariable)
			bounds = ((TypeVariable<?>) variableOrWildcard).getBounds();
		else
			bounds = ((WildcardType) variableOrWildcard).getUpperBounds();

		final var erased = JsonAdapters.erase(upperBound(type));
		for (final var bound : bounds)
			if (!JsonAdapters.erase(bound).isAssignableFrom(erased))
				return false;
		return true;
	}

	private static Type upperBound(Type type) {
		while (true)
			if (type instanceof TypeVariable)
				type = ((TypeVariable<?>) type).getBounds()[0];
			else if (type instanceof WildcardType)
				type = ((WildcardType) type).getUpperBounds()[0];
			else
				return type;
	}

	private static Type componentType(Type type) {
		if (type instanceof GenericArrayType)
			return ((GenericArrayType) type).getGenericComponentType();
		else if (type instanceof Class)
			return ((Class<?>) type).getComponentType();
		else
			return null;
	}

	private static void bind(Type type, Map<TypeVariable<?>, Type> bindings) {
		if (!(type instanceof ParameterizedType))
			return;

		final var parameterized = (ParameterizedType) type;
		bind(parameterized.getOwnerType(), bindings);

		final var parameters = JsonAdapters.erase(parameterized).getTypeParameters();
		final var arguments = parameterized.getActualTypeArguments();
		for (var i = 0; i < parameters.length; i++)
			bindings.put(parameters[i], substitute(arguments[i], bindings));
	}

	/**
	 * Follows one path from a type up to a supertype, binding each type variable
	 * on the way.
	 *
	 * @param type      subtype of {@code supertype}
	 * @param supertype generic class or interface
	 * @param bindings  type variable bindings so far
	 * @return {@code supertype}'s type arguments
	 */
	private static Type[] find(Class<?> type, Class<?> supertype, Map<TypeVariable<?>, Type> bindings) {
		if (type == supertype)
			return Stream.of(supertype.getTypeParameters()) //
					.map(parameter -> bindings.getOrDefault(parameter, parameter)) //
					.toArray(Type[]::new);

		// type is a proper subtype, so one of its direct supertypes leads there
		final var genericSuperclass = type.getGenericSuperclass();
		final Type next;
		if (genericSuperclass != null && supertype.isAssignableFrom(JsonAdapters.erase(genericSuperclass)))
			next = genericSuperclass;
		else
			next = Stream.of(type.getGenericInterfaces()) //
					.filter(i -> supertype.isAssignableFrom(JsonAdapters.erase(i))) //
					.findFirst().get();

		bind(substitute(next, bindings), bindings);
		return find(JsonAdapters.erase(next), supertype, bindings);
	}

	private static Type substitute(Type type, Map<TypeVariable<?>, Type> bindings) {
		if (type instanceof TypeVariable)
			return bindings.getOrDefault(type, type);

		if (type instanceof ParameterizedType) {
			final var parameterized = (ParameterizedType) type;
			final var owner = parameterized.getOwnerType();
			final var substitutedOwner = owner == null ? null : substitute(owner, bindings);
			final var arguments = parameterized.getActualTypeArguments();
			final var substitutedArguments = substitute(arguments, bindings);
			if (substitutedOwner == owner && substitutedArguments == arguments)
				return type;
			else
				return new ParameterizedTypeImpl(parameterized.getRawType(), substitutedOwner, substitutedArguments);
		}

		if (type instanceof GenericArrayType) {
			final var component = ((GenericArrayType) type).getGenericComponentType();
			final var substitutedComponent = substitute(component, bindings);
			if (substitutedComponent == component)
				return type;
			else if (substitutedComponent instanceof Class)
				return Array.newInstance((Class<?>) substitutedComponent, 0).getClass();
			else
				return new GenericArrayTypeImpl(substitutedComponent);
		}

		if (type instanceof WildcardType) {
			final var wildcard = (WildcardType) type;
			final var upper = wildcard.getUpperBounds();
			final var lower = wildcard.getLowerBounds();
			final var substitutedUpper = substitute(upper, bindings);
			final var substitutedLower = substitute(lower, bindings);
			if (substitutedUpper == upper && substitutedLower == lower)
				return type;
			else
				return new WildcardTypeImpl(substitutedUpper, substitutedLower);
		}

		return type;
	}

	/**
	 * Substitutes each type in an array.
	 *
	 * @return {@code types} itself if nothing changed
	 */
	private static Type[] substitute(Type[] types, Map<TypeVariable<?>, Type> bindings) {
		Type[] substituted = null;
		for (var i = 0; i < types.length; i++) {
			final var type = substitute(types[i], bindings);
			if (type != types[i]) {
				if (substituted == null)
					substituted = types.clone();
				substituted[i] = type;
			}
		}
		return substituted == null ? types : substituted;
	}

	private static String typeNames(Type[] types) {
		return Stream.of(types).map(Type::getTypeName).collect(Collectors.joining(", "));
	}

	/**
	 * Equal to, and hashes as, the JDK's own implementation, so a resolved type
	 * finds a reflected one in a map and vice versa.
	 */
	private static final class ParameterizedTypeImpl implements ParameterizedType {
		private final Type rawType;
		private final Type ownerType;
		private final Type[] actualTypeArguments;

		private ParameterizedTypeImpl(Type rawType, Type ownerType, Type[] actualTypeArguments) {
			this.rawType = rawType;
			this.ownerType = ownerType;
			this.actualTypeArguments = actualTypeArguments;
		}

		@Override
		public Type[] getActualTypeArguments() {
			return actualTypeArguments.clone();
		}

		@Override
		public Type getRawType() {
			return rawType;
		}

		@Override
		public Type getOwnerType() {
			return ownerType;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof ParameterizedType))
				return false;
			final var that = (ParameterizedType) o;
			return Objects.equals(ownerType, that.getOwnerType()) //
					&& Objects.equals(rawType, that.getRawType()) //
					&& Arrays.equals(actualTypeArguments, that.getActualTypeArguments());
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(actualTypeArguments) ^ Objects.hashCode(ownerType) ^ Objects.hashCode(rawType);
		}

		/**
		 * Names the type as the JDK's own implementation does.
		 */
		@Override
		public String toString() {
			final var name = new StringBuilder();
			if (ownerType == null)
				name.append(rawType.getTypeName());
			else
				name.append(ownerType.getTypeName()).append('$').append(((Class<?>) rawType).getSimpleName());
			return name.append('<').append(typeNames(actualTypeArguments)).append('>').toString();
		}
	}

	/**
	 * Equal to, and hashes as, the JDK's own implementation.
	 */
	private static final class GenericArrayTypeImpl implements GenericArrayType {
		private final Type genericComponentType;

		private GenericArrayTypeImpl(Type genericComponentType) {
			this.genericComponentType = genericComponentType;
		}

		@Override
		public Type getGenericComponentType() {
			return genericComponentType;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof GenericArrayType
					&& Objects.equals(genericComponentType, ((GenericArrayType) o).getGenericComponentType());
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(genericComponentType);
		}

		@Override
		public String toString() {
			return genericComponentType.getTypeName() + "[]";
		}
	}

	/**
	 * Equal to, and hashes as, the JDK's own implementation.
	 */
	private static final class WildcardTypeImpl implements WildcardType {
		private final Type[] upperBounds;
		private final Type[] lowerBounds;

		private WildcardTypeImpl(Type[] upperBounds, Type[] lowerBounds) {
			this.upperBounds = upperBounds;
			this.lowerBounds = lowerBounds;
		}

		@Override
		public Type[] getUpperBounds() {
			return upperBounds.clone();
		}

		@Override
		public Type[] getLowerBounds() {
			return lowerBounds.clone();
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof WildcardType))
				return false;
			final var that = (WildcardType) o;
			return Arrays.equals(lowerBounds, that.getLowerBounds()) //
					&& Arrays.equals(upperBounds, that.getUpperBounds());
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(lowerBounds) ^ Arrays.hashCode(upperBounds);
		}

		@Override
		public String toString() {
			if (lowerBounds.length > 0)
				return "? super " + typeNames(lowerBounds);
			else if (upperBounds[0] == Object.class)
				return "?";
			else
				return "? extends " + typeNames(upperBounds);
		}
	}

}
