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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonProperties;
import edu.iu.client.IuJsonSerializationOptions;
import iu.client.ConversionScope;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * An index created without conversions of its own converts as the JSON-B call
 * in progress does.
 */
@SuppressWarnings("javadoc")
public class IuJsonPropertiesScopeTest {

	private static final Date MIDNIGHT = Date.from(Instant.parse("2026-09-27T00:00:00Z"));

	enum Letter {
		A;

		@Override
		public String toString() {
			return "a";
		}
	}

	private static IuJsonb dotted() {
		return IuJsonbTest.jsonb(new JsonbConfig().withDateFormat("dd.MM.yyyy", Locale.ROOT));
	}

	private static IuJsonb slashed() {
		return IuJsonbTest.jsonb(new JsonbConfig().withDateFormat("yyyy/MM/dd", Locale.ROOT));
	}

	@Test
	public void testScopeNests() {
		final Function<Type, IuJsonAdapter<?>> outer = IuJsonAdapter::of;
		final Function<Type, IuJsonAdapter<?>> inner = IuJsonAdapter::of;
		assertNull(ConversionScope.current());
		ConversionScope.within(outer, () -> {
			assertSame(outer, ConversionScope.current());
			ConversionScope.within(inner, () -> {
				assertSame(inner, ConversionScope.current());
				return null;
			});
			assertSame(outer, ConversionScope.current());
			return null;
		});
		assertNull(ConversionScope.current());
	}

	@Test
	public void testRequiresConversionsWhenGiven() {
		final var object = IuJson.object().build();
		assertThrows(NullPointerException.class, () -> IuJsonProperties.of(object, (Function<Type, IuJsonAdapter<?>>) null));
		assertThrows(NullPointerException.class, () -> IuJsonProperties.builder((Function<Type, IuJsonAdapter<?>>) null));
		try (final var parser = IuJson.PROVIDER.createParser(new StringReader("{}"))) {
			parser.next();
			assertThrows(NullPointerException.class, () -> IuJsonProperties.read(parser, (Function<Type, IuJsonAdapter<?>>) null));
		}
	}

	@Test
	public void testDefaultsWithoutCall() {
		final var built = IuJsonProperties.builder().put("letter", Letter.A).put("bytes", new byte[] { 1 })
				.put("date", MIDNIGHT).build();
		assertEquals("{\"letter\":\"A\",\"bytes\":[1],\"date\":\"2026-09-27Z\"}", built.toString());

		final var indexed = IuJsonProperties.of(IuJson.parse("{\"date\":\"2026-09-27Z\",\"letter\":\"A\"}")
				.asJsonObject());
		assertEquals(MIDNIGHT, indexed.get("date", Date.class));
		assertSame(Letter.A, indexed.get("letter", Letter.class));

		try (final var parser = IuJson.PROVIDER.createParser(new StringReader("{\"date\":\"2026-09-27Z\"}"))) {
			parser.next();
			assertEquals(MIDNIGHT, IuJsonProperties.read(parser).get("date", Date.class));
		}
	}

	@Test
	public void testCallInProgressWins() {
		final var dotted = dotted();
		final var slashed = slashed();

		// created in one call, converted in another
		final var first = ConversionScope.within(dotted.conversions(),
				() -> IuJsonProperties.builder().put("date", MIDNIGHT).build());
		final var second = ConversionScope.within(dotted.conversions(),
				() -> IuJsonProperties.builder().put("date", MIDNIGHT).build());
		assertEquals("{\"date\":\"27.09.2026\"}", first.toString());
		assertEquals("{\"date\":\"2026/09/27\"}",
				ConversionScope.within(slashed.conversions(), () -> second.toString()));

		// a copy converts as the original does
		assertEquals("{\"date\":\"27.09.2026\",\"n\":1}", first.with("n", 1).toString());

		// conversions given win over the call in progress
		final var bound = IuJsonProperties.builder(IuJsonAdapter::of).put("date", MIDNIGHT).build();
		assertEquals("{\"date\":\"2026-09-27Z\"}",
				ConversionScope.within(dotted.conversions(), () -> bound.toString()));
	}

	public static class Holder {
		IuJsonProperties properties;
	}

	public static class Indexes implements JsonbDeserializer<Holder> {
		@Override
		public Holder deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final var holder = new Holder();
			holder.properties = IuJsonProperties.read(parser);
			return holder;
		}
	}

	@Test
	public void testDeserializerIndexConvertsAfterCall() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDateFormat("dd.MM.yyyy", Locale.ROOT)
				.withDeserializers(new Indexes()));
		final var holder = jsonb.fromJson("{\"date\":\"27.09.2026\",\"n\":1}", Holder.class);
		assertEquals(MIDNIGHT, holder.properties.get("date", Date.class));
		assertEquals(1, (int) holder.properties.get("n", int.class));
	}

	public static class Wrapper {
		final IuJsonProperties properties;

		Wrapper(IuJsonProperties properties) {
			this.properties = properties;
		}
	}

	public static class WritesIndex implements JsonbSerializer<Wrapper> {
		@Override
		public void serialize(Wrapper obj, JsonGenerator generator, SerializationContext ctx) {
			obj.properties.write(generator);
		}
	}

	public static class WritesThroughContext implements JsonbSerializer<Wrapper> {
		@Override
		public void serialize(Wrapper obj, JsonGenerator generator, SerializationContext ctx) {
			obj.properties.write(generator, ctx);
		}
	}

	@Test
	public void testValuesPassedToCall() {
		final var properties = IuJsonProperties.builder().put("date", MIDNIGHT).build();
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDateFormat("dd.MM.yyyy", Locale.ROOT)
				.withSerializers(new WritesIndex()));
		assertEquals("{\"date\":\"27.09.2026\"}", jsonb.toJson(new Wrapper(properties)));
	}

	public static class Bean {
		public IuJsonProperties properties;
		public String after;
	}

	@Test
	public void testBuiltInConversion() {
		final var adapter = IuJsonAdapter.of(IuJsonProperties.class);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertEquals("expected an object, found STRING",
				assertThrows(IllegalArgumentException.class, () -> adapter.fromJson(IuJson.string("x"))).getMessage());

		final var object = IuJson.parse("{\"date\":\"2026-09-27Z\"}").asJsonObject();
		assertSame(object, adapter.toJson(IuJsonProperties.of(object)));
		assertEquals(MIDNIGHT, adapter.fromJson(object).get("date", Date.class));

		// streaming
		final var writer = new StringWriter();
		try (final var generator = IuJson.PROVIDER.createGenerator(writer)) {
			generator.writeStartArray();
			adapter.write(IuJsonProperties.builder().put("n", 1).build(), generator);
			adapter.write(null, generator);
			generator.writeEnd();
		}
		assertEquals("[{\"n\":1},null]", writer.toString());
		try (final var parser = IuJson.PROVIDER.createParser(new StringReader("[{\"n\":1},null,1]"))) {
			parser.next();
			parser.next();
			assertEquals(1, (int) adapter.read(parser).get("n", int.class));
			parser.next();
			assertNull(adapter.read(parser));
			parser.next();
			assertEquals("expected an object, found VALUE_NUMBER",
					assertThrows(IllegalArgumentException.class, () -> adapter.read(parser)).getMessage());
		}
	}

	@Test
	public void testIuProperty() {
		final var adapter = IuJsonAdapter.adapt(Bean.class, () -> IuJsonSerializationOptions.DEFAULT);
		final var json = IuJson.parse("{\"after\":\"a\",\"properties\":{\"date\":\"2026-09-27Z\"}}");
		final var bean = (Bean) adapter.fromJson(json);
		assertEquals("a", bean.after);
		assertEquals(MIDNIGHT, bean.properties.get("date", Date.class));
		assertEquals(json, adapter.toJson(bean));
	}

	@Test
	public void testJsonbProperty() {
		final var jsonb = dotted();
		final var json = "{\"after\":\"a\",\"properties\":{\"date\":\"27.09.2026\"}}";

		// streaming: the parser moves past the index to read what follows
		final var bean = jsonb.fromJson(json, Bean.class);
		assertEquals("a", bean.after);
		assertEquals(MIDNIGHT, bean.properties.get("date", Date.class));
		assertEquals(json, jsonb.toJson(bean));

		final var tree = (Bean) jsonb.adapt(Bean.class).fromJson(IuJson.parse(json));
		assertEquals(MIDNIGHT, tree.properties.get("date", Date.class));
		assertEquals(json, jsonb.adapt(Bean.class).toJson(tree).toString());

		// top level, in a list, and declared Object
		assertEquals(MIDNIGHT,
				jsonb.fromJson("{\"date\":\"27.09.2026\"}", IuJsonProperties.class).get("date", Date.class));
		final List<IuJsonProperties> list = jsonb.fromJson("[{\"n\":1},null]",
				new ArrayList<IuJsonProperties>() {
					private static final long serialVersionUID = 1L;
				}.getClass().getGenericSuperclass());
		assertEquals(1, (int) list.get(0).get("n", int.class));
		assertNull(list.get(1));
		final var built = IuJsonProperties.builder().put("date", MIDNIGHT).build();
		assertEquals("[{\"date\":\"27.09.2026\"}]", jsonb.toJson(List.of(built)));
		assertEquals("{\"date\":\"27.09.2026\"}", jsonb.toJson((Object) built));
	}

	public static class BigBytes implements jakarta.json.bind.adapter.JsonbAdapter<java.math.BigInteger, byte[]> {
		@Override
		public byte[] adaptToJson(java.math.BigInteger obj) {
			return obj.toByteArray();
		}

		@Override
		public java.math.BigInteger adaptFromJson(byte[] obj) {
			return new java.math.BigInteger(obj);
		}
	}

	@Test
	public void testBuilderWithJsonb() {
		final var d = new java.math.BigInteger("123456789");
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withAdapters(new BigBytes())
				.withBinaryDataStrategy(jakarta.json.bind.config.BinaryDataStrategy.BASE_64_URL));

		// put as one type, read as another, with no call in progress: as the
		// instance converts, its adapters included
		final var properties = IuJsonProperties.builder(jsonb).put("d", d).build();
		org.junit.jupiter.api.Assertions.assertArrayEquals(d.toByteArray(), properties.get("d", byte[].class));
		assertEquals("{\"d\":\"B1vNFQ==\"}", properties.toString());

		// copies keep it
		org.junit.jupiter.api.Assertions.assertArrayEquals(d.toByteArray(),
				properties.with("n", 1).get("d", byte[].class));
		org.junit.jupiter.api.Assertions.assertArrayEquals(d.toByteArray(),
				IuJsonProperties.builder(jsonb).put("d", d).copy().build().get("d", byte[].class));

		// by the IU defaults, a number isn't bytes
		assertEquals("expected an array of bytes, found NUMBER", assertThrows(IllegalArgumentException.class,
				() -> IuJsonProperties.builder().put("d", d).build().get("d", byte[].class)).getMessage());

		assertThrows(NullPointerException.class, () -> IuJsonProperties.builder((jakarta.json.bind.Jsonb) null));
	}

	@Test
	public void testIndexWithJsonb() {
		final var d = new java.math.BigInteger("123456789");
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withAdapters(new BigBytes())
				.withBinaryDataStrategy(jakarta.json.bind.config.BinaryDataStrategy.BASE_64_URL));
		final var json = "{\"d\":\"B1vNFQ==\"}";

		// read, with no call in progress, as the instance converts
		assertEquals(d, IuJsonProperties.of(IuJson.parse(json).asJsonObject(), jsonb).get("d",
				java.math.BigInteger.class));
		try (final var parser = IuJson.PROVIDER.createParser(new StringReader(json))) {
			parser.next();
			assertEquals(d, IuJsonProperties.read(parser, jsonb).get("d", java.math.BigInteger.class));
		}

		// by the IU defaults, text isn't a number
		assertThrows(IllegalArgumentException.class,
				() -> IuJsonProperties.of(IuJson.parse(json).asJsonObject()).get("d", java.math.BigInteger.class));

		assertThrows(NullPointerException.class,
				() -> IuJsonProperties.of(IuJson.object().build(), (jakarta.json.bind.Jsonb) null));
	}

	@Test
	public void testBuilderGetsNull() {
		final var builder = IuJsonProperties.builder().put("none", null);
		assertNull(builder.get("none"));
		assertNull(builder.get("absent"));
	}

	@Test
	public void testBuilderWithOtherProvider() {
		// another provider's instance converts through its JSON text
		final var other = org.mockito.Mockito.mock(jakarta.json.bind.Jsonb.class);
		org.mockito.Mockito.when(other.toJson(1, Integer.class)).thenReturn("\"one\"");
		org.mockito.Mockito.when(other.fromJson("\"one\"", (Type) String.class)).thenReturn("uno");
		final var properties = IuJsonProperties.builder(other).put("n", 1).build();
		assertEquals("uno", properties.get("n", String.class));
		assertEquals("{\"n\":\"one\"}", properties.toString());
	}

	@Test
	public void testNullPropertiesFollowTheCall() {
		// a Java null follows the call; JSON null as read, or put, is kept
		final var properties = IuJsonProperties.builder() //
				.put("none", null) //
				.putJson("json", jakarta.json.JsonValue.NULL) //
				.put("n", 1) //
				.build();
		final var omitted = "{\"json\":null,\"n\":1}";
		final var included = "{\"none\":null,\"json\":null,\"n\":1}";

		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig());
		final var nulls = IuJsonbTest.jsonb(new JsonbConfig().withNullValues(true));
		assertEquals(omitted, jsonb.toJson(properties));
		assertEquals(included, nulls.toJson(properties));

		// tree mode, each generated anew
		assertEquals(omitted, IuJsonProperties.builder().put("none", null).putJson("json", jakarta.json.JsonValue.NULL)
				.put("n", 1).build().toJsonObject().toString());
		assertEquals(included,
				ConversionScope.within(nulls.conversions(), () -> true,
						() -> IuJsonProperties.builder().put("none", null)
								.putJson("json", jakarta.json.JsonValue.NULL).put("n", 1).build().toJsonObject())
						.toString());
		assertEquals(false, ConversionScope.isIncludeNullProperties());
	}

	@Test
	public void testWriteThroughContext() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDateFormat("dd.MM.yyyy", Locale.ROOT)
				.withSerializers(new WritesThroughContext()));

		// bound to other conversions, but written by the call's
		final var properties = IuJsonProperties.builder(IuJsonAdapter::of) //
				.putJson("raw", IuJson.string("as read")) //
				.put("date", MIDNIGHT) //
				.put("none", null) //
				.build();
		// the null property as the call writes one: omitted, else JSON null
		assertEquals("{\"raw\":\"as read\",\"date\":\"27.09.2026\"}", jsonb.toJson(new Wrapper(properties)));
		final var nulls = IuJsonbTest.jsonb(new JsonbConfig().withDateFormat("dd.MM.yyyy", Locale.ROOT)
				.withNullValues(true).withSerializers(new WritesThroughContext()));
		assertEquals("{\"raw\":\"as read\",\"date\":\"27.09.2026\",\"none\":null}",
				nulls.toJson(new Wrapper(properties)));

		final var source = IuJson.parse("{\"a\":1}").asJsonObject();
		assertEquals("{\"a\":1}", jsonb.toJson(new Wrapper(IuJsonProperties.of(source))));
	}

}
