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
package edu.iu.type;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import edu.iu.type.spi.TypeImplementation;

/**
 * Facade interface for a generic type.
 * 
 * @param <T> described generic type
 */
public interface IuType<T> extends IuNamedElement, IuParameterizedElement {

	/**
	 * Resolves a type introspection facade for a generic type.
	 * 
	 * @param type generic type
	 * @return type introspection facade
	 */
	static IuType<?> of(Type type) {
		return TypeImplementation.PROVIDER.resolveType(type);
	}

	/**
	 * Resolves a type introspection facade for a class.
	 * 
	 * <p>
	 * An introspection facade returned by this method <em>must</em>:
	 * </p>
	 * <ul>
	 * <li>Be {@link Modifier#FINAL final} and immutable.</li>
	 * <li>Have 1:1 parity with {@link Class} instances, such that
	 * {@code IuType.of(MyClass.class) == IuType.of(MyClass.class)} returns for all
	 * classes.</li>
	 * </ul>
	 * 
	 * <p>
	 * All use of this method and subsequent type introspection lookups
	 * <em>must</em> thread-safe.
	 * </p>
	 * 
	 * @param <T>  type
	 * @param type type
	 * @return type introspection facade
	 */
	@SuppressWarnings("unchecked")
	static <T> IuType<T> of(Class<?> type) {
		return (IuType<T>) of((Type) type);
	}

	/**
	 * Gets the reference used to obtain this type.
	 * 
	 * @return type reference
	 */
	IuTypeReference<T, ?> reference();

	/**
	 * Gets the generic type.
	 * 
	 * @return generic type
	 */
	Type deref();

	/**
	 * Gets the {@link IuReferenceKind#ERASURE erased} facade, which describing the
	 * {@link Class} representing the <a href=
	 * "https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.6">erasure</a>
	 * of the generic type.
	 * 
	 * <p>
	 * The {@link #deref()} of the erased facade <em>must</em> return a
	 * {@link Class}.
	 * </p>
	 * 
	 * @return erased type facade
	 * @see <a href=
	 *      "https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.6">JLS
	 *      21 Section 4.4: Type Erasure</a>
	 */
	IuType<T> erase();

	/**
	 * Iterates the type hierarchy, from most specific to least specific.
	 * 
	 * <ol>
	 * <li>All {@link Class#getGenericInterfaces()}</li>
	 * <li>{@link Class#getGenericSuperclass()}</li>
	 * <li>Iterate {@link IuType#hierarchy()} until {@link Object} is reached</li>
	 * </ol>
	 * 
	 * <p>
	 * This type described by this facade is not included. {@link Object} is always
	 * the last element.
	 * </p>
	 * 
	 * @return inherited and extended types
	 */
	Iterable<? extends IuType<? super T>> hierarchy();

	/**
	 * Refers to a type in the the described type's hierarchy.
	 * 
	 * <p>
	 * When the referent type declares type parameters, the resolved generic types
	 * associated with those parameters are described by the returned facade.
	 * </p>
	 * 
	 * @param referentType type to refer to
	 * @return referent facade
	 */
	IuType<? super T> referTo(Type referentType);

	/**
	 * Gets all types enclosed by this type.
	 * 
	 * @return enclosed types
	 */
	Iterable<? extends IuType<?>> enclosedTypes();

	/**
	 * Gets all constructors defined by this type.
	 * 
	 * @return constructors
	 */
	Iterable<? extends IuConstructor<T>> constructors();

	/**
	 * Gets a constructor defined by this type.
	 * 
	 * @param parameterTypes parameter types
	 * @return constructor
	 */
	IuConstructor<T> constructor(Type... parameterTypes);

	/**
	 * Gets a constructor declared by this type.
	 * 
	 * @param parameterTypes parameter types
	 * @return constructor
	 */
	IuConstructor<T> constructor(IuType<?>... parameterTypes);

	/**
	 * Gets all fields defined by this type.
	 * 
	 * @return fields
	 */
	Iterable<? extends IuField<?>> fields();

	/**
	 * Gets a field declared by this type.
	 * 
	 * @param name field name
	 * @return field
	 */
	IuField<?> field(String name);

	/**
	 * Gets all properties defined by this type.
	 * 
	 * @return properties by name
	 */
	Iterable<? extends IuProperty<?>> properties();

	/**
	 * Gets a property declared by this type.
	 * 
	 * @param name property name
	 * @return property
	 */
	IuProperty<?> property(String name);

	/**
	 * Gets all methods defined by this type.
	 * 
	 * @return methods
	 */
	Iterable<? extends IuMethod<?>> methods();

	/**
	 * Gets a method defined by this type.
	 * 
	 * @param name           method name
	 * @param parameterTypes parameter types
	 * @return method
	 */
	IuMethod<?> method(String name, Type... parameterTypes);

	/**
	 * Gets a method declared by this type.
	 * 
	 * @param name           method name
	 * @param parameterTypes parameter types
	 * @return method
	 */
	IuMethod<?> method(String name, IuType<?>... parameterTypes);

	/**
	 * Get the type erasure class.
	 * 
	 * <p>
	 * Shorthand for {@link #erase()}.{@link #deref()}
	 * </p>
	 * 
	 * @return type erasure class
	 * @see #erase()
	 * @see <a href=
	 *      "https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.6">JLS
	 *      21 Section 4.4: Type Erasure</a>
	 */
	@SuppressWarnings("unchecked")
	default Class<T> erasedClass() {
		return (Class<T>) erase().deref();
	}

	/**
	 * Gets a type-enforced facade for a specific sub-type of the described type.
	 * 
	 * @param subclass subclass of the described type
	 * @param <S>      sub-type
	 * @return this
	 * @throws ClassCastException If the type does not erase to a subclass
	 */
	@SuppressWarnings("unchecked")
	default <S> IuType<? extends S> sub(Class<S> subclass) throws ClassCastException {
		erasedClass().asSubclass(subclass);
		return (IuType<? extends S>) this;
	}

	/**
	 * Returns the <a href=
	 * "https://docs.oracle.com/javase/tutorial/java/data/autoboxing.html">autobox</a>
	 * equivalent
	 * 
	 * @return the object version related to a primitive type, or the class passed
	 *         in as-is if not primitive
	 */
	@SuppressWarnings("unchecked")
	default Class<T> autoboxClass() {
		var potentiallyPrimitive = erasedClass();
		if (Boolean.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Boolean.class;
		else if (Character.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Character.class;
		else if (Byte.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Byte.class;
		else if (Short.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Short.class;
		else if (Integer.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Integer.class;
		else if (Long.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Long.class;
		else if (Float.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Float.class;
		else if (Double.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Double.class;
		else if (Void.TYPE.equals(potentiallyPrimitive))
			return (Class<T>) Void.class;
		else
			return potentiallyPrimitive;
	}

	/**
	 * Returns the default value for an object or primitive type.
	 * 
	 * @return The default value that would be assigned to a field of described
	 *         primitive type if declared without an initializer; null if the
	 *         described time is not primitive.
	 */
	@SuppressWarnings("unchecked")
	default T autoboxDefault() {
		var potentiallyPrimitive = erasedClass();
		if (Boolean.TYPE.equals(potentiallyPrimitive))
			return (T) Boolean.FALSE;
		else if (Character.TYPE.equals(potentiallyPrimitive))
			return (T) Character.valueOf('\0');
		else if (Byte.TYPE.equals(potentiallyPrimitive))
			return (T) Byte.valueOf((byte) 0);
		else if (Short.TYPE.equals(potentiallyPrimitive))
			return (T) Short.valueOf((short) 0);
		else if (Integer.TYPE.equals(potentiallyPrimitive))
			return (T) Integer.valueOf(0);
		else if (Long.TYPE.equals(potentiallyPrimitive))
			return (T) Long.valueOf(0L);
		else if (Float.TYPE.equals(potentiallyPrimitive))
			return (T) Float.valueOf(0.0f);
		else if (Double.TYPE.equals(potentiallyPrimitive))
			return (T) Double.valueOf(0.0);
		else
			return null;
	}

}
