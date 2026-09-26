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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import iu.client.jsonb.visibility.PackageBean;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbVisibility;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.config.PropertyOrderStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;

@SuppressWarnings({ "javadoc", "unused" })
public class IuJsonbModelTest {

	static IuJsonb jsonb(JsonbConfig config) {
		return IuJsonbTest.jsonb(config);
	}

	static IuJsonb jsonb() {
		return jsonb(new JsonbConfig());
	}

	/**
	 * Converts through the tree adapter.
	 */
	static String tree(IuJsonb jsonb, Object value) {
		return jsonb.adapt(value.getClass()).toJson(value).toString();
	}

	static <T> T fromTree(IuJsonb jsonb, Class<T> type, String json) {
		return type.cast(jsonb.adapt(type).fromJson(IuJson.parse(json)));
	}

	public static class Defaults {
		public static String STATIC = "s";
		public String publicField = "pf";
		private String hidden = "h";
		public transient String skipped = "t";
		public final String constant = "c";
		private String viaAccessors = "a";
		private String readOnly = "r";
		private String writeOnly;

		public String getViaAccessors() {
			return viaAccessors;
		}

		public void setViaAccessors(String viaAccessors) {
			this.viaAccessors = viaAccessors;
		}

		public String getReadOnly() {
			return readOnly;
		}

		public void setWriteOnly(String writeOnly) {
			this.writeOnly = writeOnly;
		}
	}

	@Test
	public void testDefaultVisibility() {
		final var expected = "{\"constant\":\"c\",\"publicField\":\"pf\",\"readOnly\":\"r\",\"viaAccessors\":\"a\"}";
		final var jsonb = jsonb();
		assertEquals(expected, jsonb.toJson(new Defaults()));
		assertEquals(expected, tree(jsonb, new Defaults()));

		final var json = "{\"constant\":\"x\",\"publicField\":\"y\",\"writeOnly\":\"w\",\"viaAccessors\":\"v\","
				+ "\"readOnly\":\"z\",\"hidden\":\"q\",\"skipped\":\"u\"}";
		for (final var bean : new Defaults[] { jsonb.fromJson(json, Defaults.class),
				fromTree(jsonb, Defaults.class, json) }) {
			assertEquals("c", bean.constant);
			assertEquals("y", bean.publicField);
			assertEquals("w", bean.writeOnly);
			assertEquals("v", bean.viaAccessors);
			assertEquals("r", bean.readOnly);
			assertEquals("h", bean.hidden);
			assertEquals("t", bean.skipped);
		}
	}

	public static class Both {
		public String both = "field";

		public String getBoth() {
			return "getter";
		}

		public void setBoth(String both) {
			this.both = "set:" + both;
		}
	}

	@Test
	public void testAccessorsPreferredOverFields() {
		final var jsonb = jsonb();
		assertEquals("{\"both\":\"getter\"}", jsonb.toJson(new Both()));
		assertEquals("set:x", jsonb.fromJson("{\"both\":\"x\"}", Both.class).both);
	}

	public static class FieldsOnly implements PropertyVisibilityStrategy {
		@Override
		public boolean isVisible(Field field) {
			return true;
		}

		@Override
		public boolean isVisible(Method method) {
			return false;
		}
	}

	@JsonbVisibility(FieldsOnly.class)
	public static class PrivateFields {
		private String secret = "s";

		public String getIgnored() {
			return "i";
		}
	}

	public static class SubOfAnnotated extends PrivateFields {
		private String own = "o";

		public String getVisible() {
			return "v";
		}
	}

	@Test
	public void testClassVisibility() {
		final var jsonb = jsonb();
		assertEquals("{\"secret\":\"s\"}", jsonb.toJson(new PrivateFields()));
		assertEquals("x", jsonb.fromJson("{\"secret\":\"x\"}", PrivateFields.class).secret);
		// each declaring class uses its own strategy
		assertEquals("{\"secret\":\"s\",\"visible\":\"v\"}", jsonb.toJson(new SubOfAnnotated()));
	}

	@Test
	public void testPackageVisibility() {
		assertEquals("{\"packaged\":\"p\"}", jsonb().toJson(new PackageBean()));
	}

	@Test
	public void testConfiguredVisibility() {
		final var jsonb = jsonb(new JsonbConfig().withPropertyVisibilityStrategy(new FieldsOnly()));
		assertEquals("{\"constant\":\"c\",\"hidden\":\"h\",\"publicField\":\"pf\",\"readOnly\":\"r\","
				+ "\"viaAccessors\":\"a\"}", jsonb.toJson(new Defaults()));
	}

	class Inner {
		public String value = "inner";
	}

	@Test
	public void testSkipsSyntheticFields() {
		final var jsonb = jsonb(new JsonbConfig().withPropertyVisibilityStrategy(new FieldsOnly()));
		assertEquals("{\"value\":\"inner\"}", jsonb.toJson(new Inner()));
	}

	public interface Labeled {
		default String getLabel() {
			return "label";
		}
	}

	public static class Parent implements Labeled {
		public String shadow = "parent";
		private String inherited = "parent";

		public String getInherited() {
			return inherited;
		}

		public void setInherited(String inherited) {
			this.inherited = "parent:" + inherited;
		}
	}

	public static class Child extends Parent implements Labeled {
		public String shadow = "child";

		@Override
		public String getInherited() {
			return "child";
		}

		@Override
		public void setInherited(String inherited) {
			super.setInherited("child:" + inherited);
		}
	}

	@Test
	public void testNearestDeclarationWins() {
		final var jsonb = jsonb();
		assertEquals("{\"inherited\":\"child\",\"label\":\"label\",\"shadow\":\"child\"}", jsonb.toJson(new Child()));
		final var child = jsonb.fromJson("{\"shadow\":\"x\",\"inherited\":\"y\"}", Child.class);
		assertEquals("x", child.shadow);
		assertEquals("parent", ((Parent) child).shadow);
		assertEquals("parent:child:y", ((Parent) child).inherited);
	}

	@JsonbPropertyOrder({ "zeta", "alpha" })
	public static class Ordered {
		public String alpha = "a";
		public String beta = "b";
		public String gamma = "g";
		public String zeta = "z";
	}

	@JsonbPropertyOrder({ "gamma", "alpha" })
	public static class OrderedChild extends Ordered {
	}

	@Test
	public void testOrder() {
		assertEquals("{\"zeta\":\"z\",\"alpha\":\"a\",\"beta\":\"b\",\"gamma\":\"g\"}", jsonb().toJson(new Ordered()));
		assertEquals("{\"zeta\":\"z\",\"alpha\":\"a\",\"gamma\":\"g\",\"beta\":\"b\"}",
				jsonb(new JsonbConfig().withPropertyOrderStrategy(PropertyOrderStrategy.REVERSE))
						.toJson(new Ordered()));
		assertEquals("{\"zeta\":\"z\",\"alpha\":\"a\",\"beta\":\"b\",\"gamma\":\"g\"}",
				jsonb(new JsonbConfig().withPropertyOrderStrategy(PropertyOrderStrategy.ANY)).toJson(new Ordered()));
		// the nearest annotation's names come first, then names the rest list
		assertEquals("{\"gamma\":\"g\",\"alpha\":\"a\",\"zeta\":\"z\",\"beta\":\"b\"}",
				jsonb().toJson(new OrderedChild()));
	}

	public static class Collide {
		public String fooBar = "camel";
		public String foo_bar = "snake";
	}

	@Test
	public void testFormattedNameCollisions() {
		assertEquals("{\"fooBar\":\"camel\",\"foo_bar\":\"snake\"}", jsonb().toJson(new Collide()));

		final var snake = jsonb(
				new JsonbConfig().withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES));
		assertEquals("{\"foo_bar\":\"camel\"}", snake.toJson(new Collide()));
		final var read = snake.fromJson("{\"foo_bar\":\"x\"}", Collide.class);
		assertEquals("x", read.fooBar);
		assertEquals("snake", read.foo_bar);
	}

	public static class NoDefaultConstructor {
		public NoDefaultConstructor(String value) {
		}
	}

	public static class PrivateConstructor {
		public String value;

		private PrivateConstructor() {
		}
	}

	@Test
	public void testConstructors() {
		final var jsonb = jsonb();
		final var error = assertThrows(JsonbException.class, () -> jsonb.fromJson("{}", NoDefaultConstructor.class));
		assertTrue(error.getMessage().contains("no default constructor"), error.getMessage());
		assertInstanceOf(NoSuchMethodException.class, error.getCause().getCause());

		assertEquals("a", jsonb.fromJson("{\"value\":\"a\"}", PrivateConstructor.class).value);
		assertEquals("b", jsonb.fromJson("{\"value\":\"b\"}", PrivateConstructor.class).value);
	}

	public static class Temperature {
		public double celsius;
	}

	public static class AbsoluteZero implements JsonbAdapter<Temperature, Double> {
		@Override
		public Double adaptToJson(Temperature obj) {
			return obj == null ? -273.15 : obj.celsius;
		}

		@Override
		public Temperature adaptFromJson(Double obj) {
			final var t = new Temperature();
			t.celsius = obj == null ? -273.15 : obj;
			return t;
		}
	}

	public static class Omitted implements JsonbAdapter<Temperature, Double> {
		@Override
		public Double adaptToJson(Temperature obj) {
			return obj == null ? null : obj.celsius;
		}

		@Override
		public Temperature adaptFromJson(Double obj) {
			return null;
		}
	}

	public static class Weather {
		public Temperature high;
		public Temperature low;
	}

	@Test
	public void testNullProperties() {
		final var zero = jsonb(new JsonbConfig().withAdapters(new AbsoluteZero()));
		assertEquals("{\"high\":-273.15,\"low\":-273.15}", zero.toJson(new Weather()));
		assertEquals("{\"high\":-273.15,\"low\":-273.15}", tree(zero, new Weather()));

		final var omitted = jsonb(new JsonbConfig().withAdapters(new Omitted()));
		assertEquals("{}", omitted.toJson(new Weather()));
		assertEquals("{}", tree(omitted, new Weather()));

		final var nulls = jsonb(new JsonbConfig().withAdapters(new Omitted()).withNullValues(true));
		assertEquals("{\"high\":null,\"low\":null}", nulls.toJson(new Weather()));
		assertEquals("{\"high\":null,\"low\":null}", tree(nulls, new Weather()));
	}

	public static class Failing {
		public String getBoom() {
			throw new IllegalStateException("boom");
		}

		public void setBoom(String boom) {
			throw new IllegalStateException("boom " + boom);
		}
	}

	@Test
	public void testFailuresNameTheProperty() {
		final var jsonb = jsonb();
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.toJson(new Failing())).getMessage()
				.startsWith("failed to write Failing.boom: boom"));
		assertTrue(assertThrows(JsonbException.class, () -> tree(jsonb, new Failing())).getMessage()
				.startsWith("failed to write Failing.boom: boom"));
		assertTrue(assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"boom\":\"x\"}", Failing.class))
				.getMessage().startsWith("failed to read Failing.boom (line 1"));
		assertTrue(assertThrows(JsonbException.class, () -> fromTree(jsonb, Failing.class, "{\"boom\":\"x\"}"))
				.getMessage().startsWith("failed to read Failing.boom: boom x"));
	}

	@Test
	public void testReadOnlyTypes() {
		final var jsonb = jsonb();
		final var model = jsonb.model(Defaults.class);
		assertNull(model.writable(edu.iu.client.IuJsonPropertyNameFormat.IDENTITY, "readOnly"));
		assertEquals(4, model.readable(edu.iu.client.IuJsonPropertyNameFormat.IDENTITY).length);
	}

}
