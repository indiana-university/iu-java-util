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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.math.BigDecimal;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

@SuppressWarnings("javadoc")
public class IuJsonbParserTest {

	private static final String[] SAMPLES = { "{}", "[]", "\"s\"", "1", "-2.5e3", "12345678901234567890", "true",
			"false", "null", "[[1,[2]],{},[]]",
			"{\"a\":1,\"b\":[true,false,null,\"x\",{\"c\":{}}],\"d\":\"\\\"q\\\"\",\"e\":1.5}" };

	private static JsonParser text(String json) {
		return IuJson.PROVIDER.createParser(new StringReader(json));
	}

	private static IuJsonbParser tree(String json) {
		return new IuJsonbParser(IuJson.parse(json), IuJson.PROVIDER);
	}

	/**
	 * Applies the same steps to a text parser and a tree parser over the same
	 * JSON, checking that they agree after each.
	 */
	@SafeVarargs
	private static void same(String json, Consumer<JsonParser>... steps) {
		try (final var text = text(json); final var tree = tree(json)) {
			for (var i = 0; i < steps.length; i++) {
				steps[i].accept(text);
				steps[i].accept(tree);
				assertEquals(text.currentEvent(), tree.currentEvent(), json + " step " + i);
				assertEquals(text.hasNext(), tree.hasNext(), json + " step " + i);
			}
		}
	}

	private static final Consumer<JsonParser> NEXT = JsonParser::next;
	private static final Consumer<JsonParser> SKIP_ARRAY = JsonParser::skipArray;
	private static final Consumer<JsonParser> SKIP_OBJECT = JsonParser::skipObject;

	@Test
	public void testEventsMatchTextParser() {
		for (final var json : SAMPLES)
			try (final var text = text(json); final var tree = tree(json)) {
				assertNull(tree.currentEvent());
				while (text.hasNext()) {
					assertTrue(tree.hasNext(), json);
					final var event = text.next();
					assertEquals(event, tree.next(), json);
					assertEquals(event, tree.currentEvent(), json);
					switch (event) {
					case VALUE_NUMBER:
						assertEquals(text.isIntegralNumber(), tree.isIntegralNumber(), json);
						assertEquals(text.getInt(), tree.getInt(), json);
						assertEquals(text.getLong(), tree.getLong(), json);
						assertEquals(text.getBigDecimal(), tree.getBigDecimal(), json);
						// a value has no source text, so reads as its canonical text
						assertEquals(new BigDecimal(text.getString()), new BigDecimal(tree.getString()), json);
						assertEquals(tree.getBigDecimal().toString(), tree.getString(), json);
						break;

					case KEY_NAME:
					case VALUE_STRING:
						assertEquals(text.getString(), tree.getString(), json);
						break;

					default:
						break;
					}
				}
				assertFalse(tree.hasNext(), json);
				assertThrows(NoSuchElementException.class, tree::next, json);
			}
	}

	@Test
	public void testPosition() {
		final var parser = tree("{\"a\":[1]}");
		assertEquals(0, parser.position());
		parser.next();
		parser.next();
		assertEquals(2, parser.position());
		parser.next();
		parser.getArray();
		assertEquals(4, parser.position());
		parser.next();
		assertEquals(5, parser.position());
	}

	@Test
	public void testLocationIsUnknown() {
		final var location = tree("1").getLocation();
		assertEquals(-1, location.getLineNumber());
		assertEquals(-1, location.getColumnNumber());
		assertEquals(-1, location.getStreamOffset());
	}

	@Test
	public void testGetValue() {
		final var json = "{\"a\":[1,\"x\",true,false,null,{\"b\":2}],\"c\":{}}";
		final var parser = tree(json);
		assertThrows(IllegalStateException.class, parser::getValue);
		assertEquals(Event.START_OBJECT, parser.next());
		assertEquals(Event.KEY_NAME, parser.next());
		assertEquals(IuJson.string("a"), parser.getValue());
		assertEquals(Event.START_ARRAY, parser.next());
		assertEquals(Event.VALUE_NUMBER, parser.next());
		assertEquals(IuJson.number(1), parser.getValue());
		for (final var expected : List.of(IuJson.string("x"), JsonValue.TRUE, JsonValue.FALSE, JsonValue.NULL)) {
			parser.next();
			assertEquals(expected, parser.getValue());
		}
		assertEquals(Event.START_OBJECT, parser.next());
		assertEquals(IuJson.parse("{\"b\":2}"), parser.getValue());
		assertEquals(Event.END_OBJECT, parser.currentEvent());
		assertThrows(IllegalStateException.class, parser::getValue);
		assertEquals(Event.END_ARRAY, parser.next());
		assertThrows(IllegalStateException.class, parser::getValue);
		parser.next();
		assertEquals(Event.START_OBJECT, parser.next());
		assertEquals(JsonValue.EMPTY_JSON_OBJECT, parser.getObject());
		assertEquals(Event.END_OBJECT, parser.next());
		assertFalse(parser.hasNext());

		final var root = tree(json);
		root.next();
		assertEquals(IuJson.parse(json), root.getValue());
		assertFalse(root.hasNext());

		final var array = tree("[1]");
		array.next();
		assertEquals(IuJson.parse("[1]"), array.getValue());
		assertEquals(Event.END_ARRAY, array.currentEvent());
		assertFalse(array.hasNext());
	}

	@Test
	public void testIllegalState() {
		final var parser = tree("{\"a\":\"x\"}");
		assertThrows(IllegalStateException.class, parser::getString);
		parser.next();
		assertThrows(IllegalStateException.class, parser::getString);
		assertThrows(IllegalStateException.class, parser::isIntegralNumber);
		assertThrows(IllegalStateException.class, parser::getInt);
		assertThrows(IllegalStateException.class, parser::getLong);
		assertThrows(IllegalStateException.class, parser::getBigDecimal);
		assertThrows(IllegalStateException.class, parser::getArray);
		assertThrows(IllegalStateException.class, parser::getArrayStream);
		parser.next();
		assertThrows(IllegalStateException.class, parser::getObject);
		assertThrows(IllegalStateException.class, parser::getObjectStream);
		assertThrows(IllegalStateException.class, parser::getValueStream);
		parser.next();
		assertThrows(IllegalStateException.class, parser::isIntegralNumber);
	}

	@Test
	public void testSkipMatchesTextParser() {
		same("[1,{\"a\":[2,3]},4]", NEXT, NEXT, SKIP_OBJECT, SKIP_ARRAY);
		same("[1,{\"a\":[2,3]},4]", NEXT, NEXT, NEXT, SKIP_ARRAY, NEXT, NEXT, NEXT, SKIP_ARRAY, SKIP_OBJECT);
		same("[1,{\"a\":[2,3]},4]", NEXT, NEXT, NEXT, NEXT, NEXT, SKIP_OBJECT, NEXT, SKIP_ARRAY, SKIP_ARRAY);
		same("{\"a\":[2,3],\"b\":{}}", SKIP_OBJECT, SKIP_ARRAY, NEXT, NEXT, SKIP_ARRAY, NEXT, NEXT, SKIP_OBJECT);
		same("[[1],[2]]", NEXT, NEXT, SKIP_ARRAY, NEXT, SKIP_ARRAY, SKIP_ARRAY);
		same("{}", NEXT, NEXT, SKIP_OBJECT, SKIP_ARRAY);
	}

	@Test
	public void testArrayStream() {
		final var json = "[1,[2],{\"a\":3},\"x\"]";
		try (final var text = text(json); final var tree = tree(json)) {
			text.next();
			tree.next();
			assertEquals(text.getArrayStream().collect(Collectors.toList()),
					tree.getArrayStream().collect(Collectors.toList()));
			assertEquals(Event.END_ARRAY, tree.currentEvent());
			assertFalse(tree.hasNext());
		}

		final var partial = tree(json);
		partial.next();
		assertEquals(IuJson.number(1), partial.getArrayStream().findFirst().get());
		partial.skipArray();
		assertEquals(Event.END_ARRAY, partial.currentEvent());
		assertFalse(partial.hasNext());
	}

	@Test
	public void testObjectStream() {
		final var json = "{\"a\":1,\"b\":[2],\"c\":{\"d\":null}}";
		try (final var text = text(json); final var tree = tree(json)) {
			text.next();
			tree.next();
			final var expected = text.getObjectStream().collect(Collectors.toList());
			final var actual = tree.getObjectStream().collect(Collectors.toList());
			assertEquals(expected, actual);
			assertEquals(List.of("a", "b", "c"), actual.stream().map(Map.Entry::getKey).collect(Collectors.toList()));
			assertEquals(Event.END_OBJECT, tree.currentEvent());
		}
	}

	@Test
	public void testValueStream() {
		for (final var json : SAMPLES)
			try (final var text = text(json); final var tree = tree(json)) {
				assertEquals(text.getValueStream().collect(Collectors.toList()),
						tree.getValueStream().collect(Collectors.toList()), json);
				assertEquals(List.of(), tree.getValueStream().collect(Collectors.toList()), json);
			}
	}

	public static class Node {
		public String name;
		public Node next;
	}

	/**
	 * Records the name of each node it reads, then passes control down the chain.
	 */
	public static class Names implements JsonbDeserializer<Node> {
		final List<String> names = new ArrayList<>();

		@Override
		public Node deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final Node node = ctx.deserialize(rtType, parser);
			names.add(node.name);
			return node;
		}
	}

	@Test
	public void testNestedDeserializersInTreeConversion() {
		final var names = new Names();
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDeserializers(names));
		final var json = "{\"name\":\"a\",\"next\":{\"name\":\"b\",\"next\":{\"name\":\"c\"}}}";

		// each node reads at a position of its own, so starts a chain of its own
		assertEquals("a", jsonb.fromJson(json, Node.class).name);
		assertEquals(List.of("c", "b", "a"), names.names);

		names.names.clear();
		final Node node = (Node) jsonb.adapt(Node.class).fromJson(IuJson.parse(json));
		assertEquals("c", node.next.next.name);
		assertEquals(List.of("c", "b", "a"), names.names);
	}

}
