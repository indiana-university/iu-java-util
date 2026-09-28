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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

@SuppressWarnings({ "javadoc", "unchecked", "rawtypes" })
public class IuJsonbConformanceTest {

	static IuJsonb jsonb(JsonbConfig config) {
		return IuJsonbTest.jsonb(config);
	}

	static void failsAt(String prefix, Supplier<Object> conversion) {
		final var error = assertThrows(JsonbException.class, conversion::get);
		assertTrue(error.getMessage().startsWith(prefix), error::getMessage);
	}

	// ---- failure paths name items and entries

	public static class Item {
		public int count;

		public String getBoom() {
			if (count < 0)
				throw new IllegalStateException("boom");
			return "ok";
		}
	}

	public static class Holder {
		public List<Integer> numbers;
		public Map<String, Integer> byName;
		public Map<Integer, String> byNumber;
		public List<Item> items;
		public Map<String, Item> itemsByName;
	}

	@Test
	public void testReadFailuresNameTheItem() {
		final var jsonb = jsonb(new JsonbConfig());
		for (final var json : List.of("{\"numbers\":[1,\"x\"]}", "{\"byName\":{\"a\":1,\"k\\\"q\":\"x\"}}",
				"{\"items\":[{\"count\":1},{\"count\":\"x\"}]}", "{\"itemsByName\":{\"k\":{\"count\":\"x\"}}}",
				"{\"byNumber\":{\"x\":\"a\"}}")) {
			final String path;
			if (json.contains("numbers"))
				path = "Holder.numbers[1]";
			else if (json.contains("byName"))
				path = "Holder.byName[\"k\\\"q\"]";
			else if (json.contains("itemsByName"))
				path = "Holder.itemsByName[\"k\"].count";
			else if (json.contains("items"))
				path = "Holder.items[1].count";
			else
				path = "Holder.byNumber[\"x\"]";

			failsAt("failed to read " + path + " (line 1", () -> jsonb.fromJson(json, Holder.class));
			failsAt("failed to read " + path + ":", () -> jsonb.adapt(Holder.class).fromJson(IuJson.parse(json)));
		}
	}

	@Test
	public void testWriteFailuresNameTheItem() {
		final var jsonb = jsonb(new JsonbConfig());
		final var failing = new Item();
		failing.count = -1;

		final var inList = new Holder();
		inList.items = List.of(new Item(), failing);
		failsAt("failed to write Holder.items[1].boom: boom", () -> jsonb.toJson(inList));
		failsAt("failed to write Holder.items[1].boom: boom", () -> jsonb.adapt(Holder.class).toJson(inList));

		final var inMap = new Holder();
		inMap.itemsByName = Map.of("k", failing);
		failsAt("failed to write Holder.itemsByName[\"k\"].boom: boom", () -> jsonb.toJson(inMap));
		failsAt("failed to write Holder.itemsByName[\"k\"].boom: boom",
				() -> jsonb.adapt(Holder.class).toJson(inMap));
	}

	public static class Lazy {
		public Iterable<Integer> numbers;
	}

	@Test
	public void testLazyItemsConvertOutsideTheCall() {
		final var jsonb = jsonb(new JsonbConfig());
		// tree mode converts an Iterable lazily; streaming collects it as the parser
		// moves on
		final var numbers = ((Lazy) jsonb.adapt(Lazy.class).fromJson(IuJson.parse("{\"numbers\":[1,\"x\"]}"))).numbers
				.iterator();
		assertEquals(1, numbers.next());
		// converted as iterated, after the call that read it ended
		final var error = assertThrows(JsonbException.class, numbers::next);
		assertTrue(error.getMessage().startsWith("failed to read Integer: expected an int"), error::getMessage);
	}

	@Test
	public void testIuPathsTrackNoItems() {
		final IuJsonAdapter list = IuJsonAdapter.of(List.class);
		final IuJsonAdapter map = IuJsonAdapter.of(Map.class);
		final var thread = Thread.currentThread();
		assertThrows(IllegalArgumentException.class, () -> list.toJson(List.of(thread)));
		assertThrows(IllegalArgumentException.class, () -> map.toJson(Map.of("k", thread)));
		assertThrows(IllegalArgumentException.class,
				() -> StreamingWrite.write(list, List.of(thread)));
		assertThrows(IllegalArgumentException.class,
				() -> StreamingWrite.write(map, Map.of("k", thread)));

		final IuJsonAdapter<List<Integer>> numbers = IuJsonAdapter.of(new TypeReference<List<Integer>>() {
		}.type());
		assertThrows(IllegalArgumentException.class, () -> numbers.fromJson(IuJson.parse("[\"x\"]")));
		assertThrows(IllegalArgumentException.class, () -> StreamingWrite.read(numbers, "[\"x\"]"));
		final IuJsonAdapter<Map<String, Integer>> byName = IuJsonAdapter
				.of(new TypeReference<Map<String, Integer>>() {
				}.type());
		assertThrows(IllegalArgumentException.class, () -> byName.fromJson(IuJson.parse("{\"k\":\"x\"}")));
		assertThrows(IllegalArgumentException.class, () -> StreamingWrite.read(byName, "{\"k\":\"x\"}"));
	}

	static abstract class TypeReference<T> {
		Type type() {
			return ((java.lang.reflect.ParameterizedType) getClass().getGenericSuperclass())
					.getActualTypeArguments()[0];
		}
	}

	static final class StreamingWrite {
		static <T> void write(IuJsonAdapter<T> adapter, T value) {
			try (final var generator = IuJson.PROVIDER.createGenerator(new java.io.StringWriter())) {
				adapter.write(value, generator);
			}
		}

		static <T> T read(IuJsonAdapter<T> adapter, String json) {
			try (final var parser = IuJson.PROVIDER.createParser(new java.io.StringReader(json))) {
				parser.next();
				return adapter.read(parser);
			}
		}
	}

	// ---- serialization context

	public static class Root {
		public Object value;
	}

	/**
	 * Writes a null by key, then a failing value by key.
	 */
	public static class NullThenFailing implements JsonbSerializer<Root> {
		@Override
		public void serialize(Root obj, JsonGenerator generator, SerializationContext ctx) {
			generator.writeStartObject();
			ctx.serialize("omitted", null, generator);
			ctx.serialize("someKey", 1, generator);
			ctx.serialize("failing", obj.value, generator);
			generator.writeEnd();
		}
	}

	@Test
	public void testSerializeByKey() {
		final var jsonb = jsonb(new JsonbConfig().withSerializers(new NullThenFailing())
				.withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES));
		final var root = new Root();
		root.value = "v";
		// keys are written as given, whatever the naming strategy
		assertEquals("{\"someKey\":1,\"failing\":\"v\"}", jsonb.toJson(root));

		// the omitted key leaves nothing behind in the path
		root.value = Thread.currentThread();
		failsAt("failed to write Root.failing: ", () -> jsonb.toJson(root));
	}

	// ---- deserialization context

	/**
	 * Records the event the parser is at after the context reads each value.
	 */
	public static class Positions implements JsonbDeserializer<Root> {
		final List<Event> after = new ArrayList<>();

		@Override
		public Root deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final var root = new Root();
			while (parser.hasNext())
				if (parser.next() == Event.KEY_NAME) {
					final var key = parser.getString();
					final Type type;
					switch (key) {
					case "object":
						type = Map.class;
						break;
					case "array":
						type = List.class;
						break;
					case "bean":
						type = Item.class;
						break;
					case "text":
						type = String.class;
						break;
					default:
						type = Object.class;
						break;
					}
					ctx.deserialize(type, parser);
					after.add(parser.currentEvent());
				}
			return root;
		}
	}

	@Test
	public void testDeserializeLeavesTheParserAtTheValuesLastEvent() {
		final var positions = new Positions();
		final var jsonb = jsonb(new JsonbConfig().withDeserializers(positions));
		final var json = "{\"object\":{\"a\":[1]},\"array\":[{\"b\":2}],\"bean\":{\"count\":1},\"text\":\"t\","
				+ "\"number\":1,\"flag\":true,\"none\":null}";
		final var expected = List.of(Event.END_OBJECT, Event.END_ARRAY, Event.END_OBJECT, Event.VALUE_STRING,
				Event.VALUE_NUMBER, Event.VALUE_TRUE, Event.VALUE_NULL);

		jsonb.fromJson(json, Root.class);
		assertEquals(expected, positions.after);

		positions.after.clear();
		jsonb.adapt(Root.class).fromJson(IuJson.parse(json));
		assertEquals(expected, positions.after);
	}

	// ---- conversions by declared type

	public interface Named {
		String getName();
	}

	@Test
	public void testLambdaConvertsByGivenType() {
		final var jsonb = jsonb(new JsonbConfig());
		final Named named = () -> "n";
		assertEquals("{\"name\":\"n\"}", jsonb.toJson(named, Named.class));
	}

	public interface Wrapper {
		Iterable<? extends Named> getItems();
	}

	/**
	 * Reads a Named from its text.
	 */
	public static class NamedFromText implements JsonbDeserializer<Named> {
		@Override
		public Named deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final var name = parser.getString();
			return () -> name;
		}
	}

	@Test
	public void testInterfaceWithWildcardItems() {
		final var jsonb = jsonb(new JsonbConfig().withDeserializers(new NamedFromText()));
		final Iterator<? extends Named> items = jsonb.fromJson("{\"items\":[\"a\",\"b\"]}", Wrapper.class)
				.getItems().iterator();
		assertEquals("a", items.next().getName());
		assertEquals("b", items.next().getName());
	}

	public static class Tag {
		final String text;

		Tag(String text) {
			this.text = text;
		}
	}

	public static class NamedAsTag implements JsonbAdapter<Named, Tag> {
		@Override
		public Tag adaptToJson(Named obj) {
			return new Tag(obj.getName());
		}

		@Override
		public Named adaptFromJson(Tag obj) {
			return () -> obj.text;
		}
	}

	public static class TagSerializer implements JsonbSerializer<Tag> {
		@Override
		public void serialize(Tag obj, JsonGenerator generator, SerializationContext ctx) {
			generator.write("#" + obj.text);
		}
	}

	public static class Implementation implements Named {
		@Override
		public String getName() {
			return "impl";
		}
	}

	@Test
	public void testInterfaceAdapterAppliesToImplementations() {
		final var jsonb = jsonb(new JsonbConfig().withAdapters(new NamedAsTag()).withSerializers(new TagSerializer()));
		assertEquals("\"#impl\"", jsonb.toJson(new Implementation()));
		assertEquals("\"#impl\"", jsonb.adapt(Implementation.class).toJson(new Implementation()).toString());
	}

	// ---- explicit-type registration

	public static class Foo {
		public String name = "foo";
	}

	@Test
	public void testTypedComponents() {
		final JsonbSerializer<Foo> serializer = (obj, generator, ctx) -> generator.write("lambda " + obj.name);
		final JsonbDeserializer<Foo> deserializer = (parser, ctx, type) -> {
			final var foo = new Foo();
			foo.name = "read " + parser.getString();
			return foo;
		};
		final JsonbAdapter instant = new JsonbAdapter() {
			@Override
			public Object adaptToJson(Object obj) {
				return ((Instant) obj).toEpochMilli();
			}

			@Override
			public Object adaptFromJson(Object obj) {
				return Instant.ofEpochMilli((Long) obj);
			}
		};

		final var jsonb = jsonb(new JsonbConfig() //
				.withSerializers(IuJsonAdapter.typedSerializer(Foo.class, serializer)) //
				.withDeserializers(IuJsonAdapter.typedDeserializer(Foo.class, deserializer)) //
				.withAdapters(IuJsonAdapter.typedAdapter(Instant.class, Long.class, instant)));
		assertEquals("\"lambda foo\"", jsonb.toJson(new Foo()));
		assertEquals("read x", jsonb.fromJson("\"x\"", Foo.class).name);
		assertEquals("1000", jsonb.toJson(Instant.ofEpochMilli(1000)));
		assertEquals(Instant.ofEpochMilli(1000), jsonb.fromJson("1000", Instant.class));
	}

	@Test
	public void testTypedWrappersDelegate() throws Exception {
		final List<String> calls = new ArrayList<>();
		final JsonbSerializer<Foo> serializer = IuJsonAdapter.typedSerializer(Foo.class,
				(obj, generator, ctx) -> calls.add("serialize"));
		serializer.serialize(new Foo(), null, null);

		final JsonbDeserializer<Foo> deserializer = IuJsonAdapter.typedDeserializer(Foo.class, (parser, ctx, type) -> {
			calls.add("deserialize");
			return null;
		});
		deserializer.deserialize(null, null, Foo.class);

		final JsonbAdapter<String, String> adapter = IuJsonAdapter.typedAdapter(String.class, String.class,
				new JsonbAdapter<String, String>() {
					@Override
					public String adaptToJson(String obj) {
						return "to " + obj;
					}

					@Override
					public String adaptFromJson(String obj) {
						return "from " + obj;
					}
				});
		assertEquals("to x", adapter.adaptToJson("x"));
		assertEquals("from x", adapter.adaptFromJson("x"));
		assertEquals(List.of("serialize", "deserialize"), calls);

		assertThrows(NullPointerException.class, () -> IuJsonAdapter.typedSerializer(null, serializer));
		assertThrows(NullPointerException.class, () -> IuJsonAdapter.typedDeserializer(Foo.class, null));
		assertThrows(NullPointerException.class, () -> IuJsonAdapter.typedAdapter(String.class, null, adapter));
	}

	@Test
	public void testTypedComponentsKeepConfiguredOrder() {
		final List<String> calls = new ArrayList<>();
		final var jsonb = jsonb(new JsonbConfig().withSerializers( //
				IuJsonAdapter.typedSerializer(Foo.class, (obj, generator, ctx) -> {
					calls.add("typed");
					ctx.serialize(obj, generator);
				}), //
				new JsonbSerializer<Foo>() {
					@Override
					public void serialize(Foo obj, JsonGenerator generator, SerializationContext ctx) {
						calls.add("declared");
						ctx.serialize(obj, generator);
					}
				}));
		assertEquals("{\"name\":\"foo\"}", jsonb.toJson(new Foo()));
		assertEquals(List.of("typed", "declared"), calls);
	}

	@Test
	public void testRejectionsNameTheTypedFactories() {
		final JsonbSerializer<Foo> lambda = (obj, generator, ctx) -> {
		};
		final var untyped = assertThrows(JsonbException.class,
				() -> jsonb(new JsonbConfig().withSerializers(lambda)));
		assertTrue(untyped.getMessage().endsWith("or register it with IuJsonAdapter.typedSerializer(Type, ...)"),
				untyped::getMessage);

		final var variable = assertThrows(JsonbException.class, () -> jsonb(
				new JsonbConfig().withDeserializers(IuJsonAdapter.typedDeserializer(Foo.class.getTypeParameters().length == 0
						? IuJsonbConformanceTest.class.getDeclaredMethod("variable").getGenericReturnType()
						: null, (parser, ctx, type) -> null))));
		assertTrue(variable.getMessage().endsWith("or register it for an explicit type with "
				+ "IuJsonAdapter.typedDeserializer(Type, ...)"), variable::getMessage);
		assertInstanceOf(JsonbException.class, variable);
	}

	static <T> T variable() {
		return null;
	}

}
