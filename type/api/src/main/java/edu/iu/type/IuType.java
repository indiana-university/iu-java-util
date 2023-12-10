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

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.WeakHashMap;

import edu.iu.IuIterable;
import edu.iu.type.spi.TypeImplementation;

/**
 * Facade interface for a generic type.
 * 
 * <p>
 * {@link IuType} is sterotyped as a <strong>hash key</strong>, so may be used
 * as the key value with {@link WeakHashMap} to build type-level extensions from
 * specific generic type scenarios. For example IuType has a 1:1 relationship
 * with Class, but a separate 1:1 with a Classes referred to via specific
 * {@link TypeVariable}, {@link ParameterizedType}, {@link GenericArrayType}, or
 * {@link WildcardType}.
 * </p>
 * 
 * <p>
 * The <strong>hash key stereotype</strong> is implemented by {@link #of(Class)}
 * and {@link #of(Type)}, and is only implied by implementations provided by
 * those methods.
 * </p>
 *
 * <h2>Private Erasure</h2>
 * <p>
 * This interface exposes all declared field and method members from all types
 * in decorated type's hierarchy in additional to those declared directly on its
 * type erasure. That includes private members of superclasses. The purpose of
 * this utility is to find fields and bean properties on well-formed application
 * classes that may or may not be annotated as container-managed associations.
 * The container therefore uses {@code IuType} to access privately scoped
 * members and populate those associations.
 * </p>
 * <p>
 * It is the application developer's responsibility to ensure that private
 * members only shadow same-named private members in a super class when it is
 * intended for the subclass to use its member instead of any inherited
 * <strong>shadowed</strong> member of the same name. For example, when
 * using @AroundInvoke to define interceptor methods intended to be inherited
 * from a superclass, use a name reasonably unique to the declaring class. This
 * scenario mirrors method override behavior, but fields may be shadowed and
 * continue to exist as separate private fields with the same name but
 * potentially different type and value.
 * </p>
 * <p>
 * Private erasure is relevant for deserialization, remote service discovery,
 * and method invocation. Other scenarios, i.e., dependency injection,
 * <em>should</em> iterate all members unless a specific name is provided.
 * </p>
 * 
 * @param <D> declaring type, nullable
 * @param <T> described generic type
 */
public interface IuType<D, T> extends IuNamedElement<D>, IuParameterizedElement {

	/**
	 * Resolves a type introspection facade for a generic type.
	 * 
	 * @param type generic type
	 * @return type introspection facade
	 */
	static IuType<?, ?> of(Type type) {
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
	 * @param <T>      type
	 * @param rawClass type
	 * @return type introspection facade
	 */
	@SuppressWarnings("unchecked")
	static <T> IuType<?, T> of(Class<T> rawClass) {
		return (IuType<?, T>) of((Type) rawClass);
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
	IuType<D, T> erase();

	/**
	 * Gets a type-enforced facade for a specific sub-type of the described type.
	 * 
	 * @param subclass subclass of the described type
	 * @param <S>      sub-type
	 * @return this
	 * @throws ClassCastException If the type does not erase to a subclass
	 */
	@SuppressWarnings("unchecked")
	default <S> IuType<D, ? extends S> sub(Class<S> subclass) throws ClassCastException {
		erasedClass().asSubclass(subclass);
		return (IuType<D, ? extends S>) this;
	}

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
	Iterable<? extends IuType<?, ? super T>> hierarchy();

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
	IuType<?, ? super T> referTo(Type referentType);

	/**
	 * Gets enclosed types.
	 * 
	 * @return enclosed types
	 */
	Iterable<? extends IuType<?, ?>> enclosedTypes();

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
	default IuConstructor<T> constructor(Type... parameterTypes) {
		var hash = IuExecutableKey.hashCode(null, parameterTypes);
		for (var constructor : constructors()) {
			var constructorKey = constructor.getKey();
			if (hash == constructorKey.hashCode() && constructorKey.equals(null, parameterTypes))
				return constructor;
		}
		throw new IllegalArgumentException(this + " missing constructor " + IuExecutableKey.of(null, parameterTypes));
	}

	/**
	 * Gets a constructor declared by this type.
	 * 
	 * @param parameterTypes parameter types
	 * @return constructor
	 */
	default IuConstructor<T> constructor(Iterable<IuType<?, ?>> parameterTypes) {
		var hash = IuExecutableKey.hashCode(null, parameterTypes);
		for (var constructor : constructors()) {
			var constructorKey = constructor.getKey();
			if (hash == constructorKey.hashCode() && constructorKey.equals(null, parameterTypes))
				return constructor;
		}
		throw new IllegalArgumentException(this + " missing constructor " + IuExecutableKey.of(null, parameterTypes));
	}

	/**
	 * Scans constructors for those annotated with a specific annotation type.
	 * 
	 * @param annotationType annotation type to filter by
	 * @return {@link #constructors()}, filtered by annotation type
	 */
	default Iterable<? extends IuConstructor<T>> annotatedConstructors(Class<? extends Annotation> annotationType) {
		return IuIterable.filter(constructors(), c -> c.hasAnnotation(annotationType));
	}

	/**
	 * Gets all fields defined by this type, followed by all fields defined by all
	 * types in this type's hierarchy, in {@link #hierarchy()} order.
	 * 
	 * @return fields declared by this type and its hierarchy, in this followed by
	 *         {@link #hierarchy()} order
	 */
	Iterable<? extends IuField<? super T, ?>> fields();

	/**
	 * Gets a field declared by this type.
	 * 
	 * <p>
	 * When a private field has the same name as a different field declared by a
	 * super class, the "inherited" field is shadowed by this method. To retrieve
	 * all fields, including those shadowed by a superclass, use {@link #fields()}.
	 * </p>
	 * 
	 * @param <F>  field type
	 * @param name field name
	 * @return field
	 */
	@SuppressWarnings("unchecked")
	default <F> IuField<? super T, F> field(String name) {
		for (var field : fields())
			if (name.equals(field.name()))
				return (IuField<? super T, F>) field;
		throw new IllegalArgumentException(this + " missing field " + name);
	}

	/**
	 * Scans fields for those annotated with a specific annotation type.
	 * 
	 * @param annotationType annotation type to filter by
	 * @return {@link #fields()}, filtered by annotation type
	 */
	default Iterable<? extends IuField<? super T, ?>> annotatedFields(Class<? extends Annotation> annotationType) {
		return IuIterable.filter(fields(), f -> f.hasAnnotation(annotationType));
	}

	/**
	 * Gets all methods defined by this type.
	 * 
	 * <p>
	 * The result {@link Iterable iterates} all methods declared on all classes in
	 * the type erasure's hierarchy with private erasure for duplicately defined
	 * methods.
	 * </p>
	 * 
	 * @return methods
	 */
	Iterable<? extends IuMethod<? super T, ?>> methods();

	/**
	 * Gets a method defined by this type.
	 * 
	 * @param <R>            return type
	 * @param name           method name
	 * @param parameterTypes parameter types
	 * @return method
	 */
	@SuppressWarnings("unchecked")
	default <R> IuMethod<? super T, R> method(String name, Type... parameterTypes) {
		final var hash = IuExecutableKey.hashCode(name, parameterTypes);
		final var methods = methods();
		for (var method : methods) {
			var methodKey = method.getKey();
			if (hash == methodKey.hashCode() && methodKey.equals(name, parameterTypes))
				return (IuMethod<? super T, R>) method;
		}
		throw new IllegalArgumentException(
				this + " missing method " + IuExecutableKey.of(name, parameterTypes) + "; " + methods);
	}

	/**
	 * Gets a method declared by this type.
	 * 
	 * @param <R>            return type
	 * @param name           method name
	 * @param parameterTypes parameter types
	 * @return method
	 */
	@SuppressWarnings("unchecked")
	default <R> IuMethod<? super T, R> method(String name, Iterable<IuType<?, ?>> parameterTypes) {
		final var hash = IuExecutableKey.hashCode(name, parameterTypes);
		final var methods = methods();
		for (var method : methods) {
			var methodKey = method.getKey();
			if (hash == methodKey.hashCode() && methodKey.equals(name, parameterTypes))
				return (IuMethod<? super T, R>) method;
		}
		throw new IllegalArgumentException(
				this + " missing method " + IuExecutableKey.of(name, parameterTypes) + "; " + methods);
	}

	/**
	 * Scans methods for those annotated with a specific annotation type.
	 * 
	 * @param annotationType annotation type to filter by
	 * @return {@link #methods()}, filtered by annotation type
	 */
	default Iterable<? extends IuMethod<? super T, ?>> annotatedMethods(Class<? extends Annotation> annotationType) {
		return IuIterable.filter(methods(), f -> f.hasAnnotation(annotationType));
	}

	/**
	 * Gets all properties defined by this type, followed by all properties defined
	 * by all types in this type's hierarchy, in {@link #hierarchy()} order.
	 * 
	 * @return properties declared by this type and its hierarchy, in this followed
	 *         by {@link #hierarchy()} order
	 */
	Iterable<? extends IuProperty<? super T, ?>> properties();

	/**
	 * Gets a property declared by this type.
	 * 
	 * @param <P>  property type
	 * @param name property name
	 * @return property
	 */
	@SuppressWarnings("unchecked")
	default <P> IuProperty<? super T, P> property(String name) {
		for (var property : properties())
			if (name.equals(property.name()))
				return (IuProperty<? super T, P>) property;
		throw new IllegalArgumentException(this + " missing property " + name);
	}

	/**
	 * Scans properties for those annotated with a specific annotation type.
	 * 
	 * @param annotationType annotation type to filter by
	 * @return {@link #properties()}, filtered by annotation type
	 */
	default Iterable<? extends IuProperty<? super T, ?>> annotatedProperties(
			Class<? extends Annotation> annotationType) {
		return IuIterable.filter(properties(), f -> f.hasAnnotation(annotationType));
	}

	/**
	 * Observes a new instance.
	 * 
	 * <p>
	 * Observing an instance registers it with the implementation module as an
	 * available target for type introspection, for example, for resource binding.
	 * Implementors of {@link InstanceReference} may use
	 * {@link #subscribe(InstanceReference)} to be notified when new instances are
	 * observed.
	 * </p>
	 * 
	 * <p>
	 * This method is invoked internally for all instances created via
	 * {@link IuConstructor#exec(Object...)}, directly before return from that
	 * method, and <em>may</em> used to integrate with an external instance
	 * lifecycle management framework.
	 * </p>
	 * 
	 * <p>
	 * Observing an instance that is already observed has no effect, not does
	 * observing an instance of a type that has no subscribers.
	 * </p>
	 * 
	 * @param instance to observe
	 * @return thunk for removing the instance from all observation queues; may be
	 *         held to keep the instance active until torn down external, or
	 *         discarded to allow observation to end naturally when all other
	 *         references to the instance have been cleared
	 */
	Runnable observe(T instance);

	/**
	 * Subscribes a new instance reference.
	 * 
	 * @param instanceReference will accept all {@link #observe(Object) observed}
	 *                          instances until unsubscribed.
	 * @return thunk for unsubscribing the reference
	 */
	Runnable subscribe(InstanceReference<T> instanceReference);

}
