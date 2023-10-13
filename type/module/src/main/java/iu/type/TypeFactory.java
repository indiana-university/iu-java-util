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
package iu.type;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import edu.iu.type.IuType;

/**
 * Provides fully formed instances of {@link TypeTemplate} to
 * {@link TypeSpi#resolveType(Type)}.
 */
final class TypeFactory {

	private static final Map<Class<?>, TypeTemplate<?>> RAW_TYPES = new WeakHashMap<>();
	private static final ThreadLocal<Map<Type, TypeTemplate<?>>> PENDING_GENERIC_TYPES = new ThreadLocal<>();

	/**
	 * Gets the type erasure for a generic type.
	 * 
	 * @param type raw or generic type
	 * @return type erasure
	 * @see IuType#erasedClass()
	 */
	static Class<?> getErasedClass(Type type) {
		if (type instanceof Class)
			return (Class<?>) type;

		record Node(Type type, int array) {
		}

		Deque<Node> todo = new ArrayDeque<>();
		var node = new Node(type, 0);
		todo.push(node);

		while (!todo.isEmpty()) {
			node = todo.pop();

			// JLS 21 4.6: The erasure of an array type T[] is |T|[].
			if (node.type instanceof GenericArrayType)
				todo.push(new Node(((GenericArrayType) node.type).getGenericComponentType(), node.array + 1));

			// JLS 21 4.6: The erasure of a parameterized type (§4.5) G<T1,...,Tn> is |G|.
			else if (node.type instanceof ParameterizedType)
				todo.push(new Node(((ParameterizedType) node.type).getRawType(), node.array));

			// JLS 21 4.6: The erasure of a type variable (§4.4) is the erasure of its
			// leftmost bound
			else if (node.type instanceof TypeVariable)
				/*
				 * This method implements the logic for a **reference** type variable. That is,
				 * The type variable at the end of a reference chain. For example, The type
				 * variable E in the parameterized type ArrayList<E> refers to the variable E in
				 * List<E>, which in turn refers to the variable T in Iterable<T>. If the
				 * introspected instance is an ArrayList, but the type of the element being
				 * introspected is Iterable<?> and the purpose of introspection is to determine
				 * the item type for elements in the iteration, this method is only responsible
				 * for resolving the type variable E on ArrayList<String> to the class String,
				 * not for derefencing the two type variable references it takes to reach that
				 * reference variable.
				 */
				todo.push(new Node(((TypeVariable<?>) node.type).getBounds()[0], node.array));

			/*
			 * During capture conversion, a wildcard type arguments translates to a type
			 * variable with bounds equivalent to the upper bound of upper bound of the
			 * wildcard. So, may be erased to its left-most upper bound for determining the
			 * equivalent raw type of a type argument.
			 */
			// JLS 21 5.1.10:
			// If Ti is a wildcard type argument of the form ? extends Bi, then Si is a
			// fresh type variable whose upper bound is glb(Bi, Ui[A1:=S1,...,An:=Sn]) and
			// whose lower bound is the null type.
			// glb(V1,...,Vm) is defined as V1 & ... & Vm
			else if (node.type instanceof WildcardType)
				todo.push(new Node(((WildcardType) node.type).getUpperBounds()[0], node.array));

			else if (!(node.type instanceof Class))
				// java.lang.reflect.Type should be considered effectively sealed to the only
				// the types handled above. Custom implementations are not covered by JLS and
				// cannot be generated by the compiler.
				throw new IllegalArgumentException(
						"Invalid generic type, must be ParameterizedType, GenericArrayType, TypeVariable, or WildcardType");
		}

		var erasure = (Class<?>) node.type;
		for (var i = 0; i < node.array; i++)
			erasure = Array.newInstance(erasure, 0).getClass();
		return erasure;
	}

	/**
	 * Gets a resolved type facade for a raw class.
	 * 
	 * @param <T>      raw type
	 * @param rawClass raw class
	 * @return {@link TypeFacade}
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static <T> TypeTemplate<T> resolveRawClass(Class<T> rawClass) {
		var resolvedType = RAW_TYPES.get(rawClass);

		if (resolvedType == null) {
			// Box pattern helps maintain hierarchy reference chain
			// by facilitating in-place swap for finer-grained references
			// as discovered in the type hierarchy
			class Box<U> {
				U value;

				Box(U value) {
					this.value = value;
				}
			}

			// LinkedHashMap maintains object-first order
			final Map<Class<?>, Box<IuType<? super T>>> hierarchyByErasure = new LinkedHashMap<>();

			// Create a top-down (Object-first) traversal queue for all classes
			// extended and interfaces implemented by the raw class
			Deque<Type> hierarchy = new ArrayDeque<>();
			{
				Deque<Type> todo = new ArrayDeque<>();
				todo.push(rawClass); // bottom-up traversal stack

				while (!todo.isEmpty()) {
					var type = todo.pop();
					if (type != rawClass) // don't include raw class in hierarchy
						hierarchy.push(type); // push in reverse of traversal order

					var erasedClass = getErasedClass(type);

					// traversal stack will be reversed, so push Object before interfaces
					// : last in stack traversal results in first queued
					var superType = erasedClass.getGenericSuperclass();
					if (superType != null)
						todo.push(superType);

					for (var implemented : erasedClass.getGenericInterfaces())
						todo.push(implemented);
				}
			}

			// This loop trusts getHierarchy() to return Object-first so super classes may
			// be resolved first, in place, while scanning deeper in the hierarchy
			for (var superType : hierarchy) {

				var erasedSuperClass = getErasedClass(superType);

				var rawSuperType = RAW_TYPES.get(erasedSuperClass);
				if (rawSuperType == null) {
					// Build and cache new template on the fly. This is possible since we
					// already have the interim type's full hierarchy available.

					final Deque<IuType<? super T>> superHierarchy = new ArrayDeque<>();
					for (var superTypeBox : hierarchyByErasure.values()) {
						var inheritedSuperType = superTypeBox.value;
						var erasedClass = inheritedSuperType.erasedClass();

						// filter super-type hierarchy to only include assignable elements
						// Note that interfaces do not declare Object as superclass
						// https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html
						if ((erasedClass != Object.class || !erasedSuperClass.isInterface()) //
								&& erasedClass.isAssignableFrom(erasedSuperClass))
							superHierarchy.push(inheritedSuperType);
					}

					rawSuperType = new TypeTemplate<>(erasedSuperClass, s -> {
						synchronized (RAW_TYPES) {
							RAW_TYPES.put(erasedSuperClass, (TypeTemplate<?>) s);
						}
					}, (Iterable) superHierarchy);
				}

				TypeTemplate<? super T> superTypeTemplate;
				if (superType == erasedSuperClass)
					superTypeTemplate = (TypeTemplate<? super T>) rawSuperType;
				else
					superTypeTemplate = (TypeTemplate<? super T>) resolveType(superType);

				// replace previously resolved super type references with references through the
				// declaring extended class or implemented interface
				for (var inheritedSuperType : superTypeTemplate.hierarchy()) {
					var inheritedErasedClass = inheritedSuperType.erasedClass();
					var inheritedBox = Objects.requireNonNull(hierarchyByErasure.get(inheritedErasedClass));
					inheritedBox.value = inheritedSuperType;
				}

				hierarchyByErasure.put(erasedSuperClass, new Box(superTypeTemplate));
			}

			Deque<IuType<? super T>> hierarchyTemplates = new ArrayDeque<>();
			for (Box<IuType<? super T>> hierarchyReference : hierarchyByErasure.values())
				hierarchyTemplates.push(hierarchyReference.value);

			resolvedType = new TypeTemplate<>(rawClass, s -> {
				synchronized (RAW_TYPES) {
					RAW_TYPES.put(rawClass, (TypeTemplate<?>) s);
				}
			}, hierarchyTemplates);
		}

		return (TypeTemplate<T>) resolvedType;

	}

	/**
	 * Resolves a facade for a generic type.
	 * 
	 * @param type generic type
	 * @return {@link TypeFacade}
	 */
	static TypeTemplate<?> resolveType(Type type) {
		Objects.requireNonNull(type, "type");
		if (type instanceof Class)
			return resolveRawClass((Class<?>) type);
		else {
			// Establish base case for self and loop references
			final var restorePendingGenericTypes = PENDING_GENERIC_TYPES.get();
			try {
				final Map<Type, TypeTemplate<?>> pendingGenericTypes;
				if (restorePendingGenericTypes == null) {
					pendingGenericTypes = new HashMap<>();
					PENDING_GENERIC_TYPES.set(pendingGenericTypes);
				} else
					pendingGenericTypes = restorePendingGenericTypes;

				var resolvedType = pendingGenericTypes.get(type);
				if (resolvedType != null)
					return resolvedType;

				return new TypeTemplate<>(s -> pendingGenericTypes.put(type, s), type,
						resolveRawClass(getErasedClass(type)));
			} finally {
				if (restorePendingGenericTypes == null)
					PENDING_GENERIC_TYPES.remove();
			}
		}
	}

	private TypeFactory() {
	}

}
