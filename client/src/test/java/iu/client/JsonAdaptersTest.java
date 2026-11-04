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

import static iu.client.StreamingJsonAdaptersTest.adapter;
import static iu.client.StreamingJsonAdaptersTest.parser;
import static iu.client.StreamingJsonAdaptersTest.read;
import static iu.client.StreamingJsonAdaptersTest.writeBoth;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import iu.client.StreamingJsonAdaptersTest.Letter;
import iu.client.StreamingJsonAdaptersTest.TypeRef;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

/**
 * Checks strict reading, map keys, and the standard types added alongside the
 * JSON-B provider, in both tree and streaming conversion.
 */
@SuppressWarnings({ "javadoc", "unchecked", "rawtypes" })
public class JsonAdaptersTest {

	private static void fails(Type type, String json, String message) {
		final IuJsonAdapter adapter = adapter(type);
		assertEquals(message,
				assertThrows(IllegalArgumentException.class, () -> adapter.read(parser(json)), json).getMessage());
		assertThrows(IllegalArgumentException.class, () -> adapter.fromJson(IuJson.parse(json)), json);
	}

	@Test
	public void testExactIntegers() {
		assertEquals(1, read(adapter(int.class), "1.0"));
		assertEquals((byte) -128, read(adapter(byte.class), "-128"));
		assertEquals((short) 32767, read(adapter(Short.class), "32767"));
		assertEquals(1L << 40, read(adapter(long.class), String.valueOf(1L << 40)));
		assertEquals(BigInteger.valueOf(100), read(adapter(BigInteger.class), "1e2"));

		fails(int.class, "1.5", "expected an int, found 1.5");
		fails(Integer.class, String.valueOf(1L << 40), "expected an int, found " + (1L << 40));
		fails(byte.class, "128", "expected a byte, found 128");
		fails(Byte.class, "-129", "expected a byte, found -129");
		fails(short.class, "32768", "expected a short, found 32768");
		fails(Long.class, "9223372036854775808", "expected a long, found 9223372036854775808");
		fails(BigInteger.class, "1.5", "expected an integer, found 1.5");

		// floating-point types read any number
		assertEquals(1.5f, read(adapter(float.class), "1.5"));
		assertEquals(1e300, read(adapter(Double.class), "1e300"));
	}

	@Test
	public void testNumberTextForm() {
		final var integer = (TextForm<Integer>) adapter(Integer.class);
		assertEquals(5, integer.fromText("5"));
		assertEquals("5", integer.toText(5));
		assertThrows(NumberFormatException.class, () -> integer.fromText("x"));

		final var bool = (TextForm<Boolean>) adapter(Boolean.class);
		assertEquals(true, bool.fromText("true"));
		assertEquals(false, bool.fromText("false"));
		assertEquals("true", bool.toText(true));
		assertEquals("expected true or false, found yes",
				assertThrows(IllegalArgumentException.class, () -> bool.fromText("yes")).getMessage());
	}

	@Test
	public void testPrimitiveArrays() {
		assertArrayEquals(new int[] { 1, 2 }, (int[]) read(adapter(int[].class), "[1,2]"));
		assertArrayEquals(new int[] { 0 }, (int[]) read(adapter(int[].class), "[null]"));
		assertArrayEquals(new long[] { 3 }, (long[]) read(adapter(long[].class), "[3]"));
		assertArrayEquals(new short[] { 4 }, (short[]) read(adapter(short[].class), "[4]"));
		assertArrayEquals(new double[] { 1.5 }, (double[]) read(adapter(double[].class), "[1.5]"));
		assertArrayEquals(new float[] { 2.5f }, (float[]) read(adapter(float[].class), "[2.5]"));
		assertArrayEquals(new boolean[] { true, false }, (boolean[]) read(adapter(boolean[].class), "[true,false]"));
		assertArrayEquals(new char[] { 'a' }, (char[]) read(adapter(char[].class), "[\"a\"]"));
		assertNull(read(adapter(int[].class), "null"));

		assertEquals("[1,2]", writeBoth(adapter(int[].class), new int[] { 1, 2 }));
		assertEquals("[true]", writeBoth(adapter(boolean[].class), new boolean[] { true }));
		assertEquals("[\"a\"]", writeBoth(adapter(char[].class), new char[] { 'a' }));
		assertEquals("[]", writeBoth(adapter(double[].class), new double[0]));
		assertEquals("null", writeBoth(adapter(long[].class), null));

		fails(int[].class, "[1.5]", "expected an int, found 1.5");
	}

	@Test
	public void testCharacter() {
		assertEquals('a', read(adapter(Character.class), "\"a\""));
		assertNull(read(adapter(Character.class), "null"));
		assertNull(adapter(Character.class).fromJson(null));
		assertEquals('\0', read(adapter(char.class), "null"));
		assertEquals("\"a\"", writeBoth(adapter(char.class), 'a'));
		assertEquals("null", writeBoth(adapter(Character.class), null));

		fails(Character.class, "\"ab\"", "expected a single character, found 2 characters");
		fails(char.class, "\"\"", "expected a single character, found 0 characters");
		fails(Character.class, "1", "expected a character, found VALUE_NUMBER");
	}

	@Test
	public void testOptionalNumbers() {
		assertEquals(OptionalInt.of(1), read(adapter(OptionalInt.class), "1"));
		assertEquals(OptionalInt.empty(), read(adapter(OptionalInt.class), "null"));
		assertEquals(OptionalLong.of(2), read(adapter(OptionalLong.class), "2"));
		assertEquals(OptionalDouble.of(2.5), read(adapter(OptionalDouble.class), "2.5"));
		assertEquals(OptionalDouble.empty(), adapter(OptionalDouble.class).fromJson(null));

		assertEquals("3", writeBoth(adapter(OptionalInt.class), OptionalInt.of(3)));
		assertEquals("4", writeBoth(adapter(OptionalLong.class), OptionalLong.of(4)));
		assertEquals("0.5", writeBoth(adapter(OptionalDouble.class), OptionalDouble.of(0.5)));
		assertEquals("null", writeBoth(adapter(OptionalInt.class), OptionalInt.empty()));
		assertEquals("null", writeBoth(adapter(OptionalLong.class), null));

		fails(OptionalInt.class, "1.5", "expected an int, found 1.5");
	}

	@Test
	public void testCollections() {
		final var linked = read(adapter(new TypeRef<LinkedList<String>>() {
		}.type()), "[\"a\",\"b\"]");
		assertInstanceOf(LinkedList.class, linked);
		assertEquals(List.of("a", "b"), linked);

		final var queue = (PriorityQueue<Integer>) read(adapter(new TypeRef<PriorityQueue<Integer>>() {
		}.type()), "[3,1,2]");
		assertEquals(1, queue.poll());

		final var letters = read(adapter(new TypeRef<EnumSet<Letter>>() {
		}.type()), "[\"B\"]");
		assertInstanceOf(EnumSet.class, letters);
		assertEquals(EnumSet.of(Letter.B), letters);
		assertEquals("[\"A\",\"B\"]", writeBoth(adapter(new TypeRef<EnumSet<Letter>>() {
		}.type()), EnumSet.allOf(Letter.class)));
	}

	@Test
	public void testUuid() {
		final var id = UUID.randomUUID();
		assertEquals(id, read(adapter(UUID.class), "\"" + id + "\""));
		assertEquals("\"" + id + "\"", writeBoth(adapter(UUID.class), id));
	}

	@Test
	public void testJsonValues() {
		final var object = IuJson.parse("{\"a\":[1]}");
		assertSame(object, adapter(JsonObject.class).fromJson(object));
		assertEquals(object, read(adapter(JsonObject.class), "{\"a\":[1]}"));
		assertEquals(object, read(adapter(JsonStructure.class), "{\"a\":[1]}"));
		assertEquals(object, read(adapter(JsonValue.class), "{\"a\":[1]}"));
		assertEquals(IuJson.parse("[1]"), read(adapter(JsonArray.class), "[1]"));
		assertEquals(IuJson.string("s"), read(adapter(JsonString.class), "\"s\""));
		assertEquals(IuJson.number(1), read(adapter(JsonNumber.class), "1"));
		assertEquals(JsonValue.TRUE, read(adapter(JsonValue.class), "true"));

		// a JSON null is a JsonValue, and null for any narrower type
		assertEquals(JsonValue.NULL, read(adapter(JsonValue.class), "null"));
		assertNull(read(adapter(JsonObject.class), "null"));
		assertNull(adapter(JsonValue.class).fromJson(null));

		assertEquals("{\"a\":[1]}", writeBoth(adapter(JsonObject.class), object));
		assertEquals("null", writeBoth(adapter(JsonArray.class), null));

		fails(JsonObject.class, "[1]", "expected an object, found ARRAY");
		fails(JsonArray.class, "{}", "expected an array, found OBJECT");
		fails(JsonStructure.class, "1", "expected an object or array, found NUMBER");
		fails(JsonString.class, "1", "expected a string, found NUMBER");
		fails(JsonNumber.class, "\"1\"", "expected a number, found STRING");
	}

	@Test
	public void testMapKeys() {
		final var numbers = adapter(new TypeRef<Map<Integer, String>>() {
		}.type());
		assertEquals(Map.of(1, "a"), read(numbers, "{\"1\":\"a\"}"));
		assertEquals("{\"1\":\"a\"}", writeBoth(numbers, Map.of(1, "a")));
		assertThrows(NumberFormatException.class, () -> numbers.read(parser("{\"x\":\"a\"}")));

		final var flags = adapter(new TypeRef<Map<Boolean, Integer>>() {
		}.type());
		assertEquals(Map.of(true, 1), read(flags, "{\"true\":1}"));
		assertEquals("{\"false\":2}", writeBoth(flags, Map.of(false, 2)));
		assertThrows(IllegalArgumentException.class, () -> flags.read(parser("{\"yes\":1}")));

		final var id = UUID.randomUUID();
		final var ids = adapter(new TypeRef<Map<UUID, Integer>>() {
		}.type());
		assertEquals(Map.of(id, 1), read(ids, "{\"" + id + "\":1}"));
		assertEquals("{\"" + id + "\":1}", writeBoth(ids, Map.of(id, 1)));

		final var chars = adapter(new TypeRef<Map<Character, Integer>>() {
		}.type());
		assertEquals(Map.of('c', 1), read(chars, "{\"c\":1}"));

		final var letters = adapter(new TypeRef<EnumMap<Letter, Integer>>() {
		}.type());
		final var read = read(letters, "{\"B\":2}");
		assertInstanceOf(EnumMap.class, read);
		assertEquals(Map.of(Letter.B, 2), read);
		assertEquals("{\"A\":1}", writeBoth(letters, new EnumMap<>(Map.of(Letter.A, 1))));

		// a raw map writes a number or boolean key as its text
		assertEquals("{\"1\":true}", writeBoth(adapter(Map.class), Map.of(1, true)));
		assertEquals("{\"true\":1}", writeBoth(adapter(Map.class), Map.of(true, 1)));
		assertEquals("{\"false\":1}", writeBoth(adapter(Map.class), Map.of(false, 1)));
		fails(Map.class, "[1]", "expected an object, found START_ARRAY");

		// a null key, or a key with no text form, fails
		final Map<Integer, String> nullKey = new LinkedHashMap<>();
		nullKey.put(null, "a");
		assertEquals("null key",
				assertThrows(IllegalArgumentException.class, () -> numbers.toJson(nullKey)).getMessage());
		final var structured = assertThrows(IllegalArgumentException.class,
				() -> adapter(Map.class).toJson(Map.of(List.of(1), 1)));
		assertEquals("key " + List.of(1).getClass().getName() + " has no text form; converts to ARRAY",
				structured.getMessage());
	}

	@Test
	public void testMapKeysIgnoreTheValueFunction() {
		final var adapter = JsonAdapters.adapt(new TypeRef<Map<Integer, Integer>>() {
		}.type(), t -> IuJsonAdapter.from(v -> 99, v -> IuJson.number(99)));
		assertEquals(Map.of(1, 99), adapter.fromJson(IuJson.parse("{\"1\":2}")));
		assertEquals(IuJson.parse("{\"1\":99}"), adapter.toJson(Map.of(1, 2)));
	}

}
