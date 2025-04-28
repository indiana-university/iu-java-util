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
package iu.client;

import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Function;

import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;

/**
 * Thin invocation handler for wrapping JSON objects in a Java interface.
 */
public final class JsonProxy implements InvocationHandler {
	static {
		IuObject.assertNotOpen(JsonProxy.class);
	}

	/**
	 * Wraps a JSON object in a java interface.
	 * 
	 * @param <T>             target interface type
	 * @param value           value
	 * @param targetInterface target interface class
	 * @param valueAdapter    transform function: receives a {@link JsonValue} and
	 *                        method return type, if custom handling returns an
	 *                        object other than the original {@link JsonValue value}
	 * @return {@link JsonProxy}
	 */
	public static <T> T wrap(JsonObject value, Class<T> targetInterface,
			Function<Type, IuJsonAdapter<?>> valueAdapter) {
		JsonProxy.class.getModule().addReads(targetInterface.getModule());

		return targetInterface.cast(Proxy.newProxyInstance(targetInterface.getClassLoader(),
				new Class<?>[] { targetInterface }, new JsonProxy(value, valueAdapter)));
	}

	/**
	 * Retrieves the {@link JsonObject} from a {@link JsonProxy} wrapper.
	 * 
	 * @param jsonProxy wrapper
	 * @return {@link JsonObject}
	 */
	public static JsonObject unwrap(Object jsonProxy) {
		return ((JsonProxy) Proxy.getInvocationHandler(jsonProxy)).value;
	}

	private final JsonObject value;
	private final Function<Type, IuJsonAdapter<?>> valueAdapter;

	private JsonProxy(JsonObject value, Function<Type, IuJsonAdapter<?>> valueAdapter) {
		this.value = value;
		this.valueAdapter = valueAdapter;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		final var methodName = method.getName();
		if (methodName.equals("hashCode"))
			return value.hashCode();

		if (methodName.equals("equals")) {
			if (args[0] == null)
				return false;
			if (!Proxy.isProxyClass(args[0].getClass()))
				return false;

			final var other = Proxy.getInvocationHandler(args[0]);
			if (!(other instanceof JsonProxy))
				return false;
			return ((JsonProxy) other).value.equals(value);
		}

		if (methodName.equals("toString")) {
			final var writer = new StringWriter();
			IuJson.PROVIDER.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true)).createWriter(writer)
					.write(value);
			return writer.toString();
		}

		final String propertyName;
		if (methodName.startsWith("get"))
			propertyName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
		else if (methodName.startsWith("is"))
			propertyName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
		else if (method.isDefault()) {
			propertyName = null;
		} else
			throw new UnsupportedOperationException();

		final JsonValue rv = returnValueWithCaseConversion(propertyName);

		if (rv == null && method.isDefault()) {
			final var type = proxy.getClass().getInterfaces()[0];
			return MethodHandles.privateLookupIn(type, MethodHandles.lookup()).unreflectSpecial(method, type)
					.bindTo(proxy).invokeWithArguments(args);
		}

		final var genericReturnType = method.getGenericReturnType();
		try {
			return valueAdapter.apply(genericReturnType).fromJson(rv);
		} catch (UnsupportedOperationException e) {
			if (rv == null)
				return null;
			else
				throw e;
		} catch (Throwable e) {
			throw new IllegalArgumentException(
					"Invalid JSON value for return type " + genericReturnType + " in property " + propertyName, e);
		}
	}

	private JsonValue returnValueWithCaseConversion(String propertyName) {
		if (propertyName == null)
			return null;

		if (value.containsKey(propertyName))
			return value.get(propertyName);

		String lowerSnakeCasePropertyName = convertToSnakeCase(propertyName).toLowerCase();
		if (value.containsKey(lowerSnakeCasePropertyName))
			return value.get(lowerSnakeCasePropertyName);

		return value.get(lowerSnakeCasePropertyName.toUpperCase());
	}

	/**
	 * Converts a camel case property name to
	 * {@link IuJsonPropertyNameFormat#LOWER_CASE_WITH_UNDERSCORES}.
	 * 
	 * @param camelCase property name
	 * @return snake case property name
	 */
	static String convertToSnakeCase(String camelCase) {
		final var sb = new StringBuilder(camelCase);
		for (var i = 0; i < sb.length(); i++) {
			final var c = sb.charAt(i);
			if (Character.isUpperCase(c)) {
				sb.setCharAt(i, Character.toLowerCase(c));
				sb.insert(i, '_');
			}
		}
		return sb.toString();
	}

}
