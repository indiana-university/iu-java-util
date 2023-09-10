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

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import iu.type.BackwardsCompatibilityHelper;
import iu.type.ExecutableKey;
import iu.type.WrapInvocationHandler;

/**
 * Type introspection facade.
 * 
 * <p>
 * Provides uniform, optimized, access to Java type information. Instances of
 * this class are immutable and held in a weak cache keyed by generic type.
 * </p>
 * 
 * @param <T> introspection target type
 */
public class IuType<T> implements IuAnnotatedElement {

	private static final Logger LOG = Logger.getLogger(IuType.class.getName());

	private static final Map<Type, IuType<?>> BASIC = new WeakHashMap<>();
	private static final Map<Class<?>, Object> EMPTY_ARRAYS = new WeakHashMap<>();

	private static final Map<ClassLoader, ClassLoaderMetadata> CLASSLOADER_META_CACHE = new WeakHashMap<>();
	private static final String TARGET_RESOURCE = "META-INF/iu.properties";

	private static enum Breadcrumb {
		referTo, field, typeParameter, componentType, base;
	}

	private static class ChildBuilder<C> {
		private final Type type;
		private final Class<C> base;
		private final IuType<?> parent;
		private Breadcrumb breadcrumb;
		private Object breadcrumbOption;
		private Optional<TypeVariable<?>> variable;

		private ChildBuilder(Class<C> base, IuType<?> parent) {
			this.type = base;
			this.base = base;
			this.parent = parent;
		}

		@SuppressWarnings("unchecked")
		private ChildBuilder(Type type, IuType<?> parent) {
			this.type = type;
			this.base = type instanceof Class ? (Class<C>) type : null;
			this.parent = parent;
		}

		private ChildBuilder<C> variable(TypeVariable<?> variable) {
			this.variable = Optional.ofNullable(variable);
			return this;
		}

		private ChildBuilder<C> breadcrumb(Breadcrumb breadcrumb) {
			return breadcrumb(breadcrumb, null);
		}

		private ChildBuilder<C> breadcrumb(Breadcrumb breadcrumb, Object breadcrumbOption) {
			this.breadcrumb = breadcrumb;
			this.breadcrumbOption = breadcrumbOption;
			return this;
		}

		private IuType<C> build() {
			if (variable == null)
				variable = Optional.ofNullable(parent.variable);

			// common case: class w/o type parameters, return from BASIC
			if (variable.isEmpty() && base != null && base.getTypeParameters().length == 0)
				return resolve(base);

			return new IuType<>(type, variable.orElse(null), breadcrumb, breadcrumbOption, parent);
		}
	}

	private static class ClassLoaderMetadata {
		private final Map<Class<?>, Set<IuType<?>>> annotatedClassesByAnnotationType;
		private final Map<Class<?>, Set<IuField<?>>> annotatedFieldsByAnnotationType;
		private final Map<Class<?>, Set<IuMethod<?>>> annotatedMethodsByAnnotationType;
		private final Collection<URL> discoveredResources;

		private ClassLoaderMetadata(ClassLoader loader) {
			Map<Class<?>, Set<IuType<?>>> annotationMap = new LinkedHashMap<>();
			Map<Class<?>, Set<IuField<?>>> annotatedFieldMap = new LinkedHashMap<>();
			Map<Class<?>, Set<IuMethod<?>>> annotatedMethodMap = new LinkedHashMap<>();
			Queue<URL> discoveredResourceList = new ArrayDeque<URL>();

			Consumer<String> checkClass = className -> {
				if ("module-info".equals(className) || "package-info".equals(className) || className.indexOf('$') != -1)
					return; // skip enclosed classes

				try {
					var c = loader.loadClass(className);
					var resolved = resolve(c);
					var annotations = c.getDeclaredAnnotations();
					if (annotations != null)
						for (var annotation : annotations) {
							var annotationType = annotation.annotationType();
							var annotatedClasses = annotationMap.get(annotationType);
							if (annotatedClasses == null)
								annotationMap.put(annotationType, annotatedClasses = new LinkedHashSet<>());
							annotatedClasses.add(resolved);
						}

					var fields = resolved.fields();
					for (var field : fields.values())
						for (var annotationType : field.annotations().keySet()) {
							var annotatedFields = annotatedFieldMap.get(annotationType);
							if (annotatedFields == null)
								annotatedFieldMap.put(annotationType, annotatedFields = new LinkedHashSet<>());
							annotatedFields.add(field);
						}

					var methods = resolved.methods();
					if (methods != null)
						for (var method : methods)
							for (var annotationType : method.annotations().keySet()) {
								var annotatedMethods = annotatedMethodMap.get(annotationType);
								if (annotatedMethods == null)
									annotatedMethodMap.put(annotationType, annotatedMethods = new LinkedHashSet<>());
								annotatedMethods.add(method);
							}

				} catch (ClassNotFoundException | NoClassDefFoundError e) {
					LOG.log(Level.FINEST, e, () -> "invalid class definition");
					assert false : e;
				}
			};

			try {
				Enumeration<URL> resources = loader.getResources(TARGET_RESOURCE);
				while (resources.hasMoreElements()) {
					URL resource = resources.nextElement();
					String uri = resource.toURI().toString();
					if (uri.startsWith("jar:file:"))
						scanJar(new URI(uri.substring(4, uri.indexOf('!', 4))).toURL(), checkClass,
								discoveredResourceList);
					else
						continue;
				}
			} catch (IOException | URISyntaxException e) {
				throw new IllegalStateException(e);
			}
			for (var e : annotationMap.entrySet())
				e.setValue(Collections.unmodifiableSet(e.getValue()));
			this.annotatedClassesByAnnotationType = Collections.unmodifiableMap(annotationMap);
			for (var e : annotatedFieldMap.entrySet())
				e.setValue(Collections.unmodifiableSet(e.getValue()));
			this.annotatedFieldsByAnnotationType = Collections.unmodifiableMap(annotatedFieldMap);
			for (var e : annotatedMethodMap.entrySet())
				e.setValue(Collections.unmodifiableSet(e.getValue()));
			this.annotatedMethodsByAnnotationType = Collections.unmodifiableMap(annotatedMethodMap);
			discoveredResources = Collections.unmodifiableCollection(discoveredResourceList);
		}

		private void scanJar(URL url, Consumer<String> classConsumer, Queue<URL> discoveredResourceList)
				throws IOException, URISyntaxException {
			try (JarInputStream jar = new JarInputStream(url.openStream())) {
				JarEntry jarEntry = jar.getNextJarEntry();
				while (jarEntry != null) {
					String entryName = jarEntry.getName();
					discoveredResourceList
							.add(new URI("jar:" + url + (entryName.charAt(0) == '/' ? "!" : "!/") + entryName).toURL());

					if (entryName != null && entryName.endsWith(".class")) {
						int i0 = entryName.charAt(0) == '/' ? 1 : 0;
						classConsumer.accept(entryName.substring(i0, entryName.length() - 6).replace('/', '.'));
					}
					jarEntry = jar.getNextJarEntry();
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> IuType<T> findBase(IuType<T> initial) {

		// tracks component resolution status for generic array types
		class LookupMarker {
			final IuType<?> resolved;
			final boolean array;

			LookupMarker(IuType<?> type, boolean array) {
				this.resolved = type;
				this.array = array;
			}
		}

		// tracks tail-recursive base type lookup state
		class LookupState {
			IuType<?> base = null;
			Deque<LookupMarker> pendingMarkers = new ArrayDeque<>();
			LookupMarker currentMarker;
			Consumer<IuType<?>> baseContinuation;

			LookupState() {
			}

			LookupState(Consumer<IuType<?>> baseContinuation) {
				assert this.baseContinuation == null : baseContinuation + " " + this.baseContinuation;
				this.baseContinuation = baseContinuation;
			}

			void push(IuType<?> type) {
				push(type, currentMarker == null ? false : currentMarker.array);
			}

			void push(IuType<?> type, boolean array) {
				pendingMarkers.push(new LookupMarker(type, array));
			}

			LookupMarker pop() {
				return currentMarker = pendingMarkers.pop();
			}

			// handle resolved base type when exhausted
			// SIDE EFFECT: push marker on prior state
			boolean isEmpty() {
				if (pendingMarkers.isEmpty() && baseContinuation != null) {
					baseContinuation.accept(base);
					baseContinuation = null;
				}
				return pendingMarkers.isEmpty();
			}

			IuType<?> getBase() {
				return base;
			}

			void setBase(IuType<?> base) {
				if (this.base == null)
					this.base = base;

				else if (!((Class<?>) base.type).isAssignableFrom((Class<?>) this.base.type))
					throw new IllegalArgumentException("Conflict between " + this.base.type + " and " + base.type
							+ " in bounds; " + currentMarker);
			}
		}

		var initialState = new LookupState();
		initialState.push(initial);
		Deque<LookupState> lookupStack = new ArrayDeque<>();
		lookupStack.push(initialState);

		stack: while (!lookupStack.isEmpty()) {
			var state = lookupStack.pop();

			while (!state.isEmpty()) { // <- else push marker
				// on next state down in the stack when empty

				var currentMarker = state.pop();
				var current = currentMarker.resolved;
				var type = current.type;

				if (type instanceof WildcardType) {
					var wildcardType = (WildcardType) type;
					for (var bound : wildcardType.getUpperBounds())
						state.push(current.child(bound).breadcrumb(Breadcrumb.base).build());

				} else if (type instanceof TypeVariable) {
					// can't call IuType#getBounds() since it calls getBaseClass
					// and can lead to dangerous head recursion
					var typeVariable = (TypeVariable<?>) type;
					var name = typeVariable.getName();

					IuType<?> maybeParameterized = current.parent;
					while (maybeParameterized != null) {
						if (maybeParameterized.type instanceof ParameterizedType) {
							var parameterized = maybeParameterized;
							var parameterizedType = (ParameterizedType) maybeParameterized.type;

							// push tail-recursive lookup state to resolve raw parameter type
							var rawTypeState = new LookupState(base -> {
								var rawType = base.baseClass();

								if (rawType.equals(typeVariable.getGenericDeclaration())) {
									var typeParameters = rawType.getTypeParameters();
									for (int i = 0; i < typeParameters.length; i++) {
										var typeParameter = typeParameters[i];
										if (name.equals(typeParameter.getName())) {
											state.push(current.child(parameterizedType.getActualTypeArguments()[i])
													.breadcrumb(Breadcrumb.base).variable(typeParameter).build());
											return;
										}
									}
								}

								if (parameterized.parent != null)
									state.push(parameterized.parent.child(typeVariable).breadcrumb(Breadcrumb.base)
											.build());
							});
							rawTypeState.push(parameterized.child(parameterizedType.getRawType())
									.breadcrumb(Breadcrumb.base).build());
							lookupStack.push(state);
							lookupStack.push(rawTypeState);
							continue stack;
						}
						maybeParameterized = maybeParameterized.parent;
					}

					for (var bound : typeVariable.getBounds())
						state.push(current.child(bound).breadcrumb(Breadcrumb.base).build());

				} else if (type instanceof GenericArrayType) {
					var genericArray = (GenericArrayType) type;
					state.push(
							current.child(genericArray.getGenericComponentType()).breadcrumb(Breadcrumb.base).build(),
							true);

				} else if (type instanceof ParameterizedType) {
					var parameterizedType = (ParameterizedType) type;
					state.push(current.child(parameterizedType.getRawType()).breadcrumb(Breadcrumb.base).build());

				} else if (type instanceof Class) {
					if (currentMarker.array)
						state.setBase(
								current.child(current.emptyArray().getClass()).breadcrumb(Breadcrumb.base).build());
					else
						state.setBase(current);

					if (current.variable != null)
						for (var boundType : current.variable.getBounds())
							state.push(current.child(boundType).breadcrumb(Breadcrumb.base).variable(null).build(),
									currentMarker.array);

				} else if (type != null)
					throw new IllegalStateException("Unexpected generic type " + type.getClass() + " " + type);
			}
		}

		var base = initialState.getBase();
		assert base != null;
		return (IuType<T>) base;
	}

	private static IuType<?> findComponentType(IuType<?> initial) {
		Deque<IuType<?>> q = new ArrayDeque<>();
		q.push(initial);
		while (!q.isEmpty()) {
			var current = q.pop();
			var type = current.type;

			if ((type instanceof WildcardType) || (type instanceof TypeVariable)) {
				for (var a : current.getBounds())
					q.push(a);
			} else if (type instanceof GenericArrayType) {
				var genericArray = (GenericArrayType) type;
				return current.child(genericArray.getGenericComponentType()).breadcrumb(Breadcrumb.componentType)
						.build();
			} else if (type instanceof ParameterizedType) {
				var parameterizedType = (ParameterizedType) type;
				q.push(current.child(parameterizedType.getRawType()).breadcrumb(Breadcrumb.componentType).build());
			} else if (type instanceof Class) {
				var c = (Class<?>) type;
				if (c.isArray())
					return current.child(c.getComponentType()).breadcrumb(Breadcrumb.componentType).build();
			} else if (type != null)
				throw new IllegalStateException("Unexpected generic type " + type.getClass() + " " + type);
		}
		throw new IllegalArgumentException("not an array type " + initial);
	}

	private static final ClassLoaderMetadata getClassLoaderMetadata(ClassLoader loader) {
		if (loader == null)
			loader = ClassLoader.getSystemClassLoader();
		ClassLoaderMetadata metadata = CLASSLOADER_META_CACHE.get(loader);
		if (metadata == null) {
			metadata = new ClassLoaderMetadata(loader);
			synchronized (CLASSLOADER_META_CACHE) {
				CLASSLOADER_META_CACHE.put(loader, metadata);
			}
		}
		return metadata;
	}

	/**
	 * General purpose helper method for implementing
	 * {@link InvocationHandler#invoke(Object, Method, Object[])} implementing the
	 * default semantics outlined for {@link Object#hashCode()},
	 * {@link Object#equals(Object)}, and {@link Object#toString()}.
	 * 
	 * @param proxy  proxy instance
	 * @param method invoked method
	 * @param args   arguments invoked
	 * @return default values allowing a proxy to intercept the basic object methods
	 *         outlined above without needing to delegate the call to a provided.
	 * @throws Throwable if invocation fails for any reason.
	 */
	public static Object handleObjectMethods(Object proxy, Method method, Object[] args) throws Throwable {
		switch (method.getName()) {
		case "hashCode":
			if (args == null)
				return System.identityHashCode(proxy);
			break;

		case "equals":
			if (args != null && args.length == 1)
				return proxy == args[0];
			break;

		case "toString":
			if (args == null) {
				Class<?> proxyClass = proxy.getClass();
				Class<?>[] interfaces = proxyClass.getInterfaces();
				if (interfaces.length == 0)
					return proxyClass.getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
				else
					return interfaces[0].getName() + '@' + Integer.toHexString(System.identityHashCode(proxy));
			}
			break;
		}

		return null;
	}

	/**
	 * Resolves a generic type.
	 * 
	 * @param type generic type
	 * @return resolved compile-time metadata for the type
	 */
	public static IuType<?> resolve(Type type) {
		IuType<?> basic = BASIC.get(type);
		if (basic != null)
			return basic;

		basic = new IuType<>(type);
		synchronized (BASIC) {
			BASIC.put(type, basic);
		}
		return basic;
	}

	/**
	 * Resolves a class.
	 * 
	 * @param type class
	 * @param <T>  class type
	 * @return resolved compile-time metadata for the class
	 */
	@SuppressWarnings("unchecked")
	public static <T> IuType<T> resolve(Class<T> type) {
		return (IuType<T>) resolve((Type) type);
	}

	/**
	 * Gets all resources URLs discovered via a given class loader on classpath
	 * entries that also expose the target resource {@code META-INF/iu.properties}.
	 * 
	 * @param loader class loader
	 * @return discovered resource URLs
	 */
	public static Collection<URL> discoverResources(ClassLoader loader) {
		return getClassLoaderMetadata(loader).discoveredResources;
	}

	/**
	 * Gets all classes discovered via a class loader present on classpath entries
	 * that expose the target resource {@code META-INF/iu.properties} that are
	 * marked with a specific annotation.
	 * 
	 * @param loader         class loader
	 * @param annotationType annotation type
	 * @return annotated classes
	 */
	public static Set<IuType<?>> resolveAnnotatedTypes(ClassLoader loader, Class<?> annotationType) {
		var classes = getClassLoaderMetadata(loader).annotatedClassesByAnnotationType.get(annotationType);
		return classes == null ? Collections.emptySet() : classes;
	}

	/**
	 * Gets all fields discovered via a class loader present on classpath entries
	 * that expose the target resource {@code META-INF/iu.properties} that are
	 * marked with a specific annotation.
	 * 
	 * @param loader         class loader
	 * @param annotationType annotation type
	 * @return annotated fields
	 */
	public static Set<IuField<?>> resolveAnnotatedFields(ClassLoader loader, Class<?> annotationType) {
		var fields = getClassLoaderMetadata(loader).annotatedFieldsByAnnotationType.get(annotationType);
		return fields == null ? Collections.emptySet() : fields;
	}

	/**
	 * Gets all methods discovered via a class loader present on classpath entries
	 * that expose the target resource {@code META-INF/iu.properties} that are
	 * marked with a specific annotation.
	 * 
	 * @param loader         class loader
	 * @param annotationType annotation type
	 * @return annotated methods
	 */
	public static Set<IuMethod<?>> resolveAnnotatedMethods(ClassLoader loader, Class<?> annotationType) {
		var methods = getClassLoaderMetadata(loader).annotatedMethodsByAnnotationType.get(annotationType);
		return methods == null ? Collections.emptySet() : methods;
	}

	private class PropertyMetadata {
		private final Map<String, IuProperty<?>> properties;
		private final Map<Class<? extends Annotation>, Map<String, IuProperty<?>>> annotatedProperties;

		private PropertyMetadata() {
			Map<String, IuProperty.Builder<?>> propertyBuilders = new TreeMap<>();

			var baseClass = (Class<?>) type;

			PropertyDescriptor[] propertyDescriptors;
			try {
				propertyDescriptors = Introspector.getBeanInfo(baseClass).getPropertyDescriptors();
			} catch (IntrospectionException e) {
				throw new IllegalStateException(e);
			}

			for (var propertyDescriptor : propertyDescriptors) {
				var propertyName = propertyDescriptor.getName();
				if (propertyName.equals("class"))
					continue;

				IuProperty.Builder<?> propertyBuilder = null;
				var writeMethod = propertyDescriptor.getWriteMethod();
				if (writeMethod != null) {
					propertyBuilder = new IuProperty.Builder<>(method(writeMethod).parameter(0).type(), propertyName);
					propertyBuilder.write(writeMethod);
				}

				var readMethod = propertyDescriptor.getReadMethod();
				if (readMethod != null) {
					if (propertyBuilder == null)
						propertyBuilder = new IuProperty.Builder<>(method(readMethod).type(), propertyName);
					propertyBuilder.read(readMethod);
				}

				if (propertyBuilder == null)
					continue;

				propertyBuilders.put(propertyName, propertyBuilder);
			}

			Map<String, IuProperty<?>> properties = new LinkedHashMap<>();
			Map<Class<? extends Annotation>, Map<String, IuProperty<?>>> annotatedProperties = new LinkedHashMap<>();
			for (var propertyBuilder : propertyBuilders.values()) {
				var property = propertyBuilder.build();
				properties.put(property.name(), property);

				for (Class<? extends Annotation> annotationType : property.annotations().keySet()) {
					var annotatedPropertySet = annotatedProperties.get(annotationType);
					if (annotatedPropertySet == null)
						annotatedProperties.put(annotationType, annotatedPropertySet = new LinkedHashMap<>());
					annotatedPropertySet.put(property.name(), property);
				}
			}

			this.properties = Collections.unmodifiableMap(properties);
			for (var annotatedPropertyEntry : annotatedProperties.entrySet())
				annotatedPropertyEntry.setValue(Collections.unmodifiableMap(annotatedPropertyEntry.getValue()));
			this.annotatedProperties = Collections.unmodifiableMap(annotatedProperties);
		}
	}

	private class MemberMetadata {
		private final Map<Class<?>, IuType<? super T>> hierarchy;
		private final Map<String, IuField<?>> fields;
		private final Map<Class<? extends Annotation>, Map<String, IuField<?>>> annotatedFields;
		private final Map<ExecutableKey, IuConstructor<? extends T>> constructors;
		private final Map<Class<? extends Annotation>, Map<ExecutableKey, IuConstructor<?>>> annotatedConstructors;
		private final Map<ExecutableKey, IuMethod<?>> methods;
		private final Map<Class<? extends Annotation>, Map<ExecutableKey, IuMethod<?>>> annotatedMethods;

		@SuppressWarnings("unchecked")
		private MemberMetadata() {
			Map<Class<?>, IuType<? super T>> hierarchy = new LinkedHashMap<>();
			Map<String, IuField<?>> fields = new LinkedHashMap<>();
			Map<Class<? extends Annotation>, Map<String, IuField<?>>> annotatedFields = new LinkedHashMap<>();
			Map<ExecutableKey, IuConstructor<T>> constructors = new LinkedHashMap<>();
			Map<Class<? extends Annotation>, Map<ExecutableKey, IuConstructor<?>>> annotatedConstructors = new LinkedHashMap<>();
			Map<ExecutableKey, IuMethod.Builder<?>> methodBuilders = new LinkedHashMap<>();
			Map<Class<? extends Annotation>, Map<ExecutableKey, IuMethod<?>>> annotatedMethods = new LinkedHashMap<>();

			var baseClass = (Class<?>) type;
			if (!baseClass.isEnum())
				for (var constructor : baseClass.getDeclaredConstructors()) {
					var key = new ExecutableKey(constructor.getParameterTypes());
					var genericParameterTypes = constructor.getGenericParameterTypes();
					var length = genericParameterTypes.length;

					var params = constructor.getParameters();
					Class<?> enclosingClass;
					if (!Modifier.isStatic(baseClass.getModifiers()) && params.length == length + 1)
						enclosingClass = baseClass.getEnclosingClass();
					else
						enclosingClass = null;
					var oneIfEnclosed = enclosingClass == null ? 0 : 1;

					assert length + oneIfEnclosed == params.length : Arrays.toString(genericParameterTypes) + " "
							+ Arrays.toString(params) + " " + constructor;

					List<IuParameter<?>> parameters = new ArrayList<>();
					if (enclosingClass != null)
						parameters.add(new IuParameter<>(child(enclosingClass).variable(null).build(), params[0]));
					for (int i = 0; i < length; i++)
						parameters.add(new IuParameter<>(child(genericParameterTypes[i]).variable(null).build(),
								params[i + oneIfEnclosed]));
					@SuppressWarnings({ "rawtypes" })
					IuConstructor<T> resolvedConstructor = new IuConstructor(IuType.this,
							child(type).variable(null).build(), constructor, parameters);
					constructors.put(key, resolvedConstructor);

					for (var annotationClass : resolvedConstructor.annotations().keySet()) {
						var annotatedConstructorMap = annotatedConstructors.get(annotationClass);
						if (annotatedConstructorMap == null)
							annotatedConstructors.put(annotationClass, annotatedConstructorMap = new LinkedHashMap<>());
						annotatedConstructorMap.put(key, resolvedConstructor);
					}
				}

			Queue<IuType<?>> q = new ArrayDeque<>();
			q.add(IuType.this); // bottom up scan
			while (!q.isEmpty()) {
				var current = q.poll();
				var currentBase = current.baseClass();
				hierarchy.put(currentBase, (IuType<? super T>) current);

				for (var field : currentBase.getDeclaredFields()) {
					var fieldName = field.getName();
					if (!fields.containsKey(fieldName)) {
						var fieldType = current.child(field.getGenericType()).breadcrumb(Breadcrumb.field, fieldName)
								.variable(null).build();
						var resolvedField = new IuField<>(current, fieldType, field);
						fields.put(fieldName, resolvedField);

						for (var annotationClass : resolvedField.annotations().keySet()) {
							var annotatedFieldMap = annotatedFields.get(annotationClass);
							if (annotatedFieldMap == null)
								annotatedFields.put(annotationClass, annotatedFieldMap = new LinkedHashMap<>());
							annotatedFieldMap.put(fieldName, resolvedField);
						}
					}
				}

				for (var method : currentBase.getDeclaredMethods()) {
					var key = new ExecutableKey(method.getName(), method.getParameterTypes());
					var genericParameterTypes = method.getGenericParameterTypes();
					var length = genericParameterTypes.length;
					var params = method.getParameters();
					assert length == params.length
							: Arrays.toString(genericParameterTypes) + " " + Arrays.toString(params);
					List<IuParameter<?>> parameters = new ArrayList<>();
					for (int i = 0; i < length; i++)
						parameters.add(new IuParameter<>(current.child(genericParameterTypes[i]).variable(null).build(),
								params[i]));
					if (!methodBuilders.containsKey(key))
						methodBuilders.put(key,
								new IuMethod.Builder<>(current,
										current.child(method.getGenericReturnType()).variable(null).build(), method,
										parameters));
					else
						methodBuilders.get(key).inherit(currentBase, method);
				}

				var implemented = currentBase.getGenericInterfaces();
				var length = implemented.length;
				var rawImplemented = currentBase.getInterfaces();
				assert rawImplemented.length == length;
				for (int i = 0; i < length; i++) {
					q.offer(current.child(implemented[i]).breadcrumb(Breadcrumb.referTo, rawImplemented[i])
							.variable(null).build());
				}
				var extended = currentBase.getGenericSuperclass();
				if (extended != null)
					q.offer(current.child(extended).breadcrumb(Breadcrumb.referTo, currentBase.getSuperclass())
							.variable(null).build());
			}

			this.hierarchy = Collections.unmodifiableMap(hierarchy);
			this.fields = Collections.unmodifiableMap(fields);
			for (var annotatedFieldEntry : annotatedFields.entrySet())
				annotatedFieldEntry.setValue(Collections.unmodifiableMap(annotatedFieldEntry.getValue()));
			this.annotatedFields = Collections.unmodifiableMap(annotatedFields);
			this.constructors = Collections.unmodifiableMap(constructors);
			for (var annotatedConstructorEntry : annotatedConstructors.entrySet())
				annotatedConstructorEntry.setValue(Collections.unmodifiableMap(annotatedConstructorEntry.getValue()));
			this.annotatedConstructors = Collections.unmodifiableMap(annotatedConstructors);

			Map<ExecutableKey, IuMethod<?>> methods = new LinkedHashMap<>();
			for (var methodBuilderEntry : methodBuilders.entrySet())
				methods.put(methodBuilderEntry.getKey(), methodBuilderEntry.getValue().build());
			this.methods = Collections.unmodifiableMap(methods);

			for (var methodEntry : methods.entrySet()) {
				var key = methodEntry.getKey();
				var resolvedMethod = methodEntry.getValue();
				for (var annotationClass : resolvedMethod.annotations().keySet()) {
					var annotatedMethodMap = annotatedMethods.get(annotationClass);
					if (annotatedMethodMap == null)
						annotatedMethods.put(annotationClass, annotatedMethodMap = new LinkedHashMap<>());
					annotatedMethodMap.put(key, resolvedMethod);
				}

			}
			for (var annotatedMethodEntry : annotatedMethods.entrySet())
				annotatedMethodEntry.setValue(Collections.unmodifiableMap(annotatedMethodEntry.getValue()));

			this.annotatedMethods = Collections.unmodifiableMap(annotatedMethods);
		}
	}

	private final Type type;
	private final TypeVariable<?> variable;
	private final Breadcrumb breadcrumb;
	private final Object breadcrumbOption;
	private final IuType<?> parent;
	private PropertyMetadata propertyMetadata;
	private MemberMetadata memberMetadata;
	private IuType<T> base;
	private Iterable<IuType<? super T>> bounds;
	private IuType<?> componentType;
	private Map<String, IuType<?>> typeParameters;
	private Map<Class<? extends Annotation>, Annotation> annotations;

	private IuType(Type type) {
		this(type, null, null, null, null);
	}

	private IuType(Type type, TypeVariable<?> variable, Breadcrumb breadcrumb, Object breadcrumbOption,
			IuType<?> parent) {
		if (type == null)
			throw new NullPointerException("Cannot resolve null type" + (parent == null ? "" : "; from " + parent));

		if (type instanceof Class) {
			var iuTypeModule = IuType.class.getModule();
			var module = ((Class<?>) type).getModule();
			if (!iuTypeModule.canRead(module))
				iuTypeModule.addReads(module);
		}

		this.type = type;
		this.variable = variable;
		this.parent = parent;
		this.breadcrumb = breadcrumb;
		this.breadcrumbOption = breadcrumbOption;
	}

	private PropertyMetadata getPropertyMetadata() {
		if (!(type instanceof Class))
			return base().getPropertyMetadata();

		if (propertyMetadata == null)
			propertyMetadata = new PropertyMetadata();
		return propertyMetadata;
	}

	private MemberMetadata getMemberMetadata() {
		if (!(type instanceof Class))
			return base().getMemberMetadata();

		if (memberMetadata == null)
			memberMetadata = new MemberMetadata();
		return memberMetadata;
	}

	private <C> ChildBuilder<C> child(Class<C> type) {
		return new ChildBuilder<C>(type, this);
	}

	private ChildBuilder<?> child(Type type) {
		return new ChildBuilder<>(type, this);
	}

	/**
	 * Gets the resolved generic type.
	 * 
	 * @return resolved generic type
	 */
	public Type type() {
		return type;
	}

	/**
	 * Gets the common implementation type of an interface or abstract type.
	 * 
	 * @return implementation type
	 */
	public IuType<? extends T> implementationType() {
		// concrete class implements itself
		var baseClass = baseClass();
		if (!baseClass.isInterface() && !Modifier.isAbstract(baseClass.getModifiers()))
			return this;

		Class<? extends T> implClass = null;

		if (baseClass.equals(Number.class))
			implClass = BigDecimal.class.asSubclass(baseClass);

		else if (baseClass.equals(SortedMap.class))
			implClass = TreeMap.class.asSubclass(baseClass);
		else if (baseClass.equals(Map.class))
			implClass = LinkedHashMap.class.asSubclass(baseClass);
		else if (baseClass.equals(SortedSet.class))
			implClass = TreeSet.class.asSubclass(baseClass);
		else if (baseClass.equals(Set.class))
			implClass = LinkedHashSet.class.asSubclass(baseClass);
		else if (baseClass.equals(List.class))
			implClass = ArrayList.class.asSubclass(baseClass);
		else if (baseClass.equals(Collection.class) //
				|| baseClass.equals(Queue.class) //
				|| baseClass.equals(Deque.class))
			implClass = ArrayDeque.class.asSubclass(baseClass);
		else
			throw new IllegalArgumentException("No common implementation type for " + baseClass);

		return child(implClass).build();
	}

	/**
	 * Resolves a field declared by the base class of the resolved type.
	 *
	 * @param <F>       field type
	 * @param fieldName field name
	 * @return resolved compile-time field metadata
	 */
	@SuppressWarnings("unchecked")
	public <F> IuField<F> field(String fieldName) {
		var metadata = getMemberMetadata();
		var field = metadata.fields.get(fieldName);
		if (field == null)
			throw new IllegalArgumentException("Missing field " + fieldName + "; " + this);
		else
			return (IuField<F>) field;
	}

	/**
	 * Resolves a field declared in the hierarchy of the base class of the resolved
	 * type.
	 * 
	 * @param field field
	 * @return resolved compile-time field metadata
	 */
	public IuField<?> field(Field field) {
		return referTo(field.getDeclaringClass()).field(field.getName());
	}

	/**
	 * Gets all fields declared by the base class.
	 *
	 * @return declared fields
	 */
	public Map<String, IuField<?>> fields() {
		return getMemberMetadata().fields;
	}

	/**
	 * Gets all fields defined by the base class with an annotation present.
	 * 
	 * @param annotationClass annotation type
	 * @return fields with the annotation present
	 */
	public Map<String, IuField<?>> annotatedFields(Class<? extends Annotation> annotationClass) {
		var annotatedFields = getMemberMetadata().annotatedFields.get(annotationClass);
		if (annotatedFields == null)
			return Collections.emptyMap();
		else
			return annotatedFields;
	}

	/**
	 * Determines of the base class of the resolved type has an accessible
	 * constructor.
	 * 
	 * @param parameterTypes parameter types
	 * @return true if an accessible constructor exists with the given parameter
	 *         types, else false
	 */
	public boolean hasConstructor(Class<?>... parameterTypes) {
		var key = new ExecutableKey(parameterTypes);
		if (getMemberMetadata().constructors.containsKey(key))
			return true;

		IuType<? extends T> implementationType;
		try {
			implementationType = implementationType();
		} catch (IllegalArgumentException e) {
			return false;
		}

		if (implementationType.getMemberMetadata().constructors.containsKey(key))
			return true;
		return false;
	}

	/**
	 * Resolves all constructors declared by the base class of the resolved type.
	 * 
	 * @return declared constructors
	 */
	public Iterable<IuConstructor<? extends T>> constructors() {
		IuType<? extends T> implementationType;
		try {
			implementationType = implementationType();
		} catch (IllegalArgumentException e) {
			return Collections.emptySet();
		}

		return Collections.unmodifiableCollection(implementationType.getMemberMetadata().constructors.values());
	}

	/**
	 * Resolves a constructor declared by the base class of the resolved type or one
	 * of its implementation types.
	 * 
	 * @param parameterTypes parameter types
	 * @return resolved compile-time constructor metadata for the implementation
	 *         type; if multiple implementation types exist for the resolved type,
	 *         then the first one exposing a constructor that matches the parameter
	 *         types is returned.
	 */
	public IuConstructor<? extends T> constructor(Class<?>... parameterTypes) {
		var key = new ExecutableKey(parameterTypes);
		IuConstructor<? extends T> constructor = implementationType().getMemberMetadata().constructors.get(key);

		if (constructor == null)
			throw new IllegalArgumentException("Missing constructor " + Arrays.toString(parameterTypes) + "; " + this);

		return constructor;
	}

	/**
	 * Resolves a constructor declared in the hierarchy of the base class of the
	 * resolved type.
	 * 
	 * @param constructor constructor
	 * @return resolved compile-time method return type metadata
	 */
	public IuConstructor<? extends T> constructor(Constructor<T> constructor) {
		return referTo(constructor.getDeclaringClass()).constructor(constructor.getParameterTypes());
	}

	/**
	 * Gets all constructors defined by the base class with an annotation present.
	 * 
	 * @param annotationClass annotation type
	 * @return methods with the annotation present
	 */
	public Iterable<IuConstructor<?>> annotatedConstructors(Class<? extends Annotation> annotationClass) {
		var annotatedConstructors = getMemberMetadata().annotatedConstructors.get(annotationClass);
		if (annotatedConstructors == null)
			return Collections.emptySet();
		else
			return Collections.unmodifiableCollection(annotatedConstructors.values());
	}

	/**
	 * Determines if the base class of the resolved type has a method.
	 * 
	 * @param methodName     method name
	 * @param parameterTypes parameter index
	 * @return true if the base class has the method; else false
	 */
	public boolean hasMethod(String methodName, Class<?>... parameterTypes) {
		var metadata = getMemberMetadata();
		return metadata.methods.containsKey(new ExecutableKey(methodName, parameterTypes));
	}

	/**
	 * Determines if the base class of the resolved type declares a method.
	 * 
	 * @param method method
	 * @return true if the base class declares the method; else false
	 */
	public boolean declaresMethod(Method method) {
		var metadata = getMemberMetadata();
		var myMethod = metadata.methods.get(new ExecutableKey(method.getName(), method.getParameterTypes()));
		return myMethod != null && myMethod.deref().getDeclaringClass() == baseClass();
	}

	/**
	 * Determines if the base class of the resolved type has a static method.
	 * 
	 * @param methodName     method name
	 * @param parameterTypes parameter index
	 * @return true if the base class has the method; else false
	 */
	public boolean hasStaticMethod(String methodName, Class<?>... parameterTypes) {
		var metadata = getMemberMetadata();
		var k = new ExecutableKey(methodName, parameterTypes);
		return metadata.methods.containsKey(k) && metadata.methods.get(k).isStatic();
	}

	/**
	 * Resolves a method declared by the base class of the resolved type.
	 * 
	 * @param methodName     method name
	 * @param parameterTypes parameter index
	 * @return resolved compile-time method return type metadata
	 */
	public IuMethod<?> method(String methodName, Class<?>... parameterTypes) {
		var metadata = getMemberMetadata();
		var method = metadata.methods.get(new ExecutableKey(methodName, parameterTypes));
		if (method == null)
			throw new IllegalArgumentException(
					"Missing method parameter " + methodName + " " + Arrays.toString(parameterTypes) + "; " + this);
		return method;
	}

	/**
	 * Resolves a method declared in the hierarchy of the base class of the resolved
	 * type.
	 * 
	 * @param method method
	 * @return resolved compile-time method return type metadata
	 */
	public IuMethod<?> method(Method method) {
		return method(method.getName(), method.getParameterTypes());
	}

	/**
	 * Gets all methods declared by the base class.
	 * 
	 * @return declared methods
	 */
	public Iterable<IuMethod<?>> methods() {
		return Collections.unmodifiableCollection(getMemberMetadata().methods.values());
	}

	/**
	 * Gets all methods defined by the base class with an annotation present.
	 * 
	 * @param annotationClass annotation type
	 * @return methods with the annotation present
	 */
	public Iterable<IuMethod<?>> annotatedMethods(Class<? extends Annotation> annotationClass) {
		var annotatedMethods = getMemberMetadata().annotatedMethods.get(annotationClass);
		if (annotatedMethods == null)
			return Collections.emptySet();
		else
			return Collections.unmodifiableCollection(annotatedMethods.values());
	}

	/**
	 * Gets the type hierarchy for the base class, ordered from most specific to
	 * least specific.
	 * 
	 * @return extended classes and implemented interfaces
	 */
	public Collection<IuType<? super T>> hierarchy() {
		return getMemberMetadata().hierarchy.values();
	}

	/**
	 * Gets the runtime metadata for a class or interface in hierarchy of the
	 * resolved type
	 * 
	 * @param <S>       extended class or implemented interface
	 * @param superType extended class or implemented interface
	 * @return compile-time metadata for the class or interface
	 */
	@SuppressWarnings("unchecked")
	public <S> IuType<S> referTo(final Class<S> superType) {
		// common case
		var isVariable = type instanceof TypeVariable;
		if (baseClass().equals(superType) && !isVariable)
			return (IuType<S>) this;

		// attempt to dereference type variables before resolving base class
		// i.e.: Iterable<T> -> Collection<E> -> List<E>
		// ..., where <E> on List is our reference point
		class State {
			final IuType<?> type;
			final Class<?> superType;
			IuType<?> referredTo;
			State pending;

			State(IuType<?> type, Class<?> superType) {
				this.type = type;
				this.superType = superType;
			}

			void referTo(IuType<?> referredTo) {
				this.referredTo = referredTo;
			}
		}

		State initialState = new State(this, superType);
		Deque<State> stack = new ArrayDeque<>();
		stack.push(initialState);
		while (!stack.isEmpty()) {
			var state = stack.pop();

			IuType<?> referencePoint = state.type;
			var type = state.type.type;

			if (type instanceof TypeVariable) {
				var typeVariable = (TypeVariable<?>) type;
				var name = typeVariable.getName();
				var declaringClass = typeVariable.getGenericDeclaration();

				IuType<?> current = state.type;
				// current.parent will always reach the declaring class of the type variable?
				while (!declaringClass.equals(current.baseClass()))
					current = current.parent;

				if (current.type instanceof ParameterizedType)
					if (state.pending == null) {
						var next = new State(current.typeParameter(name), state.superType);
						state.pending = next;
						stack.push(state);
						stack.push(state.pending);
						continue;
					} else
						referencePoint = state.pending.referredTo;

				else {
					// special case: unresolve base type from prior reference
					if (current.parent != null //
							&& current.parent.base == current)
						referencePoint = current.parent.typeParameter(name);
					else
						referencePoint = current.typeParameter(name);
				}
			}

			var base = referencePoint.base();

			IuType<?> referredTo;
			if (base.type.equals(state.superType)) {
				referredTo = referencePoint;
			} else {
				referredTo = base.getMemberMetadata().hierarchy.get(state.superType);
				if (referredTo == null)
					throw new IllegalArgumentException(
							state.superType + " not found in type hierarchy for " + state.type + "; " + this);
			}
			state.referTo(referredTo);
		}

		return (IuType<S>) initialState.referredTo;
	}

	/**
	 * Gets the upper bounds for a generic type.
	 * 
	 * <p>
	 * For a wildcard type, the upper bounds are resolved and returned.
	 * </p>
	 * <p>
	 * For a type variable, an attempt is made to resolve the variable relative a
	 * parameterized type that declared this resolved type; if found, that resovled
	 * variable is returned; if not found, the variable bounds are resolved and
	 * returned.
	 * </p>
	 * <p>
	 * All other resolved types are returned directly as singleton bounds.
	 * </p>
	 * 
	 * @return upper bounds
	 */
	@SuppressWarnings("unchecked")
	public Iterable<IuType<? super T>> getBounds() {
		if (bounds != null)
			return bounds;

		if (type instanceof WildcardType) {
			Queue<IuType<? super T>> bounds = new ArrayDeque<>();
			var wildcardType = (WildcardType) type;
			for (var bound : wildcardType.getUpperBounds())
				bounds.offer((IuType<? super T>) child(bound).build());
			return this.bounds = bounds;

		} else if (type instanceof TypeVariable) {
			var typeVariable = (TypeVariable<?>) type;
			var name = typeVariable.getName();

			IuType<?> maybeParameterized = parent;
			while (maybeParameterized != null) {
				if (maybeParameterized.type instanceof ParameterizedType) {
					var parameterizedType = (ParameterizedType) maybeParameterized.type;
					var rawType = child(parameterizedType.getRawType()).build().baseClass();
					if (rawType.equals(typeVariable.getGenericDeclaration())) {
						var typeParameters = rawType.getTypeParameters();
						for (int i = 0; i < typeParameters.length; i++) {
							var typeParameter = typeParameters[i];
							if (name.equals(typeParameter.getName())) {
								var actualType = parameterizedType.getActualTypeArguments()[i];
								return bounds = Collections.singleton(
										(IuType<? super T>) child(actualType).variable(typeParameter).build());
							}
						}
					}
				}
				maybeParameterized = maybeParameterized.parent;
			}

			Queue<IuType<? super T>> bounds = new ArrayDeque<>();
			for (var bound : typeVariable.getBounds())
				bounds.offer((IuType<? super T>) child(bound).build());
			return this.bounds = bounds;

		} else
			return bounds = Collections.singleton(this);
	}

	/**
	 * Gets the resolved type.
	 * 
	 * @return resolved type
	 */
	public Type deref() {
		return type;
	}

	/**
	 * Resolves the base class for this type.
	 * 
	 * @return resolved base class
	 */
	public IuType<T> base() {
		if (type instanceof Class)
			return this;
		else if (base != null)
			return base;
		else
			return base = findBase(this);
	}

	/**
	 * Get the resolved base class.
	 * 
	 * <p>
	 * This method is shorthand, equivalent to
	 * {@code (Class<?>) getBase().getType()}.
	 * </p>
	 * 
	 * @return base class
	 */
	@SuppressWarnings("unchecked")
	public Class<T> baseClass() {
		return (Class<T>) base().type;
	}

	/**
	 * Enforces that this type wrapper represents a subtype of the given type.
	 * 
	 * @param type sub type
	 * @param <S>  sub type
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public <S> IuType<? extends S> sub(Class<S> type) {
		baseClass().asSubclass(type);
		return (IuType<? extends S>) this;
	}

	/**
	 * Returns the object equivalents for a primitive type.
	 * 
	 * @return the object version related to a primitive type, or the class passed
	 *         in as-is if not primitive
	 */
	@SuppressWarnings("unchecked")
	public Class<T> autoboxClass() {
		var potentiallyPrimitive = baseClass();
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
	 * @return the default value that would be assigned to a field of the type
	 *         without an initializer
	 */
	@SuppressWarnings("unchecked")
	public T autoboxDefault() {
		var potentiallyPrimitive = baseClass();
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
	 * Gets an single instance of an empty array for the resolved base type.
	 * 
	 * <p>
	 * This method is useful for use reducing object construction related to calling
	 * {@link Collection#toArray(Object[])} when the array type is known, but the
	 * size is not.
	 * </p>
	 * 
	 * @return empty array
	 */
	@SuppressWarnings("unchecked")
	public T[] emptyArray() {
		var baseClass = baseClass();
		var emptyArray = EMPTY_ARRAYS.get(baseClass);
		if (emptyArray == null) {
			emptyArray = Array.newInstance(baseClass, 0);
			synchronized (EMPTY_ARRAYS) {
				EMPTY_ARRAYS.put(baseClass, emptyArray);
			}
		}
		return (T[]) emptyArray;
	}

	/**
	 * Gets an array generator function.
	 * 
	 * <p>
	 * This method is useful for use reducing object construction related to calling
	 * {@link Collection#toArray(IntFunction)} and
	 * {@link Stream#toArray(IntFunction)}.
	 * </p>
	 * 
	 * @return array generator function
	 */
	public IntFunction<T[]> arrayGenerator() {
		return size -> {
			var empty = emptyArray();
			if (size == 0)
				return empty;
			else
				return Arrays.copyOf(empty, size);
		};
	}

	/**
	 * Determines if the type is an array.
	 * 
	 * @return true if the type is an array, else false
	 */
	public boolean isArray() {
		return (type instanceof GenericArrayType) //
				|| baseClass().isArray();
	}

	/**
	 * Resolves the component type for a potentially generic array.
	 * 
	 * @return resolved component type
	 */
	public IuType<?> componentType() {
		if (componentType != null)
			return componentType;

		if (type instanceof Class<?>) {
			var base = (Class<?>) type;
			if (!base.isArray())
				throw new IllegalArgumentException(base + " isn't an array type; " + this);
			return componentType = child(base.getComponentType()).breadcrumb(Breadcrumb.componentType).build();
		} else if (type instanceof GenericArrayType) {
			var genericArrayType = (GenericArrayType) type;
			return componentType = child(genericArrayType.getGenericComponentType())
					.breadcrumb(Breadcrumb.componentType).build();
		} else
			return componentType = findComponentType(this);
	}

	/**
	 * Resolves a type parameter associated with a class or parameterized type.
	 * 
	 * @param name type parameter name
	 * @return resovled type parameter
	 */
	public IuType<?> typeParameter(String name) {
		if (typeParameters == null) {
			Map<String, IuType<?>> typeParameters = new LinkedHashMap<>();
			if (type instanceof ParameterizedType) {
				var parameterizedType = (ParameterizedType) type;
				var rawType = child(parameterizedType.getRawType()).build().baseClass();
				var typeParams = rawType.getTypeParameters();
				var actualTypeArguments = parameterizedType.getActualTypeArguments();
				for (int i = 0; i < typeParams.length; i++) {
					var typeParameter = typeParams[i];
					typeParameters.put(typeParameter.getName(),
							child(actualTypeArguments[i]).breadcrumb(Breadcrumb.typeParameter, typeParameter.getName())
									.variable(typeParameter).build());
				}
			} else if (type instanceof Class) {
				var rawType = (Class<?>) type;
				var typeParams = rawType.getTypeParameters();
				for (int i = 0; i < typeParams.length; i++) {
					var typeParameter = typeParams[i];
					typeParameters.put(typeParameter.getName(),
							child(typeParameter).breadcrumb(Breadcrumb.typeParameter, name).variable(null).build());
				}
			}
			this.typeParameters = typeParameters;
		}

		var typeParameter = typeParameters.get(name);
		if (typeParameter != null)
			return typeParameter;
		else
			throw new IllegalArgumentException("Missing type parameter for " + name + "; " + this);
	}

	/**
	 * Gets all properties defined for the resolved type.
	 * 
	 * @return property names
	 */
	public Map<String, IuProperty<?>> properties() {
		return getPropertyMetadata().properties;
	}

	/**
	 * Gets a property of this resolved type.
	 * 
	 * @param <P>          property type
	 * @param propertyName property name
	 * @return property
	 */
	@SuppressWarnings("unchecked")
	public <P> IuProperty<P> property(String propertyName) {
		IuProperty<?> rv = getPropertyMetadata().properties.get(propertyName);
		if (rv != null)
			return (IuProperty<P>) rv;
		throw new IllegalArgumentException("Property " + propertyName + " not defined; " + this);
	}

	/**
	 * Gets properties of the resolved type with an annotation present.
	 * 
	 * @param annotationClass annotation type
	 * @return properties with and annotation of the given type present.
	 * @see IuProperty#annotation(Class)
	 */
	public Map<String, IuProperty<?>> annotatedProperties(Class<? extends Annotation> annotationClass) {
		var annotatedProperties = getPropertyMetadata().annotatedProperties.get(annotationClass);
		if (annotatedProperties == null)
			return Collections.emptyMap();
		else
			return annotatedProperties;
	}

	/**
	 * Loads an implementation to use as a non-null default for use when a full
	 * implementation is both optional and known from the specifying context.
	 * 
	 * <p>
	 * The implementation return by this method will respond to
	 * {@link Object#hashCode()}, {@link Object#equals(Object)}, and
	 * {@link Object#toString()} following the default contracts for those methods
	 * the same as if the interface were a normal class.
	 * </p>
	 * 
	 * <p>
	 * Default methods specified by the interface will be invoked with default
	 * behavior.
	 * </p>
	 * 
	 * <p>
	 * All other methods will thrown {@link UnsupportedOperationException} when
	 * invoked.
	 * </p>
	 * 
	 * @return proxy implementation providing the behavior outlined above
	 */
	public T unsupported() {
		var baseClass = baseClass();
		return baseClass.cast(Proxy.newProxyInstance(baseClass.getClassLoader(), new Class<?>[] { baseClass },
				(proxy, method, args) -> {
					Object returnValue = handleObjectMethods(proxy, method, args);
					if (returnValue != null)
						return returnValue;

					if (method.isDefault())
						return MethodHandles.privateLookupIn(baseClass, MethodHandles.lookup())
								.unreflectSpecial(method, baseClass).bindTo(proxy).invokeWithArguments(args);

					throw new UnsupportedOperationException(method.toString());
				}));
	}

	/**
	 * Gets a new instance of the base class.
	 * 
	 * @return new instance
	 * @throws Exception if invocation results in an exception
	 * @see IuConstructor#newInstance(Object...)
	 */
	public T newInstance() throws Exception {
		try {
			return constructor().newInstance();
		} catch (Throwable e) {
			throw IuException.handleInvocation(e);
		}
	}

	/**
	 * Wrap the implementation of an interface in a proxy that obscures
	 * implementation details from an external consumer.
	 * 
	 * <p>
	 * This method is shorthand for:
	 * </p>
	 * 
	 * <pre>
	 * wrap(IuType.resolve(implementation).newInstance())
	 * </pre>
	 * 
	 * @param implementation class implementing the interface
	 * @return proxying interface wrapper
	 * @see #newInstance()
	 * @see #wrap(Object)
	 */
	public T wrap(Class<? extends T> implementation) {
		try {
			return wrap(IuType.resolve(implementation).newInstance());
		} catch (Exception e) {
			throw IuException.handleUnchecked(e);
		}
	}

	/**
	 * Wrap the implementation of an interface in a proxy that obscures
	 * implementation details from an external consumer.
	 * 
	 * <p>
	 * Calls to wrapped objects are interpolated by
	 * {@link jakarta.interceptor.AroundInvoke} where defined by the implementation
	 * class, a superclass, or a bound interceptor.
	 * </p>
	 * 
	 * <p>
	 * Only types visible to the interface's {@link ClassLoader} may be obtained
	 * through the proxy returned by this method.
	 * </p>
	 * 
	 * <p>
	 * Methods on the interface that return a value specified by one or more
	 * interfaces will return a proxy implementing all implied interfaces and
	 * exhibiting the same behavior as the wrapped proxy returned by this method.
	 * </p>
	 * 
	 * <p>
	 * All non-interface return values will be returned unmodified.
	 * </p>
	 * 
	 * @param implementation object instance implementing the interface
	 * @return proxying interface wrapper
	 */
	public T wrap(T implementation) {
		var loader = implementation.getClass().getClassLoader();
		var baseClass = baseClass();
		return baseClass.cast(Proxy.newProxyInstance(loader, new Class<?>[] { baseClass },
				new WrapInvocationHandler(loader, this, implementation)));
	}

	@Override
	public boolean hasAnnotation(Class<? extends Annotation> annocationType) {
		return BackwardsCompatibilityHelper.isAnnotationPresent(annocationType, baseClass());
	}

	@Override
	public <A extends Annotation> A annotation(Class<A> annocationType) {
		return BackwardsCompatibilityHelper.getAnnotation(annocationType, baseClass());
	}

	@Override
	public Map<Class<? extends Annotation>, Annotation> annotations() {
		if (annotations == null)
			annotations = BackwardsCompatibilityHelper.getAnnotations(baseClass());
		return annotations;
	}

	@Override
	public String toString() {
		class PendingType {
			final Type type;
			final Consumer<CharSequence> andThen;
			Queue<PendingType> staged;
			StringBuilder builder = new StringBuilder();

			PendingType(Type type) {
				this(type, null);
			}

			PendingType(Type type, Consumer<CharSequence> andThen) {
				this.type = type;
				this.andThen = andThen;
			}

			private void stageInsert(Type type) {
				if (staged == null)
					staged = new ArrayDeque<>();
				var offset = builder.length();
				staged.offer(new PendingType(type, s -> builder.insert(offset, s)));
			}

			private void appendAll(String delimiter, Type[] bounds) {
				boolean first = true;
				for (var bound : bounds) {
					if (first)
						first = false;
					else
						builder.append(delimiter);
					stageInsert(bound);
				}
			}

			private void appendBounds(String prefix, Type[] bounds) {
				if (bounds.length > 2 || bounds.length == 1 && !bounds[0].equals(Object.class)) {
					builder.append(prefix);
					appendAll(" & ", bounds);
				}
			}

			private void appendParameters(Type[] parameterTypes) {
				builder.append('(');
				appendAll(",", parameterTypes);
				builder.append(')');
			}

			private void appendClass(Class<?> c) {
				if (c.isArray()) {
					stageInsert(c.getComponentType());
					builder.append("[]");
				} else if (Proxy.isProxyClass(c)) {
					builder.append("proxy(");
					appendAll(",", c.getInterfaces());
					builder.append("; ");
					stageInsert(Proxy.getInvocationHandler(c).getClass());
					builder.append(')');
				} else
					builder.append(c.getName());
			}

			private void appendTypeParams(Type[] params) {
				if (params.length > 0) {
					builder.append('<');
					appendAll(",", params);
					builder.append('>');
				}
			}

			private void appendConstructor(Constructor<?> constructor) {
				builder.append("new ");
				appendClass(constructor.getDeclaringClass());
				appendParameters(constructor.getGenericParameterTypes());
			}

			private void appendMethod(Method method) {
				appendClass(method.getDeclaringClass());
				builder.append('#');
				builder.append(method.getName());
				appendParameters(method.getGenericParameterTypes());
				var returnType = method.getGenericReturnType();
				if (!returnType.equals(Void.TYPE)) {
					builder.append(':');
					stageInsert(returnType);
				}
			}

			void appendType() {
				if (staged != null)
					return;

				if (type instanceof WildcardType) {
					var wildcard = (WildcardType) type;
					builder.append('?');
					appendBounds(" super ", wildcard.getLowerBounds());
					appendBounds(" extends ", wildcard.getLowerBounds());
				}

				else if (type instanceof TypeVariable) {
					var variable = (TypeVariable<?>) type;
					builder.append(variable.getName());
					builder.append(" from ");
					var declaration = variable.getGenericDeclaration();
					if (declaration instanceof Class)
						appendClass((Class<?>) declaration);
					else if (declaration instanceof Method)
						appendMethod((Method) declaration);
					else if (declaration instanceof Constructor)
						appendConstructor((Constructor<?>) declaration);
					else
						assert false : declaration + " " + type;
				}

				else if (type instanceof ParameterizedType) {
					var parameterized = (ParameterizedType) type;
					stageInsert(parameterized.getRawType());
					appendTypeParams(parameterized.getActualTypeArguments());
				}

				else if (type instanceof GenericArrayType) {
					var array = (GenericArrayType) type;
					stageInsert(array.getGenericComponentType());
					builder.append("[]");
				}

				else if (type instanceof Class) {
					var c = (Class<?>) type;
					appendClass(c);
				}
			}
		}

		class State {
			final Breadcrumb breadcrumb;
			final Object breadcrumbOption;
			final IuType<?> type;

			State(Breadcrumb breadcrumb, Object breadcrumbOption, IuType<?> type) {
				this.breadcrumb = breadcrumb;
				this.breadcrumbOption = breadcrumbOption;
				this.type = type;
			}

			StringBuilder result() {
				Deque<PendingType> pending = new ArrayDeque<>();

				PendingType variable;
				if (type.variable != null) {
					variable = new PendingType(type.variable);
					variable.builder.append(", from ");
					pending.push(variable);
				} else
					variable = null;

				PendingType self = new PendingType(type.type);
				pending.push(self);

				while (!pending.isEmpty()) {
					var pendingType = pending.pop();
					if (pendingType.staged == null) {
						pendingType.appendType();
						if (pendingType.staged != null) {
							pending.push(pendingType);
							while (!pendingType.staged.isEmpty())
								pending.push(pendingType.staged.poll());
							continue;
						}
					}

					if (pendingType.andThen != null)
						pendingType.andThen.accept(pendingType.builder);
				}

				if (variable != null)
					self.builder.append(variable.builder);
				return self.builder;
			}
		}

		Deque<State> stack = new ArrayDeque<>();
		var initial = new State(null, null, this);
		stack.push(initial);

		var current = initial;
		while (current.type.parent != null) {
			stack.push(current);
			assert current.type.breadcrumb != null : //
					current.type.type + " " + current.type.parent.type;
			current = new State(current.type.breadcrumb, current.type.breadcrumbOption, current.type.parent);
		}

		StringBuilder result = current.result();
		var state = current;
		do {
			if (state.breadcrumb == null)
				assert state == initial
						: result + (stack.isEmpty() ? " {initial}" : " {" + stack.peek().result() + "}");
			else {
				var breadcrumb = state.breadcrumb;
				var option = state.breadcrumbOption;
				var next = stack.peekFirst();
				if (next != null //
						&& breadcrumb != null //
						&& IuObject.equals(breadcrumb, next.breadcrumb)) {

					if (breadcrumb.equals(Breadcrumb.base))
						continue;

					if (breadcrumb.equals(Breadcrumb.referTo)) {
						Class<?> referTo = (Class<?>) option;
						Class<?> nextReferTo = (Class<?>) next.breadcrumbOption;
						if (nextReferTo.isAssignableFrom(referTo))
							continue;
					}
				}
				result = result.append(' ');
				result = result.append(breadcrumb.name());
				if (option != null) {
					result = result.append('(');
					if (option instanceof Class)
						result = result.append(((Class<?>) option).getName());
					else
						result = result.append(option);
					result = result.append(')');
				}
			}
		} while ((state = stack.pollFirst()) != null);

		return result.toString();
	}

}
