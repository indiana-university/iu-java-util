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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerationException;
import jakarta.json.stream.JsonGenerator;

/**
 * Builds the {@link JsonValue} described by {@link JsonGenerator} calls, so a
 * {@link jakarta.json.bind.serializer.JsonbSerializer} can run in a tree
 * conversion.
 *
 * <p>
 * Follows the {@link JsonGenerator} contract for a single value: calls out of
 * context throw {@link JsonGenerationException}, as does a second value, or
 * {@link #close()} before the value is complete. As in any
 * {@link jakarta.json.JsonObject}, a name written twice in the same object
 * keeps its last value.
 * </p>
 */
final class IuJsonbGenerator implements JsonGenerator {

	/**
	 * An array or object being written.
	 */
	private static final class Context {
		private final String name;
		private final JsonObjectBuilder object;
		private final JsonArrayBuilder array;
		private String key;

		private Context(String name, JsonObjectBuilder object) {
			this.name = name;
			this.object = object;
			array = null;
		}

		private Context(String name, JsonArrayBuilder array) {
			this.name = name;
			object = null;
			this.array = array;
		}
	}

	private final JsonProvider provider;
	private final Deque<Context> contexts = new ArrayDeque<>();
	private JsonValue value;

	/**
	 * Constructor.
	 *
	 * @param provider creates values and builders
	 */
	IuJsonbGenerator(JsonProvider provider) {
		this.provider = provider;
	}

	/**
	 * Gets the value written.
	 *
	 * @return value
	 * @throws JsonGenerationException if no complete value has been written
	 */
	JsonValue value() {
		if (value == null)
			throw new JsonGenerationException("incomplete JSON value");
		return value;
	}

	/**
	 * Claims the place of a value written without a name: the root, the next
	 * array item, or the value for the key just written.
	 *
	 * @return name for the value in an object; null otherwise
	 */
	private String unnamed() {
		final var context = contexts.peek();
		if (context == null) {
			if (value != null)
				throw new JsonGenerationException("a JSON value has already been written");
			return null;
		}

		if (context.array != null)
			return null;

		final var key = context.key;
		if (key == null)
			throw new JsonGenerationException("a name is required in an object");
		context.key = null;
		return key;
	}

	/**
	 * Claims the place of a value written with a name.
	 *
	 * @param name name
	 * @return name
	 */
	private String named(String name) {
		Objects.requireNonNull(name, "name");
		final var context = contexts.peek();
		if (context == null || context.object == null)
			throw new JsonGenerationException("a name is only allowed in an object");
		if (context.key != null)
			throw new JsonGenerationException("a value is required for " + context.key);
		return name;
	}

	/**
	 * Places a complete value in the innermost array or object, or as the root.
	 *
	 * @param name  name claimed for the value in an object
	 * @param value value
	 */
	private JsonGenerator add(String name, JsonValue value) {
		final var context = contexts.peek();
		if (context == null)
			this.value = value;
		else if (context.array != null)
			context.array.add(value);
		else
			context.object.add(name, value);
		return this;
	}

	private static JsonValue value(boolean value) {
		return value ? JsonValue.TRUE : JsonValue.FALSE;
	}

	@Override
	public JsonGenerator writeStartObject() {
		contexts.push(new Context(unnamed(), provider.createObjectBuilder()));
		return this;
	}

	@Override
	public JsonGenerator writeStartObject(String name) {
		contexts.push(new Context(named(name), provider.createObjectBuilder()));
		return this;
	}

	@Override
	public JsonGenerator writeKey(String name) {
		final var key = named(name);
		contexts.peek().key = key;
		return this;
	}

	@Override
	public JsonGenerator writeStartArray() {
		contexts.push(new Context(unnamed(), provider.createArrayBuilder()));
		return this;
	}

	@Override
	public JsonGenerator writeStartArray(String name) {
		contexts.push(new Context(named(name), provider.createArrayBuilder()));
		return this;
	}

	@Override
	public JsonGenerator write(String name, JsonValue value) {
		return add(named(name), Objects.requireNonNull(value, "value"));
	}

	@Override
	public JsonGenerator write(String name, String value) {
		return add(named(name), provider.createValue(value));
	}

	@Override
	public JsonGenerator write(String name, BigInteger value) {
		return add(named(name), provider.createValue(value));
	}

	@Override
	public JsonGenerator write(String name, BigDecimal value) {
		return add(named(name), provider.createValue(value));
	}

	@Override
	public JsonGenerator write(String name, int value) {
		return add(named(name), provider.createValue(value));
	}

	@Override
	public JsonGenerator write(String name, long value) {
		return add(named(name), provider.createValue(value));
	}

	@Override
	public JsonGenerator write(String name, double value) {
		return add(named(name), provider.createValue(value));
	}

	@Override
	public JsonGenerator write(String name, boolean value) {
		return add(named(name), value(value));
	}

	@Override
	public JsonGenerator writeNull(String name) {
		return add(named(name), JsonValue.NULL);
	}

	@Override
	public JsonGenerator writeEnd() {
		final var context = contexts.peek();
		if (context == null)
			throw new JsonGenerationException("not in an array or object");
		if (context.key != null)
			throw new JsonGenerationException("a value is required for " + context.key);

		contexts.pop();
		if (context.object != null)
			return add(context.name, context.object.build());
		else
			return add(context.name, context.array.build());
	}

	@Override
	public JsonGenerator write(JsonValue value) {
		Objects.requireNonNull(value, "value");
		return add(unnamed(), value);
	}

	@Override
	public JsonGenerator write(String value) {
		final var json = provider.createValue(value);
		return add(unnamed(), json);
	}

	@Override
	public JsonGenerator write(BigDecimal value) {
		final var json = provider.createValue(value);
		return add(unnamed(), json);
	}

	@Override
	public JsonGenerator write(BigInteger value) {
		final var json = provider.createValue(value);
		return add(unnamed(), json);
	}

	@Override
	public JsonGenerator write(int value) {
		return add(unnamed(), provider.createValue(value));
	}

	@Override
	public JsonGenerator write(long value) {
		return add(unnamed(), provider.createValue(value));
	}

	@Override
	public JsonGenerator write(double value) {
		final var json = provider.createValue(value);
		return add(unnamed(), json);
	}

	@Override
	public JsonGenerator write(boolean value) {
		return add(unnamed(), value(value));
	}

	@Override
	public JsonGenerator writeNull() {
		return add(unnamed(), JsonValue.NULL);
	}

	/**
	 * Does nothing, since the value is complete only as written.
	 */
	@Override
	public void flush() {
	}

	/**
	 * Checks that the value is complete.
	 *
	 * @throws JsonGenerationException if it isn't
	 */
	@Override
	public void close() {
		value();
	}

}
