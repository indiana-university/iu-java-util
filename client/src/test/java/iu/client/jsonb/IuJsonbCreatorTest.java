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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.config.PropertyNamingStrategy;

@SuppressWarnings("javadoc")
public class IuJsonbCreatorTest {

	public record Point(int x, int y) {
	}

	public record Named(@JsonbProperty("n") String name, Optional<String> note,
			@JsonbDateFormat(value = "dd.MM.yyyy", locale = "en-US") LocalDate date) {
	}

	public record Box<T>(T value) {
	}

	public static class Holder {
		public Box<LocalDate> box;
	}

	public static class Constructed {
		private final String a;
		private final int b;
		public String extra;

		@JsonbCreator
		public Constructed(@JsonbProperty("a") String a, int b) {
			this.a = a;
			this.b = b;
		}

		public String getA() {
			return a;
		}

		public int getB() {
			return b;
		}
	}

	public static class Factory {
		private String value;

		private Factory() {
		}

		@JsonbCreator
		public static Factory of(@JsonbTypeDeserializer(IuJsonbComponentAnnotationsTest.Reverse.class) String value) {
			final var factory = new Factory();
			factory.value = value;
			return factory;
		}

		public String getValue() {
			return value;
		}
	}

	public static class TwoCreators {
		@JsonbCreator
		public TwoCreators() {
		}

		@JsonbCreator
		public static TwoCreators of() {
			return new TwoCreators();
		}
	}

	public static class InstanceCreator {
		@JsonbCreator
		public InstanceCreator make() {
			return this;
		}
	}

	public static class WrongReturn {
		@JsonbCreator
		public static String of() {
			return "";
		}
	}

	public record SnakeRecord(String firstName) {
	}

	private static IuJsonb jsonb() {
		return IuJsonbTest.jsonb(new JsonbConfig());
	}

	@Test
	public void testRecord() {
		final var jsonb = jsonb();
		assertEquals("{\"x\":1,\"y\":2}", jsonb.toJson(new Point(1, 2)));
		assertEquals("{\"x\":1,\"y\":2}", jsonb.adapt(Point.class).toJson(new Point(1, 2)).toString());
		assertEquals(new Point(1, 2), jsonb.fromJson("{\"y\":2,\"x\":1}", Point.class));
		assertEquals(new Point(1, 2), jsonb.adapt(Point.class).fromJson(IuJson.parse("{\"y\":2,\"x\":1}")));

		// a component missing reads as a primitive's default
		assertEquals(new Point(1, 0), jsonb.fromJson("{\"x\":1}", Point.class));
	}

	@Test
	public void testRecordAnnotations() {
		final var jsonb = jsonb();
		final var named = new Named("a", Optional.of("b"), LocalDate.of(2026, 9, 27));
		final var json = "{\"date\":\"27.09.2026\",\"n\":\"a\",\"note\":\"b\"}";
		assertEquals(json, jsonb.toJson(named));
		assertEquals(named, jsonb.fromJson(json, Named.class));
		assertEquals(named, jsonb.adapt(Named.class).fromJson(IuJson.parse(json)));

		// missing: null, and an empty optional
		assertEquals(new Named(null, Optional.empty(), null), jsonb.fromJson("{}", Named.class));
	}

	@Test
	public void testGenericRecord() {
		final var holder = jsonb().fromJson("{\"box\":{\"value\":\"2026-09-27\"}}", Holder.class);
		assertEquals(LocalDate.of(2026, 9, 27), holder.box.value());
	}

	@Test
	public void testConstructorWithProperties() {
		final var jsonb = jsonb();
		// a property before the creator's parameters is held until the instance
		// exists; one no property reads is skipped
		final var json = "{\"extra\":\"e\",\"skip\":{\"x\":[1]},\"list\":[{}],\"n\":1,\"a\":\"x\",\"b\":2}";
		for (final var constructed : List.of(jsonb.fromJson(json, Constructed.class),
				(Constructed) jsonb.adapt(Constructed.class).fromJson(IuJson.parse(json)))) {
			assertEquals("x", constructed.a);
			assertEquals(2, constructed.b);
			assertEquals("e", constructed.extra);
		}
		assertEquals("{\"a\":\"x\",\"b\":2,\"extra\":\"e\"}", jsonb.toJson(jsonb.fromJson(json, Constructed.class)));

		// missing parameters
		final var missing = jsonb.fromJson("{\"a\":\"x\"}", Constructed.class);
		assertEquals(0, missing.b);
		final var required = IuJsonbTest.jsonb(new JsonbConfig().withCreatorParametersRequired(true));
		assertTrue(assertThrows(JsonbException.class, () -> required.fromJson("{\"a\":\"x\"}", Constructed.class))
				.getMessage().contains("missing creator parameter b of "));
		assertTrue(required.isCreatorParametersRequired());
		assertEquals(2, required.fromJson("{\"a\":\"x\",\"b\":2}", Constructed.class).b);
	}

	@Test
	public void testFactory() {
		final var jsonb = jsonb();
		assertEquals("abc", jsonb.fromJson("{\"value\":\"cba\"}", Factory.class).getValue());
		assertEquals("abc", ((Factory) jsonb.adapt(Factory.class).fromJson(IuJson.parse("{\"value\":\"cba\"}")))
				.getValue());
	}

	@Test
	public void testInvalidCreators() {
		final var jsonb = jsonb();
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.fromJson("{}", TwoCreators.class)).getMessage()
				.contains("more than one creator declared for " + TwoCreators.class.getName()));
		for (final var type : new Class<?>[] { InstanceCreator.class, WrongReturn.class })
			assertTrue(assertThrows(JsonbException.class, () -> jsonb.fromJson("{}", type)).getMessage()
					.contains("must be a constructor, or a static method returning " + type.getName()),
					type::getName);
	}

	public record Amount(@JsonbNumberFormat(value = "#0.00", locale = "en-US") double value) {
	}

	/**
	 * A creator parameter that a setter also writes: the creator wins.
	 */
	public static class Both {
		public final String a;
		public boolean set;

		@JsonbCreator
		public Both(@JsonbProperty("a") String a) {
			this.a = a;
		}

		public void setA(String a) {
			set = true;
		}
	}

	@Test
	public void testParameterFormatsAndFailures() {
		final var jsonb = jsonb();
		assertEquals(1.5, jsonb.fromJson("{\"value\":\"1.50\"}", Amount.class).value());
		assertEquals(1.5, ((Amount) jsonb.adapt(Amount.class).fromJson(IuJson.parse("{\"value\":\"1.50\"}")))
				.value());

		final var both = jsonb.fromJson("{\"a\":\"x\"}", Both.class);
		assertEquals("x", both.a);
		assertFalse(both.set);

		// a parameter that fails to convert names its path
		final var json = "{\"a\":\"x\",\"b\":\"not a number\"}";
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.fromJson(json, Constructed.class)).getMessage()
				.contains("Constructed.b"));
		assertTrue(assertThrows(JsonbException.class,
				() -> jsonb.adapt(Constructed.class).fromJson(IuJson.parse(json))).getMessage()
				.contains("Constructed.b"));
	}

	@Test
	public void testParameterNaming() {
		final var jsonb = IuJsonbTest.jsonb(
				new JsonbConfig().withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES));
		assertEquals("{\"first_name\":\"x\"}", jsonb.toJson(new SnakeRecord("x")));
		assertEquals(new SnakeRecord("x"), jsonb.fromJson("{\"first_name\":\"x\"}", SnakeRecord.class));

		final var insensitive = IuJsonbTest
				.jsonb(new JsonbConfig().withPropertyNamingStrategy(PropertyNamingStrategy.CASE_INSENSITIVE));
		assertEquals(new SnakeRecord("x"), insensitive.fromJson("{\"FIRSTNAME\":\"x\"}", SnakeRecord.class));
		assertNull(insensitive.fromJson("{}", SnakeRecord.class).firstName());
	}

}
