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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuJsonSerializationOptions;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;

@SuppressWarnings("javadoc")
public class IuJsonbEnumAdapterTest {

	public enum Level {
		LOW(1), HIGH(2) {
			@Override
			public int getRank() {
				return 20;
			}
		};

		private final int rank;

		Level(int rank) {
			this.rank = rank;
		}

		public int getRank() {
			return rank;
		}

		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}

	public enum Labeled {
		PLAIN(null), FANCY("Fancy");

		private final String name;

		Labeled(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	public enum Direction {
		NORTH, SOUTH;

		public Direction getOpposite() {
			return this == NORTH ? SOUTH : NORTH;
		}
	}

	public static class Holder {
		public Level level;
		public List<Level> levels;
		public Labeled labeled;
	}

	static IuJsonb jsonb(IuJsonSerializationOptions options) {
		final Supplier<IuJsonSerializationOptions> supplier = () -> options;
		return IuJsonbTest.jsonb(new JsonbConfig().setProperty(IuJsonb.SERIALIZATION_OPTIONS, supplier));
	}

	static final IuJsonSerializationOptions ENUM_AS_OBJECT = IuJsonSerializationOptions.ENUM_AS_OBJECT;

	@Test
	public void testText() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig());
		final var holder = new Holder();
		holder.level = Level.HIGH;
		holder.levels = List.of(Level.LOW);
		final var json = "{\"level\":\"high\",\"levels\":[\"low\"]}";
		assertEquals(json, jsonb.toJson(holder));
		assertEquals(json, jsonb.adapt(Holder.class).toJson(holder).toString());
		assertEquals("\"low\"", jsonb.toJson(Level.LOW));
		assertEquals(JsonValue.NULL, jsonb.adapt(Level.class).toJson(null));
		assertEquals("{\"labeled\":null,\"level\":null,\"levels\":null}",
				IuJsonbTest.jsonb(new JsonbConfig().withNullValues(true)).toJson(new Holder()));
	}

	@Test
	public void testRead() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig());
		final var json = "{\"level\":\"HIGH\",\"levels\":[{\"x\":{\"y\":1},\"z\":[1],\"w\":1,\"name\":\"LOW\"},"
				+ "[\"HIGH\"]],\"labeled\":null}";
		for (final var holder : new Holder[] { jsonb.fromJson(json, Holder.class),
				(Holder) jsonb.adapt(Holder.class).fromJson(IuJson.parse(json)) }) {
			assertEquals(Level.HIGH, holder.level);
			assertEquals(List.of(Level.LOW, Level.HIGH), holder.levels);
			assertNull(holder.labeled);
		}
		assertNull(jsonb.adapt(Level.class).fromJson(null));
		assertThrows(JsonbException.class, () -> jsonb.fromJson("{}", Level.class));
	}

	@Test
	public void testObject() {
		final var jsonb = jsonb(ENUM_AS_OBJECT);
		final var holder = new Holder();
		holder.level = Level.HIGH;
		holder.levels = List.of(Level.LOW);
		final var json = "{\"level\":{\"name\":\"HIGH\",\"rank\":20},\"levels\":[{\"name\":\"LOW\",\"rank\":1}]}";
		assertEquals(json, jsonb.toJson(holder));
		assertEquals(json, jsonb.adapt(Holder.class).toJson(holder).toString());
		assertEquals(Level.HIGH, jsonb.fromJson(json, Holder.class).level);
	}

	@Test
	public void testDeclaredNameProperty() {
		final var jsonb = jsonb(ENUM_AS_OBJECT);
		final var holder = new Holder();
		holder.labeled = Labeled.FANCY;
		assertEquals("{\"labeled\":{\"name\":\"Fancy\"}}", jsonb.toJson(holder));
		assertEquals("{\"labeled\":{\"name\":\"Fancy\"}}", jsonb.adapt(Holder.class).toJson(holder).toString());

		// omitted when null, so the constant's name stands in
		holder.labeled = Labeled.PLAIN;
		assertEquals("{\"labeled\":{\"name\":\"PLAIN\"}}", jsonb.toJson(holder));
		assertEquals("{\"labeled\":{\"name\":\"PLAIN\"}}", jsonb.adapt(Holder.class).toJson(holder).toString());

		// written as null when null properties are included
		final var nulls = jsonb(IuJsonSerializationOptions.of(IuJsonPropertyNameFormat.IDENTITY, true, true));
		assertTrue(nulls.toJson(holder).contains("\"labeled\":{\"name\":null}"));
		assertTrue(nulls.adapt(Holder.class).toJson(holder).toString().contains("\"labeled\":{\"name\":null}"));
	}

	@Test
	public void testNameKeyFollowsFormat() {
		final var jsonb = jsonb(
				IuJsonSerializationOptions.of(IuJsonPropertyNameFormat.UPPER_CASE_WITH_UNDERSCORES, false, true));
		final var holder = new Holder();
		holder.level = Level.LOW;
		assertEquals("{\"LEVEL\":{\"NAME\":\"LOW\",\"RANK\":1}}", jsonb.toJson(holder));
		assertEquals(Level.LOW, jsonb.fromJson("{\"LEVEL\":{\"NAME\":\"LOW\"}}", Holder.class).level);
		assertEquals(Level.HIGH,
				((Holder) jsonb.adapt(Holder.class).fromJson(IuJson.parse("{\"LEVEL\":{\"NAME\":\"HIGH\"}}"))).level);
	}

	@Test
	public void testRecursiveReference() {
		final var jsonb = jsonb(ENUM_AS_OBJECT);
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.toJson(Direction.NORTH)).getMessage()
				.contains("recursive reference"));
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.adapt(Direction.class).toJson(Direction.NORTH))
				.getMessage().contains("recursive reference"));
	}

}
