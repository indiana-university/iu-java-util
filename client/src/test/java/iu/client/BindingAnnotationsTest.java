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
package iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import edu.iu.IuException;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuJsonSerializationOptions;
import iu.client.jsonb.IuJsonbComponentAnnotationsTest;
import iu.client.jsonb.IuJsonbCreatorTest;
import iu.client.jsonb.IuJsonbPolymorphismTest;
import iu.client.jsonb.JsonbMetadata;
import jakarta.json.JsonValue;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbNillable;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.annotation.JsonbTypeSerializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;

@SuppressWarnings("javadoc")
public class BindingAnnotationsTest {

	private static final Supplier<IuJsonSerializationOptions> DEFAULT = () -> IuJsonSerializationOptions.DEFAULT;
	private static final Supplier<IuJsonSerializationOptions> LEGACY = () -> IuJsonSerializationOptions.LEGACY;

	@SuppressWarnings("unchecked")
	private static <T> IuJsonAdapter<T> adapt(Class<T> type, Supplier<IuJsonSerializationOptions> options) {
		return (IuJsonAdapter<T>) IuJsonAdapter.adapt(type, options);
	}

	@JsonbPropertyOrder({ "b", "a" })
	public static class Annotated {
		@JsonbProperty("renamed")
		public String a;
		public String b;
		@JsonbTransient
		public String hidden;
		@JsonbNillable
		public String nil;
		private String viaGetter;

		@JsonbProperty("gotten")
		public String getViaGetter() {
			return viaGetter;
		}

		@JsonbProperty("setted")
		public void setViaGetter(String viaGetter) {
			this.viaGetter = viaGetter;
		}
	}

	@Test
	public void testAnnotatedProperties() {
		final var bean = new Annotated();
		bean.a = "1";
		bean.b = "2";
		bean.hidden = "h";
		bean.viaGetter = "3";

		final var adapter = adapt(Annotated.class, DEFAULT);
		assertEquals("{\"b\":\"2\",\"renamed\":\"1\",\"nil\":null,\"gotten\":\"3\"}",
				adapter.toJson(bean).toString());

		final var read = adapter.fromJson(IuJson.parse(
				"{\"renamed\":\"x\",\"b\":\"y\",\"setted\":\"z\",\"gotten\":\"g\",\"hidden\":\"h\",\"unknown\":1}"));
		assertEquals("x", read.a);
		assertEquals("y", read.b);
		assertEquals("z", read.viaGetter);
		assertNull(read.hidden);
	}

	@Test
	public void testLegacyIgnoresFieldsAndAnnotations() {
		final var bean = new Annotated();
		bean.a = "1";
		bean.viaGetter = "3";

		final var adapter = adapt(Annotated.class, LEGACY);
		assertEquals("{\"viaGetter\":\"3\"}", adapter.toJson(bean).toString());

		final var read = adapter.fromJson(IuJson.parse("{\"viaGetter\":\"q\",\"renamed\":\"x\"}"));
		assertEquals("q", read.viaGetter);
		assertNull(read.a);
	}

	public static class TransientAccessors {
		private String r;
		private String w;

		@JsonbTransient
		public String getR() {
			return r;
		}

		public void setR(String r) {
			this.r = r;
		}

		public String getW() {
			return w;
		}

		@JsonbTransient
		public void setW(String w) {
			this.w = w;
		}

		public void setOnly(String only) {
			throw new AssertionError();
		}

		public String getReadOnly() {
			return "ro";
		}
	}

	@Test
	public void testTransientAccessors() {
		final var bean = new TransientAccessors();
		bean.r = "1";
		bean.w = "2";

		final var adapter = adapt(TransientAccessors.class, DEFAULT);
		assertEquals("{\"readOnly\":\"ro\",\"w\":\"2\"}", adapter.toJson(bean).toString());

		final var read = adapter.fromJson(IuJson.parse("{\"r\":\"1\",\"w\":\"2\",\"readOnly\":\"x\"}"));
		assertEquals("1", read.r);
		assertNull(read.w);
	}

	public static class Conflict {
		@JsonbTransient
		@JsonbProperty("x")
		public String c;
	}

	@Test
	public void testTransientConflict() {
		final var adapter = adapt(Conflict.class, DEFAULT);
		final var error = assertThrows(IllegalStateException.class, () -> adapter.toJson(new Conflict()));
		assertTrue(error.getMessage().startsWith(
				"property c of " + Conflict.class.getName() + " is transient, so can't be customized by "),
				error::getMessage);

		// discovered as before 7.1, the annotations don't apply
		assertEquals(IuJson.object().build(), adapt(Conflict.class, LEGACY).toJson(new Conflict()));
	}

	@JsonbNillable
	public static class NillableType {
		public String value;
	}

	public static class NillableGetter {
		@JsonbNillable(false)
		public String nf;

		@JsonbNillable
		public String getNf() {
			return nf;
		}
	}

	@Test
	public void testNillable() {
		assertEquals("{\"value\":null}", adapt(NillableType.class, DEFAULT).toJson(new NillableType()).toString());
		assertEquals("{\"nf\":null}", adapt(NillableGetter.class, DEFAULT).toJson(new NillableGetter()).toString());
		assertEquals("{}", adapt(NillableType.class, LEGACY).toJson(new NillableType()).toString());
	}

	public interface Proxied {
		@JsonbProperty("renamed")
		String getA();

		@JsonbTransient
		String getHidden();

		@JsonbTransient
		default String getDefaulted() {
			return "default";
		}

		String getPlain();
	}

	@Test
	public void testProxy() {
		final var json = IuJson.parse(
				"{\"renamed\":\"1\",\"hidden\":\"h\",\"defaulted\":\"d\",\"plain\":\"p\",\"a\":\"wrong\"}");

		final var proxied = adapt(Proxied.class, DEFAULT).fromJson(json);
		assertEquals("1", proxied.getA());
		assertNull(proxied.getHidden());
		assertEquals("default", proxied.getDefaulted());
		assertEquals("p", proxied.getPlain());

		final var legacy = adapt(Proxied.class, LEGACY).fromJson(json);
		assertEquals("wrong", legacy.getA());
		assertEquals("h", legacy.getHidden());
		assertEquals("d", legacy.getDefaulted());
	}

	enum Letter {
		A, B;

		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}

	public static class Letters {
		private Letter letter = Letter.A;
		private Map<Letter, String> map = Map.of(Letter.B, "b");

		public Letter getLetter() {
			return letter;
		}

		public void setLetter(Letter letter) {
			this.letter = letter;
		}

		public Map<Letter, String> getMap() {
			return map;
		}

		public void setMap(Map<Letter, String> map) {
			this.map = map;
		}
	}

	@Test
	public void testEnumText() {
		assertEquals("{\"letter\":\"A\",\"map\":{\"B\":\"b\"}}",
				adapt(Letters.class, DEFAULT).toJson(new Letters()).toString());
		assertEquals("{\"letter\":\"a\",\"map\":{\"b\":\"b\"}}",
				adapt(Letters.class, LEGACY).toJson(new Letters()).toString());

		final var read = adapt(Letters.class, DEFAULT)
				.fromJson(IuJson.parse("{\"letter\":\"B\",\"map\":{\"A\":\"a\"}}"));
		assertSame(Letter.B, read.letter);
		assertEquals(Map.of(Letter.A, "a"), read.map);
	}

	public static class Base<T> {
		public T value;
	}

	public static class Concrete extends Base<Letter> {
	}

	public static class Holder {
		public Base<Letter> holder;
	}

	@Test
	public void testGenericPropertyTypes() {
		assertSame(Letter.A, adapt(Concrete.class, DEFAULT).fromJson(IuJson.parse("{\"value\":\"A\"}")).value);
		assertSame(Letter.B,
				adapt(Holder.class, DEFAULT).fromJson(IuJson.parse("{\"holder\":{\"value\":\"B\"}}")).holder.value);
	}

	public static class Simple {
		public String s = "x";
	}

	private static String write(IuJsonAdapter<Object> adapter, Object value) {
		final var writer = new StringWriter();
		try (final var generator = IuJson.PROVIDER.createGenerator(writer)) {
			adapter.write(value, generator);
		}
		return writer.toString();
	}

	@Test
	public void testRuntimeType() {
		final var adapter = adapt(Object.class, DEFAULT);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertSame(JsonValue.TRUE, adapter.toJson(JsonValue.TRUE));
		assertEquals(IuJson.object().add("a", 1).build(), adapter.toJson(IuJson.object().add("a", 1)));
		assertEquals(IuJson.array().add(1).build(), adapter.toJson(IuJson.array().add(1)));
		assertEquals("{\"s\":\"x\"}", adapter.toJson(new Simple()).toString());
		assertEquals("\"A\"", adapter.toJson(Letter.A).toString());
		assertEquals("\"a\"", adapt(Object.class, LEGACY).toJson(Letter.A).toString());
		assertEquals("[{\"s\":\"x\"}]", adapter.toJson(List.of(new Simple())).toString());
		assertEquals("{\"k\":{\"s\":\"x\"}}", adapter.toJson(Map.of("k", new Simple())).toString());
		assertEquals("[\"a\"]", adapter.toJson(new String[] { "a" }).toString());
		assertEquals("1", adapter.toJson(1).toString());
		assertEquals("Unsupported for JSON conversion: class java.lang.Object",
				assertThrows(UnsupportedOperationException.class, () -> adapter.toJson(new Object())).getMessage());

		assertEquals("[{\"s\":\"x\"}]", write(adapter, List.of(new Simple())));
		assertEquals("null", write(adapter, null));

		// a proxy of anything but JSON converts as its own class
		final var runnable = java.lang.reflect.Proxy.newProxyInstance(Runnable.class.getClassLoader(),
				new Class<?>[] { Runnable.class }, (proxy, method, args) -> null);
		assertSame(runnable.getClass(), JsonAdapters.runtimeType(runnable));

		final var comparable = adapt(Comparable.class, DEFAULT);
		assertEquals(IuJson.string("s"), comparable.toJson("s"));

		assertEquals(Map.of("a", List.of(BigDecimal.ONE)), adapter.fromJson(IuJson.parse("{\"a\":[1]}")));
		try (final var parser = IuJson.PROVIDER.createParser(new StringReader("{\"a\":[1]}"))) {
			parser.next();
			assertEquals(Map.of("a", List.of(BigDecimal.ONE)), adapter.read(parser));
		}
	}

	public static class PrivateAnnotated {
		@JsonbTransient
		private String secret = "s";
		@JsonbProperty("n")
		private String name = "x";
		@JsonbDateFormat(value = "dd.MM.yyyy", locale = "en-US")
		private Date when;
		@JsonbNumberFormat(value = "#0.00", locale = "en-US")
		private double amount;

		public String getSecret() {
			return secret;
		}

		public String getName() {
			return name;
		}

		public Date getWhen() {
			return when;
		}

		public void setWhen(Date when) {
			this.when = when;
		}

		public double getAmount() {
			return amount;
		}

		public void setAmount(double amount) {
			this.amount = amount;
		}
	}

	@Test
	public void testPrivateFieldAnnotations() {
		final var bean = new PrivateAnnotated();
		bean.when = Date.from(java.time.Instant.parse("2026-09-27T00:00:00Z"));
		bean.amount = 1.5;
		final var adapter = adapt(PrivateAnnotated.class, DEFAULT);
		final var json = "{\"amount\":\"1.50\",\"n\":\"x\",\"when\":\"27.09.2026\"}";
		assertEquals(json, adapter.toJson(bean).toString());
		final var read = adapter.fromJson(IuJson.parse(json));
		assertEquals(bean.when, read.when);
		assertEquals(1.5, read.amount);

		// before 7.1, no annotations
		assertEquals("{\"amount\":1.5,\"name\":\"x\",\"secret\":\"s\",\"when\":\"2026-09-27Z\"}",
				adapt(PrivateAnnotated.class, LEGACY).toJson(bean).toString());
	}

	@Test
	public void testPackageFormats() {
		final var bean = new iu.client.jsonb.formats.PackageFormatted();
		bean.date = Date.from(java.time.Instant.parse("2026-09-27T12:00:00Z"));
		bean.amount = 2;
		bean.text = "t";
		final var adapter = adapt(iu.client.jsonb.formats.PackageFormatted.class, DEFAULT);
		final var json = "{\"amount\":\"2.0\",\"date\":\"27.09.2026\",\"text\":\"t\"}";
		assertEquals(json, adapter.toJson(bean).toString());
		assertEquals(2, adapter.fromJson(IuJson.parse(json)).amount);
	}

	@JsonbNumberFormat(value = "#0.0", locale = "en-US")
	public interface FormattedProxy {
		@JsonbDateFormat(value = "dd.MM.yyyy", locale = "en-US")
		Date getWhen();

		BigDecimal getAmount();

		String getText();
	}

	@Test
	public void testProxyFormats() {
		final var proxy = adapt(FormattedProxy.class, DEFAULT)
				.fromJson(IuJson.parse("{\"when\":\"27.09.2026\",\"amount\":\"1.5\",\"text\":\"t\"}"));
		assertEquals(Date.from(java.time.Instant.parse("2026-09-27T00:00:00Z")), proxy.getWhen());
		assertEquals(new BigDecimal("1.5"), proxy.getAmount());
		assertEquals("t", proxy.getText());

		final var invalid = adapt(FormattedProxy.class, DEFAULT).fromJson(IuJson.parse("{\"when\":\"x\"}"));
		assertThrows(IllegalArgumentException.class, invalid::getWhen);
	}

	@Test
	public void testTypeComponents() {
		final var money = adapt(IuJsonbComponentAnnotationsTest.Money.class, DEFAULT);
		final var value = new IuJsonbComponentAnnotationsTest.Money();
		value.amount = "1";
		assertEquals(IuJson.string("$1"), money.toJson(value));
		assertEquals("1", money.fromJson(IuJson.string("$1")).amount);

		// streaming
		final var writer = new StringWriter();
		try (final var generator = IuJson.PROVIDER.createGenerator(writer)) {
			money.write(value, generator);
		}
		assertEquals("\"$1\"", writer.toString());
		try (final var parser = IuJson.PROVIDER.createParser(new StringReader("\"$2\""))) {
			parser.next();
			assertEquals("2", money.read(parser).amount);
		}

		// before 7.1, the type converts as a business object
		final var legacy = adapt(IuJsonbComponentAnnotationsTest.Money.class, LEGACY);
		assertEquals(IuJson.object().build(), legacy.toJson(value));

		// declared on an interface, and on a superclass
		assertEquals(IuJson.string("shape:circle"), adapt(IuJsonbComponentAnnotationsTest.Circle.class, DEFAULT)
				.toJson(new IuJsonbComponentAnnotationsTest.Circle()));
		assertEquals(IuJson.string("$null"), adapt(IuJsonbComponentAnnotationsTest.SpecialMoney.class, DEFAULT)
				.toJson(new IuJsonbComponentAnnotationsTest.SpecialMoney()));
	}

	@Test
	public void testPropertyComponents() {
		final var adapter = adapt(IuJsonbComponentAnnotationsTest.Holder.class, DEFAULT);
		assertEquals(IuJsonbComponentAnnotationsTest.JSON,
				adapter.toJson(IuJsonbComponentAnnotationsTest.holder()).toString());

		final var read = adapter.fromJson(IuJson.parse("{\"code\":\"cba\",\"money\":\"$1\",\"name\":\"N\","
				+ "\"point\":[1,2],\"viaAccessors\":\"XY\"}"));
		assertEquals("abc", read.code);
		assertEquals("1", read.money.amount);
		assertEquals("n", read.name);
		assertEquals(2, read.point.y);
		assertEquals("xy", read.getViaAccessors());

		// before 7.1, public fields and annotations are left out
		assertEquals("{\"viaAccessors\":\"xy\"}", adapt(IuJsonbComponentAnnotationsTest.Holder.class, LEGACY)
				.toJson(IuJsonbComponentAnnotationsTest.holder()).toString());
	}

	public interface ProxiedComponents {
		@JsonbTypeDeserializer(IuJsonbComponentAnnotationsTest.Reverse.class)
		String getCode();

		String getPlain();
	}

	@Test
	public void testProxyComponents() {
		final var json = IuJson.parse("{\"code\":\"cba\",\"plain\":\"p\"}");
		final var proxied = adapt(ProxiedComponents.class, DEFAULT).fromJson(json);
		assertEquals("abc", proxied.getCode());
		assertEquals("p", proxied.getPlain());
		assertEquals("cba", adapt(ProxiedComponents.class, LEGACY).fromJson(json).getCode());

		// wrapped for IU conversions
		assertEquals("abc", IuJson.wrap(json.asJsonObject(), ProxiedComponents.class).getCode());
	}

	/**
	 * Converts through the IU conversions from inside a JSON-B component.
	 */
	public static class ReentersIu implements JsonbSerializer<String> {
		@Override
		public void serialize(String obj, JsonGenerator generator, SerializationContext ctx) {
			final var money = new IuJsonbComponentAnnotationsTest.Money();
			money.amount = obj;
			generator.write(adapt(IuJsonbComponentAnnotationsTest.Money.class, DEFAULT).toJson(money));
		}
	}

	public static class Reentrant {
		@JsonbTypeSerializer(ReentersIu.class)
		public String amount = "7";
	}

	@Test
	public void testNestedComponents() {
		assertEquals("{\"amount\":\"$7\"}", adapt(Reentrant.class, DEFAULT).toJson(new Reentrant()).toString());
	}

	@Test
	public void testCreators() {
		final var point = adapt(IuJsonbCreatorTest.Point.class, DEFAULT);
		assertEquals("{\"x\":1,\"y\":2}", point.toJson(new IuJsonbCreatorTest.Point(1, 2)).toString());
		assertEquals(new IuJsonbCreatorTest.Point(1, 0), point.fromJson(IuJson.parse("{\"x\":1}")));

		// a record converts the same before 7.1: its accessors are public
		assertEquals(new IuJsonbCreatorTest.Point(1, 2),
				adapt(IuJsonbCreatorTest.Point.class, LEGACY).fromJson(IuJson.parse("{\"x\":1,\"y\":2}")));

		final var constructed = adapt(IuJsonbCreatorTest.Constructed.class, DEFAULT)
				.fromJson(IuJson.parse("{\"extra\":\"e\",\"a\":\"x\",\"b\":2,\"skip\":1}"));
		assertEquals("x", constructed.getA());
		assertEquals(2, constructed.getB());
		assertEquals("e", constructed.extra);

		// parameters convert as declared: named, formatted, and by component
		final var named = adapt(IuJsonbCreatorTest.Named.class, DEFAULT)
				.fromJson(IuJson.parse("{\"n\":\"a\",\"date\":\"27.09.2026\"}"));
		assertEquals(new IuJsonbCreatorTest.Named("a", Optional.empty(), java.time.LocalDate.of(2026, 9, 27)),
				named);
		assertEquals("abc", adapt(IuJsonbCreatorTest.Factory.class, DEFAULT)
				.fromJson(IuJson.parse("{\"value\":\"cba\"}")).getValue());

		// a creator parameter that a setter also writes: the creator wins
		final var both = adapt(IuJsonbCreatorTest.Both.class, DEFAULT).fromJson(IuJson.parse("{\"a\":\"x\"}"));
		assertEquals("x", both.a);
		assertFalse(both.set);

		// named by the options' format
		final var snake = IuJsonSerializationOptions
				.of(edu.iu.client.IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES);
		assertEquals(new IuJsonbCreatorTest.SnakeRecord("x"),
				adapt(IuJsonbCreatorTest.SnakeRecord.class, () -> snake).fromJson(IuJson.parse("{\"first_name\":\"x\"}")));
	}

	@Test
	public void testCreatorParameterNeedsName() throws Exception {
		// the JDK is compiled without parameter names
		final var constructor = java.util.ArrayList.class.getConstructor(int.class);
		assertFalse(constructor.getParameters()[0].isNamePresent());
		final var metadata = new BindingMetadata() {
			@Override
			public boolean isCreator(java.lang.reflect.Executable executable) {
				return executable.equals(constructor);
			}
		};
		final var error = assertThrows(IllegalStateException.class, () -> new BeanModel(java.util.ArrayList.class,
				new BeanModel.Discovery(null, "LEXICOGRAPHICAL", metadata, false)));
		assertEquals("parameter 0 of creator " + constructor
				+ " has no name; declare it with @JsonbProperty, or compile with -parameters", error.getMessage());
	}

	@Test
	public void testRecordsNeedTheRuntimeToSupportThem() {
		assertNull(BeanModel.handle(() -> {
			throw new NoSuchMethodException();
		}));
		assertFalse(BeanModel.isRecord(null, IuJsonbCreatorTest.Point.class));
		assertNull(BeanModel.of(Annotated.class).creator());
		assertEquals("public iu.client.jsonb.IuJsonbCreatorTest$Point(int,int)",
				BeanModel.of(IuJsonbCreatorTest.Point.class).creator().toString());
	}

	@Test
	public void testPolymorphism() {
		final var animal = adapt(IuJsonbPolymorphismTest.Animal.class, DEFAULT);
		assertEquals(IuJsonbPolymorphismTest.LABRADOR,
				animal.toJson(IuJsonbPolymorphismTest.labrador()).toString());
		final var read = assertInstanceOf(IuJsonbPolymorphismTest.Labrador.class,
				animal.fromJson(IuJson.parse(IuJsonbPolymorphismTest.LABRADOR)));
		assertEquals("yellow", read.color);
		assertEquals("rex", read.getName());

		// no key reads as the declared type, an interface
		assertEquals("any", animal.fromJson(IuJson.parse("{\"name\":\"any\"}")).getName());

		// declared as its own type, with type information of its own
		final var dog = new IuJsonbPolymorphismTest.Dog();
		dog.setName("fido");
		assertEquals("{\"@animal\":\"dog\",\"barks\":false,\"name\":\"fido\"}",
				adapt(IuJsonbPolymorphismTest.Dog.class, DEFAULT).toJson(dog).toString());

		assertEquals("unknown alias cow for @animal of " + IuJsonbPolymorphismTest.Animal.class.getName(),
				assertThrows(IllegalArgumentException.class,
						() -> animal.fromJson(IuJson.parse("{\"@animal\":\"cow\"}"))).getMessage());

		// before 7.1, no type information
		assertEquals("{\"name\":\"rex\"}",
				adapt(IuJsonbPolymorphismTest.Animal.class, LEGACY).toJson(IuJsonbPolymorphismTest.labrador())
						.toString());
	}

	@Test
	public void testProxyPolymorphism() {
		final var json = IuJson.parse("{\"@shape\":\"round\",\"radius\":2}").asJsonObject();
		assertEquals(2, ((IuJsonbPolymorphismTest.Round) IuJson.wrap(json, IuJsonbPolymorphismTest.Shape.class))
				.getRadius());
		assertEquals(2, ((IuJsonbPolymorphismTest.Round) adapt(IuJsonbPolymorphismTest.Shape.class, DEFAULT)
				.fromJson(json)).getRadius());

		// a proxy wraps only an interface
		final var dog = IuJson.parse("{\"@animal\":\"dog\"}").asJsonObject();
		assertEquals("alias dog names " + IuJsonbPolymorphismTest.Dog.class.getName()
				+ ", which isn't an interface to wrap",
				assertThrows(IllegalArgumentException.class,
						() -> IuJson.wrap(dog, IuJsonbPolymorphismTest.Animal.class)).getMessage());
	}

	private static class NotPublic {
		@SuppressWarnings("unused")
		public int getValue() {
			return 1;
		}

		@SuppressWarnings("unused")
		private int getHidden() {
			return 2;
		}
	}

	private static class Writes extends java.io.FilterWriter {
		@SuppressWarnings("unused")
		Writes() {
			super(new StringWriter());
		}

		@Override
		public void write(String str) throws java.io.IOException {
			super.write(str);
		}
	}

	@Test
	public void testAccessorEdges() throws Exception {
		// a method that isn't public is made accessible
		final var hidden = NotPublic.class.getDeclaredMethod("getHidden");
		assertSame(hidden, BeanModel.accessor(hidden));
		assertEquals(2, hidden.invoke(new NotPublic()));

		// matched by parameters, not name alone
		final var list = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(List.of("a")));
		final var toArray = list.getClass().getMethod("toArray", Object[].class);
		final var accessor = BeanModel.accessor(toArray);
		assertTrue(BeanModel.isAccessible(accessor.getDeclaringClass()));
		assertEquals(List.of("a"), List.of((Object[]) accessor.invoke(list, (Object) new Object[0])));

		// FilterWriter declares write overloads, but not write(String); Writer does
		final var write = Writes.class.getMethod("write", String.class);
		assertEquals(java.io.Writer.class, BeanModel.accessor(write).getDeclaringClass());

		assertTrue(BeanModel.isAccessible(String.class));
		assertFalse(BeanModel.isAccessible(NotPublic.class));
		// public, in a package not exported
		assertFalse(BeanModel.isAccessible(Class.forName("sun.nio.cs.UTF_8")));
	}

	@Test
	public void testAccessorThroughPublicSupertype() throws Exception {
		// a JDK class that isn't public, in a package not open to this module:
		// invoked through the public interface declaring it
		final var list = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(List.of("a")));
		final var size = list.getClass().getMethod("size");
		assertFalse(BeanModel.isPublic(size.getDeclaringClass()));
		final var accessor = BeanModel.accessor(size);
		assertTrue(BeanModel.isPublic(accessor.getDeclaringClass()));
		assertEquals(1, accessor.invoke(list));

		// a public class's method is itself
		final var publicMethod = Annotated.class.getMethod("getViaGetter");
		assertSame(publicMethod, BeanModel.accessor(publicMethod));

		// with no public supertype declaring it, or none exported to this module,
		// made accessible within the module instead
		final var notPublic = NotPublic.class.getMethod("getValue");
		assertSame(notPublic, BeanModel.accessor(notPublic));
		assertEquals(1, notPublic.invoke(new NotPublic()));
		final IuJsonbCreatorTest.Point point = new IuJsonbCreatorTest.Point(1, 2);
		assertEquals(1, BeanModel.of(IuJsonbCreatorTest.Point.class).readable(PropertyNaming.of(
				edu.iu.client.IuJsonPropertyNameFormat.IDENTITY))[0].get(point));
		final Supplier<String> lambda = () -> "x";
		final var get = lambda.getClass().getMethod("get");
		assertEquals("x", BeanModel.accessor(get).invoke(lambda));
	}

	@Test
	public void testModel() throws Exception {
		final var model = BeanModel.of(Annotated.class);
		assertSame(Annotated.class, model.type());
		assertSame(model, BeanModel.of(Annotated.class));
		assertSame(BeanModel.legacy(Annotated.class), BeanModel.legacy(Annotated.class));

		final var getter = Annotated.class.getMethod("getViaGetter");
		final var property = model.property(getter);
		assertEquals("viaGetter", property.name());
		// the private field declares annotations for the property, too
		assertEquals(List.of(Annotated.class.getDeclaredField("viaGetter"), getter,
				Annotated.class.getMethod("setViaGetter", String.class)), property.members());
		assertNull(model.property(Object.class.getMethod("toString")));

		// a platform class declares no properties
		assertEquals(0, BeanModel.of(Object.class).readable(PropertyNaming.of(IuJsonPropertyNameFormat.IDENTITY)).length);
	}

	@Test
	public void testPresence() {
		assertSame(JsonbMetadata.INSTANCE, BindingMetadata.get());
		assertSame(JsonbMetadata.INSTANCE,
				JsonbPresence.metadata(JsonbPresence.class.getModule(), JsonbPresence.JSONB));
		assertSame(BindingMetadata.NONE,
				JsonbPresence.metadata(getClass().getClassLoader().getUnnamedModule(), JsonbPresence.JSONB));
		assertSame(BindingMetadata.NONE, JsonbPresence.metadata(Object.class.getModule(), JsonbPresence.JSONB));
		assertSame(BindingMetadata.NONE,
				JsonbPresence.metadata(JsonbPresence.class.getModule(), "iu.util.client.no.such.module"));
	}

	@Test
	public void testNone() {
		final var field = IuException.unchecked(() -> Annotated.class.getField("a"));
		final var none = BindingMetadata.NONE;
		assertNull(none.propertyOrder(Annotated.class));
		assertNull(none.visibility(Annotated.class));
		assertNull(none.name(field));
		assertFalse(none.isTransient(field));
		assertFalse(none.isCustomized(field));
		assertNull(none.nillable(field));
		assertNull(none.components(Annotated.class, DEFAULT));
		assertNull(none.components(String.class, null, null, new java.lang.reflect.AnnotatedElement[] { field },
				DEFAULT));
	}

}
