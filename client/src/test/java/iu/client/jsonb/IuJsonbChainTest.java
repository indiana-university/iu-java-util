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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

@SuppressWarnings("javadoc")
public class IuJsonbChainTest {

	static IuJsonb jsonb(JsonbConfig config) {
		return IuJsonbTest.jsonb(config);
	}

	public interface Named {
		String getName();
	}

	public static class Foo implements Named {
		public String getName() {
			return "foo";
		}

		public String getSecret() {
			return "secret";
		}
	}

	public static class Holder {
		public Object any;
		public Foo foo;
		public Named named;
	}

	/**
	 * Records that it ran, then passes control down the chain.
	 */
	public static class Passing<T> implements JsonbSerializer<T>, JsonbDeserializer<T> {
		final String name;
		final List<String> calls;

		Passing(String name, List<String> calls) {
			this.name = name;
			this.calls = calls;
		}

		@Override
		public void serialize(T obj, JsonGenerator generator, SerializationContext ctx) {
			calls.add(name);
			ctx.serialize(obj, generator);
		}

		@Override
		public T deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			calls.add(name);
			return ctx.deserialize(rtType, parser);
		}
	}

	@Test
	public void testSerializersPassControlDown() {
		final List<String> calls = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withSerializers( //
				new Passing<Object>("object", calls) {
				}, new Passing<Foo>("foo", calls) {
				}, new Passing<Foo>("foo again", calls) {
				}));
		final var holder = new Holder();
		holder.foo = new Foo();
		assertEquals("{\"foo\":{\"name\":\"foo\",\"secret\":\"secret\"}}", jsonb.toJson(holder));
		// the holder, then the foo, down its chain; the Object serializer leaves the
		// foo's strings alone
		assertEquals(List.of("object", "foo", "foo again", "object"), calls);
	}

	@Test
	public void testOrderDoesNotDependOnTheDeclaredType() {
		final List<String> calls = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withSerializers( //
				new Passing<Object>("object", calls) {
				}, new Passing<Foo>("foo", calls) {
				}));
		final var holder = new Holder();
		holder.named = new Foo();
		assertEquals("{\"named\":{\"name\":\"foo\"}}", jsonb.toJson(holder));
		// the holder, then named, down the Foo's chain though declared as a Named; the
		// Object serializer leaves its name alone
		assertEquals(List.of("object", "foo", "object"), calls);
	}

	@Test
	public void testContinuationKeepsTheDeclaredConversion() {
		final List<String> calls = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withSerializers(new Passing<Object>("object", calls) {
		}));
		final var holder = new Holder();
		holder.named = new Foo();
		// written as a Named, the type it's declared as, so without the secret
		assertEquals("{\"named\":{\"name\":\"foo\"}}", jsonb.toJson(holder));
		assertEquals("{\"named\":{\"name\":\"foo\"}}", jsonb.adapt(Holder.class).toJson(holder).toString());
	}

	@Test
	public void testDeserializersPassControlDown() {
		final List<String> calls = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withDeserializers( //
				new Passing<Object>("object", calls) {
				}, new Passing<Foo>("foo", calls) {
				}, new Passing<Foo>("foo again", calls) {
				}));
		jsonb.fromJson("{\"foo\":{}}", Holder.class);
		assertEquals(List.of("object", "foo", "foo again", "object"), calls);
	}

	/**
	 * Writes the value itself, ending the chain.
	 */
	public static class Ends implements JsonbSerializer<Named> {
		@Override
		public void serialize(Named obj, JsonGenerator generator, SerializationContext ctx) {
			generator.write("ended");
		}
	}

	@Test
	public void testTreeConversionPassesControlDown() {
		final List<String> calls = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withSerializers(new Passing<Foo>("foo", calls) {
		}, new Ends()));
		final var holder = new Holder();
		holder.foo = new Foo();
		assertEquals("{\"foo\":\"ended\"}", jsonb.adapt(Holder.class).toJson(holder).toString());
		assertEquals(List.of("foo"), calls);
	}

	/**
	 * Adapts text to a string, which is itself text.
	 */
	public static class PassThrough implements JsonbAdapter<CharSequence, String> {
		int toJson;
		int fromJson;

		@Override
		public String adaptToJson(CharSequence obj) {
			toJson++;
			return obj == null ? null : obj.toString();
		}

		@Override
		public CharSequence adaptFromJson(String obj) {
			fromJson++;
			return obj;
		}
	}

	public static class Text {
		public String text;
	}

	@Test
	public void testAdapterAppliesOnceToItsOwnOutput() {
		final var adapter = new PassThrough();
		final var jsonb = jsonb(new JsonbConfig().withAdapters(adapter));
		final var text = new Text();
		text.text = "t";
		assertEquals("{\"text\":\"t\"}", jsonb.toJson(text));
		assertEquals(1, adapter.toJson);
		assertEquals("t", jsonb.fromJson("{\"text\":\"t\"}", Text.class).text);
		assertEquals(1, adapter.fromJson);
		assertEquals("{\"text\":\"t\"}", jsonb.adapt(Text.class).toJson(text).toString());
		assertEquals("t", ((Text) jsonb.adapt(Text.class).fromJson(IuJson.parse("{\"text\":\"t\"}"))).text);
	}

	@Test
	public void testAdapterAppliesOnceThroughSerializersAndDeserializers() {
		final var adapter = new PassThrough();
		final List<String> calls = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withAdapters(adapter) //
				.withSerializers(new Passing<String>("string", calls) {
				}) //
				.withDeserializers(new Passing<String>("string", calls) {
				}));
		final var text = new Text();
		text.text = "t";
		assertEquals("{\"text\":\"t\"}", jsonb.toJson(text));
		assertEquals(1, adapter.toJson);
		assertEquals("t", jsonb.fromJson("{\"text\":\"t\"}", Text.class).text);
		assertEquals(1, adapter.fromJson);
		assertEquals(List.of("string", "string"), calls);
	}

	public static class Celsius {
		public int degrees;
	}

	public static class Kelvin {
		public int degrees;
	}

	public static class ToKelvin implements JsonbAdapter<Celsius, Kelvin> {
		@Override
		public Kelvin adaptToJson(Celsius obj) {
			final var k = new Kelvin();
			k.degrees = obj.degrees + 273;
			return k;
		}

		@Override
		public Celsius adaptFromJson(Kelvin obj) {
			final var c = new Celsius();
			c.degrees = obj.degrees - 273;
			return c;
		}
	}

	public static class ToCelsius implements JsonbAdapter<Kelvin, Celsius> {
		@Override
		public Celsius adaptToJson(Kelvin obj) {
			final var c = new Celsius();
			c.degrees = obj.degrees - 273;
			return c;
		}

		@Override
		public Kelvin adaptFromJson(Celsius obj) {
			final var k = new Kelvin();
			k.degrees = obj.degrees + 273;
			return k;
		}
	}

	@Test
	public void testAdapterCycleEndsInTheBuiltInConversion() {
		final var jsonb = jsonb(new JsonbConfig().withAdapters(new ToKelvin(), new ToCelsius()));
		final var celsius = new Celsius();
		celsius.degrees = 20;
		assertEquals("{\"degrees\":20}", jsonb.toJson(celsius));
		assertEquals(20, jsonb.fromJson("{\"degrees\":20}", Celsius.class).degrees);
	}

	public static class FromText implements JsonbAdapter<Instant, String> {
		@Override
		public String adaptToJson(Instant obj) {
			return obj.toString();
		}

		@Override
		public Instant adaptFromJson(String obj) {
			return Instant.parse(obj);
		}
	}

	public static class FromEpoch implements JsonbAdapter<Instant, Long> {
		@Override
		public Long adaptToJson(Instant obj) {
			return obj.toEpochMilli();
		}

		@Override
		public Instant adaptFromJson(Long obj) {
			return Instant.ofEpochMilli(obj);
		}
	}

	public static class When {
		public Instant when;
	}

	@Test
	public void testAdaptersReadSeveralFormats() {
		final var jsonb = jsonb(new JsonbConfig().withAdapters(new FromText(), new FromEpoch()));
		final var epoch = Instant.ofEpochMilli(1_700_000_000_000L);
		assertEquals(epoch, jsonb.fromJson("{\"when\":\"" + epoch + "\"}", When.class).when);
		assertEquals(epoch, jsonb.fromJson("{\"when\":1700000000000}", When.class).when);
		assertEquals(epoch,
				((When) jsonb.adapt(When.class).fromJson(IuJson.parse("{\"when\":1700000000000}"))).when);

		// the first adapter writes
		final var when = new When();
		when.when = epoch;
		assertEquals("{\"when\":\"" + epoch + "\"}", jsonb.toJson(when));
	}

	public static class Flag {
		final String text;

		Flag(String text) {
			this.text = text;
		}
	}

	public static class FlagText implements JsonbAdapter<Flag, String> {
		@Override
		public String adaptToJson(Flag obj) {
			return obj.text;
		}

		@Override
		public Flag adaptFromJson(String obj) {
			return new Flag(obj);
		}
	}

	public static class FlagNumber implements JsonbAdapter<Flag, Integer> {
		@Override
		public Integer adaptToJson(Flag obj) {
			return obj.text.length();
		}

		@Override
		public Flag adaptFromJson(Integer obj) {
			return new Flag("#" + obj);
		}
	}

	public static class Flags {
		public Flag flag;
	}

	@Test
	public void testFirstAdapterReadsAShapeNoneAccepts() {
		final var jsonb = jsonb(new JsonbConfig().withAdapters(new FlagText(), new FlagNumber()));
		assertEquals("true", jsonb.fromJson("{\"flag\":true}", Flags.class).flag.text);
		assertEquals("#5", jsonb.fromJson("{\"flag\":5}", Flags.class).flag.text);
		assertEquals("x", jsonb.fromJson("{\"flag\":\"x\"}", Flags.class).flag.text);
	}

	public static class Wrapped {
		public Text text;
	}

	/**
	 * Reads a value embedded as JSON text, from a parser of its own.
	 */
	public static class Embedded implements JsonbDeserializer<Wrapped> {
		@Override
		public Wrapped deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final var wrapped = new Wrapped();
			try (final var embedded = IuJson.PROVIDER.createParser(new StringReader(parser.getString()))) {
				wrapped.text = ctx.deserialize(Text.class, embedded);
			}
			return wrapped;
		}
	}

	@Test
	public void testDeserializerReadsFromItsOwnParser() {
		final var jsonb = jsonb(new JsonbConfig().withDeserializers(new Embedded()));
		assertEquals("t", jsonb.fromJson("\"{\\\"text\\\":\\\"t\\\"}\"", Wrapped.class).text.text);
	}

	public static class Scalars {
		public Object any = "a";
		public int count = 1;
		public boolean flag = true;
		public String none;
		public Long total = 2L;
		public Instant when = Instant.EPOCH;
	}

	static String seen(Object value) {
		return value == null ? "null" : value.getClass().getSimpleName();
	}

	/**
	 * Records the values it sees, then passes control down the chain.
	 */
	public static class Sees implements JsonbSerializer<Object>, JsonbDeserializer<Object> {
		final List<String> seen;

		Sees(List<String> seen) {
			this.seen = seen;
		}

		@Override
		public void serialize(Object obj, JsonGenerator generator, SerializationContext ctx) {
			seen.add(seen(obj));
			ctx.serialize(obj, generator);
		}

		@Override
		public Object deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			seen.add(parser.currentEvent().name());
			return ctx.deserialize(rtType, parser);
		}
	}

	@Test
	public void testObjectSerializerLeavesScalarsAlone() {
		final List<String> seen = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withSerializers(new Sees(seen)));
		final var json = "{\"any\":\"a\",\"count\":1,\"flag\":true,\"total\":2,\"when\":\"1970-01-01T00:00:00Z\"}";
		assertEquals(json, jsonb.toJson(new Scalars()));
		// text, numbers, and booleans, even declared as Object; a date is not scalar
		assertEquals(List.of("Scalars", "Instant"), seen);

		seen.clear();
		assertEquals(json, jsonb.adapt(Scalars.class).toJson(new Scalars()).toString());
		assertEquals(List.of("Scalars", "Instant"), seen);
	}

	@Test
	public void testObjectSerializerSeesNullsOfOtherTypes() {
		final List<String> seen = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withNullValues(true).withSerializers(new Sees(seen)));
		assertEquals("{\"any\":null,\"count\":null,\"text\":null,\"when\":null}", jsonb.toJson(new Nulls()));
		// any and when, but not the null declared as text or a number
		assertEquals(List.of("Nulls", "null", "null"), seen);

		seen.clear();
		assertEquals("null", jsonb.toJson(null));
		assertEquals(List.of("null"), seen);
	}

	public static class Nulls {
		public Object any;
		public Integer count;
		public String text;
		public Instant when;
	}

	/**
	 * Writes a null property of its own, with no declared type.
	 */
	public static class Untyped implements JsonbSerializer<Text> {
		@Override
		public void serialize(Text obj, JsonGenerator generator, SerializationContext ctx) {
			generator.writeStartObject();
			ctx.serialize("text", null, generator);
			generator.writeEnd();
		}
	}

	@Test
	public void testObjectSerializerSeesUntypedNulls() {
		final List<String> seen = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withSerializers(new Sees(seen), new Untyped()));
		assertEquals("{\"text\":null}", jsonb.toJson(new Text()));
		assertEquals(List.of("null"), seen);
	}

	@Test
	public void testScalarSerializersStillApply() {
		final List<String> calls = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withSerializers( //
				new Passing<Object>("object", calls) {
				}, new Passing<CharSequence>("text", calls) {
				}, new Passing<Number>("number", calls) {
				}, new Passing<Boolean>("boolean", calls) {
				}));
		jsonb.toJson(new Scalars());
		assertEquals(List.of("object", "text", "number", "boolean", "number", "object"), calls);
	}

	@Test
	public void testObjectDeserializerLeavesScalarsAlone() {
		final List<String> seen = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withDeserializers(new Sees(seen)));
		final var scalars = jsonb.fromJson(
				"{\"any\":\"b\",\"count\":2,\"flag\":false,\"none\":null,\"total\":3,\"when\":\"1970-01-01T00:00:01Z\"}",
				Scalars.class);
		assertEquals("b", scalars.any);
		assertEquals(2, scalars.count);
		assertFalse(scalars.flag);
		assertNull(scalars.none);
		assertEquals(3L, scalars.total);
		assertEquals(Instant.ofEpochSecond(1), scalars.when);
		// JSON text is left alone whatever it reads as, even an Object or a date, and
		// so is a null read as text
		assertEquals(List.of("START_OBJECT"), seen);

		seen.clear();
		assertNull(jsonb.fromJson("{\"any\":null}", Scalars.class).any);
		assertEquals(List.of("START_OBJECT", "VALUE_NULL"), seen);

		seen.clear();
		assertEquals("b", jsonb.adapt(Object.class).fromJson(IuJson.PROVIDER.createValue("b")));
		assertNull(jsonb.adapt(String.class).fromJson(JsonValue.NULL));
		assertEquals(List.of(), seen);
		assertNull(jsonb.adapt(Object.class).fromJson(JsonValue.NULL));
		assertEquals(List.of("VALUE_NULL"), seen);
	}

	/**
	 * Tags everything it adapts.
	 */
	public static class Tags implements JsonbAdapter<Object, String> {
		final List<String> seen = new ArrayList<>();

		@Override
		public String adaptToJson(Object obj) {
			seen.add(seen(obj));
			return obj == null ? null : "tag:" + obj;
		}

		@Override
		public Object adaptFromJson(String obj) {
			seen.add(seen(obj));
			return obj;
		}
	}

	@Test
	public void testObjectAdapterLeavesScalarsAlone() {
		final var tags = new Tags();
		final var jsonb = jsonb(new JsonbConfig().withAdapters(tags));
		assertEquals("\"a\"", jsonb.toJson("a"));
		assertEquals("1", jsonb.toJson(1));
		assertEquals("true", jsonb.toJson(true));
		assertEquals("\"b\"", jsonb.adapt(String.class).toJson("b").toString());
		assertEquals(List.of(), tags.seen);

		assertEquals("\"tag:1970-01-01T00:00:00Z\"", jsonb.toJson(Instant.EPOCH));
		assertEquals("\"tag:1970-01-01T00:00:00Z\"", jsonb.adapt(Instant.class).toJson(Instant.EPOCH).toString());
		assertEquals(List.of("Instant", "Instant"), tags.seen);

		// a null declared as text is left alone; one of another type, or none, isn't
		tags.seen.clear();
		assertEquals("null", jsonb.toJson(null, String.class));
		assertEquals(List.of(), tags.seen);
		assertEquals("null", jsonb.toJson(null, Instant.class));
		assertEquals("null", jsonb.toJson(null));
		assertEquals(List.of("null", "null"), tags.seen);

		tags.seen.clear();
		assertEquals("b", jsonb.fromJson("\"b\"", Object.class));
		assertEquals(true, jsonb.fromJson("true", Object.class));
		assertEquals("b", jsonb.adapt(Object.class).fromJson(IuJson.PROVIDER.createValue("b")));
		assertNull(jsonb.fromJson("null", String.class));
		assertNull(jsonb.adapt(String.class).fromJson(JsonValue.NULL));
		assertEquals(List.of(), tags.seen);

		assertNull(jsonb.fromJson("null", Instant.class));
		assertNull(jsonb.adapt(Instant.class).fromJson(JsonValue.NULL));
		assertEquals(List.of("null", "null"), tags.seen);
	}

	@Test
	public void testIsScalarShape() {
		assertTrue(IuJsonbValueAdapter.isScalar(ValueType.STRING));
		assertTrue(IuJsonbValueAdapter.isScalar(ValueType.NUMBER));
		assertTrue(IuJsonbValueAdapter.isScalar(ValueType.TRUE));
		assertTrue(IuJsonbValueAdapter.isScalar(ValueType.FALSE));
		assertFalse(IuJsonbValueAdapter.isScalar(ValueType.NULL));
		assertFalse(IuJsonbValueAdapter.isScalar(ValueType.OBJECT));
		assertFalse(IuJsonbValueAdapter.isScalar(ValueType.ARRAY));
		assertFalse(IuJsonbValueAdapter.isScalar(null));
	}

	enum Letter {
		A
	}

	@Test
	public void testAccepts() {
		assertTrue(IuJsonbValueAdapter.accepts(Object.class, ValueType.ARRAY));
		assertTrue(IuJsonbValueAdapter.accepts(JsonString.class, ValueType.NUMBER));
		assertTrue(IuJsonbValueAdapter.accepts(boolean.class, ValueType.TRUE));
		assertTrue(IuJsonbValueAdapter.accepts(Boolean.class, ValueType.FALSE));
		assertFalse(IuJsonbValueAdapter.accepts(Boolean.class, ValueType.STRING));
		assertTrue(IuJsonbValueAdapter.accepts(int.class, ValueType.NUMBER));
		assertFalse(IuJsonbValueAdapter.accepts(Integer.class, ValueType.STRING));
		assertTrue(IuJsonbValueAdapter.accepts(Map.class, ValueType.OBJECT));
		assertFalse(IuJsonbValueAdapter.accepts(Map.class, ValueType.ARRAY));
		assertTrue(IuJsonbValueAdapter.accepts(byte[].class, ValueType.STRING));
		assertFalse(IuJsonbValueAdapter.accepts(byte[].class, ValueType.ARRAY));
		for (final var arrayLike : new Class<?>[] { String[].class, List.class, Iterator.class, Enumeration.class,
				Stream.class }) {
			assertTrue(IuJsonbValueAdapter.accepts(arrayLike, ValueType.ARRAY), arrayLike.getName());
			assertFalse(IuJsonbValueAdapter.accepts(arrayLike, ValueType.OBJECT), arrayLike.getName());
		}
		assertTrue(IuJsonbValueAdapter.accepts(Letter.class, ValueType.STRING));
		assertTrue(IuJsonbValueAdapter.accepts(Letter.class, ValueType.OBJECT));
		assertFalse(IuJsonbValueAdapter.accepts(Letter.class, ValueType.NUMBER));
		assertTrue(IuJsonbValueAdapter.accepts(Instant.class, ValueType.STRING));
		assertFalse(IuJsonbValueAdapter.accepts(Instant.class, ValueType.NUMBER));
		assertTrue(IuJsonbValueAdapter.accepts(Foo.class, ValueType.OBJECT));
		assertFalse(IuJsonbValueAdapter.accepts(Foo.class, ValueType.STRING));
	}

	@Test
	public void testShape() {
		assertEquals(ValueType.OBJECT, IuJsonbValueAdapter.shape(Event.START_OBJECT));
		assertEquals(ValueType.ARRAY, IuJsonbValueAdapter.shape(Event.START_ARRAY));
		assertEquals(ValueType.STRING, IuJsonbValueAdapter.shape(Event.VALUE_STRING));
		assertEquals(ValueType.NUMBER, IuJsonbValueAdapter.shape(Event.VALUE_NUMBER));
		assertEquals(ValueType.TRUE, IuJsonbValueAdapter.shape(Event.VALUE_TRUE));
		assertEquals(ValueType.FALSE, IuJsonbValueAdapter.shape(Event.VALUE_FALSE));
		assertEquals(ValueType.NULL, IuJsonbValueAdapter.shape(Event.VALUE_NULL));
	}

}
