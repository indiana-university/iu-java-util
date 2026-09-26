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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;

/**
 * Builds {@link JsonValue}s from {@link JsonGenerator} events, so a
 * {@link jakarta.json.bind.serializer.JsonbSerializer} can run in a tree
 * conversion.
 */
public class IuJsonbGenerator implements JsonGenerator {

	private static class Builders {
		private String name;
		private JsonObjectBuilder object;
		private JsonArrayBuilder array;
	}

	private final JsonWriter writer;
	private final JsonProvider provider;
	private final Deque<Builders> builders;

	/**
	 * Constructor.
	 *
	 * @param writer   receives each top-level value
	 * @param provider creates values and builders
	 */
	public IuJsonbGenerator(JsonWriter writer, JsonProvider provider) {
		this.writer = writer;
		this.provider = provider;
		this.builders = new ArrayDeque<>();
	}

	private void writeAddOrSet(Supplier<JsonValue> write, Consumer<JsonArrayBuilder> add,
			BiConsumer<JsonObjectBuilder, String> set) {
		if (builders.isEmpty())
			writer.write(write.get());
		else {
			final var b = builders.peek();
			if (b.object != null)
				// array is always null when object is non-null
				throw new NullPointerException("not in an array context");
			else if (b.array != null)
				add.accept(b.array);
			else {
				builders.pop();
				set.accept(Objects.requireNonNull(builders.peek().object, "not in an object context"),
						Objects.requireNonNull(b.name, "missing property name"));
			}
		}
	}

	private void writeOrSet(Supplier<JsonValue> write, Consumer<JsonObjectBuilder> add) {
		if (builders.isEmpty())
			writer.write(write.get());
		else {
			final var b = builders.peek();
			add.accept(Objects.requireNonNull(b.object, "not in an object context"));
		}
	}

	@Override
	public JsonGenerator writeStartObject() {
		final var b = new Builders();
		b.object = provider.createObjectBuilder();
		builders.push(b);
		return this;
	}

	@Override
	public JsonGenerator writeStartObject(String name) {
		final var b = new Builders();
		b.name = name;
		b.object = provider.createObjectBuilder();
		builders.push(b);
		return this;
	}

	@Override
	public JsonGenerator writeKey(String name) {
		final var b = builders.peek();
		Objects.requireNonNull(b.object, "not in an object context");
		final var k = new Builders();
		k.name = name;
		builders.push(k);
		return this;
	}

	@Override
	public JsonGenerator writeStartArray() {
		final var b = new Builders();
		b.object = provider.createObjectBuilder();
		builders.push(b);
		return this;
	}

	@Override
	public JsonGenerator writeStartArray(String name) {
		final var b = new Builders();
		b.name = name;
		b.array = provider.createArrayBuilder();
		builders.push(b);
		return this;
	}

	@Override
	public JsonGenerator write(String name, JsonValue value) {
		writeOrSet(() -> value, b -> b.add(name, value));
		return this;
	}

	@Override
	public JsonGenerator write(String name, String value) {
		writeOrSet(() -> provider.createValue(value), b -> b.add(name, value));
		return this;
	}

	@Override
	public JsonGenerator write(String name, BigInteger value) {
		writeOrSet(() -> provider.createValue(value), b -> b.add(name, value));
		return this;
	}

	@Override
	public JsonGenerator write(String name, BigDecimal value) {
		writeOrSet(() -> provider.createValue(value), b -> b.add(name, value));
		return this;
	}

	@Override
	public JsonGenerator write(String name, int value) {
		writeOrSet(() -> provider.createValue(value), b -> b.add(name, value));
		return this;
	}

	@Override
	public JsonGenerator write(String name, long value) {
		writeOrSet(() -> provider.createValue(value), b -> b.add(name, value));
		return this;
	}

	@Override
	public JsonGenerator write(String name, double value) {
		writeOrSet(() -> provider.createValue(value), b -> b.add(name, value));
		return this;
	}

	@Override
	public JsonGenerator write(String name, boolean value) {
		writeOrSet(() -> value ? JsonValue.TRUE : JsonValue.FALSE, b -> b.add(name, value));
		return this;
	}

	@Override
	public JsonGenerator writeNull(String name) {
		writeOrSet(() -> JsonValue.NULL, b -> b.addNull(name));
		return this;
	}

	@Override
	public JsonGenerator writeEnd() {
		if (builders.isEmpty())
			throw new IllegalStateException("not in a structure context");

		final var b = builders.pop();
		final JsonValue value;
		if (b.object != null)
			value = b.object.build();
		else if (b.array != null)
			value = b.array.build();
		else
			throw new IllegalStateException("no value set for key " + b.name);

		if (b.name != null)
			write(b.name, value);
		else
			write(value);

		return this;
	}

	@Override
	public JsonGenerator write(JsonValue value) {
		writeAddOrSet(() -> value, a -> a.add(value), (o, n) -> o.add(n, value));
		return this;
	}

	@Override
	public JsonGenerator write(String value) {
		writeAddOrSet(() -> provider.createValue(value), a -> a.add(value), (o, n) -> o.add(n, value));
		return this;
	}

	@Override
	public JsonGenerator write(BigDecimal value) {
		writeAddOrSet(() -> provider.createValue(value), a -> a.add(value), (o, n) -> o.add(n, value));
		return this;
	}

	@Override
	public JsonGenerator write(BigInteger value) {
		writeAddOrSet(() -> provider.createValue(value), a -> a.add(value), (o, n) -> o.add(n, value));
		return this;
	}

	@Override
	public JsonGenerator write(int value) {
		writeAddOrSet(() -> provider.createValue(value), a -> a.add(value), (o, n) -> o.add(n, value));
		return this;
	}

	@Override
	public JsonGenerator write(long value) {
		writeAddOrSet(() -> provider.createValue(value), a -> a.add(value), (o, n) -> o.add(n, value));
		return this;
	}

	@Override
	public JsonGenerator write(double value) {
		writeAddOrSet(() -> provider.createValue(value), a -> a.add(value), (o, n) -> o.add(n, value));
		return this;
	}

	@Override
	public JsonGenerator write(boolean value) {
		writeAddOrSet(() -> value ? JsonValue.TRUE : JsonValue.FALSE, a -> a.add(value), (o, n) -> o.add(n, value));
		return this;
	}

	@Override
	public JsonGenerator writeNull() {
		writeAddOrSet(() -> JsonValue.NULL, a -> a.addNull(), (o, n) -> o.addNull(n));
		return this;
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
		writer.close();
	}

}
