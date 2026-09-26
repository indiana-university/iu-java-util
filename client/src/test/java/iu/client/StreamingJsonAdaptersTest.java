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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuJsonSerializationOptions;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

/**
 * Checks each standard adapter's streaming conversion against its tree
 * conversion.
 */
@SuppressWarnings({ "javadoc", "unchecked", "rawtypes" })
public class StreamingJsonAdaptersTest {

	enum Letter {
		A, B;
	}

	static JsonParser parser(String json) {
		final var parser = IuJson.PROVIDER.createParser(new StringReader(json));
		parser.next();
		return parser;
	}

	/**
	 * Reads with the streaming conversion, checking the parser ends at the
	 * value's last event, and that the tree conversion agrees.
	 */
	static <T> T read(IuJsonAdapter<T> adapter, String json) {
		final T value;
		try (final var parser = parser(json)) {
			value = adapter.read(parser);
			assertFalse(parser.hasNext(), json);
		}
		assertEquals(String.valueOf(normalize(adapter.fromJson(IuJson.parse(json)))), String.valueOf(normalize(value)));
		return value;
	}

	static <T> String write(IuJsonAdapter<T> adapter, T value) {
		final var streamed = new StringWriter();
		try (final var generator = IuJson.PROVIDER.createGenerator(streamed)) {
			adapter.write(value, generator);
		}
		return streamed.toString();
	}

	/**
	 * Writes with the streaming conversion, checking the tree conversion writes
	 * the same text.
	 */
	static <T> String writeBoth(IuJsonAdapter<T> adapter, T value) {
		final var tree = new StringWriter();
		IuJson.PROVIDER.createWriter(tree).write(adapter.toJson(value));
		final var streamed = write(adapter, value);
		assertEquals(tree.toString(), streamed);
		return streamed;
	}

	static Object normalize(Object value) {
		if (value instanceof byte[])
			return List.of(((byte[]) value).length);
		if (value instanceof Calendar)
			return ((Calendar) value).getTime();
		if (value instanceof TimeZone)
			return ((TimeZone) value).getID();
		return value;
	}

	static IuJsonAdapter adapter(Type type) {
		return IuJsonAdapter.of(type);
	}

	@Test
	public void testDefaultsConvertThroughTheTree() {
		final IuJsonAdapter<String> adapter = IuJsonAdapter.from(v -> "read " + v, v -> IuJson.string("wrote " + v));
		try (final var parser = parser("[1]")) {
			assertEquals("read [1]", adapter.read(parser));
		}
		assertEquals("\"wrote x\"", write(adapter, "x"));
	}

	@Test
	public void testText() {
		final var text = adapter(String.class);
		assertEquals("s", read(text, "\"s\""));
		assertNull(read(text, "null"));
		assertEquals("1.5", read(text, "1.5"));
		assertEquals("\"s\"", writeBoth(text, "s"));
		assertEquals("null", writeBoth(text, null));
	}

	@Test
	public void testNumbers() {
		for (final var type : new Class<?>[] { byte.class, Byte.class, short.class, Short.class, int.class,
				Integer.class, long.class, Long.class, float.class, Float.class, double.class, Double.class,
				BigDecimal.class, BigInteger.class, Number.class }) {
			final var number = adapter(type);
			assertEquals("5", read(number, "5").toString().replaceAll("\\.0$", ""), type.getName());
			assertEquals("7", read(number, "\"7\"").toString().replaceAll("\\.0$", ""), type.getName());
			read(number, "null");
			assertThrows(ClassCastException.class, () -> number.read(parser("true")), type.getName());
		}

		assertEquals(0, read(adapter(int.class), "null"));
		assertEquals("5", writeBoth(adapter(Integer.class), 5));
		assertEquals("5", writeBoth(adapter(Long.class), 5L));
		assertEquals("2.5", writeBoth(adapter(Double.class), 2.5));
		assertEquals("null", writeBoth(adapter(Integer.class), null));
	}

	@Test
	public void testBooleans() {
		final var bool = adapter(Boolean.class);
		assertEquals(true, read(bool, "true"));
		assertEquals(false, read(bool, "false"));
		assertNull(read(bool, "null"));
		assertEquals(false, read(adapter(boolean.class), "null"));
		assertEquals(true, read(bool, "\"true\""));
		assertEquals(true, read(bool, "1"));
		assertEquals(false, read(bool, "0"));
		assertEquals(true, read(bool, "{}"));
		assertEquals("true", writeBoth(bool, true));
		assertEquals("null", writeBoth(bool, null));
	}

	@Test
	public void testParsedText() {
		final var uri = adapter(URI.class);
		assertEquals(URI.create("urn:x"), read(uri, "\"urn:x\""));
		assertNull(read(uri, "null"));
		assertEquals("\"urn:x\"", writeBoth(uri, URI.create("urn:x")));
		assertEquals("null", writeBoth(uri, null));
	}

	@Test
	public void testBinary() {
		final var binary = adapter(byte[].class);
		assertArrayEquals(new byte[] { 1, 2, 3 }, (byte[]) read(binary, "\"AQID\""));
		assertNull(read(binary, "null"));
		assertEquals("\"AQID\"", writeBoth(binary, new byte[] { 1, 2, 3 }));
		assertEquals("null", writeBoth(binary, null));
	}

	@Test
	public void testDates() {
		final var date = adapter(Date.class);
		assertEquals(new Date(0), read(date, "\"1970-01-01T00:00:00Z\""));
		assertNull(read(date, "null"));
		// the text form depends on the default time zone, so check the round trip
		assertEquals(new Date(0), read(date, writeBoth(date, new Date(0))));
		assertEquals("null", writeBoth(date, null));

		final var calendar = adapter(Calendar.class);
		assertEquals(new Date(0), ((Calendar) read(calendar, "\"1970-01-01T00:00:00Z\"")).getTime());
		assertNull(read(calendar, "null"));
		final var epoch = Calendar.getInstance();
		epoch.setTimeInMillis(0);
		writeBoth(calendar, epoch);
		assertEquals("null", writeBoth(calendar, null));
	}

	@Test
	public void testTimeZone() {
		final var timeZone = adapter(TimeZone.class);
		assertEquals("UTC", ((TimeZone) read(timeZone, "\"UTC\"")).getID());
		assertNull(read(timeZone, "null"));
		assertEquals("\"UTC\"", writeBoth(timeZone, TimeZone.getTimeZone("UTC")));
		assertEquals("null", writeBoth(timeZone, null));
	}

	@Test
	public void testEnum() {
		final var letter = adapter(Letter.class);
		assertNull(read(letter, "null"));
		assertEquals(Letter.A, read(letter, "\"A\""));
		assertEquals(Letter.B, read(letter, "{\"x\":{\"y\":[1]},\"z\":[1],\"w\":1,\"name\":\"B\"}"));
		assertThrows(IllegalArgumentException.class, () -> letter.read(parser("5")));
		assertEquals(Letter.A, read(letter, "[\"A\"]"));
		assertThrows(NullPointerException.class, () -> letter.read(parser("{}")));
		assertEquals("\"A\"", writeBoth(letter, Letter.A));
		assertEquals("null", writeBoth(letter, null));

		final var upper = IuJsonAdapter.adapt(Letter.class,
				() -> IuJsonSerializationOptions.of(IuJsonPropertyNameFormat.UPPER_CASE_WITH_UNDERSCORES, false, true));
		assertEquals(Letter.B, read(upper, "{\"NAME\":\"B\"}"));
		assertEquals("{\"NAME\":\"B\"}", writeBoth(upper, Letter.B));
	}

	@Test
	public void testOptional() {
		final var optional = adapter(new TypeRef<Optional<String>>() {
		}.type());
		assertEquals(Optional.of("x"), read(optional, "\"x\""));
		assertEquals(Optional.empty(), read(optional, "null"));
		assertEquals("\"x\"", writeBoth(optional, Optional.of("x")));
		assertEquals("null", writeBoth(optional, Optional.empty()));
		assertEquals("null", writeBoth(optional, null));
	}

	@Test
	public void testArrays() {
		final var list = adapter(new TypeRef<List<Integer>>() {
		}.type());
		assertNull(read(list, "null"));
		assertEquals(List.of(), read(list, "[]"));
		assertEquals(List.of(1, 2), read(list, "[1,2]"));
		assertEquals(List.of(5), read(list, "5"));
		assertEquals("[1,2]", writeBoth(list, List.of(1, 2)));
		assertEquals("null", writeBoth(list, null));

		final var array = adapter(String[].class);
		assertArrayEquals(new String[] { "a" }, (String[]) array.read(parser("[\"a\"]")));
	}

	@Test
	public void testObjects() {
		final var map = adapter(new TypeRef<Map<String, Integer>>() {
		}.type());
		assertNull(read(map, "null"));
		assertEquals(Map.of("a", 1), read(map, "{\"a\":1}"));
		assertThrows(ClassCastException.class, () -> map.read(parser("[1]")));
		final Map<String, Integer> value = new LinkedHashMap<>();
		value.put("a", 1);
		value.put("b", null);
		assertEquals("{\"a\":1,\"b\":null}", writeBoth(map, value));
		assertEquals("null", writeBoth(map, null));
	}

	@Test
	public void testBasicRead() {
		final var basic = IuJsonAdapter.basic();
		assertEquals(List.of(BigDecimal.ONE, "x", true, false), read(basic, "[1,\"x\",true,false]"));
		assertEquals(Collections.singletonMap("a", null), read(basic, "{\"a\":null}"));
		assertNull(read(basic, "null"));
	}

	@Test
	public void testBasicWrite() {
		final IuJsonAdapter<Object> basic = IuJsonAdapter.basic();
		assertEquals("null", writeBoth(basic, null));
		assertEquals("{\"a\":1}", writeBoth(basic, IuJson.object().add("a", 1).build()));
		assertEquals("{\"a\":1}", write(basic, IuJson.object().add("a", 1)));
		assertEquals("[1]", write(basic, IuJson.array().add(1)));
		assertEquals("{\"a\":1}", writeBoth(basic, Map.of("a", 1)));
		assertEquals("[1,\"x\"]", writeBoth(basic, new Object[] { 1, "x" }));
		assertEquals("[1]", writeBoth(basic, List.of(1)));
		assertEquals("[1]", writeBoth(basic, new ArrayDeque<>(List.of(1))));
		final Iterable<Integer> iterable = () -> List.of(1).iterator();
		assertEquals("[1]", writeBoth(basic, iterable));
		assertEquals("[1]", write(basic, List.of(1).iterator()));
		assertEquals("[1]", write(basic, Collections.enumeration(List.of(1))));
		assertEquals("[1]", write(basic, Stream.of(1)));
		assertEquals("\"s\"", writeBoth(basic, "s"));
		assertEquals("5", writeBoth(basic, 5));
		assertEquals("5", writeBoth(basic, 5L));
		assertEquals("2.5", writeBoth(basic, 2.5));
		assertEquals("true", writeBoth(basic, true));
		assertEquals("false", writeBoth(basic, false));
		assertThrows(IllegalArgumentException.class, () -> write(basic, new Object()));
	}

	static abstract class TypeRef<T> {
		Type type() {
			return ((java.lang.reflect.ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
		}
	}

	@Test
	public void testLazyTypesCollectEagerly() {
		final var iterator = adapter(new TypeRef<Iterator<String>>() {
		}.type());
		try (final var parser = parser("[\"a\",\"b\"]")) {
			final var items = (Iterator<String>) iterator.read(parser);
			assertFalse(parser.hasNext());
			final List<String> collected = new ArrayList<>();
			items.forEachRemaining(collected::add);
			assertEquals(List.of("a", "b"), collected);
		}
	}

	@Test
	public void testNullItemsInArrays() {
		final Function<Type, IuJsonAdapter<?>> valueAdapter = IuJsonAdapter::of;
		final var list = IuJsonAdapter.of(new TypeRef<List<String>>() {
		}.type(), valueAdapter);
		final var withNull = new ArrayList<String>();
		withNull.add(null);
		assertEquals("[null]", writeBoth(list, withNull));
		assertEquals(withNull, read(list, "[null]"));
		assertEquals(JsonValue.NULL, IuJsonAdapter.of(String.class).toJson(null));
	}

}
