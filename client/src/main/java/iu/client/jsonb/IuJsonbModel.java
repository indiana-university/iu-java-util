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
package iu.client.jsonb;

import java.beans.Introspector;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.client.IuJsonPropertyNameFormat;
import iu.client.JsonSerializer;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbVisibility;
import jakarta.json.bind.config.PropertyOrderStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * Property metadata for a business object type, introspected once and shared by
 * every conversion of that type.
 */
final class IuJsonbModel {

	private static final IuJsonPropertyNameFormat[] FORMATS = IuJsonPropertyNameFormat.values();

	/**
	 * JSON-B default visibility: public fields and public accessor methods.
	 */
	private static final PropertyVisibilityStrategy DEFAULT_VISIBILITY = new PropertyVisibilityStrategy() {
		@Override
		public boolean isVisible(Field field) {
			return Modifier.isPublic(field.getModifiers());
		}

		@Override
		public boolean isVisible(Method method) {
			return Modifier.isPublic(method.getModifiers());
		}
	};

	/**
	 * One logical property, merged from the nearest visible field, getter, and
	 * setter sharing its name. A visible getter is preferred over a visible field
	 * for reading, and a visible setter over a visible non-final field for
	 * writing.
	 */
	static final class Property {
		private final IuJsonb jsonb;
		private final String name;
		private final int discovered;
		private final Map<IuJsonPropertyNameFormat, String> jsonNames = new EnumMap<>(IuJsonPropertyNameFormat.class);
		private Method getter;
		private Field readField;
		private Method setter;
		private Field writeField;
		private volatile IuJsonbValueAdapter<Object> readAdapter;
		private volatile IuJsonbValueAdapter<Object> writeAdapter;

		private Property(String name, int discovered, IuJsonb jsonb) {
			this.jsonb = jsonb;
			this.name = name;
			this.discovered = discovered;
			for (final var format : FORMATS)
				jsonNames.put(format, JsonSerializer.formatPropertyName(name, format));
		}

		/**
		 * Gets the JSON property name.
		 *
		 * @param format property name format
		 * @return Java property name, formatted
		 */
		String jsonName(IuJsonPropertyNameFormat format) {
			return jsonNames.get(format);
		}

		private boolean isReadable() {
			return getter != null || readField != null;
		}

		private boolean isWritable() {
			return setter != null || writeField != null;
		}

		private Object get(Object bean) {
			if (getter != null)
				return IuException.uncheckedInvocation(() -> getter.invoke(bean));
			else
				return IuException.unchecked(() -> readField.get(bean));
		}

		private void set(Object bean, Object value) {
			if (setter != null)
				IuException.uncheckedInvocation(() -> setter.invoke(bean, value));
			else
				IuException.unchecked(() -> writeField.set(bean, value));
		}

		private IuJsonbValueAdapter<Object> readAdapter() {
			var adapter = readAdapter;
			if (adapter == null)
				readAdapter = adapter = jsonb.adapt(getter != null //
						? getter.getGenericReturnType() //
						: readField.getGenericType());
			return adapter;
		}

		private IuJsonbValueAdapter<Object> writeAdapter() {
			var adapter = writeAdapter;
			if (adapter == null)
				writeAdapter = adapter = jsonb.adapt(setter != null //
						? setter.getGenericParameterTypes()[0] //
						: writeField.getGenericType());
			return adapter;
		}

		/**
		 * Adds this property of a bean to an object builder.
		 *
		 * <p>
		 * A null value converts like any other when the context includes null
		 * properties. Otherwise only a configured
		 * {@link jakarta.json.bind.adapter.JsonbAdapter} is consulted, and the
		 * property is omitted unless it adapts the null to a value.
		 * </p>
		 *
		 * @param bean    bean
		 * @param builder object builder
		 * @param context call in progress
		 * @return true if added; false if omitted
		 */
		boolean add(Object bean, JsonObjectBuilder builder, IuSerializationContext context) {
			final var name = jsonName(context.format());
			context.push(name);
			try {
				final var value = get(bean);
				final var adapter = readAdapter();
				if (value != null || context.isIncludeNullProperties()) {
					builder.add(name, adapter.toJson(value));
					return true;
				}

				final var adapted = adapter.nullProperty(context, List.of());
				if (adapted == null)
					return false;
				builder.add(name, adapted);
				return true;
			} catch (RuntimeException e) {
				throw context.fail(e);
			} finally {
				context.pop();
			}
		}

		/**
		 * Writes this property of a bean to a generator, with null handling as in
		 * {@link #add(Object, JsonObjectBuilder, IuSerializationContext)}.
		 *
		 * @param bean      bean
		 * @param generator generator, in an object context
		 * @param context   call in progress
		 * @return true if written; false if omitted
		 */
		boolean write(Object bean, JsonGenerator generator, IuSerializationContext context) {
			final var name = jsonName(context.format());
			context.push(name);
			try {
				final var value = get(bean);
				final var adapter = readAdapter();
				if (value != null || context.isIncludeNullProperties()) {
					generator.writeKey(name);
					adapter.write(value, generator);
					return true;
				} else
					return adapter.writeNullProperty(name, generator, context, List.of());
			} catch (RuntimeException e) {
				throw context.fail(e);
			} finally {
				context.pop();
			}
		}

		/**
		 * Sets this property of a bean from a JSON value.
		 *
		 * @param bean    bean
		 * @param key     JSON property name, for the path
		 * @param value   JSON value
		 * @param context call in progress
		 */
		void fromJson(Object bean, String key, JsonValue value, IuDeserializationContext context) {
			context.push(key);
			try {
				set(bean, writeAdapter().fromJson(value));
			} catch (RuntimeException e) {
				throw context.fail(e);
			} finally {
				context.pop();
			}
		}

		/**
		 * Sets this property of a bean from a parser.
		 *
		 * @param bean    bean
		 * @param key     JSON property name, for the path
		 * @param parser  parser, positioned at the value's first event
		 * @param context call in progress
		 */
		void read(Object bean, String key, JsonParser parser, IuDeserializationContext context) {
			context.push(key);
			try {
				set(bean, writeAdapter().read(parser));
			} catch (RuntimeException e) {
				throw context.fail(e);
			} finally {
				context.pop();
			}
		}
	}

	private final Class<?> type;
	private final Map<IuJsonPropertyNameFormat, Property[]> readable = new EnumMap<>(IuJsonPropertyNameFormat.class);
	private final Map<IuJsonPropertyNameFormat, Map<String, Property>> writable = new EnumMap<>(
			IuJsonPropertyNameFormat.class);
	private volatile Constructor<?> constructor;

	/**
	 * Introspects a business object type.
	 *
	 * @param type  business object type
	 * @param jsonb provider, for its visibility and order strategies and
	 *              property adapters
	 */
	IuJsonbModel(Class<?> type, IuJsonb jsonb) {
		this.type = type;

		// discovery order: nearest declaration first, so the first field, getter, or
		// setter found for a name wins
		final Map<String, Property> properties = new LinkedHashMap<>();
		final Map<Class<?>, PropertyVisibilityStrategy> strategies = new HashMap<>();
		final Set<String> listed = new LinkedHashSet<>();

		final Deque<Class<?>> todo = new ArrayDeque<>();
		final Set<Class<?>> done = new HashSet<>();
		todo.push(type);
		while (!todo.isEmpty()) {
			final var next = todo.pop();
			if (!done.add(next))
				continue;

			final var propertyOrder = next.getAnnotation(JsonbPropertyOrder.class);
			if (propertyOrder != null)
				listed.addAll(Arrays.asList(propertyOrder.value()));

			for (final var field : next.getDeclaredFields()) {
				final var modifiers = field.getModifiers();
				if (Modifier.isStatic(modifiers) //
						|| Modifier.isTransient(modifiers) //
						|| field.isSynthetic() //
						|| !visibility(next, strategies, jsonb).isVisible(field))
					continue;

				accessible(field);
				final var property = property(field.getName(), properties, jsonb);
				if (property.readField == null)
					property.readField = field;
				if (property.writeField == null && !Modifier.isFinal(modifiers))
					property.writeField = field;
			}

			for (final var descriptor : IuException.unchecked(() -> Introspector.getBeanInfo(next))
					.getPropertyDescriptors()) {
				final var getter = descriptor.getReadMethod();
				if (isAccessor(getter, strategies, jsonb)) {
					final var property = property(descriptor.getName(), properties, jsonb);
					if (property.getter == null)
						property.getter = getter;
				}

				final var setter = descriptor.getWriteMethod();
				if (isAccessor(setter, strategies, jsonb)) {
					final var property = property(descriptor.getName(), properties, jsonb);
					if (property.setter == null)
						property.setter = setter;
				}
			}

			for (final var i : next.getInterfaces())
				todo.push(i);

			final var parent = next.getSuperclass();
			if (parent != null && !IuObject.isPlatformName(parent.getName()))
				todo.push(parent);
		}

		// IuJsonb accepts only the three strategies
		final var propertyOrderStrategy = jsonb.propertyOrderStrategy();
		final Comparator<Property> strategyOrder;
		if (PropertyOrderStrategy.ANY.equals(propertyOrderStrategy))
			strategyOrder = Comparator.comparingInt(p -> p.discovered);
		else if (PropertyOrderStrategy.REVERSE.equals(propertyOrderStrategy))
			strategyOrder = Comparator.comparing((Property p) -> p.name).reversed();
		else // LEXICOGRAPHICAL
			strategyOrder = Comparator.comparing(p -> p.name);

		// listed properties first, in listed order, then the rest by strategy
		final Map<String, Integer> rank = new HashMap<>();
		for (final var name : listed)
			rank.putIfAbsent(name, rank.size());
		final Comparator<Property> order = (a, b) -> {
			final var i = rank.get(a.name);
			final var j = rank.get(b.name);
			if (i != null)
				return j != null ? Integer.compare(i, j) : -1;
			else if (j != null)
				return 1;
			else
				return strategyOrder.compare(a, b);
		};
		final var sorted = properties.values().stream().sorted(order).toArray(Property[]::new);

		// names that collide after formatting resolve to the nearest declaration
		for (final var format : FORMATS) {
			final Map<String, Property> readByName = new HashMap<>();
			final Map<String, Property> writeByName = new HashMap<>();
			for (final var property : properties.values()) {
				final var jsonName = property.jsonName(format);
				if (property.isReadable())
					readByName.putIfAbsent(jsonName, property);
				if (property.isWritable())
					writeByName.putIfAbsent(jsonName, property);
			}

			readable.put(format, Stream.of(sorted) //
					.filter(p -> readByName.get(p.jsonName(format)) == p) //
					.toArray(Property[]::new));
			writable.put(format, Map.copyOf(writeByName));
		}
	}

	/**
	 * Gets readable properties in serialization order.
	 *
	 * @param format property name format
	 * @return readable properties
	 */
	Property[] readable(IuJsonPropertyNameFormat format) {
		return readable.get(format);
	}

	/**
	 * Gets a writable property by JSON name.
	 *
	 * @param format   property name format
	 * @param jsonName formatted property name
	 * @return writable property; null if not defined
	 */
	Property writable(IuJsonPropertyNameFormat format, String jsonName) {
		return writable.get(format).get(jsonName);
	}

	/**
	 * Creates a new instance using the no-arg constructor.
	 *
	 * @return new instance
	 */
	Object newInstance() {
		var constructor = this.constructor;
		if (constructor == null) {
			try {
				constructor = accessible(type.getDeclaredConstructor());
			} catch (NoSuchMethodException e) {
				throw new JsonbException("no default constructor for " + type.getName(), e);
			}
			this.constructor = constructor;
		}

		final var c = constructor;
		return IuException.uncheckedInvocation(() -> c.newInstance());
	}

	private static Property property(String name, Map<String, Property> properties, IuJsonb jsonb) {
		var property = properties.get(name);
		if (property == null)
			properties.put(name, property = new Property(name, properties.size(), jsonb));
		return property;
	}

	private static boolean isAccessor(Method method, Map<Class<?>, PropertyVisibilityStrategy> strategies,
			IuJsonb jsonb) {
		if (method == null)
			return false;

		final var declaringClass = method.getDeclaringClass();
		return declaringClass != Object.class //
				&& declaringClass != Enum.class //
				&& visibility(declaringClass, strategies, jsonb).isVisible(method);
	}

	/**
	 * Resolves the visibility strategy for members of a class: its own
	 * {@link JsonbVisibility}, then its package's, then the configured strategy,
	 * then the JSON-B default.
	 */
	private static PropertyVisibilityStrategy visibility(Class<?> declaringClass,
			Map<Class<?>, PropertyVisibilityStrategy> strategies, IuJsonb jsonb) {
		return strategies.computeIfAbsent(declaringClass, c -> {
			var annotation = c.getAnnotation(JsonbVisibility.class);
			if (annotation == null)
				annotation = c.getPackage().getAnnotation(JsonbVisibility.class);

			if (annotation != null) {
				final var strategyClass = annotation.value();
				return IuException.uncheckedInvocation(() -> strategyClass.getConstructor().newInstance());
			} else
				return Objects.requireNonNullElse(jsonb.propertyVisibilityStrategy(), DEFAULT_VISIBILITY);
		});
	}

	/**
	 * Suppresses access checks on a non-public field or constructor where the
	 * declaring module permits it; where it doesn't, access fails when the member
	 * is used, naming the module and package to open. Accessor methods are used
	 * as-is.
	 */
	private static <A extends AccessibleObject & Member> A accessible(A member) {
		if (!Modifier.isPublic(member.getModifiers()))
			member.trySetAccessible();
		return member;
	}

}
