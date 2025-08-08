/*
 * Copyright © 2025 Indiana University
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

import java.beans.Introspector;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuVisitor;
import edu.iu.type.InstanceReference;
import edu.iu.type.IuConstructor;
import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;
import edu.iu.type.IuTypeReference;
import jakarta.annotation.PreDestroy;

/**
 * Represents the internal structure of a {@link TypeFacade}.
 * 
 * <p>
 * Each template is a standalone representation of a single generic type,
 * potentially paired with a raw template representing its erasure.
 * </p>
 * 
 * <p>
 * Note that {@link TypeTemplate} is not sterotyped as a hash key, but
 * {@link IuType} is. This hash key behavior comes from same-instance identity
 * default {@link #hashCode()} and {@link #equals(Object)} implementations of
 * instances managed by {@link TypeFactory}. Since ClassLoading data is loaded
 * exactly once and remains static once loaded, type introspection instances
 * match that load-once static behavior. It is expected that each raw type
 * (primitive, class, interface, enum, record, etc) has exactly one
 * {@link TypeTemplate} instance, and that each {@link TypeTemplate} instance
 * backed by a generic type contains a {@link TypeFacade} backed by a singleton
 * raw type instances representing its {@link #erase() type erasure}. This
 * behavior mirrors the hash key behavior of {@link Type}, so internal checks
 * for equality (and inequality) may use == (and !=).
 * </p>
 * 
 * <p>
 * Note also that Java does not constrain the number of {@link Type} instances,
 * and considers those instances other than {@link Class} to be disposable.
 * {@link TypeFactory} does not manage {@link TypeTemplate} instances for
 * generic type markers once returned to the application.
 * </p>
 * 
 * <h2>Initialization Order</h2>
 * <p>
 * Type templates are initialized in two phases, managed by {@link TypeFactory}.
 * </p>
 * <ol>
 * <li>Declared elements</li>
 * <li>Inherited elements</li>
 * </ol>
 * 
 * <h3>Declared Elements</h3>
 * <p>
 * Resolved in order upon instantiation:
 * </p>
 * <ol>
 * <li>{@link ElementBase#ElementBase(Consumer)} {@code preInitHook} binds raw
 * {@link Class} instances to {@link TypeFactory}{@code #RAW_TYPES}</li>
 * <li>{@link AnnotatedElementBase#AnnotatedElementBase(AnnotatedElement, Consumer)}
 * binds {@link #annotatedElement}</li>
 * <li>{@link DeclaredElementBase#DeclaredElementBase(AnnotatedElement, Consumer, Type, TypeTemplate)}
 * binds:
 * <ul>
 * <li>{@link #type}</li>
 * <li>{@link #declaringType()}, potentially null or unsealed</li>
 * </ul>
 * </li>
 * <li>Apply actual type arguments from {@link ParameterizedType}</li>
 * <li>{@link #erase()}</li>
 * <li>{@link #constructors()}</li>
 * </ol>
 * 
 * <h3>Inherited Elements</h3>
 * <p>
 * Resolved by {@link #sealHierarchy(Iterable)}, order incidental
 * </p>
 * <ul>
 * <li>{@link #fields()}</li>
 * <li>{@link #properties()}</li>
 * <li>{@link #methods()}</li>
 * <li>{@link #typeParameters()}</li>
 * </ul>
 * 
 * @param <D> declaring type
 * @param <T> raw or generic type
 */
final class TypeTemplate<D, T> extends DeclaredElementBase<D, Class<T>> implements IuType<D, T>, ParameterizedFacade {

	@SuppressWarnings("unchecked")
	private static <D> TypeTemplate<?, D> resolveDeclaringType(Class<?> enclosed) {
		final var declaringClass = enclosed.getDeclaringClass();
		if (declaringClass == null)
			return null;
		return (TypeTemplate<?, D>) TypeFactory.resolveRawClass(declaringClass);
	}

	// Declared
	private IuType<D, T> erasedType;
	private Iterable<TypeFacade<T, ?>> enclosedTypes;
	private Iterable<ConstructorFacade<T>> constructors;

	// Inherited
	private Iterable<TypeFacade<?, ? super T>> hierarchy;
	private Iterable<FieldFacade<? super T, ?>> fields;
	private Iterable<PropertyFacade<? super T, ?>> properties;
	private Iterable<MethodFacade<? super T, ?>> methods;

	// Parameterized
	private final ParameterizedElement parameterizedElement = new ParameterizedElement();

	// Instance management
	private final IuVisitor<InstanceReference<T>> instanceReferences = new IuVisitor<>();

	private TypeTemplate(Class<T> annotatedElement, Consumer<TypeTemplate<?, ?>> preInitHook, Type type,
			TypeTemplate<D, T> erasedType) {
		super(annotatedElement, preInitHook, type, resolveDeclaringType(annotatedElement));

		if (declaringType == null || isStatic())
			initializeDeclared(erasedType);
		else
			declaringType.template.postInit(() -> initializeDeclared(erasedType));
	}

	/**
	 * Raw class constructor intended for use only by {@link TypeFactory}.
	 * 
	 * @param rawClass    raw class
	 * @param preInitHook receives a handle to {@code this} after binding the
	 *                    annotated element but before initializing and members
	 */
	TypeTemplate(Class<T> rawClass, Consumer<TypeTemplate<?, ?>> preInitHook) {
		this(rawClass, preInitHook, rawClass, null);
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
	TypeTemplate(Consumer<TypeTemplate<?, ?>> preInitHook, Type type, TypeTemplate<D, T> erasedType) {
		this(erasedType.erasedClass(), preInitHook, type, erasedType);
		assert !(type instanceof Class) : type;
		assert erasedType.erasedClass() == TypeFactory.getErasedClass(type)
				: erasedType + " " + TypeUtils.printType(type);

		erasedType.postInit(() -> sealHierarchy(erasedType.hierarchy));
	}

	private boolean isNative() {
		final var packageName = annotatedElement.getPackageName();
		final var targetModule = annotatedElement.getModule();
		final var typeImplModule = getClass().getModule();
		return IuObject.isPlatformName(name()) //
				|| targetModule == typeImplModule //
				|| !targetModule.isOpen(packageName, typeImplModule);
	}

	private void initializeDeclared(TypeTemplate<D, T> erasedType) {
		if (declaringType != null && !isStatic())
			parameterizedElement.apply(declaringType.template.typeParameters());

		if (erasedType == null) {
			this.erasedType = this;
			initializeConstructors();
		} else {
			this.erasedType = new TypeFacade<D, T>(erasedType, this, IuReferenceKind.ERASURE);
			erasedType.postInit(() -> {
				initializeConstructors();
			});
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void initializeEnclosedTypes() {
		Queue<TypeFacade<T, ?>> enclosedTypes = new ArrayDeque<>();

		if (!isNative()) {
			Class<?>[] enclosedClasses = annotatedElement.getDeclaredClasses();
			postInit(() -> {
				for (var enclosedClass : enclosedClasses) {
					final var enclosedType = TypeFactory.resolveRawClass(enclosedClass);
					enclosedTypes.offer(new TypeFacade(enclosedType, this, IuReferenceKind.ENCLOSING_TYPE));
				}
			});
		}

		this.enclosedTypes = enclosedTypes;
	}

	@SuppressWarnings("unchecked")
	private void initializeConstructors() {
		Queue<ConstructorFacade<T>> constructors = new ArrayDeque<>();

		if (!isNative() //
				&& !annotatedElement.isInterface() //
				&& !annotatedElement.isEnum())
			for (var constructor : annotatedElement.getDeclaredConstructors())
				// _unchecked warning_: see source for #getDeclaredConstructors()
				// => This cast is safe as of Java 17
				constructors.offer(new ConstructorFacade<>((Constructor<T>) constructor, this));

		this.constructors = constructors;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Iterable<FieldFacade<? super T, ?>> initializeFields() {
		Queue<FieldFacade<? super T, ?>> rv = new ArrayDeque<>();

		if (!isNative()) //
			for (var field : annotatedElement.getDeclaredFields()) {
				TypeTemplate<?, ?> fieldType;
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

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Iterable<PropertyFacade<? super T, ?>> initializeProperties() {
		Queue<PropertyFacade<? super T, ?>> rv = new ArrayDeque<>();

		if (!isNative()) //
			for (var property : IuException.unchecked(() -> Introspector.getBeanInfo(annotatedElement))
					.getPropertyDescriptors()) {
				if (property.getName().equals("class"))
					continue;

				var readMethod = property.getReadMethod();
				var writeMethod = property.getWriteMethod();

				Type propertyType;
				if (readMethod != null)
					propertyType = readMethod.getGenericReturnType();
				else if (writeMethod != null)
					propertyType = writeMethod.getGenericParameterTypes()[0];
				else
					continue;

				TypeTemplate<?, T> propertyTypeTemplate;
				if (propertyType == annotatedElement)
					propertyTypeTemplate = this;
				else
					propertyTypeTemplate = (TypeTemplate<?, T>) TypeFactory.resolveType(propertyType);

				rv.offer(new PropertyFacade(property, propertyTypeTemplate, this));
			}

		for (var superType : hierarchy)
			superType.template.postInit(() -> {
				for (var inheritedProperty : superType.template.properties)
					rv.offer(inheritedProperty);
			});

		return rv;
	}

	private Iterable<MethodFacade<? super T, ?>> initializeMethods() {
		Queue<MethodFacade<? super T, ?>> rv = new ArrayDeque<>();

		if (!isNative()) //
			for (var method : annotatedElement.getDeclaredMethods()) {
				if (method.isSynthetic())
					continue; // skip lambdas

				TypeTemplate<?, ?> returnType;
				if (method.getReturnType() == annotatedElement)
					returnType = this;
				else
					returnType = TypeFactory.resolveType(method.getGenericReturnType());

				rv.offer(new MethodFacade<>(method, returnType, this));
			}

		for (var superType : hierarchy)
			superType.template.postInit(() -> {
				for (var inheritedMethod : superType.template.methods)
					rv.offer(inheritedMethod);
			});

		return rv;
	}

	private void doSealHierarchy(Iterable<? extends IuType<?, ? super T>> hierarchy) {
		Map<Class<?>, TypeFacade<?, ? super T>> hierarchyByErasure = new LinkedHashMap<>();
		for (var superType : hierarchy) {
			var templateReference = superType.reference();

			TypeTemplate<?, ? super T> superTypeTemplate;
			if (templateReference == null) {
				superTypeTemplate = (TypeTemplate<?, ? super T>) superType;

				// common case: direct generalization of raw class
				hierarchyByErasure.put(superType.erasedClass(),
						new TypeFacade<>(superTypeTemplate, this, IuReferenceKind.SUPER));
				continue;
			}

			else // exception case: inherited generalization ...
				superTypeTemplate = ((TypeFacade<?, ? super T>) superType).template;

			// ... by type erasure, same as direct
			var erasedReferrerClass = ((IuType<?, ?>) templateReference.referrer()).erasedClass();
			if (erasedReferrerClass == annotatedElement) {
				hierarchyByErasure.put(superType.erasedClass(),
						new TypeFacade<>(superTypeTemplate, this, IuReferenceKind.SUPER));
				continue;
			}

			// ... via superclass or interface, look up unsealed reference by erasure
			final var referrer = Objects.requireNonNull(hierarchyByErasure.get(erasedReferrerClass));
			final var superTypeFacade = new TypeFacade<>(superTypeTemplate, referrer, IuReferenceKind.SUPER);
			hierarchyByErasure.put(superTypeFacade.erasedClass(), superTypeFacade);
		}

		this.hierarchy = hierarchyByErasure.values();

		if (type instanceof ParameterizedType parameterizedType) {
			final var actualTypeArguments = parameterizedType.getActualTypeArguments();
			final var typeVariables = annotatedElement.getTypeParameters();
			final var length = actualTypeArguments.length;
			assert typeVariables.length == length; // enforced by javac
			for (var i = 0; i < length; i++)
				parameterizedElement.apply(this, typeVariables[i], actualTypeArguments[i]);
		}

		fields = initializeFields();
		properties = initializeProperties();
		methods = initializeMethods();

		parameterizedElement.seal(annotatedElement, this);
		super.seal();
	}

	/**
	 * Unsupported, use {@link #sealHierarchy(Iterable)} to provide hierarchy when
	 * sealing.
	 * 
	 * @throws UnsupportedOperationException when invoked
	 */
	@Override
	final void seal() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("use sealHierarchy() only with TypeTemplate");
	}

	private boolean isStatic() {
		final var erased = erasedClass();
		if (erased.isInterface() || erased.isRecord() || erased.isEnum())
			return true;

		final var mod = annotatedElement.getModifiers();
		return (mod | Modifier.STATIC) == mod;
	}

	/**
	 * Seals {@link #hierarchy()} and resolves <strong>inherited elements</strong>.
	 * 
	 * @param hierarchy Resolved type hierarchy
	 */
	void sealHierarchy(Iterable<? extends IuType<?, ? super T>> hierarchy) {
		if (declaringType == null || isStatic())
			doSealHierarchy(hierarchy);
		else
			declaringType.template.postInit(() -> doSealHierarchy(hierarchy));
	}

	@Override
	public Runnable subscribe(InstanceReference<T> instanceReference) {
		instanceReferences.accept(instanceReference);
		return () -> instanceReferences.clear(instanceReference);
	}

	@Override
	public void observe(T instance) {
		instanceReferences.visit(listener -> {
			if (listener != null)
				listener.accept(instance);
			return null;
		});
	}

	@Override
	public void destroy(T instance) {
		Throwable e = null;
		for (final var method : annotatedMethods(PreDestroy.class))
			e = IuException.suppress(e, () -> IuException.checkedInvocation(() -> {
				method.exec(instance);
				return null;
			}));

		e = IuException.suppress(e, () -> instanceReferences.visit(listener -> {
			if (listener != null)
				listener.clear(instance);
			return null;
		}));

		if (e != null)
			throw IuException.unchecked(e);
	}

	@Override
	public Map<String, TypeFacade<?, ?>> typeParameters() {
		checkSealed();
		return parameterizedElement.typeParameters();
	}

	@Override
	public String name() {
		return annotatedElement.getName();
	}

	@Override
	public IuTypeReference<T, ?> reference() {
		return null;
	}

	@Override
	public Type deref() {
		return type;
	}

	@Override
	public IuType<D, T> erase() {
		return erasedType;
	}

	@Override
	public Class<T> erasedClass() {
		return annotatedElement;
	}

	@Override
	public IuType<?, ? super T> referTo(Type referentType) {
		return TypeUtils.referTo(this, hierarchy(), referentType);
	}

	@Override
	public Iterable<TypeFacade<T, ?>> enclosedTypes() {
		if (enclosedTypes == null)
			initializeEnclosedTypes();
		return enclosedTypes;
	}

	@Override
	public Iterable<? extends IuConstructor<T>> constructors() {
		return constructors;
	}

	@Override
	public Iterable<TypeFacade<?, ? super T>> hierarchy() {
		if (hierarchy == null)
			throw new IllegalStateException("hierarchy not sealed");
		return hierarchy;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <F> FieldFacade<? super T, F> field(String name) {
		return (FieldFacade<? super T, F>) IuType.super.field(name);
	}

	@Override
	public Iterable<FieldFacade<? super T, ?>> fields() {
		if (fields == null)
			throw new IllegalStateException("fields not sealed");
		return fields;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <P> PropertyFacade<? super T, P> property(String name) {
		return (PropertyFacade<? super T, P>) IuType.super.property(name);
	}

	@Override
	public Iterable<PropertyFacade<? super T, ?>> properties() {
		if (properties == null)
			throw new IllegalStateException("properties not sealed");
		return properties;
	}

	@Override
	public Iterable<MethodFacade<? super T, ?>> methods() {
		if (methods == null)
			throw new IllegalStateException("methods not sealed");
		return methods;
	}

	@Override
	public String toString() {
		return "IuType[" + TypeUtils.printType(deref()) + ']';
	}

}
