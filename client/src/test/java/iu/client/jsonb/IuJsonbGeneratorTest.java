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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerationException;
import jakarta.json.stream.JsonGenerator;

@SuppressWarnings("javadoc")
public class IuJsonbGeneratorTest {

	/**
	 * Runs the same calls against a text generator and a tree generator, checking
	 * that they write the same value or fail the same way.
	 */
	private static void same(Consumer<JsonGenerator> calls) {
		final var text = new StringWriter();
		RuntimeException textFailure = null;
		try (final var generator = IuJson.PROVIDER.createGenerator(text)) {
			calls.accept(generator);
		} catch (RuntimeException e) {
			textFailure = e;
		}

		final var tree = new IuJsonbGenerator(IuJson.PROVIDER);
		RuntimeException treeFailure = null;
		try {
			calls.accept(tree);
			tree.close();
		} catch (RuntimeException e) {
			treeFailure = e;
		}

		if (textFailure == null) {
			assertNull(treeFailure);
			assertEquals(IuJson.parse(text.toString()), tree.value());
		} else {
			assertNotNull(treeFailure, textFailure::toString);
			assertEquals(textFailure.getClass(), treeFailure.getClass(), treeFailure::toString);
		}
	}

	@Test
	public void testStructuresMatchTextGenerator() {
		same(g -> g.writeStartObject() //
				.write("s", "x").write("bi", BigInteger.TEN).write("bd", new BigDecimal("1.5")) //
				.write("i", 1).write("l", 2L).write("d", 2.5).write("t", true).write("f", false) //
				.writeNull("n").write("v", JsonValue.EMPTY_JSON_OBJECT) //
				.writeStartObject("o").writeEnd() //
				.writeStartArray("a") //
				.write("x").write(BigDecimal.ONE).write(BigInteger.TWO).write(3).write(4L).write(5.5) //
				.write(true).write(false).writeNull().write(JsonValue.EMPTY_JSON_ARRAY) //
				.writeStartObject() //
				.writeKey("k").write("v") //
				.writeKey("k2").writeStartArray().writeEnd() //
				.writeKey("k3").writeStartObject().writeEnd() //
				.writeEnd() //
				.writeStartArray().writeEnd() //
				.writeEnd() //
				.writeEnd());
		same(g -> g.writeStartArray().writeStartArray().write(1).writeEnd().writeStartObject().writeEnd().writeEnd());
	}

	@Test
	public void testRootValuesMatchTextGenerator() {
		final List<Consumer<JsonGenerator>> roots = List.of(g -> g.write("x"), g -> g.write(1), g -> g.write(1L),
				g -> g.write(1.5), g -> g.write(BigDecimal.ONE), g -> g.write(BigInteger.ONE), g -> g.write(true),
				g -> g.write(false), g -> g.writeNull(), g -> g.write(JsonValue.TRUE),
				g -> g.writeStartObject().writeEnd(), g -> g.writeStartArray().writeEnd());
		for (final var root : roots)
			same(root);
	}

	@Test
	public void testFailuresMatchTextGenerator() {
		same(g -> {
		});
		same(g -> g.write(1).write(2));
		same(g -> g.write(1).writeStartObject());
		same(g -> g.writeStartObject().writeEnd().writeStartArray());
		same(g -> g.write("a", 1));
		same(g -> g.writeStartObject("a"));
		same(g -> g.writeStartArray("a"));
		same(g -> g.writeKey("a"));
		same(g -> g.writeNull("a"));
		same(g -> g.writeStartArray().write("a", 1));
		same(g -> g.writeStartArray().writeKey("a"));
		same(g -> g.writeStartObject().write(1));
		same(g -> g.writeStartObject().writeStartObject());
		same(g -> g.writeStartObject().writeStartArray());
		same(g -> g.writeStartObject().writeKey("a").writeKey("b"));
		same(g -> g.writeStartObject().writeKey("a").write("b", 1));
		same(g -> g.writeEnd());
		same(g -> g.writeStartObject());
		same(g -> g.writeStartArray().write(1));
		same(g -> g.write(Double.NaN));
		same(g -> g.writeStartObject().write("d", Double.POSITIVE_INFINITY));
	}

	@Test
	public void testFailureKeepsThePendingKey() {
		final var generator = new IuJsonbGenerator(IuJson.PROVIDER);
		generator.writeStartObject().writeKey("a");
		assertThrows(NumberFormatException.class, () -> generator.write(Double.NaN));
		generator.write(1).writeEnd();
		assertEquals(IuJson.parse("{\"a\":1}"), generator.value());
	}

	@Test
	public void testEndRequiresTheValueForAKey() {
		final var generator = new IuJsonbGenerator(IuJson.PROVIDER);
		generator.writeStartObject().writeKey("a");
		assertThrows(JsonGenerationException.class, generator::writeEnd);
	}

	@Test
	public void testIncompleteValue() {
		final var generator = new IuJsonbGenerator(IuJson.PROVIDER);
		assertThrows(JsonGenerationException.class, generator::value);
		generator.writeStartArray();
		generator.flush();
		assertThrows(JsonGenerationException.class, generator::value);
		assertThrows(JsonGenerationException.class, generator::close);
		generator.writeEnd();
		generator.close();
		generator.close();
		assertEquals(JsonValue.EMPTY_JSON_ARRAY, generator.value());
	}

	@Test
	public void testDuplicateNameKeepsLastValue() {
		final var generator = new IuJsonbGenerator(IuJson.PROVIDER);
		generator.writeStartObject().write("a", 1).writeKey("a").write(2).writeEnd();
		assertEquals(IuJson.parse("{\"a\":2}"), generator.value());
	}

	@Test
	public void testNullNamesAndValues() {
		final var generator = new IuJsonbGenerator(IuJson.PROVIDER);
		generator.writeStartObject();
		assertThrows(NullPointerException.class, () -> generator.writeKey(null));
		assertThrows(NullPointerException.class, () -> generator.write("a", (JsonValue) null));
		generator.writeKey("a");
		assertThrows(NullPointerException.class, () -> generator.write((JsonValue) null));
	}

	public static class Pair {
		public String first = "a";
		public int second = 2;
	}

	/**
	 * Writes a pair as an array.
	 */
	public static class PairArray implements JsonbSerializer<Pair> {
		@Override
		public void serialize(Pair obj, JsonGenerator generator, SerializationContext ctx) {
			generator.writeStartArray().write(obj.first);
			ctx.serialize(obj.second, generator);
			generator.writeEnd();
		}
	}

	public static class Pairs {
		public Pair pair = new Pair();
		public List<Pair> pairs = List.of(new Pair());
	}

	@Test
	public void testSerializerWritesAnArrayInTreeConversion() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withSerializers(new PairArray()));
		final var json = "{\"pair\":[\"a\",2],\"pairs\":[[\"a\",2]]}";
		assertEquals(json, jsonb.toJson(new Pairs()));
		assertEquals(json, jsonb.adapt(Pairs.class).toJson(new Pairs()).toString());
	}

	/**
	 * Writes nothing.
	 */
	public static class Nothing implements JsonbSerializer<Pair> {
		@Override
		public void serialize(Pair obj, JsonGenerator generator, SerializationContext ctx) {
		}
	}

	@Test
	public void testSerializerMustWriteAValueInTreeConversion() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withSerializers(new Nothing()));
		final var failure = assertThrows(JsonbException.class, () -> jsonb.adapt(Pair.class).toJson(new Pair()));
		assertInstanceOf(JsonGenerationException.class, failure.getCause());
	}

}
