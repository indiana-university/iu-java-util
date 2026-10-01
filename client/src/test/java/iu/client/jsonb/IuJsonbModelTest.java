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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Stream;

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
		assertInstanceOf(IllegalStateException.class, error.getCause());

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

	public static class Base<T> {
		public T value;
		public List<T> values;
		private T accessed;

		public T getAccessed() {
			return accessed;
		}

		public void setAccessed(T accessed) {
			this.accessed = accessed;
		}
	}

	public static class Integers extends Base<Integer> {
	}

	public static class Overriding extends Base<Integer> {
		// the compiler adds a bridge method, Object getAccessed(), which isn't a
		// property of its own
		@Override
		public Integer getAccessed() {
			return 7;
		}
	}

	@Test
	public void testGenericSuperclassTypes() {
		final var jsonb = jsonb();
		final var json = "{\"accessed\":3,\"value\":1,\"values\":[2]}";
		for (final var bean : List.of(jsonb.fromJson(json, Integers.class), fromTree(jsonb, Integers.class, json))) {
			assertInstanceOf(Integer.class, bean.value);
			assertEquals(1, bean.value);
			assertEquals(List.of(2), bean.values);
			assertInstanceOf(Integer.class, bean.values.get(0));
			assertInstanceOf(Integer.class, bean.getAccessed());
		}
		final var integers = jsonb.fromJson(json, Integers.class);
		assertEquals(json, jsonb.toJson(integers));
		assertEquals(json, tree(jsonb, integers));

		assertEquals("{\"accessed\":7}", jsonb.toJson(new Overriding()));
	}

	public static class Box<T> {
		public T content;
	}

	public static class Boxes {
		public Box<Instant> when;
		public Box<List<Integer>> counts;
	}

	@Test
	public void testGenericPropertyTypes() {
		final var jsonb = jsonb();
		final var boxes = new Boxes();
		boxes.when = new Box<>();
		boxes.when.content = Instant.EPOCH;
		boxes.counts = new Box<>();
		boxes.counts.content = List.of(1);

		final var json = "{\"counts\":{\"content\":[1]},\"when\":{\"content\":\"1970-01-01T00:00:00Z\"}}";
		assertEquals(json, jsonb.toJson(boxes));
		assertEquals(json, tree(jsonb, boxes));
		for (final var read : List.of(jsonb.fromJson(json, Boxes.class), fromTree(jsonb, Boxes.class, json))) {
			assertEquals(Instant.EPOCH, read.when.content);
			assertEquals(List.of(1), read.counts.content);
			assertInstanceOf(Integer.class, read.counts.content.get(0));
		}

		// each parameterization has a model of its own
		final var instants = Boxes.class.getFields()[0].getGenericType();
		assertEquals(instants, Boxes.class.getFields()[0].getGenericType());
		assertTrue(jsonb.model(Box.class) != jsonb.model(instants));
	}

	static class PackagePrivate {
		private String name = "n";

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	private static class Private {
		public String text = "t";
	}

	static class Enclosing {
		public static class Nested {
			public String getNested() {
				return "x";
			}
		}
	}

	@Test
	public void testAccessorsOfNonPublicClasses() {
		final var jsonb = jsonb();
		assertEquals("{\"name\":\"n\"}", jsonb.toJson(new PackagePrivate()));
		assertEquals("m", jsonb.fromJson("{\"name\":\"m\"}", PackagePrivate.class).name);
		assertEquals("{\"text\":\"t\"}", jsonb.toJson(new Private()));
		assertEquals("u", jsonb.fromJson("{\"text\":\"u\"}", Private.class).text);
		assertEquals("{\"nested\":\"x\"}", jsonb.toJson(new Enclosing.Nested()));

		assertTrue(iu.client.BeanModel.isPublic(IuJsonbModelTest.class));
		assertFalse(iu.client.BeanModel.isPublic(PackagePrivate.class));
		assertFalse(iu.client.BeanModel.isPublic(Enclosing.Nested.class));
	}

	public static class Guarded {
		public String read = "r";
		public String write;
		public String open = "o";

		private String getRead() {
			return "hidden";
		}

		void setWrite(String write) {
			this.write = "hidden " + write;
		}
	}

	@Test
	public void testNonPublicAccessorsCloseTheirField() {
		final var jsonb = jsonb();
		// the private getter closes reading, and the package-private setter writing
		assertEquals("{\"open\":\"o\",\"write\":null}", jsonb(new JsonbConfig().withNullValues(true))
				.toJson(new Guarded()));
		final var read = jsonb.fromJson("{\"read\":\"x\",\"write\":\"y\",\"open\":\"z\"}", Guarded.class);
		assertEquals("x", read.read);
		assertNull(read.write);
		assertEquals("z", read.open);
	}

	public static class Flags {
		public boolean isOn() {
			return true;
		}

		public boolean getOn() {
			return false;
		}
	}

	public static class Overloaded {
		private String value;

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

		public void setValue(int value) {
			this.value = "int " + value;
		}
	}

	public static class OverloadedField implements java.io.Serializable {
		private static final long serialVersionUID = 1L;

		public String value;

		public void setValue(String value) {
			this.value = "string " + value;
		}

		public void setValue(int value) {
			this.value = "int " + value;
		}
	}

	public static class Ambiguous {
		public void setValue(String value) {
		}

		public void setValue(Integer value) {
		}
	}

	@Test
	public void testAccessorSelection() {
		final var jsonb = jsonb();
		// is over get, for a boolean
		assertEquals("{\"on\":true}", jsonb.toJson(new Flags()));
		// the setter that takes what the getter reads
		assertEquals("x", jsonb.fromJson("{\"value\":\"x\"}", Overloaded.class).getValue());
		// or the field's type; a platform interface such as Serializable adds nothing
		assertEquals("string x", jsonb.fromJson("{\"value\":\"x\"}", OverloadedField.class).value);
		// with nothing to choose by
		final var error = assertThrows(JsonbException.class, () -> jsonb.fromJson("{}", Ambiguous.class));
		assertTrue(error.getMessage().contains("ambiguous setters for property value of " + Ambiguous.class.getName()),
				error::getMessage);
	}

	public static class Names {
		public static String getStatic() {
			return null;
		}

		public String get() {
			return null;
		}

		public boolean is() {
			return false;
		}

		public void set(String value) {
		}

		public String isText() {
			return null;
		}

		public void getNothing() {
		}

		public String setReturning(String value) {
			return value;
		}

		public void setTwo(String a, String b) {
		}

		public String other() {
			return null;
		}

		public void other(String value) {
		}

		public String getURL() {
			return null;
		}

		public boolean isOn() {
			return false;
		}

		public void setOn(boolean on) {
		}
	}

	static String accessorName(String name, Class<?>... parameterTypes) throws Exception {
		return iu.client.BeanModel.accessorName(Names.class.getMethod(name, parameterTypes));
	}

	@Test
	public void testAccessorNames() throws Exception {
		assertNull(accessorName("getStatic"));
		assertNull(accessorName("get"));
		assertNull(accessorName("is"));
		assertNull(accessorName("set", String.class));
		assertNull(accessorName("isText"));
		assertNull(accessorName("getNothing"));
		assertNull(accessorName("setReturning", String.class));
		assertNull(accessorName("setTwo", String.class, String.class));
		assertNull(accessorName("other"));
		assertNull(accessorName("other", String.class));
		assertEquals("URL", accessorName("getURL"));
		assertEquals("on", accessorName("isOn"));
		assertEquals("on", accessorName("setOn", boolean.class));

		final var bridge = Stream.of(Overriding.class.getDeclaredMethods()).filter(Method::isBridge).findFirst().get();
		assertNull(iu.client.BeanModel.accessorName(bridge));
	}

	public static class Optionals {
		public Optional<String> text = Optional.empty();
		public OptionalInt count = OptionalInt.empty();
		public OptionalLong total = OptionalLong.of(1);
		public OptionalDouble ratio = OptionalDouble.empty();
		public Optional<String> present = Optional.of("p");
	}

	@Test
	public void testEmptyOptionalsAreAbsent() {
		final var omitting = jsonb();
		assertEquals("{\"present\":\"p\",\"total\":1}", omitting.toJson(new Optionals()));
		assertEquals("{\"present\":\"p\",\"total\":1}", tree(omitting, new Optionals()));

		final var including = jsonb(new JsonbConfig().withNullValues(true));
		final var json = "{\"count\":null,\"present\":\"p\",\"ratio\":null,\"text\":null,\"total\":1}";
		assertEquals(json, including.toJson(new Optionals()));
		assertEquals(json, tree(including, new Optionals()));

		assertTrue(iu.client.BeanModel.isAbsent(null));
		assertTrue(iu.client.BeanModel.isAbsent(Optional.empty()));
		assertTrue(iu.client.BeanModel.isAbsent(OptionalInt.empty()));
		assertTrue(iu.client.BeanModel.isAbsent(OptionalLong.empty()));
		assertTrue(iu.client.BeanModel.isAbsent(OptionalDouble.empty()));
		assertFalse(iu.client.BeanModel.isAbsent(Optional.of(1)));
		assertFalse(iu.client.BeanModel.isAbsent(OptionalInt.of(1)));
		assertFalse(iu.client.BeanModel.isAbsent(OptionalLong.of(1)));
		assertFalse(iu.client.BeanModel.isAbsent(OptionalDouble.of(1)));
		assertFalse(iu.client.BeanModel.isAbsent(""));
	}

}
