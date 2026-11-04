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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonProperties;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

@SuppressWarnings("javadoc")
public class IuJsonPropertiesJsonbTest {

	public static class Holder {
		IuJsonProperties properties;
		Integer first;
	}

	public static class Outer {
		public Holder holder;
		public String after;
	}

	/**
	 * Indexes the object, reading only its first property before returning.
	 */
	public static class Indexes implements JsonbDeserializer<Holder> {
		final boolean readFirst;

		Indexes(boolean readFirst) {
			this.readFirst = readFirst;
		}

		@Override
		public Holder deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final var holder = new Holder();
			holder.properties = IuJsonProperties.deserialize(parser, ctx);
			if (readFirst)
				holder.first = holder.properties.get("first", Integer.class);
			return holder;
		}
	}

	static Object field(IuJsonProperties properties, String name) throws Exception {
		final var field = IuJsonProperties.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(properties);
	}

	static final String JSON = "{\"holder\" : {\"first\":1 , \"nested\" : {\"a\":[1,{\"b\":\"x,}\"}]} ,\n"
			+ "  \"last\": \"l\" } , \"after\":\"z\"}";

	@Test
	public void testContinuesFromTheText() throws Exception {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDeserializers(new Indexes(true)));
		final var outer = jsonb.fromJson(JSON, Outer.class);
		assertEquals("z", outer.after);
		assertEquals(1, outer.holder.first);

		// detached onto a parser over the same text, the rest still unread
		final var properties = outer.holder.properties;
		assertNotNull(field(properties, "parser"));
		assertEquals(true, field(properties, "owned"));

		assertEquals("l", properties.get("last", String.class));
		final Map<?, ?> nested = properties.get("nested", Map.class);
		assertEquals(Map.of("a", List.of(1, Map.of("b", "x,}"))), normalize(nested));

		// read through, so the text is released
		assertNull(field(properties, "parser"));
		assertEquals(List.of("first", "nested", "last"), new ArrayList<>(properties.names()));
		assertEquals(IuJson.parse(JSON).asJsonObject().getJsonObject("holder"), properties.toJsonObject());
	}

	static Object normalize(Object value) {
		if (value instanceof List)
			return ((List<?>) value).stream().map(IuJsonPropertiesJsonbTest::normalize)
					.collect(java.util.stream.Collectors.toList());
		if (value instanceof Map)
			return ((Map<?, ?>) value).entrySet().stream().collect(
					java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> normalize(e.getValue())));
		if (value instanceof java.math.BigDecimal)
			return ((java.math.BigDecimal) value).intValueExact();
		return value;
	}

	@Test
	public void testDetachesOnce() throws Exception {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDeserializers(new Indexes(true)));
		final var properties = jsonb.fromJson(JSON, Outer.class).holder.properties;
		final var continuation = field(properties, "parser");
		properties.detach();
		assertSame(continuation, field(properties, "parser"));
		assertNull(properties.get("missing", String.class));
		assertNull(field(properties, "parser"));
	}

	public static class Embedding {
		public Holder holder;
	}

	/**
	 * Reads a Holder embedded as JSON text, from a parser of its own.
	 */
	public static class Embedded implements JsonbDeserializer<Embedding> {
		@Override
		public Embedding deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			parser.next();
			parser.next();
			final var embedding = new Embedding();
			try (final var embedded = IuJson.PROVIDER.createParser(new StringReader(parser.getString()))) {
				embedded.next();
				embedding.holder = ctx.deserialize(Holder.class, embedded);
			}
			return embedding;
		}
	}

	@Test
	public void testCapturesTheRestFromAnotherParser() throws Exception {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDeserializers(new Embedded(), new Indexes(true)));
		// the call reads text, but not the text the embedded holder is parsed from
		final var embedding = jsonb.fromJson("{\"text\":\"{\\\"first\\\":1,\\\"last\\\":\\\"l\\\"}\"}",
				Embedding.class);
		final var properties = embedding.holder.properties;
		assertNull(field(properties, "parser"));
		assertEquals("l", properties.get("last", String.class));
	}

	@Test
	public void testContinuesFromTheStart() throws Exception {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDeserializers(new Indexes(false)));
		final var outer = jsonb.fromJson(JSON, Outer.class);
		assertEquals("z", outer.after);
		final var properties = outer.holder.properties;
		assertEquals(true, field(properties, "owned"));
		assertEquals(1, (Integer) properties.get("first", Integer.class));
		assertEquals(List.of("first", "nested", "last"), new ArrayList<>(properties.names()));
	}

	@Test
	public void testContinuesAtTheEnd() throws Exception {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDeserializers(new Indexes(true)));
		final var outer = jsonb.fromJson("{\"holder\":{\"first\":1},\"after\":\"z\"}", Outer.class);
		// nothing left to read, so the text is released as soon as it's detached
		final var properties = outer.holder.properties;
		assertNull(field(properties, "parser"));
		assertEquals(List.of("first"), new ArrayList<>(properties.names()));
	}

	@Test
	public void testCapturesTheRestFromAReader() throws Exception {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDeserializers(new Indexes(true)));
		final var outer = jsonb.fromJson(new StringReader(JSON), Outer.class);
		assertEquals("z", outer.after);
		final var properties = outer.holder.properties;
		assertNull(field(properties, "parser"));
		assertEquals("l", properties.get("last", String.class));
	}

	@Test
	public void testReferencesATree() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDeserializers(new Indexes(true)));
		final var object = IuJson.parse(JSON).asJsonObject();
		final var outer = (Outer) jsonb.adapt(Outer.class).fromJson(object);
		assertEquals(1, outer.holder.first);
		assertSame(object.getJsonObject("holder"), outer.holder.properties.toJsonObject());
	}

	public interface Wrapped {
		int getFirst();

		String getLast();

		Map<String, Object> getNested();
	}

	public static class Proxied {
		public Wrapped holder;
		public String after;
	}

	@Test
	public void testInterfaceReadsFromTheText() throws Exception {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig());
		final var proxied = jsonb.fromJson(JSON, Proxied.class);
		assertEquals("z", proxied.after);
		assertEquals("l", proxied.holder.getLast());
		assertEquals(1, proxied.holder.getFirst());
		assertTrue(proxied.holder.getNested().containsKey("a"));
		assertEquals(IuJson.parse(JSON).asJsonObject().getJsonObject("holder"), IuJson.unwrap(proxied.holder));

		final var tree = (Proxied) jsonb.adapt(Proxied.class).fromJson(IuJson.parse(JSON));
		assertEquals("l", tree.holder.getLast());
	}

}
