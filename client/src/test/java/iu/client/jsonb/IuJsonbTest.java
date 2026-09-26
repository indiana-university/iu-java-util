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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuJsonSerializationOptions;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.config.PropertyOrderStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

@SuppressWarnings({ "javadoc", "rawtypes", "unchecked" })
public class IuJsonbTest {

	static IuJsonb jsonb(JsonbConfig config) {
		return (IuJsonb) new IuJsonbBuilder().withConfig(config).withProvider(IuJson.PROVIDER).build();
	}

	public static class Bean {
		private String firstName = "first";
		private Integer count;

		public String getFirstName() {
			return firstName;
		}

		public void setFirstName(String firstName) {
			this.firstName = firstName;
		}

		public Integer getCount() {
			return count;
		}

		public void setCount(Integer count) {
			this.count = count;
		}
	}

	public interface Named {
	}

	public interface Tagged {
	}

	public static class Both implements Named, Tagged {
	}

	public static class Writes<T> implements JsonbSerializer<T> {
		final String text;

		Writes(String text) {
			this.text = text;
		}

		@Override
		public void serialize(T obj, JsonGenerator generator, SerializationContext ctx) {
			generator.write(text);
		}
	}

	public static class Reads<T> implements JsonbDeserializer<T> {
		final T value;

		Reads(T value) {
			this.value = value;
		}

		@Override
		public T deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			parser.getValue();
			return value;
		}
	}

	public static class Identity<T> implements JsonbAdapter<T, T> {
		@Override
		public T adaptToJson(T obj) {
			return obj;
		}

		@Override
		public T adaptFromJson(T obj) {
			return obj;
		}
	}

	@Test
	public void testProvider() {
		assertInstanceOf(IuJsonb.class, JsonbBuilder.create());
		assertInstanceOf(IuJsonb.class, new IuJsonbProvider().create().build());
	}

	@Test
	public void testDefaults() {
		final var jsonb = jsonb(new JsonbConfig());
		assertEquals("{\"firstName\":\"first\"}", jsonb.toJson(new Bean()));
		assertSame(IuJson.PROVIDER, jsonb.provider());
		assertNull(jsonb.propertyVisibilityStrategy());
		assertEquals(PropertyOrderStrategy.LEXICOGRAPHICAL, jsonb.propertyOrderStrategy());
	}

	@Test
	public void testFormatting() {
		assertTrue(jsonb(new JsonbConfig().withFormatting(true)).toJson(new Bean()).contains("\n"));
		assertEquals("{\"firstName\":\"first\"}", jsonb(new JsonbConfig().withFormatting(false)).toJson(new Bean()));
	}

	@Test
	public void testOrderStrategies() {
		for (final var strategy : new String[] { PropertyOrderStrategy.ANY, PropertyOrderStrategy.LEXICOGRAPHICAL,
				PropertyOrderStrategy.REVERSE })
			assertEquals(strategy, jsonb(new JsonbConfig().withPropertyOrderStrategy(strategy)).propertyOrderStrategy());
		assertThrows(UnsupportedOperationException.class,
				() -> jsonb(new JsonbConfig().withPropertyOrderStrategy("SIDEWAYS")));
	}

	@Test
	public void testVisibilityStrategy() {
		final var strategy = new PropertyVisibilityStrategy() {
			@Override
			public boolean isVisible(java.lang.reflect.Field field) {
				return true;
			}

			@Override
			public boolean isVisible(java.lang.reflect.Method method) {
				return false;
			}
		};
		assertSame(strategy, jsonb(new JsonbConfig().withPropertyVisibilityStrategy(strategy))
				.propertyVisibilityStrategy());
	}

	@Test
	public void testNamingStrategies() {
		assertEquals("{\"first_name\":\"first\"}",
				jsonb(new JsonbConfig().withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES))
						.toJson(new Bean()));
		assertEquals("{\"firstName\":\"first\"}",
				jsonb(new JsonbConfig().withPropertyNamingStrategy(PropertyNamingStrategy.IDENTITY))
						.toJson(new Bean()));
		assertThrows(UnsupportedOperationException.class, () -> jsonb(
				new JsonbConfig().withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_DASHES)));
		assertThrows(UnsupportedOperationException.class,
				() -> jsonb(new JsonbConfig().withPropertyNamingStrategy(name -> name)));
	}

	@Test
	public void testNullValues() {
		assertEquals("{\"count\":null,\"firstName\":\"first\"}",
				jsonb(new JsonbConfig().withNullValues(true)).toJson(new Bean()));
		assertEquals("{\"firstName\":\"first\"}", jsonb(new JsonbConfig().withNullValues(false)).toJson(new Bean()));
	}

	@Test
	public void testSerializationOptions() {
		final Supplier<IuJsonSerializationOptions> snake = () -> IuJsonSerializationOptions
				.of(IuJsonPropertyNameFormat.UPPER_CASE_WITH_UNDERSCORES, true);
		assertEquals("{\"COUNT\":null,\"FIRST_NAME\":\"first\"}",
				jsonb(new JsonbConfig().setProperty(IuJsonb.SERIALIZATION_OPTIONS, snake)).toJson(new Bean()));

		final Supplier<IuJsonSerializationOptions> none = () -> null;
		assertEquals("{\"firstName\":\"first\"}",
				jsonb(new JsonbConfig().setProperty(IuJsonb.SERIALIZATION_OPTIONS, none)).toJson(new Bean()));

		final Supplier<IuJsonSerializationOptions> nullFormat = () -> new IuJsonSerializationOptions() {
			@Override
			public IuJsonPropertyNameFormat getPropertyNameFormat() {
				return null;
			}
		};
		assertEquals("{\"firstName\":\"first\"}",
				jsonb(new JsonbConfig().setProperty(IuJsonb.SERIALIZATION_OPTIONS, nullFormat)).toJson(new Bean()));
	}

	@Test
	public void testSerializationOptionsMustAgree() {
		final Supplier<IuJsonSerializationOptions> identity = () -> IuJsonSerializationOptions.DEFAULT;
		final var agree = jsonb(new JsonbConfig().setProperty(IuJsonb.SERIALIZATION_OPTIONS, identity)
				.withPropertyNamingStrategy(PropertyNamingStrategy.IDENTITY).withNullValues(false));
		assertEquals("{\"firstName\":\"first\"}", agree.toJson(new Bean()));

		final var nullsOnly = jsonb(
				new JsonbConfig().setProperty(IuJsonb.SERIALIZATION_OPTIONS, identity).withNullValues(false));
		assertEquals("{\"firstName\":\"first\"}", nullsOnly.toJson(new Bean()));

		final var formatOnly = jsonb(new JsonbConfig().setProperty(IuJsonb.SERIALIZATION_OPTIONS, identity)
				.withPropertyNamingStrategy(PropertyNamingStrategy.IDENTITY));
		assertEquals("{\"firstName\":\"first\"}", formatOnly.toJson(new Bean()));

		final var formatConflict = jsonb(new JsonbConfig().setProperty(IuJsonb.SERIALIZATION_OPTIONS, identity)
				.withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES));
		assertTrue(assertThrows(JsonbException.class, () -> formatConflict.toJson(new Bean())).getMessage()
				.contains("property name format"));

		final var nullConflict = jsonb(
				new JsonbConfig().setProperty(IuJsonb.SERIALIZATION_OPTIONS, identity).withNullValues(true));
		assertTrue(assertThrows(JsonbException.class, () -> nullConflict.toJson(new Bean())).getMessage()
				.contains("include null properties"));
	}

	@Test
	public void testComponentsMustDeclareTypes() {
		final JsonbSerializer<String> lambda = (obj, generator, ctx) -> generator.write(obj);
		assertTrue(assertThrows(JsonbException.class,
				() -> jsonb(new JsonbConfig().withSerializers(lambda))).getMessage().contains("can't determine"));
		// a type variable would apply to every type
		assertTrue(assertThrows(JsonbException.class,
				() -> jsonb(new JsonbConfig().withSerializers(new Writes<>("x")))).getMessage()
				.contains("converts the type variable T"));
		assertThrows(JsonbException.class, () -> jsonb(new JsonbConfig().withDeserializers(new Reads<>("x"))));
		assertThrows(JsonbException.class, () -> jsonb(new JsonbConfig().withAdapters(new Identity<>())));
	}

	static List<String> texts(List<JsonbSerializer> chain) {
		return chain.stream().map(s -> s instanceof Writes ? ((Writes) s).text : "?").collect(Collectors.toList());
	}

	@Test
	public void testSeveralComponentsForOneType() {
		final var jsonb = jsonb(new JsonbConfig().withSerializers(new Writes<String>("x") {
		}, new Writes<String>("y") {
		}).withDeserializers(new Reads<String>("x") {
		}, new Reads<String>("y") {
		}).withAdapters(new Identity<String>() {
		}, new Identity<String>() {
		}));
		assertEquals(List.of("x", "y"), texts(jsonb.serializers(String.class, false)));
		assertEquals(2, jsonb.deserializers(String.class, false).size());
		assertEquals(2, jsonb.adapters(String.class, false).size());
	}

	@Test
	public void testChains() {
		final var jsonb = jsonb(new JsonbConfig() //
				.withSerializers(new Writes<Named>("named") {
				}, new Writes<Tagged>("tagged") {
				}, new Writes<CharSequence>("text") {
				}) //
				.withDeserializers(new Reads<String>("read") {
				}) //
				.withAdapters(new Identity<Integer>() {
				}));

		assertEquals(List.of(), jsonb(new JsonbConfig()).serializers(String.class, false));
		assertEquals(List.of("text"), texts(jsonb.serializers(String.class, false)));
		assertEquals(List.of("named"), texts(jsonb.serializers(Named.class, false)));
		assertEquals(List.of(), jsonb.serializers(Integer.class, false));
		// neither is more specific, so they run in the order configured
		assertEquals(List.of("named", "tagged"), texts(jsonb.serializers(Both.class, false)));

		assertEquals("read", ((Reads) jsonb.deserializers(String.class, false).get(0)).value);
		assertEquals(List.of(), jsonb.deserializers(CharSequence.class, false));

		assertEquals(Integer.class, jsonb.adapters(int.class, false).get(0).original);
		assertEquals(Integer.class, jsonb.adapters(Integer.class, false).get(0).adapted);
		assertEquals(List.of(), jsonb.adapters(Number.class, false));
	}

	static class Holder<T extends CharSequence> {
		T value;
		List<? extends CharSequence> values;
	}

	@Test
	public void testChainByBound() throws Exception {
		final var jsonb = jsonb(new JsonbConfig().withSerializers(new Writes<CharSequence>("text") {
		}));
		assertEquals(List.of("text"),
				texts(jsonb.serializers(Holder.class.getDeclaredField("value").getGenericType(), false)));
		final var wildcard = ((java.lang.reflect.ParameterizedType) Holder.class.getDeclaredField("values")
				.getGenericType()).getActualTypeArguments()[0];
		assertEquals(List.of("text"), texts(jsonb.serializers(wildcard, false)));
	}

	@Test
	public void testMostSpecificFirst() {
		final var jsonb = jsonb(new JsonbConfig().withSerializers(new Writes<Object>("object") {
		}, new Writes<CharSequence>("text") {
		}, new Writes<String>("string") {
		}, new Writes<Comparable<?>>("comparable") {
		}));
		// String is both a CharSequence and a Comparable, which are unrelated, so
		// those two run in the order configured
		assertEquals(List.of("string", "text", "comparable", "object"), texts(jsonb.serializers(String.class, false)));
		assertEquals(List.of("comparable", "object"), texts(jsonb.serializers(Integer.class, false)));

		// a scalar value leaves out only the Object serializer
		assertEquals(List.of("string", "text", "comparable"), texts(jsonb.serializers(String.class, true)));
		assertEquals(List.of("comparable"), texts(jsonb.serializers(int.class, true)));
	}

	@Test
	public void testIsScalar() {
		for (final var scalar : new Class<?>[] { String.class, CharSequence.class, StringBuilder.class,
				Number.class, Integer.class, int.class, java.math.BigDecimal.class, double.class, Boolean.class,
				boolean.class })
			assertTrue(IuJsonb.isScalar(scalar), scalar.getName());
		for (final var other : new Class<?>[] { Object.class, Character.class, char.class, Comparable.class,
				java.time.Instant.class, Named.class, List.class, String[].class })
			assertFalse(IuJsonb.isScalar(other), other.getName());
	}

	static class Lists<E> implements JsonbSerializer<List<E>> {
		@Override
		public void serialize(List<E> obj, JsonGenerator generator, SerializationContext ctx) {
		}
	}

	@Test
	public void testEquivalentTypesRunInConfiguredOrder() throws Exception {
		final var jsonb = jsonb(new JsonbConfig().withSerializers(new Lists<>(), new Writes<List<?>>("any") {
		}));
		final var listOfString = Holder.class.getDeclaredField("values").getGenericType();
		assertEquals(List.of("?", "any"), texts(jsonb.serializers(listOfString, false)));
	}

	@Test
	public void testFromJson() {
		final var jsonb = jsonb(new JsonbConfig());
		final var json = "{\"firstName\":\"x\",\"count\":2}";
		assertEquals("x", jsonb.fromJson(json, Bean.class).firstName);
		assertEquals(2, jsonb.<Bean>fromJson(json, (Type) Bean.class).count);
		assertEquals("x", jsonb.fromJson(new StringReader(json), Bean.class).firstName);
		assertEquals("x", jsonb.fromJson(bytes(json), Bean.class).firstName);
		assertNull(jsonb.fromJson((String) null, Bean.class));
		assertThrows(NullPointerException.class, () -> jsonb.fromJson((Reader) null, Bean.class));
		assertThrows(NullPointerException.class, () -> jsonb.fromJson((InputStream) null, Bean.class));
	}

	@Test
	public void testFromJsonFailure() {
		final var jsonb = jsonb(new JsonbConfig());
		final var error = assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"count\":\"x\"}", Bean.class));
		assertTrue(error.getMessage().startsWith("failed to read Bean.count (line 1"), error.getMessage());
		assertInstanceOf(NumberFormatException.class, error.getCause());
	}

	@Test
	public void testToJson() throws Exception {
		final var jsonb = jsonb(new JsonbConfig());
		final var json = "{\"firstName\":\"first\"}";
		assertEquals(json, jsonb.toJson(new Bean()));
		assertEquals(json, jsonb.toJson(new Bean(), Bean.class));
		assertEquals("null", jsonb.toJson(null));

		final var writer = new StringWriter();
		jsonb.toJson(new Bean(), writer);
		assertEquals(json, writer.toString());

		final var writer2 = new StringWriter();
		jsonb.toJson(new Bean(), Bean.class, writer2);
		assertEquals(json, writer2.toString());

		final var out = new ByteArrayOutputStream();
		jsonb.toJson(new Bean(), out);
		assertEquals(json, out.toString(StandardCharsets.UTF_8));

		final var out2 = new ByteArrayOutputStream();
		jsonb.toJson(new Bean(), Bean.class, out2);
		assertEquals(json, out2.toString(StandardCharsets.UTF_8));

		jsonb.close();
	}

	@Test
	public void testToJsonFailure() {
		final var jsonb = jsonb(new JsonbConfig());
		final var error = assertThrows(JsonbException.class, () -> jsonb.toJson(new Object()));
		assertInstanceOf(IllegalArgumentException.class, error.getCause());
	}

	@Test
	public void testCachesPerType() {
		final var jsonb = jsonb(new JsonbConfig());
		assertSame(jsonb.adapt(Bean.class), jsonb.adapt(Bean.class));
		assertSame(jsonb.model(Bean.class), jsonb.model(Bean.class));
	}

	static InputStream bytes(String json) {
		return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
	}

}
