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

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuConstructor;
import edu.iu.type.IuField;
import edu.iu.type.IuMethod;
import edu.iu.type.IuProperty;
import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;
import edu.iu.type.IuTypeReference;

/**
 * Represents the internal structure of a {@link TypeFacade}.
 * 
 * <p>
 * Each template is a standalone representation of a single generic type,
 * potentially paired with a raw template.
 * </p>
 * 
 * <p>
 * Note that {@link TypeTemplate} is not sterotyped as a hash key, but
 * {@link IuType} is. This hash key behavior comes from same-instance identity
 * (default) {@link #hashCode()} and {@link #equals(Object)} implementations by
 * instances managed by {@link TypeFactory}. Since ClassLoading data is loaded
 * exactly once and remains static once loaded, type introspection instances
 * should match that load-once static behavior. It is expected that each raw
 * type (primitive, class, interface, enum, record, etc) has exactly one
 * {@link TypeTemplate} instance, and a {@link TypeFacade} instance for each raw
 * type. Internal checks for equality (and inequality) may use == (and !=).
 * </p>
 * 
 * <p>
 * Note also that Java does not constrain the number of {@link Type} instances,
 * and considers those instances other than {@link Class} to be disposable.
 * {@link TypeFactory} does not manage {@link TypeTemplate} instances for
 * generic type markers once returned to the application.
 * </p>
 * 
 * @param <T> raw or generic type
 */
final class TypeTemplate<T> extends ParameterizedElementBase<Class<T>> implements IuType<T> {

	private final Type type;
	private final IuType<T> erasedType;
	private final Iterable<TypeFacade<? super T>> hierarchy;
	private final Iterable<ConstructorFacade<T>> constructors;
	private final Iterable<FieldFacade<? super T, ?>> fields;
	private final Iterable<MethodFacade<? super T, ?>> methods;

	private TypeTemplate(Class<T> annotatedElement, Consumer<TypeTemplate<T>> preInitHook, Type type,
			TypeTemplate<T> erasedType, Iterable<? extends IuType<? super T>> hierarchy) {
		super(annotatedElement, preInitHook == null ? null : s -> preInitHook.accept((TypeTemplate<T>) s));

		this.type = type;
		this.erasedType = initializeErasedType(erasedType);
		this.hierarchy = initializeHierarchy(hierarchy);
		this.constructors = initializeConstructors();
		this.fields = initializeFields();
		this.methods = initializeMethods();

		finishPostInit();

		if (type instanceof ParameterizedType) {
			final var parameterizedType = (ParameterizedType) type;
			final var actualTypeArguments = parameterizedType.getActualTypeArguments();
			final var typeParameters = annotatedElement.getTypeParameters();
			final var length = typeParameters.length;
			for (var i = 0; i < length; i++) {
				var typeParam = typeParameters[i].getName();
				var typeArgument = TypeFactory.resolveType(actualTypeArguments[i]);
				this.typeParameters.put(typeParam,
						new TypeFacade<>(typeArgument, this, IuReferenceKind.TYPE_PARAM, typeParam));
			}
		}

		typeParameters = Collections.unmodifiableMap(typeParameters);

		if (this.erasedType instanceof TypeFacade)
			((TypeFacade<T>) this.erasedType).sealTypeParameters(typeParameters);

		for (var superType : this.hierarchy)
			superType.sealTypeParameters(typeParameters);
	}

	/**
	 * Raw class constructor intended for use only by {@link TypeFactory}.
	 * 
	 * @param rawClass    raw class
	 * @param preInitHook receives a handle to {@code this} after binding the
	 *                    annotated element but before initializing and members
	 * @param hierarchy   pre-calculated type hierarchy; a {@link TypeTemplate}
	 *                    cannot be created without fully formed instances of all
	 *                    extended classes and implemented interfaces provided as an
	 *                    argument to this parameter
	 */
	TypeTemplate(Class<T> rawClass, Consumer<TypeTemplate<T>> preInitHook,
			Iterable<? extends IuType<? super T>> hierarchy) {
		this(rawClass, preInitHook, rawClass, null, hierarchy);
	}

	/**
	 * Generic type constructor intended for use only by {@link TypeFactory}.
	 * 
	 * @param preInitHook receives a handle to {@code this} after binding the
	 *                    annotated element but before initializing and members
	 * @param type        generic type; <em>must not</em> be a class
	 * @param erasedType  pre-calculated raw type template; a {@link TypeTemplate}
	 *                    cannot be created for a generic type without a
	 *                    fully-formed instance of its type erasure, provided as an
	 *                    argument to this parameter
	 */
	TypeTemplate(Consumer<TypeTemplate<T>> preInitHook, Type type, TypeTemplate<T> erasedType) {
		this(erasedType.erasedClass(), preInitHook, type, erasedType, erasedType.hierarchy);
		assert !(type instanceof Class) : type;
		assert erasedType.erasedClass() == TypeFactory.getErasedClass(type)
				: erasedType + " " + TypeUtils.printType(type);
	}

	// Builder constructor helpers
	private IuType<T> initializeErasedType(TypeTemplate<T> erasedType) {
		if (erasedType == null)
			return this;
		else
			return new TypeFacade<T>(erasedType, this, IuReferenceKind.ERASURE);
	}

	private Iterable<TypeFacade<? super T>> initializeHierarchy(Iterable<? extends IuType<? super T>> hierarchy) {
		if (hierarchy == null)
			if (annotatedElement != Object.class && annotatedElement != Enum.class && !annotatedElement.isInterface())
				throw new IllegalStateException("Missing hierarchy for " + annotatedElement);
			else
				hierarchy = Collections.emptySet();

		TypeFacade<? super T> last = null;
		Map<Class<?>, TypeFacade<? super T>> hierarchyByErasure = new LinkedHashMap<>();
		for (var superType : hierarchy) {
			var templateReference = superType.reference();

			IuAnnotatedElement referrer;
			TypeTemplate<? super T> superTypeTemplate;
			if (templateReference == null) {
				referrer = this;
				superTypeTemplate = (TypeTemplate<? super T>) superType;
			} else {
				superTypeTemplate = ((TypeFacade<? super T>) superType).template;

				var erasedReferrerClass = ((IuType<?>) templateReference.referrer()).erasedClass();
				if (erasedReferrerClass == annotatedElement)
					referrer = this;
				else {
					referrer = Objects.requireNonNull(hierarchyByErasure.get(erasedReferrerClass),
							() -> hierarchyByErasure + "; " + erasedReferrerClass);
				}
			}

			last = new TypeFacade<>(superTypeTemplate, referrer, IuReferenceKind.SUPER);
			var replaced = hierarchyByErasure.put(last.erasedClass(), last);
			assert replaced == null : replaced;
		}

		if (hierarchyByErasure.isEmpty()) {
			if (annotatedElement != Object.class && annotatedElement != Enum.class && !annotatedElement.isInterface()
					&& !annotatedElement.isPrimitive())
				throw new IllegalArgumentException("Missing hierarchy for " + annotatedElement);
		} else
			assert last.erasedClass() == Object.class || last.erasedClass().isInterface() : hierarchyByErasure;

		return hierarchyByErasure.values();
	}

	private boolean isNative() {
		return TypeUtils.isPlatformType(name()) //
				|| ("iu.type".equals(annotatedElement.getPackageName()) //
						&& annotatedElement.getEnclosingClass() == null //
						&& annotatedElement.getEnclosingMethod() == null //
						&& annotatedElement.getEnclosingConstructor() == null);
	}

	@SuppressWarnings("unchecked")
	private Iterable<ConstructorFacade<T>> initializeConstructors() {
		Queue<ConstructorFacade<T>> rv = new ArrayDeque<>();

		if (!isNative() //
				&& !annotatedElement.isInterface() //
				&& !annotatedElement.isEnum() //
				&& !annotatedElement.isPrimitive())
			for (var constructor : annotatedElement.getDeclaredConstructors())
				// _unchecked warning_: see source for #getDeclaredConstructors()
				// => This cast is safe as of Java 17
				rv.offer(new ConstructorFacade<>((Constructor<T>) constructor, this));

		return rv;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Iterable<FieldFacade<? super T, ?>> initializeFields() {
		Queue<FieldFacade<? super T, ?>> rv = new ArrayDeque<>();

		if (!isNative()) //
			for (var field : annotatedElement.getDeclaredFields()) {
				TypeTemplate<?> fieldType;
				if (field.getType() == annotatedElement)
					fieldType = this;
				else
					fieldType = TypeFactory.resolveType(field.getGenericType());

				rv.offer(new FieldFacade(field, fieldType, this));
			}

		for (var superType : hierarchy)
			superType.template.postInit(() -> {
				for (var inheritedField : superType.template.fields)
					rv.offer(inheritedField);
			});

		return rv;
	}

	private Iterable<MethodFacade<? super T, ?>> initializeMethods() {
		Queue<MethodFacade<? super T, ?>> rv = new ArrayDeque<>();

		if (!isNative()) //
			for (var method : annotatedElement.getDeclaredMethods())
				rv.offer(new MethodFacade<>(method, this));

		for (var superType : hierarchy)
			superType.template.postInit(() -> {
				for (var inheritedMethod : superType.template.methods)
					rv.offer(inheritedMethod);
			});

		return rv;
	}

	@Override
	public String name() {
		return annotatedElement.getName();
	}

	@Override
	public IuType<?> declaringType() {
		return this;
	}

	@Override
	public IuTypeReference<T, ?> reference() {
		return null;
	}

	@Override
	public Type deref() {
		// initialization race condition required by toString()
		return type == null ? annotatedElement : type;
	}

	@Override
	public IuType<T> erase() {
		return erasedType;
	}

	@Override
	public Class<T> erasedClass() {
		return annotatedElement;
	}

	@Override
	public Iterable<TypeFacade<? super T>> hierarchy() {
		return hierarchy;
	}

	@Override
	public TypeFacade<? super T> referTo(Type referentType) {
		return TypeUtils.referTo(this, hierarchy, referentType);
	}

	@Override
	public Iterable<? extends IuType<?>> enclosedTypes() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<? extends IuConstructor<T>> constructors() {
		return constructors;
	}

	@Override
	public Iterable<? extends IuField<?>> fields() {
		return fields;
	}

	@Override
	public Iterable<? extends IuProperty<?>> properties() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<? extends IuMethod<?>> methods() {
		return methods;
	}

	@Override
	public String toString() {
		return "IuType[" + TypeUtils.printType(deref()) + ']';
	}

}
