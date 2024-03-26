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
import java.io.OutputStream;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonConfig;
import jakarta.json.JsonConfig.KeyStrategy;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
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

	private static final JsonBuilderFactory FACTORY = PROVIDER
			.createBuilderFactory(Map.of(JsonConfig.KEY_STRATEGY, KeyStrategy.NONE));

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
	 * Creates a JSON value from a {@link Boolean#TYPE boolean}.
	 * 
	 * @param value {@link Boolean#TYPE boolean}
	 * @return {@link JsonValue}
	 */
	public static JsonValue bool(boolean value) {
		return value ? JsonValue.TRUE : JsonValue.FALSE;
	}

	/**
	 * Creates a JSON value from a {@link Number}.
	 * 
	 * @param value {@link Number}
	 * @return {@link JsonNumber}
	 */
	public static JsonNumber number(Number value) {
		return PROVIDER.createValue(value);
	}

	/**
	 * Creates a JSON value from a {@link String}.
	 * 
	 * @param value {@link String}
	 * @return {@link JsonString}
	 */
	public static JsonString string(String value) {
		return PROVIDER.createValue(value);
	}

	/**
	 * Serializes a JSON value to an {@link OutputStream}.
	 * 
	 * @param value {@link JsonValue}
	 * @param out   {@link OutputStream}
	 */
	public static void serialize(JsonValue value, OutputStream out) {
		PROVIDER.createWriter(out).write(value);
	}

	/**
	 * Creates an array builder that rejects duplicate values.
	 * 
	 * @return {@link JsonArrayBuilder}
	 */
	public static JsonArrayBuilder array() {
		return FACTORY.createArrayBuilder();
	}

	/**
	 * Creates a builder for modifying an array.
	 * 
	 * @param array array to copy
	 * @return {@link JsonArrayBuilder}
	 */
	public static JsonArrayBuilder array(JsonArray array) {
		return PROVIDER.createArrayBuilder(array);
	}

	/**
	 * Creates an object builder that rejects duplicate values.
	 * 
	 * @return {@link JsonObjectBuilder}
	 */
	public static JsonObjectBuilder object() {
		return FACTORY.createObjectBuilder();
	}

	/**
	 * Creates a builder for modifying an object.
	 *
	 * @param object object to copy
	 * @return {@link JsonObjectBuilder}
	 */
	public static JsonObjectBuilder object(JsonObject object) {
		return PROVIDER.createObjectBuilder(object);
	}

	/**
	 * Adds a value to an object builder.
	 * 
	 * @param builder {@link JsonObjectBuilder}
	 * @param name    name
	 * @param value   value
	 */
	public static void add(JsonObjectBuilder builder, String name, Object value) {
		add(builder, name, () -> value, () -> true, IuJsonAdapter.basic());
	}

	/**
	 * Adds a value to an object builder.
	 * 
	 * @param <T>           value type
	 * @param builder       {@link JsonObjectBuilder}
	 * @param name          property name
	 * @param valueSupplier value supplier
	 * @param adapter       JSON type adapter for handling non-null values
	 */
	public static <T> void add(JsonObjectBuilder builder, String name, Supplier<T> valueSupplier,
			IuJsonAdapter<T> adapter) {
		add(builder, name, valueSupplier, () -> true, adapter);
	}

	/**
	 * Adds a value to an object builder.
	 * 
	 * @param <T>       value type
	 * @param builder   {@link JsonObjectBuilder}
	 * @param name      property name
	 * @param value     value
	 * @param condition supplies true if the value should be added; false to do
	 *                  nothing
	 */
	public static <T> void add(JsonObjectBuilder builder, String name, T value, BooleanSupplier condition) {
		add(builder, name, () -> value, condition, IuJsonAdapter.basic());
	}

	/**
	 * Adds a value to an object builder.
	 * 
	 * @param <T>           value type
	 * @param builder       {@link JsonObjectBuilder}
	 * @param name          property name
	 * @param valueSupplier value supplier
	 * @param condition     supplies true if the value should be added; false to do
	 *                      nothing
	 * @param adapter       JSON type adapter for handling non-null values
	 */
	public static <T> void add(JsonObjectBuilder builder, String name, Supplier<T> valueSupplier,
			BooleanSupplier condition, IuJsonAdapter<T> adapter) {
		if (!condition.getAsBoolean())
			return;

		final var a = valueSupplier.get();
		if (a != null)
			builder.add(name, adapter.toJson(a));
	}

	/**
	 * Adds a value to an array builder.
	 * 
	 * @param <T>     value type
	 * @param builder {@link JsonArrayBuilder}
	 * @param value   value
	 */
	public static <T> void add(JsonArrayBuilder builder, T value) {
		add(builder, () -> value, () -> true, IuJsonAdapter.basic());
	}

	/**
	 * Adds a value to an array builder.
	 * 
	 * @param <T>           value type
	 * @param builder       {@link JsonArrayBuilder}
	 * @param valueSupplier value supplier
	 * @param adapter       JSON type adapter for handling non-null values
	 */
	public static <T> void add(JsonArrayBuilder builder, Supplier<T> valueSupplier, IuJsonAdapter<T> adapter) {
		add(builder, valueSupplier, () -> true, adapter);
	}

	/**
	 * Adds a value to an array builder.
	 * 
	 * @param <T>       value type
	 * @param builder   {@link JsonArrayBuilder}
	 * @param value     value
	 * @param condition supplies true if the value should be added; false to do
	 *                  nothing
	 */
	public static <T> void add(JsonArrayBuilder builder, T value, BooleanSupplier condition) {
		add(builder, () -> value, condition, IuJsonAdapter.basic());
	}

	/**
	 * Adds a value to an array builder.
	 * 
	 * @param <T>           value type
	 * @param builder       {@link JsonArrayBuilder}
	 * @param valueSupplier value supplier
	 * @param condition     supplies true if the value should be added; false to do
	 *                      nothing
	 * @param adapter       JSON type adapter for handling non-null values
	 */
	public static <T> void add(JsonArrayBuilder builder, Supplier<T> valueSupplier, BooleanSupplier condition,
			IuJsonAdapter<T> adapter) {
		if (!condition.getAsBoolean())
			return;

		final var a = valueSupplier.get();
		if (a != null)
			builder.add(adapter.toJson(a));
	}

	/**
	 * Gets a property value from a JSON object.
	 * 
	 * @param <T>    result type
	 * @param object {@link JsonObject}
	 * @param name   property name
	 * @return property value
	 */
	@SuppressWarnings("unchecked")
	public static <T> T get(JsonObject object, String name) {
		return (T) get(object, name, null, IuJsonAdapter.of(Object.class));
	}

	/**
	 * Gets a property value from a JSON object.
	 * 
	 * @param <T>     result type
	 * @param object  {@link JsonObject}
	 * @param name    property name
	 * @param adapter adapter for converting non-null values to Java
	 * @return property value
	 */
	public static <T> T get(JsonObject object, String name, IuJsonAdapter<T> adapter) {
		return get(object, name, null, adapter);
	}

	/**
	 * Gets a property value from a JSON object, accepting if non-null..
	 * 
	 * @param <T>      result type
	 * @param object   {@link JsonObject}
	 * @param name     property name
	 * @param consumer accepts the property value if non-null; else skipped
	 */
	public static <T> void get(JsonObject object, String name, Consumer<T> consumer) {
		get(object, name, IuJsonAdapter.basic(), consumer);
	}

	/**
	 * Gets a property value from a JSON object, accepting if non-null..
	 * 
	 * @param <T>      result type
	 * @param object   {@link JsonObject}
	 * @param name     property name
	 * @param adapter  JSON type adapter
	 * @param consumer accepts the property value if non-null; else skipped
	 */
	public static <T> void get(JsonObject object, String name, IuJsonAdapter<T> adapter, Consumer<T> consumer) {
		final var value = get(object, name, null, adapter);
		if (value != null)
			consumer.accept(value);
	}

	/**
	 * Gets a property value from a JSON object.
	 * 
	 * @param <T>          result type
	 * @param object       {@link JsonObject}
	 * @param name         property name
	 * @param defaultValue value to return if the property is missing
	 * @param adapter      adapter for converting non-null values to Java
	 * @return property value, or defaultValue if the property is missing
	 */
	public static <T> T get(JsonObject object, String name, T defaultValue, IuJsonAdapter<T> adapter) {
		final var v = object.get(name);
		if (v == null)
			return defaultValue;
		else
			return adapter.fromJson(v);
	}

	private IuJson() {
	}
}