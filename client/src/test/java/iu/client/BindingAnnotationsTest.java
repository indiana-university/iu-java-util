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
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import edu.iu.IuException;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.client.IuJsonSerializationOptions;
import iu.client.jsonb.JsonbMetadata;
import jakarta.json.JsonValue;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbNillable;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbTransient;

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
	}

}
