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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import iu.client.jsonb.IuJsonbChainTest.Sees;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;

@SuppressWarnings({ "javadoc", "rawtypes" })
public class IuJsonbRuntimeTypeTest {

	static IuJsonb jsonb(JsonbConfig config) {
		return IuJsonbTest.jsonb(config);
	}

	public static class Foo {
		public String getName() {
			return "foo";
		}
	}

	public interface Named {
		String getName();
	}

	public static class Tagged implements Named {
		@Override
		public String getName() {
			return "t";
		}

		public String getTag() {
			return "x";
		}
	}

	public enum Level {
		LOW, HIGH
	}

	public static class Holder {
		public Object array;
		public Object bean;
		public Map<String, Object> byName;
		public Comparable<?> comparable;
		public List<Object> items;
		public Object level;
		public Object list;
		public Object map;
		public Named named;
		public Object number;
		public Serializable serializable;
		public Object set;
		public Object text;
		public Object when;
		public Object zone;
	}

	@Test
	public void testBroadDeclaredTypesWriteAsTheirRuntimeType() {
		final var holder = new Holder();
		holder.array = new Object[] { new Foo(), 1 };
		holder.bean = new Foo();
		holder.byName = Map.of("f", new Foo());
		holder.comparable = "c";
		holder.items = List.of(new Foo(), "s");
		holder.level = Level.HIGH;
		holder.list = List.of(new Foo());
		holder.map = Map.of("k", new Foo());
		holder.named = new Tagged();
		holder.number = new AtomicInteger(5);
		holder.serializable = 7;
		holder.set = Set.of(1);
		holder.text = new StringBuilder("sb");
		holder.when = Instant.EPOCH;
		holder.zone = TimeZone.getTimeZone("UTC");

		// named is declared as an interface, which isn't broad, so writes only what
		// the interface declares
		final var json = "{\"array\":[{\"name\":\"foo\"},1],\"bean\":{\"name\":\"foo\"},"
				+ "\"byName\":{\"f\":{\"name\":\"foo\"}},\"comparable\":\"c\",\"items\":[{\"name\":\"foo\"},\"s\"],"
				+ "\"level\":\"HIGH\",\"list\":[{\"name\":\"foo\"}],\"map\":{\"k\":{\"name\":\"foo\"}},"
				+ "\"named\":{\"name\":\"t\"},\"number\":5,\"serializable\":7,\"set\":[1],\"text\":\"sb\","
				+ "\"when\":\"1970-01-01T00:00:00Z\",\"zone\":\"UTC\"}";
		final var jsonb = jsonb(new JsonbConfig());
		assertEquals(json, jsonb.toJson(holder));
		assertEquals(json, jsonb.adapt(Holder.class).toJson(holder).toString());
	}

	@Test
	public void testUnsupportedRuntimeType() {
		final var jsonb = jsonb(new JsonbConfig());
		final var holder = new Holder();
		holder.bean = Thread.currentThread();
		final var error = assertThrows(JsonbException.class, () -> jsonb.toJson(holder));
		assertTrue(error.getMessage().startsWith("failed to write Holder.bean"), error::getMessage);
		assertEquals("Unsupported for JSON conversion: class java.lang.Thread", error.getCause().getMessage());

		// a plain Object has nothing to write, in either mode
		holder.bean = new Object();
		for (final var failure : List.of(assertThrows(JsonbException.class, () -> jsonb.toJson(holder)),
				assertThrows(JsonbException.class, () -> jsonb.adapt(Holder.class).toJson(holder))))
			assertEquals("Unsupported for JSON conversion: class java.lang.Object", failure.getCause().getMessage());
	}

	@Test
	public void testNullObjectsIncluded() {
		final var jsonb = jsonb(new JsonbConfig().withNullValues(true));
		final var json = jsonb.adapt(Holder.class).toJson(new Holder()).asJsonObject();
		assertEquals(jakarta.json.JsonValue.NULL, json.get("bean"));
		assertEquals(json.toString(), jsonb.toJson(new Holder()));
	}

	public static class Names {
		public Iterable<String> names = List.of("a");
	}

	@Test
	public void testIterableIsBroadButConvertsAsItself() {
		final var jsonb = jsonb(new JsonbConfig());
		assertEquals("{\"names\":[\"a\"]}", jsonb.toJson(new Names()));
		final var names = jsonb.fromJson("{\"names\":[\"b\"]}", Names.class).names.iterator();
		assertEquals("b", names.next());
		assertTrue(!names.hasNext());
	}

	@Test
	public void testObjectReadsAsJsonTypes() {
		final var jsonb = jsonb(new JsonbConfig());
		final var json = "{\"bean\":{\"a\":[1,\"b\",true,null]},\"serializable\":\"x\"}";
		for (final var holder : List.of(jsonb.fromJson(json, Holder.class),
				(Holder) jsonb.adapt(Holder.class).fromJson(IuJson.parse(json)))) {
			final var bean = (Map) holder.bean;
			final var a = (List) bean.get("a");
			assertEquals(new BigDecimal("1"), a.get(0));
			assertEquals("b", a.get(1));
			assertEquals(true, a.get(2));
			assertNull(a.get(3));
			assertEquals("x", holder.serializable);
		}

		// what reads must still be an instance of the declared type
		final var error = assertThrows(JsonbException.class,
				() -> jsonb.fromJson("{\"comparable\":[1]}", Holder.class));
		assertTrue(error.getMessage().startsWith("failed to read Holder.comparable"), error::getMessage);
	}

	@Test
	public void testNestedObjectValuesHonorConfiguration() {
		final List<String> seen = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withDeserializers(new Sees(seen)).withSerializers(new Sees(seen)));
		jsonb.fromJson("{\"bean\":{\"a\":{\"b\":1}},\"list\":[{\"c\":2}]}", Holder.class);
		// the holder, bean, a, list, and the list's item; none of the numbers
		assertEquals(List.of("START_OBJECT", "START_OBJECT", "START_OBJECT", "START_ARRAY", "START_OBJECT"), seen);

		seen.clear();
		final var holder = new Holder();
		holder.map = Map.of("k", Map.of("n", new Foo()));
		assertEquals("{\"map\":{\"k\":{\"n\":{\"name\":\"foo\"}}}}", jsonb.toJson(holder));
		assertEquals(List.of("Holder", "Map1", "Map1", "Foo"), seen);
	}

	/**
	 * Passes a JDK-internal list back to its context.
	 */
	public static class AsList implements JsonbSerializer<Foo> {
		@Override
		public void serialize(Foo obj, JsonGenerator generator, SerializationContext ctx) {
			ctx.serialize(List.of(obj.getName()), generator);
		}
	}

	@Test
	public void testSerializerPassesAJdkCollection() {
		final var jsonb = jsonb(new JsonbConfig().withSerializers(new AsList()));
		assertEquals("[\"foo\"]", jsonb.toJson(new Foo()));
		assertEquals("[\"foo\"]", jsonb.adapt(Foo.class).toJson(new Foo()).toString());
	}

	@Test
	public void testConversionTypes() {
		final var jsonb = jsonb(new JsonbConfig());
		final Map<Type, Class<?>> expected = Map.ofEntries( //
				Map.entry(List.of(1).getClass(), List.class), //
				Map.entry(Map.of().getClass(), Map.class), //
				Map.entry(new HashMap<>().keySet().getClass(), Set.class), //
				Map.entry(EnumSet.of(Level.LOW).getClass(), Set.class), //
				Map.entry(Collections.unmodifiableCollection(List.of()).getClass(), java.util.Collection.class), //
				Map.entry(java.nio.file.Path.of("p").getClass(), Iterable.class), //
				Map.entry(List.of().iterator().getClass(), java.util.Iterator.class), //
				Map.entry(Collections.emptyEnumeration().getClass(), java.util.Enumeration.class), //
				Map.entry(Stream.of(1).getClass(), Stream.class), //
				Map.entry(AtomicInteger.class, Number.class), //
				Map.entry(TimeZone.getTimeZone("UTC").getClass(), TimeZone.class), //
				Map.entry(StringBuilder.class, CharSequence.class), //
				Map.entry(String.class, String.class), //
				Map.entry(Thread.class, Object.class), //
				Map.entry(Runnable.class, Object.class));
		for (final var entry : expected.entrySet())
			assertEquals(entry.getValue(), jsonb.conversionType((Class<?>) entry.getKey()), entry.getKey().toString());

		assertInstanceOf(Class.class, jsonb.conversionType(String.class));
	}

	public interface Key {
		String getKid();
	}

	public interface Keys {
		Iterable<? extends Key> getKeys();
	}

	public static class KeyImpl implements Key {
		@Override
		public String getKid() {
			return "a";
		}
	}

	/**
	 * An iterable of a class of the application's own.
	 */
	public static class CustomIterable implements Iterable<Key> {
		@Override
		public java.util.Iterator<Key> iterator() {
			return List.<Key>of(new KeyImpl()).iterator();
		}
	}

	@Test
	public void testIterableOfAnyClassWritesAsArray() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig());
		final var json = "{\"keys\":[{\"kid\":\"a\"}]}";
		final Keys list = () -> List.of(new KeyImpl());
		final Keys lambda = () -> () -> List.<Key>of(new KeyImpl()).iterator();
		final Keys custom = CustomIterable::new;
		for (final var keys : List.of(list, lambda, custom)) {
			assertEquals(json, jsonb.toJson(keys, Keys.class));
			assertEquals(json, jsonb.adapt(Keys.class).toJson(keys).toString());
			assertEquals(IuJson.parse(json), edu.iu.client.IuJsonAdapter
					.adapt(Keys.class, () -> edu.iu.client.IuJsonSerializationOptions.DEFAULT).toJson(keys));
		}

		// and reads back
		final var read = jsonb.fromJson(jsonb.toJson(lambda, Keys.class), Keys.class);
		assertEquals("a", read.getKeys().iterator().next().getKid());
	}

	public static class KeyList extends ArrayList<Key> {
		private static final long serialVersionUID = 1L;
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOwnContainerClassesConvertAsTheirContainer() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig());
		final var array = "[{\"kid\":\"a\"}]";
		final var list = new KeyList();
		list.add(new KeyImpl());
		final Iterable<Key> lambda = () -> List.<Key>of(new KeyImpl()).iterator();
		for (final Object value : List.of(new CustomIterable(), list, lambda)) {
			assertEquals(array, jsonb.toJson(value), value::toString);
			assertEquals(IuJson.parse(array), ((edu.iu.client.IuJsonAdapter<Object>) edu.iu.client.IuJsonAdapter
					.adapt((Type) value.getClass(), () -> edu.iu.client.IuJsonSerializationOptions.DEFAULT)).toJson(value));

			// put with no type, as its runtime class
			assertEquals("{\"keys\":" + array + "}",
					jsonb.toJson(edu.iu.client.IuJsonProperties.builder(jsonb).put("keys", value).build()));
		}

		// read into a new instance, items as the element type
		final var iu = edu.iu.client.IuJsonAdapter.adapt(KeyList.class,
				() -> edu.iu.client.IuJsonSerializationOptions.DEFAULT);
		for (final KeyList read : List.of(jsonb.fromJson(array, KeyList.class),
				(KeyList) jsonb.adapt(KeyList.class).fromJson(IuJson.parse(array)), iu.fromJson(IuJson.parse(array)),
				iterate(iu, array))) {
			assertEquals(1, read.size());
			assertEquals("a", read.get(0).getKid());
		}
		assertNull(jsonb.fromJson("null", KeyList.class));
		assertNull(iu.fromJson(jakarta.json.JsonValue.NULL));

		final var bag = jsonb.fromJson("{\"a\":1}", Bag.class);
		assertEquals(1, bag.get("a"));
		assertEquals("{\"a\":1}", jsonb.toJson(bag));

		// only a collection or map is created to read
		assertTrue(assertThrows(jakarta.json.bind.JsonbException.class, () -> jsonb.fromJson(array, CustomIterable.class))
				.getMessage().contains("can't read " + CustomIterable.class.getName() + " from JSON"));
	}

	public static class Bag extends HashMap<String, Integer> {
		private static final long serialVersionUID = 1L;
	}

	private static KeyList iterate(edu.iu.client.IuJsonAdapter<KeyList> adapter, String json) {
		try (final var parser = IuJson.PROVIDER.createParser(new java.io.StringReader(json))) {
			parser.next();
			return adapter.read(parser);
		}
	}

}
