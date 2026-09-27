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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

@SuppressWarnings({ "javadoc", "unchecked" })
public class FormatAdaptersTest {

	private static final Instant NOON = Instant.parse("2026-09-27T12:34:00Z");
	private static final Instant MIDNIGHT = Instant.parse("2026-09-27T00:00:00Z");

	private static <T> IuJsonAdapter<T> pattern(Class<T> type, String pattern) {
		return (IuJsonAdapter<T>) FormatAdapters.pattern(type, pattern, Locale.ROOT);
	}

	private static <T> IuJsonAdapter<T> millis(Class<T> type) {
		return (IuJsonAdapter<T>) FormatAdapters.millis(type);
	}

	private static <T> IuJsonAdapter<T> strict(Class<T> type) {
		return (IuJsonAdapter<T>) FormatAdapters.strict(type);
	}

	private static GregorianCalendar calendar(Instant instant, String zone) {
		final var calendar = new GregorianCalendar(TimeZone.getTimeZone(zone));
		calendar.setTime(Date.from(instant));
		return calendar;
	}

	@Test
	public void testIsDate() {
		for (final var type : new Class<?>[] { Date.class, Calendar.class, GregorianCalendar.class, Instant.class,
				LocalDate.class, LocalTime.class, LocalDateTime.class, OffsetTime.class, OffsetDateTime.class,
				ZonedDateTime.class })
			assertTrue(FormatAdapters.isDate(type), type::getName);
		assertFalse(FormatAdapters.isDate(String.class));
		assertFalse(FormatAdapters.hasLegacyDates(Date.class));
		assertFalse(FormatAdapters.hasLegacyDates(Instant.class));
		assertTrue(FormatAdapters.hasLegacyDates(ZonedDateTime.class));
	}

	@Test
	public void testLocale() {
		assertEquals(Locale.FRANCE, FormatAdapters.locale("fr-FR", Locale.GERMANY));
		assertEquals(Locale.GERMANY, FormatAdapters.locale(FormatAdapters.DEFAULT, Locale.GERMANY));
		assertEquals(Locale.GERMANY, FormatAdapters.locale("", Locale.GERMANY));
		assertEquals(Locale.GERMANY, FormatAdapters.locale(null, Locale.GERMANY));
		assertEquals(Locale.getDefault(), FormatAdapters.locale(null, null));
	}

	@Test
	public void testDateResolution() {
		final var declaredDefault = new BindingMetadata.Format(FormatAdapters.DEFAULT, FormatAdapters.DEFAULT);

		// nothing declared or configured leaves the built-in conversion
		assertNull(FormatAdapters.date(Date.class, null, null, null, false));
		assertNull(FormatAdapters.date(Date.class, null, FormatAdapters.DEFAULT, null, false));

		// declaring the default format restores the built-in conversion, over a
		// configured pattern
		assertSame(JsonAdapters.adapt(Date.class, null),
				FormatAdapters.date(Date.class, declaredDefault, "dd.MM.yyyy", null, false));

		// the default is strict I-JSON when configured
		final var strict = (IuJsonAdapter<Object>) FormatAdapters.date(Date.class, null, null, null, true);
		assertEquals(IuJson.string("2026-09-27T12:34:00Z"), strict.toJson(Date.from(NOON)));
		assertEquals(IuJson.string("2026-09-27T12:34:00Z"),
				((IuJsonAdapter<Object>) FormatAdapters.date(Date.class, declaredDefault, null, null, true))
						.toJson(Date.from(NOON)));

		// a configured pattern, in the configured locale
		final var configured = (IuJsonAdapter<Object>) FormatAdapters.date(LocalDate.class, null, "d MMMM yyyy",
				Locale.FRANCE, false);
		assertEquals(IuJson.string("27 septembre 2026"), configured.toJson(LocalDate.of(2026, 9, 27)));

		// a declared pattern wins, in its own locale
		final var declared = (IuJsonAdapter<Object>) FormatAdapters.date(LocalDate.class,
				new BindingMetadata.Format("d MMMM yyyy", "de-DE"), "yyyy", Locale.FRANCE, true);
		assertEquals(IuJson.string("27 September 2026"), declared.toJson(LocalDate.of(2026, 9, 27)));

		final var millis = (IuJsonAdapter<Object>) FormatAdapters.date(Date.class, null, FormatAdapters.TIME_IN_MILLIS,
				null, false);
		assertEquals(IuJson.number(1000L), millis.toJson(new Date(1000L)));
	}

	@Test
	public void testDeclared() {
		final var date = new BindingMetadata.Format("dd.MM.yyyy", FormatAdapters.DEFAULT);
		final var number = new BindingMetadata.Format("#,##0.00", "en-US");

		assertNull(FormatAdapters.declared(Date.class, null, null, null, null, false));
		assertNull(FormatAdapters.declared(String.class, date, number, null, null, false));
		assertEquals(IuJson.string("27.09.2026"), ((IuJsonAdapter<Object>) FormatAdapters.declared(Date.class, date,
				number, null, Locale.ROOT, false)).toJson(Date.from(NOON)));

		// a date format doesn't apply to a number, nor a number format to a date
		final var formatted = (IuJsonAdapter<Object>) FormatAdapters.declared(int.class, date, number, null, null,
				false);
		assertEquals(IuJson.string("1,234.00"), formatted.toJson(1234));
		assertNull(FormatAdapters.declared(Date.class, null, number, null, null, false));
	}

	@Test
	public void testDatePattern() {
		final var date = pattern(Date.class, "yyyy-MM-dd HH:mm");
		assertEquals(JsonValue.NULL, date.toJson(null));
		assertNull(date.fromJson(JsonValue.NULL));
		assertNull(date.fromJson(null));
		assertEquals(IuJson.string("2026-09-27 12:34"), date.toJson(Date.from(NOON)));
		assertEquals(Date.from(NOON), date.fromJson(IuJson.string("2026-09-27 12:34")));

		// no time reads as midnight; an offset read is honored
		assertEquals(Date.from(MIDNIGHT), pattern(Date.class, "dd.MM.yyyy").fromJson(IuJson.string("27.09.2026")));
		assertEquals(Date.from(NOON), pattern(Date.class, "yyyy-MM-dd'T'HH:mmXXX")
				.fromJson(IuJson.string("2026-09-27T14:34+02:00")));

		final var instant = pattern(Instant.class, "yyyy-MM-dd HH:mm:ss");
		assertEquals(IuJson.string("2026-09-27 12:34:00"), instant.toJson(NOON));
		assertEquals(NOON, instant.fromJson(IuJson.string("2026-09-27 12:34:00")));
	}

	@Test
	public void testCalendarPattern() {
		final var adapter = pattern(Calendar.class, "yyyy-MM-dd HH:mm XXX");
		final var calendar = calendar(NOON, "America/New_York");
		assertEquals(IuJson.string("2026-09-27 08:34 -04:00"), adapter.toJson(calendar));
		final var read = adapter.fromJson(IuJson.string("2026-09-27 08:34 -04:00"));
		assertEquals(calendar.getTime(), read.getTime());
		assertEquals(ZoneOffset.ofHours(-4), read.getTimeZone().toZoneId().normalized());

		assertEquals(calendar.getTime(),
				pattern(GregorianCalendar.class, "yyyy-MM-dd HH:mm XXX").fromJson(IuJson.string("2026-09-27 08:34 -04:00"))
						.getTime());
	}

	@Test
	public void testLocalPatterns() {
		final var date = (IuJsonAdapter<Object>) FormatAdapters.pattern(LocalDate.class, "d MMM yyyy",
				Locale.FRANCE);
		assertEquals(IuJson.string("27 sept. 2026"), date.toJson(LocalDate.of(2026, 9, 27)));
		assertEquals(LocalDate.of(2026, 9, 27), date.fromJson(IuJson.string("27 sept. 2026")));

		final var time = pattern(LocalTime.class, "HH:mm");
		assertEquals(IuJson.string("12:34"), time.toJson(LocalTime.of(12, 34)));
		assertEquals(LocalTime.of(12, 34), time.fromJson(IuJson.string("12:34")));

		final var dateTime = pattern(LocalDateTime.class, "yyyyMMddHHmm");
		assertEquals(IuJson.string("202609271234"), dateTime.toJson(LocalDateTime.of(2026, 9, 27, 12, 34)));
		assertEquals(LocalDateTime.of(2026, 9, 27, 12, 34), dateTime.fromJson(IuJson.string("202609271234")));
		assertEquals(LocalDateTime.of(2026, 9, 27, 0, 0),
				pattern(LocalDateTime.class, "yyyyMMdd").fromJson(IuJson.string("20260927")));
	}

	@Test
	public void testOffsetAndZonedPatterns() {
		final var time = pattern(OffsetTime.class, "HH:mm[XXX]");
		final var offsetTime = OffsetTime.of(12, 34, 0, 0, ZoneOffset.ofHours(2));
		assertEquals(IuJson.string("12:34+02:00"), time.toJson(offsetTime));
		assertEquals(offsetTime, time.fromJson(IuJson.string("12:34+02:00")));
		// no offset reads in UTC
		assertEquals(OffsetTime.of(12, 34, 0, 0, ZoneOffset.UTC), time.fromJson(IuJson.string("12:34")));

		final var offset = pattern(OffsetDateTime.class, "yyyy-MM-dd HH:mmXXX");
		final var offsetDateTime = OffsetDateTime.of(2026, 9, 27, 14, 34, 0, 0, ZoneOffset.ofHours(2));
		assertEquals(IuJson.string("2026-09-27 14:34+02:00"), offset.toJson(offsetDateTime));
		assertEquals(offsetDateTime, offset.fromJson(IuJson.string("2026-09-27 14:34+02:00")));

		final var zoned = pattern(ZonedDateTime.class, "yyyy-MM-dd HH:mm VV");
		final var zonedDateTime = NOON.atZone(ZoneId.of("America/New_York"));
		assertEquals(IuJson.string("2026-09-27 08:34 America/New_York"), zoned.toJson(zonedDateTime));
		assertEquals(zonedDateTime, zoned.fromJson(IuJson.string("2026-09-27 08:34 America/New_York")));
		// no zone reads in UTC
		assertEquals(NOON.atZone(ZoneOffset.UTC),
				pattern(ZonedDateTime.class, "yyyy-MM-dd HH:mm").fromJson(IuJson.string("2026-09-27 12:34")));
	}

	@Test
	public void testMillis() {
		final var date = millis(Date.class);
		assertEquals(JsonValue.NULL, date.toJson(null));
		assertNull(date.fromJson(null));
		assertNull(date.fromJson(JsonValue.NULL));
		assertEquals(IuJson.number(NOON.toEpochMilli()), date.toJson(Date.from(NOON)));
		assertEquals(Date.from(NOON), date.fromJson(IuJson.number(NOON.toEpochMilli())));
		assertEquals(Date.from(NOON), date.fromJson(IuJson.string(Long.toString(NOON.toEpochMilli()))));
		assertEquals("expected a number, found TRUE",
				assertThrows(IllegalArgumentException.class, () -> date.fromJson(JsonValue.TRUE)).getMessage());

		final var millis = IuJson.number(NOON.toEpochMilli());
		final var calendar = calendar(NOON, "America/New_York");
		assertEquals(millis, millis(Calendar.class).toJson(calendar));
		final var read = millis(Calendar.class).fromJson(millis);
		assertEquals(calendar.getTime(), read.getTime());
		assertEquals(ZoneOffset.UTC, read.getTimeZone().toZoneId().normalized());

		assertEquals(millis, millis(Instant.class).toJson(NOON));
		assertEquals(NOON, millis(Instant.class).fromJson(millis));

		assertEquals(IuJson.number(MIDNIGHT.toEpochMilli()), millis(LocalDate.class).toJson(LocalDate.of(2026, 9, 27)));
		assertEquals(LocalDate.of(2026, 9, 27), millis(LocalDate.class).fromJson(millis));

		final var local = LocalDateTime.of(2026, 9, 27, 12, 34);
		assertEquals(millis, millis(LocalDateTime.class).toJson(local));
		assertEquals(local, millis(LocalDateTime.class).fromJson(millis));

		final var offset = NOON.atOffset(ZoneOffset.ofHours(2));
		assertEquals(millis, millis(OffsetDateTime.class).toJson(offset));
		assertEquals(NOON.atOffset(ZoneOffset.UTC), millis(OffsetDateTime.class).fromJson(millis));

		final var zoned = NOON.atZone(ZoneId.of("America/New_York"));
		assertEquals(millis, millis(ZonedDateTime.class).toJson(zoned));
		assertEquals(NOON.atZone(ZoneOffset.UTC), millis(ZonedDateTime.class).fromJson(millis));

		for (final var type : new Class<?>[] { LocalTime.class, OffsetTime.class })
			assertEquals(FormatAdapters.TIME_IN_MILLIS + " doesn't apply to " + type.getName(),
					assertThrows(UnsupportedOperationException.class, () -> FormatAdapters.millis(type))
							.getMessage());
	}

	@Test
	public void testStrict() {
		final var date = strict(Date.class);
		assertEquals(JsonValue.NULL, date.toJson(null));
		assertNull(date.fromJson(JsonValue.NULL));
		assertEquals(IuJson.string("2026-09-27T00:00:00Z"), date.toJson(Date.from(MIDNIGHT)));
		assertEquals(Date.from(MIDNIGHT), date.fromJson(IuJson.string("2026-09-27T00:00:00Z")));

		final var calendar = calendar(NOON, "America/New_York");
		assertEquals(IuJson.string("2026-09-27T08:34:00-04:00"), strict(Calendar.class).toJson(calendar));
		assertEquals(IuJson.string("2026-09-27T08:34:00-04:00"), strict(GregorianCalendar.class).toJson(calendar));

		assertEquals(IuJson.string("2026-09-27T12:34:00Z"), strict(Instant.class).toJson(NOON));
		assertEquals(NOON, strict(Instant.class).fromJson(IuJson.string("2026-09-27T12:34:00Z")));

		final var localDate = strict(LocalDate.class);
		assertEquals(IuJson.string("2026-09-27T00:00:00Z"), localDate.toJson(LocalDate.of(2026, 9, 27)));
		assertEquals(LocalDate.of(2026, 9, 27), localDate.fromJson(IuJson.string("2026-09-27T00:00:00Z")));
		// read in UTC, with an offset
		assertEquals(LocalDate.of(2026, 9, 28), localDate.fromJson(IuJson.string("2026-09-27T23:00:00-02:00")));
		assertEquals(LocalDate.of(2026, 9, 27), localDate.fromJson(IuJson.string("2026-09-27T23:00:00")));
		assertEquals(LocalDate.of(2026, 9, 27), localDate.fromJson(IuJson.string("2026-09-27")));
		assertNull(localDate.fromJson(JsonValue.NULL));

		final var localDateTime = strict(LocalDateTime.class);
		final var local = LocalDateTime.of(2026, 9, 27, 12, 34);
		assertEquals(IuJson.string("2026-09-27T12:34:00Z"), localDateTime.toJson(local));
		assertEquals(local, localDateTime.fromJson(IuJson.string("2026-09-27T12:34:00Z")));
		assertEquals(local, localDateTime.fromJson(IuJson.string("2026-09-27T12:34:00")));

		// the others write with an offset and seconds already, or have no date
		for (final var type : new Class<?>[] { LocalTime.class, OffsetTime.class, OffsetDateTime.class,
				ZonedDateTime.class })
			assertSame(JsonAdapters.adapt(type, null), FormatAdapters.strict(type), type::getName);
	}

	@Test
	public void testNumberPattern() {
		final var number = new BindingMetadata.Format("#,##0.00", "en-US");
		final var adapter = (IuJsonAdapter<Object>) FormatAdapters.declared(Integer.class, null, number, null, null,
				false);
		assertEquals(JsonValue.NULL, adapter.toJson(null));
		assertNull(adapter.fromJson(JsonValue.NULL));
		assertEquals(IuJson.string("1,234.00"), adapter.toJson(1234));
		assertEquals(1234, adapter.fromJson(IuJson.string("1,234.00")));
		assertEquals(5, adapter.fromJson(IuJson.number(5)));

		// the whole text must parse, exactly as the type reads
		for (final var bad : new String[] { "abc", "12x" })
			assertEquals("expected a number formatted as #,##0.00, found \"" + bad + "\"",
					assertThrows(IllegalArgumentException.class, () -> adapter.fromJson(IuJson.string(bad)))
							.getMessage());
		assertEquals("expected an int, found 1234.50", assertThrows(IllegalArgumentException.class,
				() -> adapter.fromJson(IuJson.string("1,234.50"))).getMessage());

		// no pattern formats by the locale
		final var german = (IuJsonAdapter<Object>) FormatAdapters.declared(BigDecimal.class, null,
				new BindingMetadata.Format("", "de-DE"), null, null, false);
		assertEquals(IuJson.string("1.234,5"), german.toJson(new BigDecimal("1234.5")));
		assertEquals(new BigDecimal("1234.5"), german.fromJson(IuJson.string("1.234,5")));
		assertEquals("expected a number formatted as de_DE, found \"x\"",
				assertThrows(IllegalArgumentException.class, () -> german.fromJson(IuJson.string("x"))).getMessage());
	}

}
