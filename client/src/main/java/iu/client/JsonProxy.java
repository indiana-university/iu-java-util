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
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonProperties;
import edu.iu.client.IuJsonPropertyNameFormat;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;

/**
 * Thin invocation handler for wrapping JSON objects in a Java interface.
 *
 * <p>
 * Backed by {@link IuJsonProperties}, which converts each property only when
 * its getter is first called, and keeps the result. The JSON object a proxy
 * {@link #unwrap(Object) unwraps} to is the one it wraps, or, for a proxy of
 * Java values, one generated once when first needed.
 * </p>
 */
public final class JsonProxy implements InvocationHandler {
	static {
		IuObject.assertNotOpen(JsonProxy.class);
	}

	private static final Object NULL = new Object();

	/**
	 * Wraps a JSON object in a java interface, reading property names in a
	 * specific format.
	 *
	 * @param <T>                target interface type
	 * @param value              value
	 * @param targetInterface    target interface class
	 * @param propertyNameFormat format of the property names in {@code value}; a
	 *                           getter reads only the name formatted this way
	 * @param valueAdapter       transform function: receives a
	 *                           {@link jakarta.json.JsonValue} and method return
	 *                           type, if custom handling returns an object other
	 *                           than the original {@link jakarta.json.JsonValue
	 *                           value}
	 * @return {@link JsonProxy}
	 */
	public static <T> T wrap(JsonObject value, Class<T> targetInterface, IuJsonPropertyNameFormat propertyNameFormat,
			Function<Type, IuJsonAdapter<?>> valueAdapter) {
		return wrap(IuJsonProperties.of(value, valueAdapter), targetInterface, propertyNameFormat);
	}

	/**
	 * Wraps indexed properties in a java interface, reading property names in a
	 * specific format.
	 *
	 * @param <T>                target interface type
	 * @param properties         properties
	 * @param targetInterface    target interface class
	 * @param propertyNameFormat format of the property names in
	 *                           {@code properties}; a getter reads only the name
	 *                           formatted this way
	 * @return {@link JsonProxy}
	 */
	public static <T> T wrap(IuJsonProperties properties, Class<T> targetInterface,
			IuJsonPropertyNameFormat propertyNameFormat) {
		return wrap(properties, targetInterface, name -> JsonSerializer.formatPropertyName(name, propertyNameFormat),
				false);
	}

	/**
	 * Wraps indexed properties in a java interface, naming properties by a
	 * function.
	 *
	 * @param <T>             target interface type
	 * @param properties      properties
	 * @param targetInterface target interface class
	 * @param naming          gets the JSON name of a Java property name
	 * @param ignoreCase      true to match a JSON name that differs only in case
	 *                        when none matches exactly
	 * @return {@link JsonProxy}
	 */
	public static <T> T wrap(IuJsonProperties properties, Class<T> targetInterface, Function<String, String> naming,
			boolean ignoreCase) {
		return wrap(properties, targetInterface, naming, ignoreCase, BindingMetadata.get());
	}

	/**
	 * Wraps indexed properties in a java interface, naming properties by a
	 * function, and reading binding annotations on its getters from metadata.
	 *
	 * @param <T>             target interface type
	 * @param properties      properties
	 * @param targetInterface target interface class
	 * @param naming          gets the JSON name of a Java property name
	 * @param ignoreCase      true to match a JSON name that differs only in case
	 *                        when none matches exactly
	 * @param metadata        binding annotations: a getter that declares its JSON
	 *                        name reads that name, and a transient getter reads
	 *                        as absent
	 * @return {@link JsonProxy}
	 */
	static <T> T wrap(IuJsonProperties properties, Class<T> targetInterface, Function<String, String> naming,
			boolean ignoreCase, BindingMetadata metadata) {
		JsonProxy.class.getModule().addReads(targetInterface.getModule());

		return targetInterface.cast(Proxy.newProxyInstance(targetInterface.getClassLoader(),
				new Class<?>[] { targetInterface }, new JsonProxy(properties, naming, ignoreCase, metadata)));
	}

	/**
	 * Retrieves the {@link JsonObject} from a {@link JsonProxy} wrapper.
	 * 
	 * @param jsonProxy wrapper
	 * @return {@link JsonObject}
	 */
	public static JsonObject unwrap(Object jsonProxy) {
		return properties(jsonProxy).toJsonObject();
	}

	/**
	 * Retrieves the properties a {@link JsonProxy} wrapper reads.
	 * 
	 * @param jsonProxy wrapper
	 * @return {@link IuJsonProperties}
	 */
	public static IuJsonProperties properties(Object jsonProxy) {
		return ((JsonProxy) Proxy.getInvocationHandler(jsonProxy)).properties;
	}

	private final IuJsonProperties properties;
	private final Function<String, String> naming;
	private final boolean ignoreCase;
	private final BindingMetadata metadata;
	private final Map<String, Object> resolved = new ConcurrentHashMap<>();

	private JsonProxy(IuJsonProperties properties, Function<String, String> naming, boolean ignoreCase,
			BindingMetadata metadata) {
		this.properties = properties;
		this.naming = naming;
		this.ignoreCase = ignoreCase;
		this.metadata = metadata;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		final var methodName = method.getName();
		final var resolved = this.resolved.get(methodName);
		if (resolved != null)
			return resolved == NULL //
					? null //
					: resolved;

		final var paramTypes = method.getParameterTypes();

		if (methodName.equals("equals") //
				&& paramTypes.length == 1 //
				&& paramTypes[0] == Object.class) {
			if (args[0] == null)
				return false;
			if (!Proxy.isProxyClass(args[0].getClass()))
				return false;

			final var other = Proxy.getInvocationHandler(args[0]);
			if (!(other instanceof JsonProxy))
				return false;
			return ((JsonProxy) other).properties.toJsonObject().equals(properties.toJsonObject());
		}

		if (paramTypes.length > 0)
			throw new UnsupportedOperationException();

		if (methodName.equals("hashCode"))
			return properties.toJsonObject().hashCode();

		if (methodName.equals("toString")) {
			final var writer = new StringWriter();
			IuJson.PROVIDER.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true)).createWriter(writer)
					.write(properties.toJsonObject());
			return checkResolvedValue(methodName, writer.toString());
		}

		// named as Introspector names them, so a getter reads the key JsonSerializer
		// writes for it
		final String propertyName;
		if (methodName.startsWith("get"))
			propertyName = Introspector.decapitalize(methodName.substring(3));
		else if (methodName.startsWith("is"))
			propertyName = Introspector.decapitalize(methodName.substring(2));
		else
			throw new UnsupportedOperationException();

		// a transient getter reads as absent; a declared name overrides naming
		final var isTransient = metadata.isTransient(method);
		final var declaredName = metadata.name(method);
		final var jsonName = jsonName(declaredName != null ? declaredName : naming.apply(propertyName));
		final var present = !isTransient && properties.containsKey(jsonName);
		if (!present && method.isDefault()) {
			final var type = proxy.getClass().getInterfaces()[0];
			return checkResolvedValue(methodName, MethodHandles.privateLookupIn(type, MethodHandles.lookup())
					.unreflectSpecial(method, type).bindTo(proxy).invokeWithArguments(args));
		}

		if (isTransient)
			return checkResolvedValue(methodName, null);

		final var genericReturnType = method.getGenericReturnType();
		// a declared date or number format reads the raw value
		final var formatted = FormatAdapters.declared(genericReturnType, //
				declared(metadata::dateFormat, method), declared(metadata::numberFormat, method), null, null, false);
		try {
			if (formatted != null)
				return checkResolvedValue(methodName,
						formatted.fromJson(properties.get(jsonName, JsonValue.class)));
			return checkResolvedValue(methodName, properties.get(jsonName, genericReturnType));
		} catch (UnsupportedOperationException e) {
			if (!present)
				return checkResolvedValue(methodName, null);
			else
				throw e;
		} catch (Throwable e) {
			throw new IllegalArgumentException(
					"Invalid JSON value for return type " + genericReturnType + " in property " + propertyName, e);
		}
	}

	/**
	 * Gets the format a getter declares, or its interface or package does.
	 */
	private static BindingMetadata.Format declared(Function<AnnotatedElement, BindingMetadata.Format> declared,
			Method getter) {
		final var format = declared.apply(getter);
		return format == null ? declared.apply(getter.getDeclaringClass()) : format;
	}

	/**
	 * Gets the JSON name a property reads: its name, or, when matching ignores
	 * case and no name matches exactly, the first that differs only in case.
	 */
	private String jsonName(String jsonName) {
		if (ignoreCase && !properties.containsKey(jsonName))
			for (final var name : properties.names())
				if (name.equalsIgnoreCase(jsonName))
					return name;
		return jsonName;
	}

	private <T> T checkResolvedValue(String methodName, T value) {
		resolved.put(methodName, value == null ? NULL : value);
		return value;
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
