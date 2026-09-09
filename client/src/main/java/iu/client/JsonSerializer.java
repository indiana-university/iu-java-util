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
	@SuppressWarnings({ "unchecked", "rawtypes" })
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
		final var snapshot = Objects.requireNonNullElse(options.get(), IuJsonSerializationOptions.DEFAULT);
		final var propertyNameFormat = Objects.requireNonNullElse(snapshot.getPropertyNameFormat(),
				IuJsonSerializationOptions.PROPERTY_NAME_FORMAT);
		final var includeNullProperties = snapshot.isIncludeNullProperties();

		final var builder = IuJson.object();

		final Deque<Class<?>> todo = new ArrayDeque<>();
		final Set<String> seen = new HashSet<>();
		todo.push(type);
		while (!todo.isEmpty()) {
			final var next = todo.pop();
			for (final var propertyDescriptor : IuException.unchecked(() -> Introspector.getBeanInfo(next))
					.getPropertyDescriptors()) {
				final var readMethod = propertyDescriptor.getReadMethod();
				if (readMethod == null || readMethod.getDeclaringClass() == Object.class)
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

		return builder.build();
	}

}
