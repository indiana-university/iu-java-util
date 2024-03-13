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
package iu.crypt;

import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;

/**
 * Holds an instance of {@link JsonProvider}.
 * <p>
 * This class <em>may</em> be intentionally initialized by the authentication
 * bootstrap with the desired {@link JsonProvider} SPI provided by the current
 * thread's context.
 * </p>
 */
class JsonP {

	/**
	 * Singleton instance of {@link JsonProvider}.
	 */
	static final JsonProvider PROVIDER;

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
	 * Parses a JSON object from serialized form.
	 * 
	 * @param serialized serialized JSON
	 * @return JSON object
	 */
	static JsonObject parse(String serialized) {
		return PROVIDER.createReader(new StringReader(serialized)).readObject();
	}

	/**
	 * Parses a JSON object from Base-64 encoded serialized form.
	 * 
	 * @param encoded Base-64 encoded serialized JSON
	 * @return JSON object
	 */
	static JsonObject parseBase64Url(String encoded) {
		return PROVIDER.createReader(new StringReader(EncodingUtils.utf8(EncodingUtils.base64Url(encoded))))
				.readObject();
	}

	/**
	 * Converts a non-structural JSON value to Java.
	 * 
	 * @param value non-structural, non-null JSON value
	 * @return Java value
	 */
	static Object toJava(JsonValue value) {
		if (value instanceof JsonString)
			return ((JsonString) value).getString();
		else if (value instanceof JsonNumber)
			return ((JsonNumber) value).bigDecimalValue();
		else if (JsonValue.TRUE.equals(value))
			return true;
		else if (JsonValue.FALSE.equals(value))
			return true;
		else
			throw new IllegalArgumentException();
	}

	/**
	 * Converts a Java value to JSON.
	 * 
	 * @param value non-structural, non-null Java value
	 * @return JSON value
	 */
	static JsonValue toJson(Object value) {
		if (value instanceof String)
			return JsonP.PROVIDER.createValue((String) value);
		else if (value instanceof Number)
			return JsonP.PROVIDER.createValue((Number) value);
		else if (Boolean.TRUE.equals(value))
			return JsonValue.TRUE;
		else if (Boolean.FALSE.equals(value))
			return JsonValue.FALSE;
		else
			throw new IllegalArgumentException();
	}

	/**
	 * Converts a JSON array containing string values to a {@link Set} of strings.
	 * 
	 * @param a JSON array
	 * @return {@link Set} of strings
	 */
	static Set<String> toStringSet(JsonArray a) {
		final Set<String> s = new LinkedHashSet<>(a.size());
		for (var i = 0; i < a.size(); i++)
			s.add(a.getString(i));
		return Collections.unmodifiableSet(s);
	}

	/**
	 * Adds a string value to an object builder.
	 * 
	 * @param b       JSON object builder
	 * @param allowed test to be applied to name
	 * @param name    name
	 * @param s       string value supplier
	 */
	static void add(JsonObjectBuilder b, Predicate<String> allowed, String name, Supplier<String> s) {
		add(b, allowed, name, s, a -> a);
	}

	/**
	 * Adds a string value to an object builder.
	 * 
	 * @param b       JSON object builder
	 * @param allowed test to be applied to name
	 * @param name    name
	 * @param s       value supplier
	 * @param f       text serialization function
	 */
	static <T> void add(JsonObjectBuilder b, Predicate<String> allowed, String name, Supplier<T> s,
			Function<T, String> f) {
		if (!allowed.test(name))
			return;

		final var a = s.get();
		if (a != null)
			b.add(name, f.apply(a));
	}

	/**
	 * Adds an object value to an object builder.
	 * 
	 * @param b       JSON object builder
	 * @param allowed test to be applied to name
	 * @param name    name
	 * @param s       value supplier
	 * @param c       object serialization consumer
	 */
	static <T> void addObject(JsonObjectBuilder b, Predicate<String> allowed, String name, Supplier<T> s,
			BiConsumer<JsonObjectBuilder, T> c) {
		if (!allowed.test(name))
			return;

		final var a = s.get();
		if (a != null) {
			final var vb = PROVIDER.createObjectBuilder();
			c.accept(vb, a);
			b.add(name, vb);
		}
	}

	/**
	 * Adds an array value to an object builder.
	 * 
	 * @param b       JSON object builder
	 * @param allowed test to be applied to name
	 * @param name    name
	 * @param s       value supplier
	 * @param c       object serialization consumer
	 */
	static <T> void addArray(JsonObjectBuilder b, Predicate<String> allowed, String name, Supplier<T[]> s,
			BiConsumer<JsonArrayBuilder, T> c) {
		if (!allowed.test(name))
			return;

		final var a = s.get();
		if (a != null) {
			final var vb = PROVIDER.createArrayBuilder();
			for (final var i : a)
				c.accept(vb, i);
			b.add(name, vb);
		}
	}

	/**
	 * Reads a string from a JSON object, either returning null if missing or
	 * applying to a function.
	 * 
	 * @param o JSON object
	 * @param n attribute name
	 * @return string value applied to the function, or null if missing
	 */
	static String string(JsonObject o, String n) {
		if (!o.containsKey(n))
			return null;
		else
			return o.getString(n);
	}

	/**
	 * Reads a string from a JSON object, either returning null if missing or
	 * applying to a function.
	 * 
	 * @param <T> return type
	 * @param o   JSON object
	 * @param n   attribute name
	 * @param f   function to apply non-null string values to
	 * @return string value applied to the function, or null if missing
	 */
	static <T> T string(JsonObject o, String n, Function<String, T> f) {
		if (!o.containsKey(n))
			return null;
		else
			return f.apply(o.getString(n));
	}

	/**
	 * Reads a string from a JSON object, either returning null if missing or
	 * applying to a function.
	 * 
	 * @param o JSON object
	 * @param n attribute name
	 * @return string value applied to the function, or null if missing
	 */
	static JsonObject object(JsonObject o, String n) {
		if (!o.containsKey(n))
			return null;
		else
			return o.getJsonObject(n);
	}

	/**
	 * Reads a string from a JSON object, either returning null if missing or
	 * applying to a function.
	 * 
	 * @param <T> return type
	 * @param o   JSON object
	 * @param n   attribute name
	 * @param f   function to apply non-null object values to
	 * @return string value applied to the function, or null if missing
	 */
	static <T> T object(JsonObject o, String n, Function<JsonObject, T> f) {
		if (!o.containsKey(n))
			return null;
		else
			return f.apply(o.getJsonObject(n));
	}

	/**
	 * Reads a string from a JSON object, either returning null if missing or
	 * applying to a function.
	 * 
	 * @param <T> return type
	 * @param o   JSON object
	 * @param n   attribute name
	 * @param f   function to apply non-null object values to
	 * @return string value applied to the function, or null if missing
	 */
	static <T> T array(JsonObject o, String n, Function<JsonArray, T> f) {
		if (!o.containsKey(n))
			return null;
		else
			return f.apply(o.getJsonArray(n));
	}

	private JsonP() {
	}
}
