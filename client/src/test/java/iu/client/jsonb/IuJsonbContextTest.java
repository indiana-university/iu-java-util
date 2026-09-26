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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;

import edu.iu.client.IuJson;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;

@SuppressWarnings("javadoc")
public class IuJsonbContextTest {

	public static class Failing {
		public String getBoom() {
			throw new IllegalStateException();
		}

		public void setBoom(String boom) {
			throw new IllegalStateException("boom " + boom);
		}
	}

	public static class Child {
		public String value;
	}

	public static class Outer {
		public Child child;
		public Failing failing;
	}

	public static class Root {
		public Outer outer;
	}

	static IuJsonb jsonb(JsonbConfig config) {
		return IuJsonbTest.jsonb(config);
	}

	@Test
	public void testFailureWithoutMessage() {
		assertEquals("failed to write Failing.boom", assertThrows(JsonbException.class,
				() -> jsonb(new JsonbConfig()).toJson(new Failing())).getMessage());
	}

	@Test
	public void testAnonymousRootIsNamedByClass() {
		final var anonymous = new Object() {
			public String getBoom() {
				throw new IllegalStateException("anonymous");
			}
		};
		assertEquals("failed to write " + anonymous.getClass().getName() + ".boom: anonymous",
				assertThrows(JsonbException.class, () -> jsonb(new JsonbConfig()).toJson(anonymous)).getMessage());
	}

	@Test
	public void testRootFailures() {
		final var jsonb = jsonb(new JsonbConfig());
		// a JsonbException at the root, with nothing to add, is thrown as-is
		assertEquals("expected object for " + Child.class.getName() + ", found ARRAY",
				assertThrows(JsonbException.class, () -> jsonb.adapt(Child.class).fromJson(IuJson.array().build()))
						.getMessage());
		// with a location, it's described
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.fromJson("[]", Child.class)).getMessage()
				.startsWith("failed to read Child (line 1, column "));
	}

	static JsonParser withoutLocation(String json) {
		final var real = IuJson.PROVIDER.createParser(new StringReader(json));
		final var parser = mock(JsonParser.class, withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(real)));
		final var location = mock(JsonLocation.class);
		doReturn(-1L).when(location).getLineNumber();
		doReturn(location).when(parser).getLocation();
		parser.next();
		return parser;
	}

	@Test
	public void testUnknownLocation() {
		final var jsonb = jsonb(new JsonbConfig());
		assertEquals("expected START_OBJECT for " + Child.class.getName() + ", found START_ARRAY",
				assertThrows(JsonbException.class, () -> jsonb.adapt(Child.class).read(withoutLocation("[]")))
						.getMessage());
		assertEquals("failed to read Failing.boom: boom x", assertThrows(JsonbException.class,
				() -> jsonb.adapt(Failing.class).read(withoutLocation("{\"boom\":\"x\"}"))).getMessage());
	}

	/**
	 * Writes an outer value's properties through the context, by key.
	 */
	public static class OuterSerializer implements JsonbSerializer<Outer> {
		SerializationContext stored;

		@Override
		public void serialize(Outer obj, JsonGenerator generator, SerializationContext ctx) {
			stored = ctx;
			generator.writeStartObject();
			ctx.serialize("child", obj.child, generator);
			if (obj.failing != null)
				try {
					ctx.serialize("failing", obj.failing, generator);
				} catch (RuntimeException e) {
					throw new IllegalStateException("wrapped", e);
				}
			generator.writeEnd();
		}
	}

	@Test
	public void testSerializeByKey() {
		final var serializer = new OuterSerializer();
		final var jsonb = jsonb(new JsonbConfig().withSerializers(serializer));
		final var root = new Root();
		root.outer = new Outer();
		root.outer.child = new Child();
		root.outer.child.value = "v";
		assertEquals("{\"outer\":{\"child\":{\"value\":\"v\"}}}", jsonb.toJson(root));
		assertEquals("{\"outer\":{\"child\":null}}", jsonb.toJson(new Root() {
			{
				outer = new Outer();
			}
		}));

		// a failure keeps the deepest path, even when user code wraps it
		root.outer.failing = new Failing();
		final var error = assertThrows(JsonbException.class, () -> jsonb.toJson(root));
		assertEquals("failed to write Root.outer.failing.boom: wrapped", error.getMessage());

		// a context used after its call runs as a call of its own
		final var writer = new StringWriter();
		try (final var generator = IuJson.PROVIDER.createGenerator(writer)) {
			serializer.stored.serialize(root.outer.child, generator);
		}
		assertEquals("{\"value\":\"v\"}", writer.toString());
	}

	/**
	 * Writes a child through a new call on the same provider.
	 */
	public static class NestingSerializer implements JsonbSerializer<Child> {
		IuJsonb jsonb;

		@Override
		public void serialize(Child obj, JsonGenerator generator, SerializationContext ctx) {
			generator.write(jsonb.toJson(obj.value + " nested"));
		}
	}

	@Test
	public void testNestedCall() {
		final var serializer = new NestingSerializer();
		final var jsonb = jsonb(new JsonbConfig().withSerializers(serializer));
		serializer.jsonb = jsonb;
		final var outer = new Outer();
		outer.child = new Child();
		outer.child.value = "v";
		assertEquals("{\"child\":\"\\\"v nested\\\"\"}", jsonb.toJson(outer));
		assertNull(jsonb.serialization.get());
	}

	/**
	 * Reads an outer value's properties through the context, from each key.
	 */
	public static class OuterDeserializer implements JsonbDeserializer<Outer> {
		DeserializationContext stored;
		IuJsonb jsonb;

		@Override
		public Outer deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			stored = ctx;
			final var outer = new Outer();
			while (parser.next() == JsonParser.Event.KEY_NAME)
				if ("child".equals(parser.getString()))
					outer.child = ctx.deserialize(Child.class, parser);
				else {
					parser.next();
					// a new call on the same provider, from inside this one
					final var nested = jsonb.fromJson("{\"value\":\"" + parser.getString() + "\"}", Child.class);
					outer.child.value += nested.value;
				}
			return outer;
		}
	}

	@Test
	public void testDeserializeFromKey() {
		final var deserializer = new OuterDeserializer();
		final var jsonb = jsonb(new JsonbConfig().withDeserializers(deserializer));
		deserializer.jsonb = jsonb;
		assertEquals("a+b",
				jsonb.fromJson("{\"outer\":{\"child\":{\"value\":\"a\"},\"more\":\"+b\"}}", Root.class).outer.child.value);
		assertNull(jsonb.deserialization.get());

		final var error = assertThrows(JsonbException.class,
				() -> jsonb.fromJson("{\"outer\":{\"child\":[]}}", Root.class));
		assertTrue(error.getMessage().startsWith("failed to read Root.outer.child (line 1, column "),
				error.getMessage());

		// a context used after its call runs as a call of its own, from a new parser
		try (final var parser = IuJson.PROVIDER.createParser(new StringReader("{\"value\":\"c\"}"))) {
			assertEquals("c", deserializer.stored.deserialize(Child.class, parser).value);
		}
	}

	@Test
	public void testRequiresACall() {
		final var jsonb = jsonb(new JsonbConfig());
		assertThrows(NullPointerException.class, () -> IuSerializationContext.require(jsonb));
		assertThrows(NullPointerException.class, () -> IuDeserializationContext.require(jsonb));
		assertInstanceOf(Class.class, IuSerializationContext.runtimeType(null));
	}

}
