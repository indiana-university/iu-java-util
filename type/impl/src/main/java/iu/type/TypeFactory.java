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
import java.util.function.Consumer;

import edu.iu.type.IuType;
import edu.iu.type.IuTypeKey;

/**
 * Provides fully formed instances of {@link TypeTemplate} to
 * {@link TypeSpi#resolveType(Type)}.
 */
final class TypeFactory {

	private static final Map<Class<?>, TypeTemplate<?, ?>> RAW_TYPES = new WeakHashMap<>();
	private static final ThreadLocal<Map<IuTypeKey, TypeTemplate<?, ?>>> PENDING_GENERIC_TYPES = new ThreadLocal<>();
	private static final ThreadLocal<Map<Class<?>, TypeTemplate<?, ?>>> PENDING_RAW_TYPES = new ThreadLocal<>();

	/**
	 * Clears all indexed and cached data.
	 * 
	 * <p>
	 * <em>Should</em> be used:
	 * </p>
	 * 
	 * <ul>
	 * <li>Between internal unit tests to reset state.</li>
	 * <li>As an administrative function from a container management interface.</li>
	 * </ul>
	 */
	static void clear() {
		synchronized (RAW_TYPES) {
			RAW_TYPES.clear();
		}
	}

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
	 * Applies a front-loaded pre-initialization cache to the current thread to
	 * prevent head recursion while resolving type templates for the global raw type
	 * cache.
	 * 
	 * <p>
	 * This encapsulates both raw global cache and tail-recursive hierarchy
	 * resolution with global cache resolution.
	 * </p>
	 * 
	 * @param <T>      raw type
	 * @param rawClass raw class
	 * @param register pre-initialization hook consumer. <em>Must</em> implement all
	 *                 cache-miss resolution logic; will be passed a value to supply
	 *                 to {@link TypeTemplate#TypeTemplate(Class, Consumer)} for
	 *                 registering the new instance on the current thread before
	 *                 applying internal constructor logic. At least one
	 *                 {@link TypeTemplate} instance <em>must</em> be created, the
	 *                 last instance created will be applied to the global cache and
	 *                 returned. Exceptions thrown from
	 *                 {@link Consumer#accept(Object)} will pending
	 *                 {@link TypeTemplate}s cached on the current thread to be
	 *                 discarded.
	 * @return cached or resolved {@link TypeTemplate} instance
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	static <T> TypeTemplate<?, T> applyRawTypeCache(Class<T> rawClass,
			Consumer<Consumer<TypeTemplate<?, ?>>> register) {
		TypeTemplate resolvedType = RAW_TYPES.get(rawClass);
		if (resolvedType != null)
			return resolvedType;

		final var threadRawTypes = PENDING_RAW_TYPES.get();
		final Map<Class<?>, TypeTemplate<?, ?>> pendingRawTypes;
		if (threadRawTypes == null)
			PENDING_RAW_TYPES.set(pendingRawTypes = new HashMap<>());
		else
			pendingRawTypes = threadRawTypes;

		resolvedType = pendingRawTypes.get(rawClass);
		if (resolvedType != null)
			return resolvedType;

		try {
			register.accept(s -> pendingRawTypes.put(rawClass, s));
			resolvedType = Objects.requireNonNull(pendingRawTypes.get(rawClass));

			synchronized (RAW_TYPES) {
				RAW_TYPES.put(rawClass, resolvedType);
			}

			return resolvedType;
		} finally {
			if (threadRawTypes == null)
				PENDING_RAW_TYPES.remove();
		}
	}

	/**
	 * Gets a resolved type facade for a raw class.
	 * 
	 * @param <T>      raw type
	 * @param rawClass raw class
	 * @return {@link TypeFacade}
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	static <T> TypeTemplate<?, T> resolveRawClass(Class<T> rawClass) {
		// Box pattern helps maintain hierarchy reference chain
		// by facilitating in-place swap for finer-grained references
		// as discovered in the type hierarchy
		class Box {
			IuType<?, ? super T> value;

			Box(IuType<?, ? super T> value) {
				this.value = value;
			}
		}

		return applyRawTypeCache(rawClass, preInitHook -> {
			final var resolvedType = new TypeTemplate<>(rawClass, preInitHook);

			// LinkedHashMap maintains object-first order
			final Map<Class<?>, Box> hierarchyByErasure = new LinkedHashMap<>();

			// Create a top-down (Object-first) traversal queue for all classes
			// extended and interfaces implemented by the raw class
			final Deque<Type> hierarchy = new ArrayDeque<>();
			{
				final Deque<Type> todo = new ArrayDeque<>();
				todo.push(rawClass); // bottom-up traversal stack

				while (!todo.isEmpty()) {
					final var type = todo.pop();
					if (type != rawClass) // don't include raw class in hierarchy
						hierarchy.push(type); // push in reverse of traversal order

					final var erasedClass = getErasedClass(type);

					// truncate hierarchy at Object for Java language-native types
					if (erasedClass == Object.class)
						continue;
					else if (erasedClass == Class.class)
						todo.push(Object.class);

					else {
						// traversal stack will be reversed, so push Object before interfaces
						// : last in stack traversal results in first queued
						final var superType = erasedClass.getGenericSuperclass();
						if (superType != null)
							todo.push(superType);

						for (final var implemented : erasedClass.getGenericInterfaces())
							todo.push(implemented);
					}
				}
			}

			// This loop trusts getHierarchy() to return Object-first so super classes may
			// be resolved first, in place, while scanning deeper in the hierarchy
			for (var superType : hierarchy) {
				final var erasedSuperClass = (Class<? super T>) getErasedClass(superType);
				final var rawSuperType = applyRawTypeCache(erasedSuperClass, superPreInitHook -> {
					var pendingRawSuperType = new TypeTemplate<>(erasedSuperClass, superPreInitHook);

					// Build and cache new template on the fly. This is possible since we
					// already have the interim type's full hierarchy available.
					final Deque<IuType<?, ? super T>> superHierarchy = new ArrayDeque<>();
					for (var superTypeBox : hierarchyByErasure.values()) {
						final var inheritedSuperType = superTypeBox.value;
						final var erasedClass = inheritedSuperType.erasedClass();

						// filter super-type hierarchy to only include assignable elements
						// Note that interfaces do not declare Object as superclass
						// https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html
						if ((erasedClass != Object.class || !erasedSuperClass.isInterface()) //
								&& erasedClass.isAssignableFrom(erasedSuperClass))
							superHierarchy.push(inheritedSuperType);
					}

					pendingRawSuperType.sealHierarchy((Iterable) superHierarchy);
				});

				TypeTemplate<?, ? super T> superTypeTemplate;
				if (superType == erasedSuperClass)
					superTypeTemplate = (TypeTemplate<?, ? super T>) rawSuperType;
				else
					superTypeTemplate = (TypeTemplate<?, ? super T>) resolveType(superType);

				// replace previously resolved super type references with references through the
				// declaring extended class or implemented interface
				for (var inheritedSuperType : superTypeTemplate.hierarchy()) {
					var inheritedErasedClass = inheritedSuperType.erasedClass();
					var inheritedBox = Objects.requireNonNull(hierarchyByErasure.get(inheritedErasedClass));
					inheritedBox.value = inheritedSuperType;
				}

				hierarchyByErasure.put(erasedSuperClass, new Box(superTypeTemplate));
			}

			final Deque<IuType<?, ? super T>> hierarchyTemplates = new ArrayDeque<>();
			for (final var hierarchyReference : hierarchyByErasure.values())
				hierarchyTemplates.push(hierarchyReference.value);

			resolvedType.sealHierarchy(hierarchyTemplates);
		});
	}

	/**
	 * Resolves a facade for a generic type.
	 * 
	 * @param type generic type
	 * @return {@link TypeFacade}
	 */
	static TypeTemplate<?, ?> resolveType(Type type) {
		Objects.requireNonNull(type, "type");
		if (type instanceof Class)
			return resolveRawClass((Class<?>) type);
		else {
			// Establish base case for self and loop references
			final var restorePendingGenericTypes = PENDING_GENERIC_TYPES.get();
			try {
				final Map<IuTypeKey, TypeTemplate<?, ?>> pendingGenericTypes;
				if (restorePendingGenericTypes == null) {
					pendingGenericTypes = new HashMap<>();
					PENDING_GENERIC_TYPES.set(pendingGenericTypes);
				} else
					pendingGenericTypes = restorePendingGenericTypes;

				final var key = IuTypeKey.of(type);
				final var resolvedType = pendingGenericTypes.get(key);
				if (resolvedType != null)
					return resolvedType;

				return new TypeTemplate<>(s -> pendingGenericTypes.put(key, s), type,
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
