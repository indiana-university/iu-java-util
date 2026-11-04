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
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbTypeAdapter;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.annotation.JsonbTypeSerializer;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

@SuppressWarnings("javadoc")
public class IuJsonbComponentAnnotationsTest {

	@JsonbTypeAdapter(MoneyAdapter.class)
	public static class Money {
		public String amount;

		public Money() {
		}

		Money(String amount) {
			this.amount = amount;
		}
	}

	public static class MoneyAdapter implements JsonbAdapter<Money, String> {
		@Override
		public String adaptToJson(Money obj) {
			return "$" + obj.amount;
		}

		@Override
		public Money adaptFromJson(String obj) {
			return new Money(obj.substring(1));
		}
	}

	@JsonbTypeSerializer(PointSerializer.class)
	@JsonbTypeDeserializer(PointDeserializer.class)
	public static class Point {
		public int x;
		public int y;
	}

	public static class PointSerializer implements JsonbSerializer<Point> {
		@Override
		public void serialize(Point obj, JsonGenerator generator, SerializationContext ctx) {
			generator.writeStartArray().write(obj.x).write(obj.y).writeEnd();
		}
	}

	public static class PointDeserializer implements JsonbDeserializer<Point> {
		@Override
		public Point deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			final var array = parser.getArray();
			final var point = new Point();
			point.x = array.getInt(0);
			point.y = array.getInt(1);
			return point;
		}
	}

	@JsonbTypeSerializer(ShapeSerializer.class)
	public interface Shape {
		String getName();
	}

	public static class Circle implements Shape {
		@Override
		public String getName() {
			return "circle";
		}
	}

	public static class ShapeSerializer implements JsonbSerializer<Shape> {
		@Override
		public void serialize(Shape obj, JsonGenerator generator, SerializationContext ctx) {
			generator.write("shape:" + obj.getName());
		}
	}

	public static class Upper implements JsonbAdapter<String, String> {
		@Override
		public String adaptToJson(String obj) {
			return obj.toUpperCase();
		}

		@Override
		public String adaptFromJson(String obj) {
			return obj.toLowerCase();
		}
	}

	public static class Reverse implements JsonbSerializer<String>, JsonbDeserializer<String> {
		@Override
		public void serialize(String obj, JsonGenerator generator, SerializationContext ctx) {
			generator.write(new StringBuilder(obj).reverse().toString());
		}

		@Override
		public String deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
			return new StringBuilder(parser.getString()).reverse().toString();
		}
	}

	/**
	 * Wraps a value, passing it down to its type's own conversion.
	 */
	public static class Wraps implements JsonbSerializer<Object> {
		@Override
		public void serialize(Object obj, JsonGenerator generator, SerializationContext ctx) {
			generator.writeStartObject();
			ctx.serialize("wrapped", obj, generator);
			generator.writeEnd();
		}
	}

	public static class Holder {
		public Money money;
		public Point point;
		public List<Money> monies;
		public Object any;
		@JsonbTypeAdapter(Upper.class)
		public String name;
		@JsonbTypeAdapter(Upper.class)
		public String other;
		@JsonbTypeSerializer(Reverse.class)
		@JsonbTypeDeserializer(Reverse.class)
		public String code;
		@JsonbTypeSerializer(Wraps.class)
		public Money wrapped;
		private String viaAccessors;

		@JsonbTypeSerializer(Reverse.class)
		public String getViaAccessors() {
			return viaAccessors;
		}

		@JsonbTypeAdapter(Upper.class)
		public void setViaAccessors(String viaAccessors) {
			this.viaAccessors = viaAccessors;
		}
	}

	public static Holder holder() {
		final var holder = new Holder();
		holder.money = new Money("1");
		holder.point = new Point();
		holder.point.x = 1;
		holder.point.y = 2;
		holder.monies = List.of(new Money("2"));
		holder.any = new Money("3");
		holder.name = "n";
		holder.other = "o";
		holder.code = "abc";
		holder.wrapped = new Money("4");
		holder.viaAccessors = "xy";
		return holder;
	}

	public static final String JSON = "{\"any\":\"$3\",\"code\":\"cba\",\"money\":\"$1\",\"monies\":[\"$2\"],"
			+ "\"name\":\"N\",\"other\":\"O\",\"point\":[1,2],\"viaAccessors\":\"yx\",\"wrapped\":{\"wrapped\":\"$4\"}}";

	@Test
	public void testTypeAndPropertyComponents() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig());
		assertEquals(JSON, jsonb.toJson(holder()));
		assertEquals(JSON, jsonb.adapt(Holder.class).toJson(holder()).toString());

		final var json = "{\"any\":\"$3\",\"code\":\"cba\",\"monies\":[\"$2\"],\"money\":\"$1\","
				+ "\"name\":\"N\",\"point\":[1,2],\"viaAccessors\":\"XY\"}";
		for (final var read : new Holder[] { jsonb.fromJson(json, Holder.class),
				(Holder) jsonb.adapt(Holder.class).fromJson(IuJson.parse(json)) }) {
			assertEquals("1", read.money.amount);
			assertEquals(2, read.point.y);
			assertEquals("2", read.monies.get(0).amount);
			assertEquals("$3", read.any);
			assertEquals("n", read.name);
			assertEquals("abc", read.code);
			assertEquals("xy", read.viaAccessors);
		}

		// top level
		assertEquals("\"$5\"", jsonb.toJson(new Money("5")));
		assertEquals("5", jsonb.fromJson("\"$5\"", Money.class).amount);
	}

	@Test
	public void testInterfaceComponentAppliesToImplementations() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig());
		assertEquals("\"shape:circle\"", jsonb.toJson(new Circle()));
		assertEquals("[\"shape:circle\"]", jsonb.toJson(List.of(new Circle())));
	}

	public static class ConfiguredMoney implements JsonbAdapter<Money, String> {
		@Override
		public String adaptToJson(Money obj) {
			return "configured";
		}

		@Override
		public Money adaptFromJson(String obj) {
			return new Money("configured");
		}
	}

	public static class SpecialMoney extends Money {
	}

	public static class SpecialMoneyAdapter implements JsonbAdapter<SpecialMoney, String> {
		@Override
		public String adaptToJson(SpecialMoney obj) {
			return "special";
		}

		@Override
		public SpecialMoney adaptFromJson(String obj) {
			return new SpecialMoney();
		}
	}

	@Test
	public void testDeclaredBeforeConfigured() {
		// declared by the type wins a tie with a configured one
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withAdapters(new ConfiguredMoney()));
		assertEquals("\"$1\"", jsonb.toJson(new Money("1")));

		// a configured one for a subtype is more specific
		final var special = IuJsonbTest.jsonb(new JsonbConfig().withAdapters(new SpecialMoneyAdapter()));
		assertEquals("\"special\"", special.toJson(new SpecialMoney()));
		assertInstanceOf(SpecialMoney.class, special.fromJson("\"x\"", SpecialMoney.class));
	}

	public static class NoDefaultConstructor implements JsonbSerializer<String> {
		NoDefaultConstructor(String unused) {
		}

		@Override
		public void serialize(String obj, JsonGenerator generator, SerializationContext ctx) {
		}
	}

	public static class Unconstructable {
		@JsonbTypeSerializer(NoDefaultConstructor.class)
		public String value = "v";
	}

	@Test
	public void testComponentNeedsNoArgConstructor() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig());
		assertThrows(JsonbException.class, () -> jsonb.toJson(new Unconstructable()));
	}

	public interface Proxied {
		@JsonbTypeDeserializer(Reverse.class)
		String getCode();

		@JsonbDateFormat("d MMMM yyyy")
		LocalDate getDate();

		String getPlain();
	}

	@Test
	public void testProxyGetters() {
		// the provider's own conversions, locale included
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withLocale(Locale.FRANCE));
		final var json = "{\"code\":\"cba\",\"date\":\"27 septembre 2026\",\"plain\":\"p\"}";
		for (final var proxied : new Proxied[] { jsonb.fromJson(json, Proxied.class),
				(Proxied) jsonb.adapt(Proxied.class).fromJson(IuJson.parse(json)) }) {
			assertEquals("abc", proxied.getCode());
			assertEquals(LocalDate.of(2026, 9, 27), proxied.getDate());
			assertEquals("p", proxied.getPlain());
		}
	}

}
