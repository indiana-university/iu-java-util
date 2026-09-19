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

import java.beans.Introspector;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuJsonSerializationOptions;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

/**
 * Converts from a JavaBeans business object to JSON.
 */
public final class JsonSerializer {
	static {
		IuObject.assertNotOpen(JsonSerializer.class);
	}

	private JsonSerializer() {
	}

	/**
	 * Name of the property that holds {@link Enum#name()} when an enum value
	 * converts to a {@link JsonObject}.
	 */
	static final String NAME = "name";

	/**
	 * Formats a property name for JSON serialization.
	 * 
	 * @param propertyName       property name
	 * @param propertyNameFormat format
	 * @return formatted property name
	 */
	static String formatPropertyName(String propertyName, IuJsonPropertyNameFormat propertyNameFormat) {
		switch (propertyNameFormat) {
		case LOWER_CASE_WITH_UNDERSCORES:
			return JsonProxy.convertToSnakeCase(propertyName);

		case UPPER_CASE_WITH_UNDERSCORES:
			return JsonProxy.convertToSnakeCase(propertyName).toUpperCase();

		default:
		case IDENTITY:
			return propertyName;
		}
	}

	/**
	 * Serializes a business object as JSON, with default options apart from the
	 * property name format.
	 *
	 * @param <T>                value type
	 * @param type               value type for introspection
	 * @param value              business object to serialize
	 * @param propertyNameFormat property name format
	 * @param adapt              adapter function
	 * @return {@link JsonObject}
	 * @see #serialize(Class, Object, Supplier, Function)
	 */
	public static <T> JsonObject serialize(Class<T> type, T value, IuJsonPropertyNameFormat propertyNameFormat,
			Function<Type, IuJsonAdapter<?>> adapt) {
		final var options = IuJsonSerializationOptions.of(propertyNameFormat);
		return serialize(type, value, () -> options, adapt);
	}

	/**
	 * Serializes a business object as JSON.
	 *
	 * <p>
	 * Includes an entry for each readable JavaBeans property of {@code type},
	 * including properties inherited from superclasses and declared as interface
	 * default methods. Properties declared by {@link Object}, in particular
	 * {@link Object#getClass() class}, are skipped. A property with a null value is
	 * skipped unless
	 * {@link IuJsonSerializationOptions#isIncludeNullProperties()}. When more than
	 * one declaration maps to the same formatted property name, the declaration
	 * nearest {@code type} wins.
	 * </p>
	 *
	 * <p>
	 * A value wrapped by {@link IuJson#wrap(JsonObject, Class)} is returned as its
	 * source {@link JsonObject}, without introspection, so no option applies to it.
	 * This preserves properties the wrapped interface doesn't declare, as when a
	 * value is handled through a stub.
	 * </p>
	 *
	 * <p>
	 * One options snapshot is read for each invocation, so an adapter that captured
	 * {@code options} observes a configuration change without being recreated. A
	 * supplier that answers null, or an option that answers null, reads as the
	 * default for this invocation.
	 * </p>
	 *
	 * @param <T>     value type
	 * @param type    value type for introspection
	 * @param value   business object to serialize
	 * @param options supplies the options in effect; <em>should</em> return
	 *                quickly, as it is called on every invocation
	 * @param adapt   adapter function
	 * @return {@link JsonObject}
	 */
	public static <T> JsonObject serialize(Class<T> type, T value, Supplier<IuJsonSerializationOptions> options,
			Function<Type, IuJsonAdapter<?>> adapt) {

		final var valueClass = value.getClass();
		if (Proxy.isProxyClass(valueClass)) {
			final var invocationHandler = Proxy.getInvocationHandler(value);
			if (invocationHandler instanceof JsonProxy)
				return JsonProxy.unwrap(value);
		}

		// one snapshot per invocation, so an options change takes effect without
		// recreating the adapters that captured the supplier
		final var snapshot = snapshot(options);

		final var builder = IuJson.object();
		addProperties(type, value, propertyNameFormat(snapshot), snapshot.isIncludeNullProperties(), adapt, builder,
				new HashSet<>());

		return builder.build();
	}

	/**
	 * Serializes an enum value as JSON.
	 *
	 * <p>
	 * Returns a {@link jakarta.json.JsonString JsonString} holding
	 * {@link Enum#toString()} unless
	 * {@link IuJsonSerializationOptions#isEnumAsObject()}, in which case the value
	 * converts to a {@link JsonObject} holding the {@link #NAME} property, with
	 * {@link Enum#name()}, followed by the readable JavaBeans properties of
	 * {@code type}. The {@link #NAME} property comes first either way; an enum
	 * that declares its own answers the value, which is only sound when that
	 * property answers the constant name.
	 * </p>
	 *
	 * <p>
	 * Introspection uses {@code type} rather than the value's class, so a constant
	 * declared with a class body converts to the same shape as every other
	 * constant of the enum, while a property it overrides answers the override.
	 * </p>
	 *
	 * @param type    enum type for introspection
	 * @param value   enum value to serialize
	 * @param options supplies the options in effect; <em>should</em> return
	 *                quickly, as it is called on every invocation
	 * @param adapt   adapter function
	 * @return {@link JsonValue}
	 */
	static JsonValue serializeEnum(Class<?> type, Enum<?> value, Supplier<IuJsonSerializationOptions> options,
			Function<Type, IuJsonAdapter<?>> adapt) {

		// one snapshot per invocation, as in serialize(Class, Object, Supplier,
		// Function)
		final var snapshot = snapshot(options);
		if (!snapshot.isEnumAsObject())
			return IuJson.string(value.toString());

		final var propertyNameFormat = propertyNameFormat(snapshot);
		final var nameProperty = formatPropertyName(NAME, propertyNameFormat);

		final var properties = IuJson.object();
		addProperties(type, value, propertyNameFormat, snapshot.isIncludeNullProperties(), adapt, properties,
				new HashSet<>());
		final var declared = properties.build();

		// the name property comes first whether or not the enum declares one of its
		// own; a builder rejects a duplicate key, so the value is chosen up front
		final var builder = IuJson.object();
		if (declared.containsKey(nameProperty))
			builder.add(nameProperty, declared.get(nameProperty));
		else
			builder.add(nameProperty, IuJson.string(value.name()));

		for (final var declaredProperty : declared.entrySet())
			if (!nameProperty.equals(declaredProperty.getKey()))
				builder.add(declaredProperty.getKey(), declaredProperty.getValue());

		return builder.build();
	}

	/**
	 * Reads the options in effect for one conversion.
	 *
	 * @param options options supplier
	 * @return {@link IuJsonSerializationOptions}; a supplier that answers null
	 *         reads as {@link IuJsonSerializationOptions#DEFAULT}
	 */
	private static IuJsonSerializationOptions snapshot(Supplier<IuJsonSerializationOptions> options) {
		return Objects.requireNonNullElse(options.get(), IuJsonSerializationOptions.DEFAULT);
	}

	/**
	 * Reads the property name format from an options snapshot.
	 *
	 * @param snapshot options snapshot
	 * @return {@link IuJsonPropertyNameFormat}; a snapshot that answers null reads
	 *         as {@link IuJsonSerializationOptions#PROPERTY_NAME_FORMAT}
	 */
	private static IuJsonPropertyNameFormat propertyNameFormat(IuJsonSerializationOptions snapshot) {
		return Objects.requireNonNullElse(snapshot.getPropertyNameFormat(),
				IuJsonSerializationOptions.PROPERTY_NAME_FORMAT);
	}

	/**
	 * Adds an entry for each readable JavaBeans property of a type, walking its
	 * superclasses and interfaces.
	 *
	 * @param type                  value type for introspection
	 * @param value                 value to read properties from
	 * @param propertyNameFormat    property name format
	 * @param includeNullProperties true to include a property with a null value;
	 *                              false to omit it
	 * @param adapt                 adapter function
	 * @param builder               receives one entry per property; a name already
	 *                              present is replaced rather than skipped, since
	 *                              only names added by this walk are tracked
	 * @param seen                  property names already added by this walk; a
	 *                              repeated name is skipped, so the declaration
	 *                              nearest {@code type} wins
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void addProperties(Class<?> type, Object value, IuJsonPropertyNameFormat propertyNameFormat,
			boolean includeNullProperties, Function<Type, IuJsonAdapter<?>> adapt, JsonObjectBuilder builder,
			Set<String> seen) {

		final Deque<Class<?>> todo = new ArrayDeque<>();
		todo.push(type);
		while (!todo.isEmpty()) {
			final var next = todo.pop();
			for (final var propertyDescriptor : IuException.unchecked(() -> Introspector.getBeanInfo(next))
					.getPropertyDescriptors()) {
				final var readMethod = propertyDescriptor.getReadMethod();
				if (readMethod == null //
						|| readMethod.getDeclaringClass() == Object.class //
						|| readMethod.getDeclaringClass() == Enum.class)
					continue;

				final var propertyName = formatPropertyName(propertyDescriptor.getName(), propertyNameFormat);
				if (!seen.add(propertyName))
					continue;

				final var propertyValue = IuException.uncheckedInvocation(() -> readMethod.invoke(value));
				final var adapter = adapt.apply(readMethod.getGenericReturnType());
				if (propertyValue == null && includeNullProperties)
					builder.addNull(propertyName);
				else
					IuJson.add(builder, propertyName, () -> propertyValue, (IuJsonAdapter) adapter);
			}

			for (final var i : next.getInterfaces())
				todo.push(i);

			final var parent = next.getSuperclass();
			if (parent != null && !IuObject.isPlatformName(parent.getName()))
				todo.push(parent);
		}
	}

}
