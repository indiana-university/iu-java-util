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

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonSerializationOptions;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * Date and number conversions by format, shared by the JSON-B provider and the
 * IU binding paths: a date pattern, milliseconds since the epoch, or strict
 * I-JSON for dates, and a decimal pattern for numbers, as JSON-B defines them.
 *
 * <p>
 * A date type is {@link Date}, {@link Calendar}, {@link GregorianCalendar},
 * {@link Instant}, {@link LocalDate}, {@link LocalTime}, {@link LocalDateTime},
 * {@link OffsetTime}, {@link OffsetDateTime}, or {@link ZonedDateTime}. A date
 * without a zone of its own, {@link Date} or {@link Instant}, formats in UTC; a
 * {@link Calendar} formats in its own zone. A date read without a time is at
 * midnight, and without a zone or offset is in UTC.
 * </p>
 */
public final class FormatAdapters {

	/**
	 * Marks the default format or locale, as JSON-B's {@code DEFAULT_FORMAT} and
	 * {@code DEFAULT_LOCALE} do.
	 */
	public static final String DEFAULT = "##default";

	/**
	 * Marks a date written as milliseconds since the epoch, as a number, as
	 * JSON-B's {@code TIME_IN_MILLIS} does.
	 */
	public static final String TIME_IN_MILLIS = "##time-in-millis";

	private static final Set<Class<?>> DATE_TYPES = Set.of(Date.class, Calendar.class, GregorianCalendar.class,
			Instant.class, LocalDate.class, LocalTime.class, LocalDateTime.class, OffsetTime.class,
			OffsetDateTime.class, ZonedDateTime.class);

	private static final Set<Class<?>> LEGACY_DATE_TYPES = Set.of(Calendar.class, GregorianCalendar.class,
			LocalTime.class, LocalDateTime.class, OffsetTime.class, OffsetDateTime.class, ZonedDateTime.class);

	private FormatAdapters() {
	}

	/**
	 * Determines if a type is a date type.
	 *
	 * @param type type
	 * @return true for a date type
	 */
	public static boolean isDate(Class<?> type) {
		return DATE_TYPES.contains(type);
	}

	/**
	 * Gets the conversion a declared format sets for a property.
	 *
	 * @param type              property type
	 * @param date              date format declared; null if none
	 * @param number            number format declared; null if none
	 * @param configuredPattern date pattern configured for every date; null if
	 *                          none
	 * @param configuredLocale  locale configured; null for the default locale
	 * @param strict            true for strict I-JSON
	 * @return conversion; null if neither format applies to the type
	 */
	public static IuJsonAdapter<?> declared(Type type, BindingMetadata.Format date, BindingMetadata.Format number,
			String configuredPattern, Locale configuredLocale, boolean strict) {
		final var erased = JsonAdapters.erase(type);
		if (date != null && isDate(erased))
			return date(erased, date, configuredPattern, configuredLocale, strict);
		else if (number != null && Number.class.isAssignableFrom((Class<?>) GenericTypes.box(erased)))
			return new NumberFormatAdapter(JsonAdapters.adapt(type, null), number.pattern(),
					locale(number.locale(), configuredLocale));
		else
			return null;
	}

	/**
	 * Gets the conversion for a date type.
	 *
	 * <p>
	 * A declared format wins over a configured one, even when it declares the
	 * default format. The default format is strict I-JSON when configured, and
	 * otherwise the built-in conversion.
	 * </p>
	 *
	 * @param type              date type
	 * @param declared          format declared; null if none
	 * @param configuredPattern date pattern configured; null if none
	 * @param configuredLocale  locale configured; null for the default locale
	 * @param strict            true for strict I-JSON
	 * @return conversion; null if nothing is declared or configured
	 */
	public static IuJsonAdapter<?> date(Class<?> type, BindingMetadata.Format declared, String configuredPattern,
			Locale configuredLocale, boolean strict) {
		final String pattern;
		final String locale;
		if (declared != null) {
			pattern = declared.pattern();
			locale = declared.locale();
		} else {
			pattern = configuredPattern;
			locale = null;
		}

		if (pattern == null || DEFAULT.equals(pattern))
			if (strict)
				return strict(type);
			else if (declared != null)
				return JsonAdapters.adapt(type, null);
			else
				return null;
		else if (TIME_IN_MILLIS.equals(pattern))
			return millis(type);
		else
			return pattern(type, pattern, locale(locale, configuredLocale));
	}

	/**
	 * Determines if dates of a type converted differently before 7.1.
	 *
	 * @param type type
	 * @return true if {@link IuJsonSerializationOptions#isLegacyDates()} applies
	 */
	public static boolean hasLegacyDates(Class<?> type) {
		return LEGACY_DATE_TYPES.contains(type);
	}

	/**
	 * Gets a conversion for a date type that converts as before 7.1 when the
	 * options in effect for each conversion say so.
	 *
	 * @param type    date type that {@link #hasLegacyDates(Class) converted
	 *                differently} before 7.1
	 * @param options supplies the options in effect for each conversion
	 * @return conversion
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static IuJsonAdapter<?> legacyDates(Class<?> type, Supplier<IuJsonSerializationOptions> options) {
		final IuJsonAdapter standard = JsonAdapters.adapt(type, null);
		final IuJsonAdapter legacy = legacy(type);
		return new IuJsonAdapter<Object>() {
			private IuJsonAdapter<Object> adapter() {
				return JsonSerializer.snapshot(options).isLegacyDates() ? legacy : standard;
			}

			@Override
			public Object fromJson(JsonValue jsonValue) {
				return adapter().fromJson(jsonValue);
			}

			@Override
			public JsonValue toJson(Object javaValue) {
				return adapter().toJson(javaValue);
			}

			@Override
			public Object read(JsonParser parser) {
				return adapter().read(parser);
			}

			@Override
			public void write(Object value, JsonGenerator generator) {
				adapter().write(value, generator);
			}
		};
	}

	private static IuJsonAdapter<?> legacy(Class<?> type) {
		if (type == LocalTime.class)
			return ParsingJsonAdapter.of(LocalTime.class, LocalTime::parse);
		else if (type == LocalDateTime.class)
			return ParsingJsonAdapter.of(LocalDateTime.class, LocalDateTime::parse);
		else if (type == OffsetTime.class)
			return ParsingJsonAdapter.of(OffsetTime.class, OffsetTime::parse);
		else if (type == OffsetDateTime.class)
			return ParsingJsonAdapter.of(OffsetDateTime.class, OffsetDateTime::parse);
		else if (type == ZonedDateTime.class)
			return ParsingJsonAdapter.of(ZonedDateTime.class, ZonedDateTime::parse);
		else // Calendar, GregorianCalendar
			return CalendarJsonAdapter.LEGACY;
	}

	/**
	 * Resolves a locale.
	 *
	 * @param declared   language tag declared; null, empty, or {@link #DEFAULT}
	 *                   for the configured locale
	 * @param configured locale configured; null for {@link Locale#getDefault()}
	 * @return locale
	 */
	static Locale locale(String declared, Locale configured) {
		if (declared != null && !declared.isEmpty() && !DEFAULT.equals(declared))
			return Locale.forLanguageTag(declared);
		else if (configured != null)
			return configured;
		else
			return Locale.getDefault();
	}

	/**
	 * Gets the date and time a parsed date names: at midnight if it names no
	 * time, and in UTC if it names no zone or offset.
	 *
	 * @param parsed parsed date
	 * @return {@link ZonedDateTime}
	 */
	static ZonedDateTime zoned(TemporalAccessor parsed) {
		final var local = LocalDate.from(parsed).atTime(time(parsed));
		final var zone = parsed.query(TemporalQueries.zone());
		return ZonedDateTime.ofLocal(local, zone == null ? ZoneOffset.UTC : zone,
				parsed.query(TemporalQueries.offset()));
	}

	private static LocalTime time(TemporalAccessor parsed) {
		final var time = parsed.query(TemporalQueries.localTime());
		return time == null ? LocalTime.MIDNIGHT : time;
	}

	private static ZoneOffset offset(TemporalAccessor parsed) {
		final var offset = parsed.query(TemporalQueries.offset());
		return offset == null ? ZoneOffset.UTC : offset;
	}

	/**
	 * Gets a value with a date, as a date and time: {@link Date} and
	 * {@link Instant} in UTC, {@link Calendar} in its own zone,
	 * {@link LocalDate} at midnight UTC, and {@link LocalDateTime} in UTC.
	 */
	private static ZonedDateTime zoned(Object value) {
		if (value instanceof Date)
			return ((Date) value).toInstant().atZone(ZoneOffset.UTC);
		else if (value instanceof Calendar) {
			final var calendar = (Calendar) value;
			return calendar.toInstant().atZone(calendar.getTimeZone().toZoneId());
		} else if (value instanceof Instant)
			return ((Instant) value).atZone(ZoneOffset.UTC);
		else if (value instanceof LocalDate)
			return ((LocalDate) value).atStartOfDay(ZoneOffset.UTC);
		else if (value instanceof LocalDateTime)
			return ((LocalDateTime) value).atZone(ZoneOffset.UTC);
		else if (value instanceof OffsetDateTime)
			return ((OffsetDateTime) value).toZonedDateTime();
		else
			return (ZonedDateTime) value;
	}

	/**
	 * Converts a date and time to a date type other than {@link LocalTime} and
	 * {@link OffsetTime}.
	 */
	private static Object fromZoned(Class<?> type, ZonedDateTime zoned) {
		if (type == ZonedDateTime.class)
			return zoned;
		else if (type == OffsetDateTime.class)
			return zoned.toOffsetDateTime();
		else if (type == Instant.class)
			return zoned.toInstant();
		else if (type == Date.class)
			return Date.from(zoned.toInstant());
		else if (type == LocalDate.class)
			return zoned.toLocalDate();
		else if (type == LocalDateTime.class)
			return zoned.toLocalDateTime();
		else // Calendar, GregorianCalendar
			return GregorianCalendar.from(zoned);
	}

	/**
	 * Converts a parsed date to a date type.
	 */
	private static Object resolve(Class<?> type, TemporalAccessor parsed) {
		if (type == LocalTime.class)
			return LocalTime.from(parsed);
		else if (type == OffsetTime.class)
			return time(parsed).atOffset(offset(parsed));
		else if (type == LocalDate.class)
			return LocalDate.from(parsed);
		else if (type == LocalDateTime.class)
			return LocalDate.from(parsed).atTime(time(parsed));
		else
			return fromZoned(type, zoned(parsed));
	}

	private static IuJsonAdapter<Object> text(Function<Object, String> print, Function<String, Object> parse) {
		return IuJsonAdapter.from(json -> {
			final var text = TextJsonAdapter.INSTANCE.fromJson(json);
			return text == null ? null : parse.apply(text);
		}, value -> value == null ? JsonValue.NULL : IuJson.string(print.apply(value)));
	}

	/**
	 * Converts a date type by a pattern.
	 *
	 * @param type    date type
	 * @param pattern {@link DateTimeFormatter} pattern
	 * @param locale  locale
	 * @return conversion
	 */
	static IuJsonAdapter<Object> pattern(Class<?> type, String pattern, Locale locale) {
		final var formatter = DateTimeFormatter.ofPattern(pattern, locale);
		final var local = type == LocalTime.class || type == OffsetTime.class;
		return text(value -> formatter.format(local ? (TemporalAccessor) value : zoned(value)),
				text -> resolve(type, formatter.parse(text)));
	}

	/**
	 * Converts a date type as milliseconds since the epoch: written as a number,
	 * and read from a number or text; {@link LocalDate} and
	 * {@link LocalDateTime} in UTC.
	 *
	 * @param type date type, other than {@link LocalTime} and {@link OffsetTime}
	 * @return conversion
	 * @throws UnsupportedOperationException for {@link LocalTime} or
	 *                                       {@link OffsetTime}, which have no
	 *                                       date
	 */
	static IuJsonAdapter<Object> millis(Class<?> type) {
		if (type == LocalTime.class || type == OffsetTime.class)
			throw new UnsupportedOperationException(TIME_IN_MILLIS + " doesn't apply to " + type.getName());

		return IuJsonAdapter.from(json -> {
			final long millis;
			if (json == null || JsonValue.NULL.equals(json))
				return null;
			else if (json instanceof JsonNumber)
				millis = ((JsonNumber) json).longValueExact();
			else if (json instanceof JsonString)
				millis = Long.parseLong(((JsonString) json).getString());
			else
				throw JsonAdapters.expected("a number", json.getValueType());
			return fromZoned(type, Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC));
		}, value -> value == null //
				? JsonValue.NULL
				: IuJson.number(zoned(value).toInstant().toEpochMilli()));
	}

	/**
	 * Converts a date type as strict I-JSON writes it: {@link Date},
	 * {@link Calendar}, {@link GregorianCalendar}, {@link Instant},
	 * {@link LocalDate}, and {@link LocalDateTime} as an ISO date and time with
	 * an offset, seconds included; {@link LocalDate} and {@link LocalDateTime}
	 * in UTC. Other types convert as built in, which writes them that way where
	 * they have a date and an offset.
	 *
	 * <p>
	 * Reads what it writes, and whatever the built-in conversion reads.
	 * </p>
	 *
	 * @param type date type
	 * @return conversion
	 */
	@SuppressWarnings("unchecked")
	static IuJsonAdapter<Object> strict(Class<?> type) {
		final IuJsonAdapter<Object> builtIn = JsonAdapters.adapt(type, null);
		if (type == LocalTime.class //
				|| type == OffsetTime.class //
				|| type == OffsetDateTime.class //
				|| type == ZonedDateTime.class)
			return builtIn;

		final var local = type == LocalDate.class || type == LocalDateTime.class;
		return IuJsonAdapter.from(json -> {
			if (local) {
				final var text = TextJsonAdapter.INSTANCE.fromJson(json);
				if (text != null && text.indexOf('T') != -1) {
					// a date and time; with an offset, in UTC
					final var parsed = DateTimeFormatter.ISO_DATE_TIME.parse(text);
					final var dateTime = parsed.query(TemporalQueries.offset()) == null //
							? LocalDateTime.from(parsed)
							: zoned(parsed).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
					return type == LocalDate.class ? dateTime.toLocalDate() : dateTime;
				}
			}
			return builtIn.fromJson(json);
		}, value -> value == null //
				? JsonValue.NULL
				: IuJson.string(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zoned(value))));
	}

	/**
	 * Converts a number type by a {@link DecimalFormat} pattern: written as text,
	 * and read from text in that format, or from a number.
	 */
	private static final class NumberFormatAdapter implements IuJsonAdapter<Object> {
		private final IuJsonAdapter<Object> number;
		private final String pattern;
		private final Locale locale;

		@SuppressWarnings("unchecked")
		private NumberFormatAdapter(IuJsonAdapter<?> number, String pattern, Locale locale) {
			this.number = (IuJsonAdapter<Object>) number;
			this.pattern = pattern;
			this.locale = locale;
		}

		/**
		 * Creates a format for one conversion; a format isn't thread-safe.
		 */
		private DecimalFormat format() {
			final var format = (DecimalFormat) NumberFormat.getInstance(locale);
			if (!pattern.isEmpty())
				format.applyPattern(pattern);
			format.setParseBigDecimal(true);
			return format;
		}

		@Override
		public Object fromJson(JsonValue json) {
			if (!(json instanceof JsonString))
				return number.fromJson(json);

			final var text = ((JsonString) json).getString();
			final var position = new ParsePosition(0);
			final var parsed = format().parse(text, position);
			if (parsed == null || position.getIndex() != text.length())
				throw new IllegalArgumentException(
						"expected a number formatted as " + (pattern.isEmpty() ? locale : pattern) + ", found " + json);
			return number.fromJson(IuJson.number((BigDecimal) parsed));
		}

		@Override
		public JsonValue toJson(Object value) {
			if (value == null)
				return JsonValue.NULL;
			else
				return IuJson.string(format().format(value));
		}
	}

}
