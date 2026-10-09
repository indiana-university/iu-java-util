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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
public class IuJsonbBoundedParserTest {

	/**
	 * Parses {@code [value,4]} and returns a view bounded to value.
	 */
	private static IuJsonbBoundedParser view(String value) {
		final var parser = IuJson.PROVIDER.createParser(new StringReader("[" + value + ",4]"));
		parser.next();
		parser.next();
		return new IuJsonbBoundedParser(parser, null);
	}

	/**
	 * Checks the parser the view reads from continues after the value.
	 */
	private static void continues(IuJsonbBoundedParser view) {
		final var parser = IuJsonbBoundedParser.underlying(view);
		assertEquals(Event.VALUE_NUMBER, parser.next());
		assertEquals(4, parser.getInt());
	}

	@Test
	public void testScalarIsItsOnlyEvent() {
		final var view = view("\"s\"");
		assertEquals(Event.VALUE_STRING, view.currentEvent());
		assertEquals("s", view.getString());
		assertEquals(IuJson.string("s"), view.getValue());
		assertFalse(view.hasNext());
		assertThrows(NoSuchElementException.class, view::next);
		view.release();
		continues(view);
	}

	@Test
	public void testLoopsToTheEndOfTheValue() {
		final var view = view("{\"a\":[1,{\"b\":2.5}],\"c\":3}");
		final List<Event> events = new ArrayList<>();
		while (view.hasNext())
			events.add(view.next());
		assertEquals(List.of(Event.KEY_NAME, Event.START_ARRAY, Event.VALUE_NUMBER, Event.START_OBJECT,
				Event.KEY_NAME, Event.VALUE_NUMBER, Event.END_OBJECT, Event.END_ARRAY, Event.KEY_NAME,
				Event.VALUE_NUMBER, Event.END_OBJECT), events);
		view.release();
		continues(view);
	}

	@Test
	public void testReleaseSkipsWhatsUnread() {
		final List<Event> seen = new ArrayList<>();
		final var view = view("{\"a\":[1,{\"b\":2}],\"c\":3}");
		view.next();
		view.next();
		view.next();
		view.beforeRelease(() -> seen.add(view.currentEvent()));
		view.release();
		// the hook runs once, where the reader left the parser
		assertEquals(List.of(Event.VALUE_NUMBER), seen);
		view.release();
		assertEquals(List.of(Event.VALUE_NUMBER), seen);
		continues(view);
	}

	@Test
	public void testDelegates() {
		final var view = view("12345678901");
		assertTrue(view.isIntegralNumber());
		assertEquals(12345678901L, view.getLong());
		assertEquals((int) 12345678901L, view.getInt());
		assertEquals(new BigDecimal("12345678901"), view.getBigDecimal());
		assertEquals(IuJsonbBoundedParser.underlying(view).getLocation().getStreamOffset(),
				view.getLocation().getStreamOffset());
		view.close();
		view.release();
		continues(view);
	}

	@Test
	public void testStructures() {
		final var object = view("{\"a\":1}");
		assertEquals(IuJson.parse("{\"a\":1}"), object.getValue());
		assertFalse(object.hasNext());
		object.release();
		continues(object);

		final var array = view("[1,[2]]");
		assertEquals(IuJson.parse("[1,[2]]"), array.getValue());
		assertFalse(array.hasNext());
		array.release();
		continues(array);

		final var nested = view("{\"a\":{\"b\":1},\"c\":[2]}");
		nested.next();
		nested.next();
		assertEquals(IuJson.parse("{\"b\":1}"), nested.getObject());
		nested.next();
		nested.next();
		assertEquals(IuJson.parse("[2]"), nested.getArray());
		assertEquals(Event.END_OBJECT, nested.next());
		assertFalse(nested.hasNext());
		continues(nested);
	}

	@Test
	public void testStreams() {
		final var array = view("[1,{\"a\":2},\"x\"]");
		assertEquals(List.of(IuJson.number(1), IuJson.parse("{\"a\":2}"), IuJson.string("x")),
				array.getArrayStream().collect(Collectors.toList()));
		assertFalse(array.hasNext());
		continues(array);

		final var object = view("{\"a\":1,\"b\":[2]}");
		final Map<String, JsonValue> entries = object.getObjectStream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		assertEquals(Map.of("a", IuJson.number(1), "b", IuJson.parse("[2]")), entries);
		assertFalse(object.hasNext());
		continues(object);

		final var scalar = view("1");
		assertThrows(IllegalStateException.class, scalar::getArrayStream);
		assertThrows(IllegalStateException.class, scalar::getObjectStream);
		assertThrows(IllegalStateException.class, scalar::getValueStream);
	}

	@Test
	public void testSkipsOnlyWithinTheValue() {
		final var view = view("{\"a\":[1,2],\"b\":{\"c\":3}}");
		// not in an array, so nothing to skip
		view.skipArray();
		assertEquals(Event.START_OBJECT, view.currentEvent());
		view.next();
		view.next();
		view.skipObject();
		assertEquals(Event.START_ARRAY, view.currentEvent());
		view.skipArray();
		assertEquals(Event.END_ARRAY, view.currentEvent());
		view.skipObject();
		assertFalse(view.hasNext());
		// outside the value, both do nothing
		view.skipObject();
		view.skipArray();
		continues(view);
	}

	@Test
	public void testNoContinuationWithoutACall() {
		assertEquals(null, view("{}").continuation());
		assertFalse(view("{}").isTree());
	}

	@Test
	public void testUnderlying() {
		final var view = view("[1]");
		final var nested = new IuJsonbBoundedParser(view, null);
		assertSame(IuJsonbBoundedParser.underlying(view), IuJsonbBoundedParser.underlying(nested));
	}

	public static class Box {
		public Inner inner;
		public String name;
	}

	public static class Inner {
		public int value;
	}

	/**
	 * Follows the example in the JsonbDeserializer documentation.
	 */
	public static class BoxDeserializer implements JsonbDeserializer<Box> {
		@Override
		public Box deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final var box = new Box();
			while (parser.hasNext()) {
				final var event = parser.next();
				if (event == Event.KEY_NAME && parser.getString().equals("inner"))
					box.inner = ctx.deserialize(Inner.class, parser);
				else if (event == Event.KEY_NAME && parser.getString().equals("name")) {
					parser.next();
					box.name = parser.getString();
				}
			}
			return box;
		}
	}

	/**
	 * Reads only the first property, leaving the rest.
	 */
	public static class FirstOnly implements JsonbDeserializer<Inner> {
		@Override
		public Inner deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final var inner = new Inner();
			parser.next();
			parser.next();
			inner.value = parser.getInt();
			return inner;
		}
	}

	public static class Boxes {
		public List<Box> boxes;
		public String after;
	}

	@Test
	public void testDocumentedDeserializer() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDeserializers(new BoxDeserializer()));
		final var json = "{\"boxes\":[{\"inner\":{\"value\":1},\"name\":\"a\"},{\"name\":\"b\"}],\"after\":\"x\"}";
		for (final var boxes : List.of(jsonb.fromJson(json, Boxes.class),
				(Boxes) jsonb.adapt(Boxes.class).fromJson(IuJson.parse(json)))) {
			assertEquals(1, boxes.boxes.get(0).inner.value);
			assertEquals("a", boxes.boxes.get(0).name);
			assertEquals("b", boxes.boxes.get(1).name);
			assertEquals("x", boxes.after);
		}
	}

	@Test
	public void testPartialDeserializer() {
		final var jsonb = IuJsonbTest.jsonb(
				new JsonbConfig().withDeserializers(new BoxDeserializer(), new FirstOnly()));
		final var json = "{\"boxes\":[{\"inner\":{\"value\":1,\"skipped\":[{}],\"also\":2},\"name\":\"a\"}],"
				+ "\"after\":\"x\"}";
		for (final var boxes : List.of(jsonb.fromJson(json, Boxes.class),
				(Boxes) jsonb.adapt(Boxes.class).fromJson(IuJson.parse(json)))) {
			assertEquals(1, boxes.boxes.get(0).inner.value);
			assertEquals("a", boxes.boxes.get(0).name);
			assertEquals("x", boxes.after);
		}
	}

}
