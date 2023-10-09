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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.WeakHashMap;

import edu.iu.type.IuType;

/**
 * Provides fully formed instances of {@link TypeTemplate} to
 * {@link TypeSpi#resolveType(Type)}.
 */
final class TypeFactory {

	private static final TypeTemplate<Object> OBJECT = TypeTemplate.builder(Object.class).build();
	private static final Map<Class<?>, TypeTemplate<?>> RAW_TYPES = new WeakHashMap<>();

	private static class ClassMetadata {
		/**
		 * Object-first.
		 */
		private final Iterable<Type> hierarchy;
		private final Map<ExecutableKey, Constructor<?>> constructors;
		private final Map<String, Field> fields;
		private final Map<ExecutableKey, Method> methods;

		private ClassMetadata(Class<?> rawClass) {
			{
				Deque<Type> hierarchy = new ArrayDeque<>();
				Deque<Type> todo = new ArrayDeque<>();
				todo.push(rawClass);

				while (!todo.isEmpty()) {
					var type = todo.pop();
					if (type != rawClass) // Object-first reversal of todo
						hierarchy.push(type);

					var erasedClass = getErasedClass(type);

					// todo is Object-last so push superType first
					var superType = erasedClass.getGenericSuperclass();
					if (superType != null)
						todo.push(superType);

					for (var implemented : erasedClass.getGenericInterfaces())
						todo.push(implemented);
				}

				this.hierarchy = hierarchy;
			}

			constructors = new LinkedHashMap<>();
			for (var constructor : rawClass.getDeclaredConstructors())
				constructors.put(ExecutableKey.of(constructor), constructor);

			methods = new LinkedHashMap<>();
			fields = new LinkedHashMap<>();

			for (var type : hierarchy) {
				var erasedClass = getErasedClass(type);
				for (var field : erasedClass.getDeclaredFields()) {
					var fieldName = field.getName();
					if (!fields.containsKey(fieldName))
						fields.put(fieldName, field);
				}
			}

		}
	}

	private static final Map<Class<?>, ClassMetadata> CLASS_METADATA = new WeakHashMap<>();

	private static ClassMetadata getClassMetadata(Class<?> rawClass) {
		ClassMetadata classMetadata = CLASS_METADATA.get(rawClass);
		if (classMetadata == null) {
			classMetadata = new ClassMetadata(rawClass);
			synchronized (CLASS_METADATA) {
				CLASS_METADATA.put(rawClass, classMetadata);
			}
		}
		return classMetadata;
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
	 * Gets a resolved type facade for a raw class.
	 * 
	 * @param <T>      raw type
	 * @param rawClass raw class
	 * @return {@link TypeFacade}
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static <T> TypeTemplate<T> resolveRawClass(Class<T> rawClass) {
		if (rawClass == Object.class)
			return (TypeTemplate<T>) OBJECT;

		var resolvedType = RAW_TYPES.get(rawClass);

		if (resolvedType == null) {
			var metadata = getClassMetadata(rawClass);

			// Resulting queue is Object-last, modifications use push()
			class Box<U> {
				U value;

				Box(U value) {
					this.value = value;
				}
			} // box pattern maintains hierarchy reference chain
			final Map<Class<?>, Box<TypeTemplate<? super T>>> hierarchyByErasure = new LinkedHashMap<>();

			for (var type : metadata.hierarchy) {
				// Must be Object-first so super classes may be resolved first
				var erasedSuperClass = getErasedClass(type);
				if (erasedSuperClass == Object.class)
					hierarchyByErasure.put(Object.class, new Box(OBJECT));

				else {
					var superType = RAW_TYPES.get(erasedSuperClass);
					if (superType == null) {
						var superTypeBuilder = TypeTemplate.builder(erasedSuperClass);

						final Deque<TypeTemplate<? super T>> superHierarchy = new ArrayDeque<>();
						for (var h : hierarchyByErasure.values()) {
							var inheritedSuperType = h.value;
							var erasedClass = inheritedSuperType.erasedClass();
							if ((erasedClass != Object.class || !erasedSuperClass.isInterface()) //
									&& erasedClass.isAssignableFrom(erasedSuperClass))
								superHierarchy.offer(inheritedSuperType);
						}
						superTypeBuilder.hierarchy((Iterable) superHierarchy);

						superType = superTypeBuilder.build();
						synchronized (RAW_TYPES) {
							RAW_TYPES.put(erasedSuperClass, superType);
						}
					}

					for (var inheritedSuperType : superType.hierarchy())
						Objects.requireNonNull(hierarchyByErasure.get(
								inheritedSuperType.erasedClass())).value = (TypeTemplate<? super T>) inheritedSuperType;

					hierarchyByErasure.put(erasedSuperClass, new Box(superType));
				}
			}

			var resolvedTypeBuilder = TypeTemplate.builder(rawClass);
			
			Queue<TypeTemplate<? super T>> hierarchy = new ArrayDeque<>();
			for (Box<TypeTemplate<? super T>> hierarchyReference : hierarchyByErasure.values())
				hierarchy.offer(hierarchyReference.value);
			resolvedTypeBuilder.hierarchy(hierarchy);
			
			resolvedType = resolvedTypeBuilder.build();

			synchronized (RAW_TYPES) {
				RAW_TYPES.put(rawClass, resolvedType);
			}
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
			var typeErasure = resolveRawClass(getErasedClass(type));
			var typeBuilder = TypeFacade.builder(type, typeErasure);
			return typeBuilder.build();
		}
	}

	private TypeFactory() {
	}

}
