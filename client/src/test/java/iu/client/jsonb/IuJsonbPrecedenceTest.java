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

import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * Precedence between serializers, deserializers, and adapters: the most
 * specific runs, and between equally specific ones, the serializer or
 * deserializer.
 */
@SuppressWarnings("javadoc")
public class IuJsonbPrecedenceTest {

	public interface Ref {
	}

	public interface Key extends Ref {
	}

	public static class RefImpl implements Ref {
	}

	public static class AdaptedKey implements Key {
	}

	public static class KeyImpl implements Key {
	}

	public static class Holder {
		public Ref ref;
	}

	/**
	 * Converts a reference to and from text, as a reference, whatever its runtime
	 * type.
	 */
	public static class RefAdapter implements JsonbAdapter<Ref, String> {
		@Override
		public String adaptToJson(Ref obj) {
			return "ref";
		}

		@Override
		public Ref adaptFromJson(String obj) {
			return new RefImpl();
		}
	}

	public static class KeyAdapter implements JsonbAdapter<Key, String> {
		@Override
		public String adaptToJson(Key obj) {
			return "key adapter";
		}

		@Override
		public Key adaptFromJson(String obj) {
			return new AdaptedKey();
		}
	}

	public static class KeySerializer implements JsonbSerializer<Key> {
		@Override
		public void serialize(Key obj, JsonGenerator generator, SerializationContext ctx) {
			generator.writeStartObject().write("key", true).writeEnd();
		}
	}

	public static class KeyDeserializer implements JsonbDeserializer<Key> {
		@Override
		public Key deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			parser.skipObject();
			return new KeyImpl();
		}
	}

	public static class RefSerializer implements JsonbSerializer<Ref> {
		@Override
		public void serialize(Ref obj, JsonGenerator generator, SerializationContext ctx) {
			generator.write("ref serializer");
		}
	}

	/**
	 * Reads any reference as a key, so it may read either type.
	 */
	public static class RefDeserializer implements JsonbDeserializer<Ref> {
		@Override
		public Ref deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			parser.skipObject();
			return new KeyImpl();
		}
	}

	public static class RefNumber implements JsonbAdapter<Ref, Integer> {
		@Override
		public Integer adaptToJson(Ref obj) {
			return 1;
		}

		@Override
		public Ref adaptFromJson(Integer obj) {
			return new RefImpl();
		}
	}

	public static class RefBoolean implements JsonbAdapter<Ref, Boolean> {
		@Override
		public Boolean adaptToJson(Ref obj) {
			return true;
		}

		@Override
		public Ref adaptFromJson(Boolean obj) {
			return new KeyImpl();
		}
	}

	@Test
	public void testAdapterByShape() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withAdapters(new RefNumber(), new RefBoolean()));
		assertInstanceOf(RefImpl.class, jsonb.fromJson("1", Ref.class));
		assertInstanceOf(KeyImpl.class, jsonb.fromJson("true", Ref.class));
		assertInstanceOf(KeyImpl.class, jsonb.adapt(Ref.class).fromJson(jakarta.json.JsonValue.TRUE));

		// neither reads text, so the first is used, and fails
		assertThrows(JsonbException.class, () -> jsonb.fromJson("\"x\"", Ref.class));
		assertThrows(JsonbException.class, () -> jsonb.adapt(Ref.class).fromJson(IuJson.string("x")));
	}

	public static class RefOptional implements JsonbAdapter<Ref, java.util.Optional<Integer>> {
		@Override
		public java.util.Optional<Integer> adaptToJson(Ref obj) {
			return java.util.Optional.of(1);
		}

		@Override
		public Ref adaptFromJson(java.util.Optional<Integer> obj) {
			return new RefImpl();
		}
	}

	@Test
	public void testAdapterNotAcceptingShapeStillReads() {
		// judged from its type, an Optional reads text, but the only adapter is
		// used anyway, and reads its item's shape
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withAdapters(new RefOptional()));
		assertInstanceOf(RefImpl.class, jsonb.fromJson("1", Ref.class));
		assertInstanceOf(RefImpl.class, jsonb.adapt(Ref.class).fromJson(IuJson.number(1)));
	}

	public static class Text {
		public String text;
	}

	/**
	 * Adapts null to null, so a null property is left to the adapted type's own
	 * adapters, which are the same.
	 */
	public static class SameText implements JsonbAdapter<String, String> {
		@Override
		public String adaptToJson(String obj) {
			return obj;
		}

		@Override
		public String adaptFromJson(String obj) {
			return obj;
		}
	}

	@Test
	public void testNullPropertyAdaptedOnce() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withAdapters(new SameText()));
		assertEquals("{}", jsonb.toJson(new Text()));
		assertEquals("{}", jsonb.adapt(Text.class).toJson(new Text()).toString());
	}

	@Test
	public void testSubtypeComponentsBeatSupertypeAdapter() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig() //
				.withAdapters(new RefAdapter()) //
				.withSerializers(new KeySerializer()) //
				.withDeserializers(new KeyDeserializer()));

		// read
		assertInstanceOf(KeyImpl.class, jsonb.fromJson("{}", Key.class));
		assertInstanceOf(KeyImpl.class, jsonb.adapt(Key.class).fromJson(IuJson.object().build()));
		assertInstanceOf(RefImpl.class, jsonb.fromJson("\"r\"", Ref.class));
		assertInstanceOf(RefImpl.class, jsonb.adapt(Ref.class).fromJson(IuJson.string("r")));

		// write
		assertEquals("{\"key\":true}", jsonb.toJson(new KeyImpl()));
		assertEquals("{\"key\":true}", jsonb.adapt(Key.class).toJson(new KeyImpl()).toString());
		assertEquals("\"ref\"", jsonb.toJson(new RefImpl()));

		// selected by runtime type, wherever declared
		final var holder = new Holder();
		holder.ref = new KeyImpl();
		assertEquals("{\"ref\":{\"key\":true}}", jsonb.toJson(holder));
		assertEquals("{\"ref\":{\"key\":true}}", jsonb.adapt(Holder.class).toJson(holder).toString());
		holder.ref = new RefImpl();
		assertEquals("{\"ref\":\"ref\"}", jsonb.toJson(holder));
	}

	@Test
	public void testTieGoesToSerializerAndDeserializer() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig() //
				.withAdapters(new KeyAdapter()) //
				.withSerializers(new KeySerializer()) //
				.withDeserializers(new KeyDeserializer()));

		assertEquals("{\"key\":true}", jsonb.toJson(new KeyImpl()));
		assertEquals("{\"key\":true}", jsonb.adapt(Key.class).toJson(new KeyImpl()).toString());
		assertInstanceOf(KeyImpl.class, jsonb.fromJson("{}", Key.class));

		// the deserializer reads text too; the adapter isn't consulted
		assertInstanceOf(KeyImpl.class, jsonb.fromJson("\"k\"", Key.class));
		assertInstanceOf(KeyImpl.class, jsonb.adapt(Key.class).fromJson(IuJson.string("k")));

		// an undefined value has no parser event, so only the adapter sees it
		assertInstanceOf(AdaptedKey.class, jsonb.adapt(Key.class).fromJson(null));
	}

	@Test
	public void testSubtypeAdapterBeatsSupertypeComponents() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig() //
				.withAdapters(new KeyAdapter()) //
				.withSerializers(new RefSerializer()) //
				.withDeserializers(new RefDeserializer()));

		assertEquals("\"key adapter\"", jsonb.toJson(new KeyImpl()));
		assertEquals("\"ref serializer\"", jsonb.toJson(new RefImpl()));
		assertInstanceOf(AdaptedKey.class, jsonb.fromJson("\"k\"", Key.class));
		assertInstanceOf(AdaptedKey.class, jsonb.adapt(Key.class).fromJson(IuJson.string("k")));

		// the adapter reads text, not an object, so the deserializer reads one
		assertInstanceOf(KeyImpl.class, jsonb.fromJson("{}", Key.class));
		assertInstanceOf(KeyImpl.class, jsonb.adapt(Key.class).fromJson(IuJson.object().build()));
	}

}
