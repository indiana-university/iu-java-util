/*
 * Copyright © 2024 Indiana University
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
package edu.iu.client;

import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;

/**
 * JSON-P processing utilities.
 */
public class IuJson {

	/**
	 * Singleton {@link JsonProvider}.
	 */
	public static final JsonProvider PROVIDER;

	static {
		final var current = Thread.currentThread();
		final var contextToRestore = current.getContextClassLoader();
		try {
			current.setContextClassLoader(JsonProvider.class.getClassLoader());
			PROVIDER = JsonProvider.provider();
		} finally {
			current.setContextClassLoader(contextToRestore);
		}
	}

	/**
	 * Parses a JSON value from an HTTP response.
	 * 
	 * @param serialized raw serialized JSON input stream
	 * @return {@link JsonValue}
	 */
	public static JsonValue parse(HttpResponse<InputStream> serialized) {
		return parse(serialized.body());
	}

	/**
	 * Parses a JSON value from serialized form.
	 * 
	 * @param serialized raw serialized JSON input stream
	 * @return {@link JsonValue}
	 */
	public static JsonValue parse(InputStream serialized) {
		return PROVIDER.createReader(serialized).readValue();
	}

	/**
	 * Parses a JSON value from serialized form.
	 * 
	 * @param serialized raw serialized JSON data, as generated by
	 *                   {@link JsonValue#toString()}
	 * @return {@link JsonValue}
	 */
	public static JsonValue parse(String serialized) {
		return PROVIDER.createReader(new StringReader(serialized)).readValue();
	}

	/**
	 * Converts a non-structural JSON value to its Java equivalent.
	 * 
	 * @param <T>   Java type; <em>must</em> be assignable from {@link String},
	 *              {@link BigDecimal}, or {@link Boolean} if known and a non-null
	 *              value is expected
	 * @param value {@link JsonValue}; <em>must</em> be non-null and not assignable
	 *              to {@link JsonStructure}
	 * @return Java equivalent
	 * @throws IllegalArgumentException If the JSON value is null or assignable to
	 *                                  {@link JsonStructure}.
	 * @throws ClassCastException       If the type argument bounds don't permit
	 *                                  direct conversion to the equivalent Java
	 *                                  value.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T toJava(JsonValue value) throws IllegalArgumentException, ClassCastException {
		if (value instanceof JsonString)
			return (T) ((JsonString) value).getString();
		else if (value instanceof JsonNumber)
			return (T) ((JsonNumber) value).bigDecimalValue();
		else if (JsonValue.TRUE.equals(value))
			return (T) Boolean.TRUE;
		else if (JsonValue.FALSE.equals(value))
			return (T) Boolean.FALSE;
		else if (JsonValue.NULL.equals(value))
			return null;
		else
			throw new IllegalArgumentException();
	}

	/**
	 * Converts a non-structural JSON value to text.
	 * 
	 * <p>
	 * This method can be useful when the JSON type is not known or not reliable
	 * (i.e. Some OAuth servers return expires_in as string instead of number).
	 * </p>
	 * 
	 * <p>
	 * The value returned from this method is not reversible. Use
	 * {@link JsonValue#toString()} to serialize the JSON value.
	 * </p>
	 * 
	 * @param value {@link JsonValue}; <em>must</em> be non-null and not assignable
	 *              to {@link JsonStructure}
	 * @return text representation of the JSON value
	 * @throws IllegalArgumentException If the JSON value is null or assignable to
	 *                                  {@link JsonStructure}.
	 */
	public static String asText(JsonValue value) {
		if (value instanceof JsonString)
			return ((JsonString) value).getString();
		else if ((value instanceof JsonNumber) //
				|| JsonValue.TRUE.equals(value) //
				|| JsonValue.FALSE.equals(value))
			return value.toString();
		else if (JsonValue.NULL.equals(value))
			return null;
		else
			throw new IllegalArgumentException();
	}

	/**
	 * Converts a Java value to its JSON equivalent.
	 * 
	 * @param value {@link String}, {@link Number}, {@link Boolean}, primitive,
	 *              null, {@link JsonValue}, {@link JsonObjectBuilder}, or
	 *              {@link JsonArrayBuilder}.
	 * @return {@link JsonValue}
	 * @throws IllegalArgumentException If value cannot be converted JSON without
	 *                                  first externally applying serialization
	 *                                  logic.
	 */
	public static JsonValue toJson(Object value) throws IllegalArgumentException {
		if (value instanceof JsonValue)
			return (JsonValue) value;
		else if (value instanceof JsonObjectBuilder)
			return ((JsonObjectBuilder) value).build();
		else if (value instanceof JsonArrayBuilder)
			return ((JsonArrayBuilder) value).build();
		else if (value instanceof String)
			return PROVIDER.createValue((String) value);
		else if (value instanceof Number)
			return PROVIDER.createValue((Number) value);
		else if (Boolean.TRUE.equals(value))
			return JsonValue.TRUE;
		else if (Boolean.FALSE.equals(value))
			return JsonValue.FALSE;
		else if (value == null)
			return JsonValue.NULL;
		else
			throw new IllegalArgumentException();
	}

	/**
	 * Adds a value to an object builder.
	 * 
	 * @param builder {@link JsonObjectBuilder}
	 * @param name    name
	 * @param value   value
	 */
	public static void add(JsonObjectBuilder builder, String name, Object value) {
		add(builder, a -> true, name, () -> value, IuJson::toJson);
	}

	/**
	 * Adds a value to an object builder.
	 * 
	 * @param builder       {@link JsonObjectBuilder}
	 * @param name          name
	 * @param valueSupplier value supplier
	 */
	public static void add(JsonObjectBuilder builder, String name, Supplier<?> valueSupplier) {
		add(builder, a -> true, name, valueSupplier, IuJson::toJson);
	}

	/**
	 * Adds a value to an object builder.
	 * 
	 * @param builder       {@link JsonObjectBuilder}
	 * @param nameFilter    filter predicate, builder will only be modified if
	 *                      nameFilter.test(name) returns true
	 * @param name          name
	 * @param valueSupplier value supplier
	 */
	public static void add(JsonObjectBuilder builder, Predicate<String> nameFilter, String name,
			Supplier<?> valueSupplier) {
		add(builder, nameFilter, name, valueSupplier, IuJson::toJson);
	}

	/**
	 * Adds a value to an object builder.
	 * 
	 * @param builder             {@link JsonObjectBuilder}
	 * @param nameFilter          filter predicate, builder will only be modified if
	 *                            nameFilter.test(name) returns true
	 * @param name                property name
	 * @param valueSupplier       value supplier
	 * @param valueToJsonFunction function that converts the supplied value to
	 *                            {@link JsonValue}
	 */
	public static <T> void add(JsonObjectBuilder builder, Predicate<String> nameFilter, String name,
			Supplier<T> valueSupplier, Function<T, JsonValue> valueToJsonFunction) {
		if (!nameFilter.test(name))
			return;

		final var a = valueSupplier.get();
		if (a != null)
			builder.add(name, valueToJsonFunction.apply(a));
	}

	/**
	 * Adds a value to an array builder.
	 * 
	 * @param builder JSON array builder
	 * @param value   value
	 */
	public static void add(JsonArrayBuilder builder, Object value) {
		add(builder, () -> value, IuJson::toJson);
	}

	/**
	 * Adds a value to an array builder.
	 * 
	 * @param builder       JSON array builder
	 * @param valueSupplier value supplier
	 */
	public static void add(JsonArrayBuilder builder, Supplier<?> valueSupplier) {
		add(builder, valueSupplier, IuJson::toJson);
	}

	/**
	 * Adds a string value to an object builder.
	 * 
	 * @param builder             JSON object builder
	 * @param valueSupplier       value supplier
	 * @param valueToJsonFunction function that converts the supplied value to
	 *                            {@link JsonValue}
	 */
	public static <T> void add(JsonArrayBuilder builder, Supplier<T> valueSupplier,
			Function<T, JsonValue> valueToJsonFunction) {
		final var a = valueSupplier.get();
		if (a != null)
			builder.add(valueToJsonFunction.apply(a));
	}

	/**
	 * Gets text property value from a JSON object.
	 * 
	 * @param object {@link JsonObject}
	 * @param name   property name
	 * @return text value
	 * @see #asText(JsonValue)
	 */
	public static String text(JsonObject object, String name) {
		return get(object, name, null, IuJson::asText);
	}

	/**
	 * Gets text property value from a JSON object.
	 * 
	 * @param object       {@link JsonObject}
	 * @param name         property name
	 * @param defaultValue value to return if the property is missing
	 * @return text value
	 * @see #asText(JsonValue)
	 */
	public static String text(JsonObject object, String name, String defaultValue) {
		return get(object, name, defaultValue, IuJson::asText);
	}

	/**
	 * Gets a text property value from a JSON object.
	 * 
	 * @param <T>                 result type
	 * 
	 * @param object              {@link JsonObject}
	 * @param name                property name
	 * @param textToValueFunction converts a non-null text to the result type
	 * @return result of applying a test property value to textToValueFunction;
	 *         defaultValue if the property is missing
	 */
	public static <T> T text(JsonObject object, String name, Function<String, T> textToValueFunction) {
		return text(object, name, null, textToValueFunction);
	}

	/**
	 * Gets a text property value from a JSON object.
	 * 
	 * @param <T>                 result type
	 * 
	 * @param object              {@link JsonObject}
	 * @param name                property name
	 * @param defaultValue        value to return if the property is missing
	 * @param textToValueFunction converts a non-null text to the result type
	 * @return result of applying a test property value to textToValueFunction;
	 *         defaultValue if the property is missing
	 */
	public static <T> T text(JsonObject object, String name, T defaultValue, Function<String, T> textToValueFunction) {
		return get(object, name, defaultValue, v -> {
			final var textValue = asText(v);
			if (textValue == null)
				return defaultValue;
			else
				return textToValueFunction.apply(textValue);
		});
	}

	/**
	 * Gets a property value from a JSON object.
	 * 
	 * @param <T>    result type
	 * 
	 * @param object {@link JsonObject}
	 * @param name   property name
	 * @return property value
	 * @see #toJava(JsonValue)
	 */
	public static <T> T get(JsonObject object, String name) {
		return get(object, name, null, IuJson::toJava);
	}

	/**
	 * Gets a property value from a JSON object.
	 * 
	 * @param <T>          result type
	 * 
	 * @param object       {@link JsonObject}
	 * @param name         property name
	 * @param defaultValue value to return if the property is missing
	 * @return property value
	 * @see #toJava(JsonValue)
	 */
	public static <T> T get(JsonObject object, String name, T defaultValue) {
		return get(object, name, defaultValue, IuJson::toJava);
	}

	/**
	 * Gets a property value from a JSON object.
	 * 
	 * @param <T>                 result type
	 * 
	 * @param object              {@link JsonObject}
	 * @param name                property name
	 * @param jsonToValueFunction converts a non-null JSON value to the result type
	 * @return result of applying a non-null JSON value to jsonToValueFunction;
	 *         defaultValue if the property is missing
	 */
	public static <T> T get(JsonObject object, String name, Function<JsonValue, T> jsonToValueFunction) {
		return get(object, name, null, jsonToValueFunction);
	}

	/**
	 * Gets a property value from a JSON object.
	 * 
	 * @param <T>                 result type
	 * 
	 * @param object              {@link JsonObject}
	 * @param name                property name
	 * @param defaultValue        value to return if the property is missing
	 * @param jsonToValueFunction converts a non-null JSON value to the result type
	 * @return result of applying a non-null JSON value to jsonToValueFunction;
	 *         defaultValue if the property is missing
	 */
	public static <T> T get(JsonObject object, String name, T defaultValue,
			Function<JsonValue, T> jsonToValueFunction) {
		final var v = object.get(name);
		if (v == null)
			return defaultValue;
		else
			return jsonToValueFunction.apply(v);
	}

	private IuJson() {
	}
}
