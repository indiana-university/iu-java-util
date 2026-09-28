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
import java.util.function.Supplier;

import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonProperties;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuJsonSerializationOptions;
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
		final var options = IuJsonSerializationOptions.of(propertyNameFormat);
		final var metadata = BindingMetadata.get();
		return wrap(properties, targetInterface, name -> JsonSerializer.formatPropertyName(name, propertyNameFormat),
				false, metadata, declared(metadata, () -> options));
	}

	/**
	 * Gets the conversion a getter declares.
	 */
	@FunctionalInterface
	public interface Declared {
		/**
		 * Gets the conversion a getter declares.
		 *
		 * @param type    return type
		 * @param date    date format the getter, its interface, or its package
		 *                declares; null if none
		 * @param number  number format the getter, its interface, or its package
		 *                declares; null if none
		 * @param members the getter
		 * @return conversion; null if the getter declares none
		 */
		IuJsonAdapter<?> adapt(Type type, BindingMetadata.Format date, BindingMetadata.Format number,
				AnnotatedElement[] members);
	}

	/**
	 * Gets the conversions getters declare for the IU conversions: by the JSON-B
	 * components a getter declares, else by a date or number format.
	 *
	 * @param metadata binding annotations
	 * @param options  supplies the options in effect for each conversion
	 * @return {@link Declared}
	 */
	static Declared declared(BindingMetadata metadata, Supplier<IuJsonSerializationOptions> options) {
		return (type, date, number, members) -> {
			final var components = metadata.components(type, date, number, members, options);
			return components == null ? FormatAdapters.declared(type, date, number, null, null, false) : components;
		};
	}

	/**
	 * Wraps indexed properties in a java interface, naming properties by a
	 * function, converting a getter that declares a conversion by it.
	 *
	 * @param <T>             target interface type
	 * @param properties      properties
	 * @param targetInterface target interface class
	 * @param naming          gets the JSON name of a Java property name
	 * @param ignoreCase      true to match a JSON name that differs only in case
	 *                        when none matches exactly
	 * @param declared        gets the conversion a getter declares
	 * @return {@link JsonProxy}
	 */
	public static <T> T wrap(IuJsonProperties properties, Class<T> targetInterface, Function<String, String> naming,
			boolean ignoreCase, Declared declared) {
		return wrap(properties, targetInterface, naming, ignoreCase, BindingMetadata.get(), declared);
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
	 * @param declared        gets the conversion a getter declares
	 * @return {@link JsonProxy}
	 */
	static <T> T wrap(IuJsonProperties properties, Class<T> targetInterface, Function<String, String> naming,
			boolean ignoreCase, BindingMetadata metadata, Declared declared) {
		// type information picks the subtype interface
		final var model = metadata == BindingMetadata.NONE ? BeanModel.legacy(targetInterface)
				: BeanModel.of(targetInterface);
		final var dispatch = model.dispatch();
		if (dispatch != null && properties.containsKey(dispatch.key())) {
			final var subtype = model.subtype(properties.get(dispatch.key(), String.class));
			if (subtype != targetInterface) {
				if (!subtype.isInterface())
					throw new IllegalArgumentException("alias " + properties.get(dispatch.key(), String.class)
							+ " names " + subtype.getName() + ", which isn't an interface to wrap");
				return targetInterface
						.cast(wrap(properties, subtype, naming, ignoreCase, metadata, declared));
			}
		}

		JsonProxy.class.getModule().addReads(targetInterface.getModule());

		return targetInterface.cast(Proxy.newProxyInstance(targetInterface.getClassLoader(),
				new Class<?>[] { targetInterface }, new JsonProxy(properties, naming, ignoreCase, metadata, declared)));
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
	private final Declared declared;
	private final Map<String, Object> resolved = new ConcurrentHashMap<>();

	private JsonProxy(IuJsonProperties properties, Function<String, String> naming, boolean ignoreCase,
			BindingMetadata metadata, Declared declared) {
		this.properties = properties;
		this.naming = naming;
		this.ignoreCase = ignoreCase;
		this.metadata = metadata;
		this.declared = declared;
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
		// a declared conversion reads the raw value
		final var declaredAdapter = declared.adapt(genericReturnType, //
				format(metadata::dateFormat, method), format(metadata::numberFormat, method),
				new AnnotatedElement[] { method });
		try {
			if (declaredAdapter != null)
				return checkResolvedValue(methodName,
						declaredAdapter.fromJson(properties.get(jsonName, JsonValue.class)));
			return checkResolvedValue(methodName, properties.get(jsonName, genericReturnType));
		} catch (UnsupportedOperationException e) {
			// a type with no conversion, whatever the value; an absent property
			// converts nothing, so reads as null or the type's empty value
			throw e;
		} catch (Throwable e) {
			throw new IllegalArgumentException(
					"Invalid JSON value for return type " + genericReturnType + " in property " + propertyName, e);
		}
	}

	/**
	 * Gets the format a getter declares, or its interface or package does.
	 */
	private static BindingMetadata.Format format(Function<AnnotatedElement, BindingMetadata.Format> declared,
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
