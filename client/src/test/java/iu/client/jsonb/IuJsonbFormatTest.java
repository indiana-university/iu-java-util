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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.Locale;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonSerializationOptions;
import iu.client.jsonb.formats.ClassFormatted;
import iu.client.jsonb.formats.PackageFormatted;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.config.BinaryDataStrategy;

@SuppressWarnings("javadoc")
public class IuJsonbFormatTest {

	private static final Instant NOON = Instant.parse("2026-09-27T12:34:00Z");
	private static final Instant MIDNIGHT = Instant.parse("2026-09-27T00:00:00Z");

	/**
	 * Checks a value writes as expected, and reads back, in both modes.
	 */
	private static <T> T roundTrip(IuJsonb jsonb, Class<T> type, T value, String json) {
		assertEquals(json, jsonb.toJson(value));
		assertEquals(json, jsonb.adapt(type).toJson(value).toString());
		final var tree = type.cast(jsonb.adapt(type).fromJson(IuJson.parse(json)));
		assertEquals(json, jsonb.toJson(tree));
		return jsonb.fromJson(json, type);
	}

	@Test
	public void testConfiguredDateFormat() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDateFormat("dd.MM.yyyy", Locale.ROOT));
		assertEquals("\"27.09.2026\"", jsonb.toJson(Date.from(NOON)));
		assertEquals(Date.from(MIDNIGHT), jsonb.fromJson("\"27.09.2026\"", Date.class));

		// a locale configured as a language tag
		final var french = IuJsonbTest.jsonb(new JsonbConfig().setProperty(JsonbConfig.DATE_FORMAT, "d MMMM yyyy")
				.setProperty(JsonbConfig.LOCALE, "fr-FR"));
		assertEquals("\"27 septembre 2026\"", french.toJson(LocalDate.of(2026, 9, 27)));
		assertEquals(LocalDate.of(2026, 9, 27), french.fromJson("\"27 septembre 2026\"", LocalDate.class));
	}

	public static class Dates {
		@JsonbDateFormat(value = "yyyy-MM-dd HH:mm", locale = "en-US")
		public Date field;
		private Date viaAccessors;
		@JsonbDateFormat(JsonbDateFormat.DEFAULT_FORMAT)
		public Date isoDefault;
		@JsonbDateFormat(JsonbDateFormat.TIME_IN_MILLIS)
		public Instant millis;
		public Date plain;

		@JsonbDateFormat(value = "dd.MM.yyyy", locale = "en-US")
		public Date getViaAccessors() {
			return viaAccessors;
		}

		@JsonbDateFormat(value = "yyyyMMdd", locale = "en-US")
		public void setViaAccessors(Date viaAccessors) {
			this.viaAccessors = viaAccessors;
		}
	}

	@Test
	public void testDeclaredDateFormats() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withDateFormat("yyyy/MM/dd", Locale.ROOT));
		final var dates = new Dates();
		dates.field = Date.from(NOON);
		dates.viaAccessors = Date.from(MIDNIGHT);
		dates.isoDefault = Date.from(NOON);
		dates.millis = NOON;
		dates.plain = Date.from(NOON);

		// the declared format wins; the default format declared is the built-in
		// one, not the configured one
		final var json = "{\"field\":\"2026-09-27 12:34\",\"isoDefault\":\"2026-09-27T12:34:00Z\",\"millis\":"
				+ NOON.toEpochMilli() + ",\"plain\":\"2026/09/27\",\"viaAccessors\":\"27.09.2026\"}";
		assertEquals(json, jsonb.toJson(dates));
		assertEquals(json, jsonb.adapt(Dates.class).toJson(dates).toString());

		// the setter reads by its own format
		final var read = jsonb.fromJson("{\"field\":\"2026-09-27 12:34\",\"isoDefault\":\"2026-09-27T12:34:00Z\","
				+ "\"millis\":" + NOON.toEpochMilli() + ",\"plain\":\"2026/09/27\",\"viaAccessors\":\"20260927\"}",
				Dates.class);
		assertEquals(Date.from(NOON), read.field);
		assertEquals(Date.from(MIDNIGHT), read.viaAccessors);
		assertEquals(Date.from(NOON), read.isoDefault);
		assertEquals(NOON, read.millis);
		assertEquals(Date.from(MIDNIGHT), read.plain);
	}

	@Test
	public void testTypeAndPackageDateFormats() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig());
		final var classFormatted = new ClassFormatted();
		classFormatted.date = Date.from(NOON);
		assertEquals(Date.from(MIDNIGHT),
				roundTrip(jsonb, ClassFormatted.class, classFormatted, "{\"date\":\"2026-09-27\"}").date);

		final var packageFormatted = new PackageFormatted();
		packageFormatted.date = Date.from(NOON);
		packageFormatted.amount = 1.25;
		packageFormatted.text = "t";
		final var read = roundTrip(jsonb, PackageFormatted.class, packageFormatted,
				"{\"amount\":\"1.2\",\"date\":\"27.09.2026\",\"text\":\"t\"}");
		assertEquals(Date.from(MIDNIGHT), read.date);
		assertEquals(1.2, read.amount);
	}

	public static class Amounts {
		@JsonbNumberFormat("#,##0.00")
		public BigDecimal amount;
		@JsonbNumberFormat(value = "#0.0", locale = "de-DE")
		public double rate;
		public int plain;
	}

	@Test
	public void testNumberFormats() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withLocale(Locale.US));
		final var amounts = new Amounts();
		amounts.amount = new BigDecimal("1234.5");
		amounts.rate = 0.5;
		amounts.plain = 1;
		final var read = roundTrip(jsonb, Amounts.class, amounts,
				"{\"amount\":\"1,234.50\",\"plain\":1,\"rate\":\"0,5\"}");
		assertEquals(new BigDecimal("1234.50"), read.amount);
		assertEquals(0.5, read.rate);

		// a number reads too
		assertEquals(new BigDecimal("2"), jsonb.fromJson("{\"amount\":2}", Amounts.class).amount);
		assertThrows(JsonbException.class, () -> jsonb.fromJson("{\"amount\":\"x\"}", Amounts.class));
	}

	public static class DateText implements JsonbAdapter<Date, String> {
		@Override
		public String adaptToJson(Date obj) {
			return "adapted";
		}

		@Override
		public Date adaptFromJson(String obj) {
			return Date.from(NOON);
		}
	}

	public static class FormattedDate {
		@JsonbDateFormat("yyyy-MM-dd")
		public Date date = Date.from(MIDNIGHT);
	}

	@Test
	public void testComponentsBeforeFormats() {
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withAdapters(new DateText()));
		assertEquals("{\"date\":\"adapted\"}", jsonb.toJson(new FormattedDate()));
		assertEquals(Date.from(NOON), jsonb.fromJson("{\"date\":\"x\"}", FormattedDate.class).date);
	}

	public static class Strict {
		public Date date = Date.from(MIDNIGHT);
		public LocalDate localDate = LocalDate.of(2026, 9, 27);
		public byte[] bytes = { (byte) 0xfb, (byte) 0xff, 1 };
	}

	@Test
	public void testStrictIJson() {
		final Supplier<IuJsonSerializationOptions> legacy = () -> IuJsonSerializationOptions.LEGACY;
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().withStrictIJSON(true)
				.withBinaryDataStrategy(BinaryDataStrategy.BYTE).setProperty(IuJsonAdapter.SERIALIZATION_OPTIONS, legacy));
		assertTrue(jsonb.isStrictIJson());

		// base64url whatever else is configured; dates with an offset and seconds
		final var json = "{\"bytes\":\"-_8B\",\"date\":\"2026-09-27T00:00:00Z\",\"localDate\":\"2026-09-27T00:00:00Z\"}";
		assertEquals(json, jsonb.toJson(new Strict()));
		final var read = jsonb.fromJson(json, Strict.class);
		assertArrayEquals(new Strict().bytes, read.bytes);
		assertEquals(Date.from(MIDNIGHT), read.date);
		assertEquals(LocalDate.of(2026, 9, 27), read.localDate);

		// an object or array only, at the top level
		assertEquals("[1]", jsonb.toJson(new int[] { 1 }));
		for (final var scalar : new Object[] { "s", 1, null })
			assertTrue(assertThrows(JsonbException.class, () -> jsonb.toJson(scalar)).getMessage()
					.contains(JsonbConfig.STRICT_IJSON + " writes only an object or array at the top level"));

		// a configured date format wins
		final var formatted = IuJsonbTest
				.jsonb(new JsonbConfig().withStrictIJSON(true).withDateFormat("dd.MM.yyyy", Locale.ROOT));
		assertTrue(formatted.toJson(new Strict()).contains("\"date\":\"27.09.2026\""));
	}

	public static class Times {
		public LocalTime time = LocalTime.of(12, 34);
	}

	@Test
	public void testLegacyDates() {
		final Supplier<IuJsonSerializationOptions> legacy = () -> IuJsonSerializationOptions.LEGACY;
		final var jsonb = IuJsonbTest.jsonb(new JsonbConfig().setProperty(IuJsonAdapter.SERIALIZATION_OPTIONS, legacy));
		assertEquals("{\"time\":\"12:34\"}", jsonb.toJson(new Times()));
		assertEquals(LocalTime.of(12, 34), jsonb.fromJson("{\"time\":\"12:34:00\"}", Times.class).time);
		assertSame(IuJsonSerializationOptions.LEGACY, jsonb.callOptions());

		final var standard = IuJsonbTest.jsonb(new JsonbConfig());
		assertEquals("{\"time\":\"12:34:00\"}", standard.toJson(new Times()));
		assertEquals(LocalTime.of(12, 34), standard.fromJson("{\"time\":\"12:34\"}", Times.class).time);
	}

}
