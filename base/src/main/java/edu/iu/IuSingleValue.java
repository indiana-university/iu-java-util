/*
 * Copyright © 2024 Indiana University
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
package edu.iu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.BaseStream;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Provides consistent <strong>serialization</strong>,
 * <strong>deserialization</strong>, and <strong>conversion</strong> behavior
 * for immutable <strong>single value</strong> types and sequences.
 * 
 * <h2>Identity Conversion</h2>
 * <p>
 * All values use <a href=
 * "https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.1">identity
 * conversion</a> when the source value is an {@link Class#isInstance(Object)
 * instance of} the target type.
 * </p>
 * 
 * <h2>Strict Reversibility</h2>
 * <p>
 * <strong>Serialization</strong> from a <strong>single value</string>
 * <em>must</em> result in text that <strong>deserializes</strong> to a value
 * {@link #equals(Object) equal to} the original value.
 * </p>
 * <p>
 * <strong>Conversion</strong> to <strong>single value</strong> type
 * <em>must</em> result in a value that is {@link #equals(Object) equal to} the
 * original value when <strong>converted</strong> back to the original type.
 * </p>
 * <p>
 * Note that <strong>deserialization</strong> <em>may</em> lose precision or
 * context; it is <em>not required</em> for <strong>serializing</strong> a
 * <strong>deserialized value</strong> to result in the original
 * <strong>serialized form</strong>.
 * </p>
 * 
 * <h2>Strings</h2>
 * <p>
 * {@link String} uses <a href=
 * "https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.1">identity
 * conversion</a> for both <strong>serialization</strong> and
 * <strong>deserialization</strong>. <strong>Conversion</strong> to
 * {@link String} is <strong>serialization</strong>; <strong>conversion</strong>
 * from {@link String} to any other type is <strong>deserialization</strong>.
 * </p>
 * 
 * <h2>Null Values</h2>
 * <p>
 * When <strong>serializing</strong> {@code null} to a {@link String},
 * {@code null} is returned. A {@code null} value <em>must not</em> be
 * <strong>serialized</strong> to a {@link Writer}; applications requiring the
 * ability to <strong>serialize</strong> a {@code null} value over a stream
 * <em>should</em> should use a higher-level abstraction (i.e., JSON-B with null
 * values enabled).
 * </p>
 * <p>
 * When <strong>converting</strong> or <strong>deserializing</strong> from a
 * {@code null} {@link String}, {@code null} is returned. {@code null} will not
 * be returned <strong>deserializing</strong> from a {@link Reader}.
 * </p>
 *
 * <h2>Primitive Values</h2>
 * <p>
 * The primitive types below are treated as equivalent, as may be used
 * interchangeably via <a href=
 * "https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.7">boxing</a>
 * and <a href=
 * "https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.8">unboxing</a>.
 * When <strong>serializing</strong> from a primitive type,
 * <strong>deserializing</strong> to a primitive type, or
 * <strong>converting</strong> between primitive and non-primitive types, the
 * boxed value is handled as <strong>parseable</strong>.
 * </p>
 * <ul>
 * <li>{@link Character} ~= {@link Character#TYPE char}</li>
 * <li>{@link Boolean} ~= {@link Boolean#TYPE boolean}</li>
 * <li>{@link Integer} ~= {@link Integer#TYPE int}</li>
 * <li>{@link Long} ~= {@link Long#TYPE long}</li>
 * <li>{@link Short} ~= {@link Short#TYPE short}</li>
 * <li>{@link Byte} ~= {@link Byte#TYPE byte}</li>
 * <li>{@link Double} ~= {@link Double#TYPE double}</li>
 * <li>{@link Float} ~= {@link Float#TYPE float}</li>
 * </ul>
 * 
 * <p>
 * <a href=
 * "https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html"><strong>Conversion</strong>
 * between primitive types</a> <em>may</em> narrow precision but <strong>strict
 * reversibility</strong> still applies: <strong>conversion</strong> <em>must
 * not</em> result in value loss; <strong>converting</strong> back to the
 * original type <em>must</em> result in the original value.
 * </p>
 * 
 * <h2>Parseable Values</h2>
 * <p>
 * The types listed are <strong>serialized</strong> using {@link #toString()}
 * and <strong>deserialized</strong> as described:
 * </p>
 * <ul>
 * <li>{@link Character}, with {@link CharSequence#charAt(int)
 * CharSequence.charAt(0)}, after verifying {@link CharSequence#length()} ==
 * 1</li>
 * <li>{@link Boolean}, {@link Boolean#TRUE} when
 * {@link String#equalsIgnoreCase(String) case-insensitive equal} one of the
 * following to implicit {@code true} values; else {@link Boolean#FALSE} (unless
 * {@code null}:
 * <ul>
 * <li>{@code "true"}</li>
 * <li>{@code "Y"}</li>
 * <li>{@code "1"}</li>
 * <li>{@code "-1"}</li>
 * </ul>
 * </li>
 * <li>{@link Number}, with {@link BigDecimal#BigDecimal(String)}</li>
 * <li>{@link BigDecimal}, with {@link BigDecimal#BigDecimal(String)}</li>
 * <li>{@link BigInteger}, with {@link BigInteger#BigInteger(String)}</li>
 * <li>{@link Integer}, with {@link Integer#parseInt(String)}</li>
 * <li>{@link Long}, with {@link Long#parseLong(String)}</li>
 * <li>{@link Short}, with {@link Short#parseShort(String)}</li>
 * <li>{@link Byte}, with {@link Byte#parseByte(String)}</li>
 * <li>{@link Double}, with {@link Double#parseDouble(String)}</li>
 * <li>{@link Float}, with {@link Float#parseFloat(String)}</li>
 * <li>{@link URL}, with {@link URL#URL(String)}</li>
 * <li>{@link URI}, with {@link URI#URI(String)}</li>
 * <li>{@link TimeZone}, with {@link TimeZone#getTimeZone(String)}</li>
 * <li>{@link Pattern}, with {@link Pattern#compile(String)}</li>
 * <li>{@link Enum}, with {@link Enum#valueOf(Class, String)}</li>
 * </ul>
 * 
 * <h2>Binary Data</h2>
 * <p>
 * <strong>Binary</strong> data is <strong>serialized</strong> with
 * {@link Base64#getEncoder()} and deserialized with
 * {@link Base64#getDecoder()}.
 * </p>
 * <p>
 * <strong>Binary</strong> data is <strong>converted</strong> to other types by
 * first using {@link String#String(byte[], String)} with {@code "UTF-8"}. Other
 * types are converted to binary by converting to {@link String} then using
 * {@link String#getBytes(String)} with {@code "UTF-8"}.
 * </p>
 * 
 * <h2>Date/Time Values</h2>
 * <p>
 * Date/time values are <strong>serialized</strong> using
 * <a href="https://en.wikipedia.org/wiki/ISO_8601">ISO-8601</a>, via
 * {@link #toString()} from {@link Temporal}.
 * </p>
 * <p>
 * Date/time values are <strong>deserialized</strong> using
 * {@link DateTimeFormatter#parse(CharSequence)} then <strong>converted</strong>
 * to the target type:
 * </p>
 * <ul>
 * <li>{@link Instant}, with {@link Instant#from(TemporalAccessor)}</li>
 * <li>{@link Year}, with {@link Year#from(TemporalAccessor)}</li>
 * <li>{@link YearMonth}, with {@link YearMonth#from(TemporalAccessor)}</li>
 * <li>{@link LocalDate}, with {@link LocalDate#from(TemporalAccessor)}</li>
 * <li>{@link LocalDateTime}, with
 * {@link LocalDateTime#from(TemporalAccessor)}</li>
 * <li>{@link LocalTime}, with {@link LocalTime#from(TemporalAccessor)}</li>
 * <li>{@link ZonedDateTime}, with
 * {@link ZonedDateTime#from(TemporalAccessor)}</li>
 * <li>{@link OffsetDateTime}, with
 * {@link OffsetDateTime#from(TemporalAccessor)}</li>
 * <li>{@link OffsetTime}, with {@link OffsetTime#from(TemporalAccessor)}</li>
 * <li>{@link Duration}, with {@link Duration#parse(CharSequence)}</li>
 * </ul>
 *
 * <h3>Date and Long</h3>
 * <p>
 * {@link Date#toInstant()} is used to <strong>convert</strong> to
 * {@link Instant} before <strong>serializing</strong>.
 * </p>
 * <p>
 * {@link Date#from(Instant)} is used to <strong>convert</strong> to
 * {@link Date} after first <strong>deserializing</strong> as or
 * <strong>converting</strong> to {@link Instant}.
 * </p>
 * <p>
 * {@link Long} may be <strong>converted</strong> using {@link Date#Date(long)},
 * then <strong>converted</strong> to other date/time types. When
 * <strong>serialized form</strong> is <strong>parseable</strong> as
 * {@link Long}, it will be <strong>serialized</strong> first to {@link Long}
 * then <strong>converted</strong> to {@link Date} before
 * <strong>converting</strong> to other date/time types.
 * 
 * <h3>Relative Date/Time Values</h3>
 * <p>
 * Date/time values <em>may</em> be deserialized using the following ABNF
 * grammar to designate a value <strong>relative</strong> to the current
 * date/time.
 * 
 * <pre>
 * 	relative = "now" [" " interval]
 * 	interval = sign [" "] 1*DIGIT " " unit
 * 	sign     = "+" / "-"
 * 	unit     = {@link TimeUnit} / {@link ChronoUnit}
 * </pre>
 * 
 * <h2>Single Value Sequences</h2>
 * <p>
 * <strong>Single value sequences</strong> may be
 * </p>
 * <ul>
 * <li><strong>Serialized</strong> from any of the following types or a subtype
 * to <a href="https://datatracker.ietf.org/doc/html/rfc4180">RFC-4180 CSV
 * Format</a>
 * <ul>
 * <li>
 * </ul>
 * </li>
 * <li><strong>Deserialized</strong> from
 * <a href="https://datatracker.ietf.org/doc/html/rfc4180">RFC-4180 CSV
 * Format</a> by passing one of the following {@link Class} values as target
 * type.
 * <ul>
 * <li>Any {@link Class#isArray() array class} with a <strong>single
 * value</strong> {@link Class#getComponentType() component type}, except for
 * {@code byte[]} and {@code char[]}. Note that immutability cannot be enforced
 * for a <strong>single value array</strong>, so care must be taken when sharing
 * instances.</li>
 * </ul>
 * </li>
 * <li><strong>Deserialized</strong> from
 * <a href="https://datatracker.ietf.org/doc/html/rfc4180">RFC-4180 CSV
 * Format</a> by passing {@link ParameterizedType} as target type, with one of
 * the following {@link Class classes} as its
 * {@link ParameterizedType#getRawType() raw type} and a <strong>single
 * value</strong> {@link Class} as its
 * {@link ParameterizedType#getActualTypeArguments() first actual type
 * argument}.
 * <ul>
 * <li>{@link Iterable}</li>
 * <li>{@link Collection}</li>
 * <li>{@link Set}</li>
 * <li>{@link SortedSet}</li>
 * <li>{@link List}</li>
 * </ul>
 * </li>
 * <li><strong>Converted</strong> between the <strong>single value
 * sequence</strong> types listed above by <strong>converting</strong> each
 * <strong>single value</strong> in the <strong>sequence</strong>.</li>
 * <li><strong>Converted to</strong> or <strong>deserialized as</strong> a
 * <strong>single value</strong> as long as the sequence contains
 * <em>exactly</em> one item.
 * </ul>
 * 
 * <h2>Mutable Values</h2>
 * <p>
 * <strong>Single values</strong> and <strong>single value sequences</strong>
 * are immutable. However, equivalent <strong>mutable</strong> types may be used
 * as <strong>serialization sources</strong>. In these cases, <strong>strict
 * reversibility</strong> does not apply: <strong>deserializing</strong> the
 * serialized form of a mutable value results in a <strong>single value</strong>
 * or <strong>single value sequence</strong> with a value equivalent to, but not
 * necessarily {@link #equals(Object) equal to} the original mutable equivalent.
 * For example, {@link StringBuilder} <strong>serializes</strong> via
 * {@link StringBuilder#toString()}, then deserializes by identity.
 * </p>
 * 
 * <p>
 * Potentially mutable serialization sources are:
 * </p>
 * <ul>
 * <li>{@code char[]}</li>
 * <li>{@code byte[]}</li>
 * <li>{@link CharSequence}</li>
 * <li>{@link Reader}</li>
 * <li>{@link InputStream}</li>
 * <li>{@link DetachedWriter}</li>
 * <li>{@link StringWriter}</li>
 * <li>{@link DetachedOutputStream}</li>
 * <li>{@link ByteArrayOutputStream}</li>
 * <li>{@link Date}; note that {@link Date} is also considered a <strong>single
 * value type</strong> since it mutator methods are largely deprecated in not
 * commonly used.</li>
 * <li>{@link Calendar}</li>
 * <li>Any <strong>single value array</strong> type</li>
 * <li>{@link BaseStream}</li>
 * <li>{@link Enumeration}</li>
 * <li>{@link Iterable}</li>
 * <li>{@link Iterator}</li>
 * <li>{@link Spliterator}</li>
 * </ul>
 * 
 * <h2>Standards</h2>
 * <p>
 * This utility supports <strong>parseable</strong> and
 * <strong>primitive</strong> type conversion scenarios described by <a href=
 * "https://jakarta.ee/specifications/jsonb/3.0/jakarta-jsonb-spec-3.0#default-mapping">JSON-B
 * 3.0 Section 3: Default Mapping</a>.
 * </p>
 * 
 * <h2>Non Requirements</h2>
 * <p>
 * This utility implements specific rules for converting between basic types
 * from the {@link java.base} module. It is not intended as a flexible type
 * conversion framework. No types other than those listed above are supported.
 * In particular, conversion between {@link Object}, {@link Map}, and other
 * complex data types is not implemented by this utility.
 * </p>
 */
public final class IuSingleValue {

	private static Class<?> getTargetClass(Type targetType) {
		if (targetType instanceof Class<?> targetClass)
			return getNonPrimitiveClass(targetClass);
		else if (targetType instanceof ParameterizedType parameterizedType)
			return getTargetClass(parameterizedType.getRawType());
		else
			throw new IllegalArgumentException("Invalid target type");
	}

	/**
	 * Determines whether or not a {@link Class} represents a <strong>single value
	 * type</strong>.
	 * 
	 * @param type class to check
	 * @return {@code true} if {@code type} is a <strong>single value type</strong>;
	 *         else {@code false}
	 */
	public static boolean isSingleValue(Class<?> type) {
		return CharSequence.class.isAssignableFrom(type) //
				|| type.isPrimitive() //
				|| type.isEnum() //
				|| type == char[].class //
				|| type == byte[].class //
				|| type == Character.class //
				|| type == Boolean.class //
				|| type == Integer.class //
				|| type == Long.class //
				|| type == Short.class //
				|| type == Byte.class //
				|| type == Double.class //
				|| type == Float.class //
				|| type == BigDecimal.class //
				|| type == BigInteger.class //
				|| type == Date.class //
				|| type == URL.class //
				|| type == URI.class //
				|| type == TimeZone.class //
				|| type == Pattern.class //
				|| type == Instant.class //
				|| type == Year.class //
				|| type == YearMonth.class //
				|| type == LocalDateTime.class //
				|| type == LocalDate.class //
				|| type == LocalTime.class //
				|| type == ZonedDateTime.class //
				|| type == OffsetDateTime.class //
				|| type == OffsetTime.class //
				|| type == LocalTime.class //
				|| type == Duration.class //
		;
	}

	/**
	 * Determines whether or not a type represents a <strong>single value
	 * sequence</strong>.
	 * 
	 * @param type generic type
	 * @return true if the type represents a single value sequence; else false
	 */
	public static boolean isSingleValueSequence(Type type) {
		if (type instanceof Class<?> c)
			return (c != byte[].class //
					&& c != char[].class //
					&& c.isArray() //
					&& isSingleValue(c.getComponentType())) //
					|| type == IntStream.class //
					|| type == LongStream.class //
					|| type == DoubleStream.class;
		else if (type instanceof ParameterizedType pt) {
			final var raw = pt.getRawType();
			return (raw == Iterable.class //
					|| raw == Collection.class //
					|| raw == Set.class //
					|| raw == SortedSet.class //
					|| raw == List.class //
					|| raw == Stream.class) //
					&& (pt.getActualTypeArguments()[0] instanceof Class<?> c) //
					&& isSingleValue(c);
		} else
			return false;
	}

	/**
	 * Returns the non-primitive <a href=
	 * "https://docs.oracle.com/javase/tutorial/java/data/autoboxing.html">autobox</a>
	 * equivalent of a potentially primitive type.
	 * 
	 * @param <T>                       non-primitive type
	 * @param potentiallyPrimitiveClass potentially primitive {@link Class}
	 * 
	 * @return non-primitive class, or the class passed in as-is if not primitive
	 */
	@SuppressWarnings("unchecked")
	public static <T> Class<T> getNonPrimitiveClass(Class<T> potentiallyPrimitiveClass) {
		if (Boolean.TYPE.equals(potentiallyPrimitiveClass))
			return (Class<T>) Boolean.class;
		else if (Character.TYPE.equals(potentiallyPrimitiveClass))
			return (Class<T>) Character.class;
		else if (Byte.TYPE.equals(potentiallyPrimitiveClass))
			return (Class<T>) Byte.class;
		else if (Short.TYPE.equals(potentiallyPrimitiveClass))
			return (Class<T>) Short.class;
		else if (Integer.TYPE.equals(potentiallyPrimitiveClass))
			return (Class<T>) Integer.class;
		else if (Long.TYPE.equals(potentiallyPrimitiveClass))
			return (Class<T>) Long.class;
		else if (Float.TYPE.equals(potentiallyPrimitiveClass))
			return (Class<T>) Float.class;
		else if (Double.TYPE.equals(potentiallyPrimitiveClass))
			return (Class<T>) Double.class;
		else if (Void.TYPE.equals(potentiallyPrimitiveClass))
			return (Class<T>) Void.class;
		else
			return potentiallyPrimitiveClass;
	}

	/**
	 * Gets the default value for an object or primitive type.
	 * 
	 * @param <T>                       potentially primitive type
	 * @param potentiallyPrimitiveClass potentially primitive {@link Class}
	 * 
	 * @return The value assigned to a field of the potentially primitive type when
	 *         declared without an initializer; null unless the type is primitive.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getDefaultValue(Class<T> potentiallyPrimitiveClass) {
		if (Boolean.TYPE.equals(potentiallyPrimitiveClass))
			return (T) Boolean.FALSE;
		else if (Character.TYPE.equals(potentiallyPrimitiveClass))
			return (T) Character.valueOf('\0');
		else if (Byte.TYPE.equals(potentiallyPrimitiveClass))
			return (T) Byte.valueOf((byte) 0);
		else if (Short.TYPE.equals(potentiallyPrimitiveClass))
			return (T) Short.valueOf((short) 0);
		else if (Integer.TYPE.equals(potentiallyPrimitiveClass))
			return (T) Integer.valueOf(0);
		else if (Long.TYPE.equals(potentiallyPrimitiveClass))
			return (T) Long.valueOf(0L);
		else if (Float.TYPE.equals(potentiallyPrimitiveClass))
			return (T) Float.valueOf(0.0f);
		else if (Double.TYPE.equals(potentiallyPrimitiveClass))
			return (T) Double.valueOf(0.0);
		else
			return null;
	}

	/**
	 * <strong>Serializes</strong> a source value.
	 * 
	 * @param sourceValue source value, <em>must</em> be a
	 *                    {@link #isSingleValue(Class) single value}, <strong>single
	 *                    value sequence</strong>, or null.
	 * @return serialized text
	 * @throws IllegalArgumentException If the value provided is not a
	 *                                  <strong>single value or sequence</strong>.
	 */
	public static String serialize(Object sourceValue) throws IllegalArgumentException {
		if (sourceValue == null)
			return null;

		if (sourceValue instanceof CharSequence a)
			return a.toString();

		StringWriter w = new StringWriter();
		IuException.unchecked(() -> serialize(sourceValue, w));
		return w.toString();
	}

	/**
	 * <strong>Serializes</strong> a source value.
	 * 
	 * @param sourceValue source value, <em>must</em> be a
	 *                    {@link #isSingleValue(Class) single value}, <strong>single
	 *                    value sequence</strong>, or null.
	 * @param out         {@link OutputStream} serialization target
	 * @throws IllegalArgumentException If the value provided is not a
	 *                                  <strong>single value or sequence</strong>.
	 * @throws IOException              If a write error occurs
	 */
	public static void serialize(Object sourceValue, OutputStream out) throws IllegalArgumentException, IOException {
		serialize(Objects.requireNonNull(sourceValue), new OutputStreamWriter(out, "UTF-8"));
	}

	/**
	 * <strong>Serializes</strong> a source value.
	 * 
	 * <p>
	 * Accepts:
	 * </p>
	 * <ul>
	 * <li>{@link #isSingleValue(Class) single value types}</li>
	 * <li>{@code char[]}</li>
	 * <li>{@code byte[]} of UTF-8 encoded character data</li>
	 * <li>{@link CharSequence}</li>
	 * <li>{@link Reader}</li>
	 * <li>{@link InputStream} of UTF-8 encoded character data</li>
	 * <li>{@link StringWriter}</li>
	 * <li>{@link DetachedWriter}</li>
	 * <li>{@link DetachedOutputStream}, of UTF-8 encoded character data</li>
	 * </ul>
	 * 
	 * @param sourceValue source value, <em>must</em> be a
	 *                    {@link #isSingleValue(Class) single value}, <strong>single
	 *                    value sequence</strong>, or null.
	 * @param writer      {@link Writer} serialization target
	 * @throws IllegalArgumentException If the value provided is not a
	 *                                  <strong>single value or sequence</strong>.
	 * @throws IOException              If a write error occurs
	 */
	public static void serialize(Object sourceValue, Writer writer) throws IllegalArgumentException, IOException {
		Objects.requireNonNull(sourceValue);

		if (sourceValue instanceof char[] a)
			writer.write(a);

		if (sourceValue instanceof byte[] a)
			writer.append(new String(a, "UTF-8"));

		if (sourceValue instanceof CharSequence s)
			writer.append(s);

		else if (sourceValue instanceof Reader r)
			IuStream.copy(r, writer);

		else if (sourceValue instanceof InputStream i)
			IuStream.copy(new InputStreamReader(i, "UTF-8"), writer);

		else if (sourceValue instanceof DetachedWriter w)
			writer.write(w.text().toString());

		else if (sourceValue instanceof StringWriter w)
			writer.write(w.toString());

		else if (sourceValue instanceof Date d)
			writer.write(d.toInstant().toString());

		else if (sourceValue instanceof Calendar c)
			writer.write(c.getTime().toInstant().toString());

		else if (isSingleValue(sourceValue.getClass()))
			writer.write(sourceValue.toString());

		else if (sourceValue.getClass().isArray())
			serializeSequence(new Iterator<>() {
				private int i;

				@Override
				public boolean hasNext() {
					return i < Array.getLength(sourceValue);
				}

				@Override
				public Object next() {
					return Array.get(sourceValue, i++);
				}
			}, writer);

		else if (sourceValue instanceof BaseStream<?, ?> a)
			serializeSequence(a.iterator(), writer);

		else if (sourceValue instanceof Iterable<?> a)
			serializeSequence(a.iterator(), writer);

		else if (sourceValue instanceof Iterator<?> a)
			serializeSequence(a, writer);

		else if (sourceValue instanceof Spliterator<?> a)
			serializeSequence(Spliterators.iterator(a), writer);

		else
			throw new IllegalArgumentException();

		writer.flush();
	}

	/**
	 * <strong>Deserializes</strong> a <strong>single value</strong> or
	 * <strong>single value sequence</strong>.
	 * 
	 * @param <T>         target type
	 * @param text        serialized text
	 * @param targetClass target class, must represent a <strong>single
	 *                    value</strong> or <strong>single value sequence</strong>
	 * @return <strong>deserialized</strong> single value
	 * @throws IllegalArgumentException If the value provided is not the
	 *                                  <strong>serialized form</strong> of a
	 *                                  <strong>single value or sequence</strong>.
	 */
	public static <T> T deserialize(CharSequence text, Class<T> targetClass) throws IllegalArgumentException {
		return deserialize(text, (Type) targetClass);
	}

	/**
	 * <strong>Deserializes</strong> a <strong>single value</strong> or
	 * <strong>single value sequence</strong>.
	 * 
	 * @param <T>        target type
	 * @param text       serialized text
	 * @param targetType target class, must represent a <strong>single
	 *                   value</strong> or <strong>single value sequence</strong>
	 * @return <strong>deserialized</strong> single value
	 * @throws IllegalArgumentException If the value provided is not the
	 *                                  <strong>serialized form</strong> of a
	 *                                  <strong>single value or sequence</strong>.
	 */
	public static <T> T deserialize(CharSequence text, Type targetType) throws IllegalArgumentException {
		if (text == null)
			return null;
		else
			return IuException.unchecked(() -> deserialize(new StringReader(text.toString()), targetType));
	}

	/**
	 * <strong>Deserializes</strong> a <strong>single value</strong> or
	 * <strong>single value sequence</strong>.
	 * 
	 * @param <T>         target type
	 * @param in          {@link InputStream} deserialization source
	 * @param targetClass target class, must represent a <strong>single
	 *                    value</strong> or <strong>single value sequence</strong>
	 * @return <strong>deserialized</strong> single value
	 * @throws IllegalArgumentException If the value provided is not the
	 *                                  <strong>serialized form</strong> of a
	 *                                  <strong>single value or sequence</strong>.
	 * @throws IOException              if a read error occurs
	 */
	public static <T> T deserialize(InputStream in, Class<T> targetClass) throws IllegalArgumentException, IOException {
		return deserialize(in, (Type) targetClass);
	}

	/**
	 * <strong>Deserializes</strong> a single value.
	 * 
	 * @param <T>        target type
	 * @param in         {@link InputStream} deserialization source
	 * @param targetType target class, must represent a <strong>single
	 *                   value</strong> or <strong>single value sequence</strong>
	 * @return <strong>deserialized</strong> single value
	 * @throws IllegalArgumentException If the value provided is not the
	 *                                  <strong>serialized form</strong> of a
	 *                                  <strong>single value or sequence</strong>.
	 * @throws IOException              if a read error occurs
	 */
	public static <T> T deserialize(InputStream in, Type targetType) throws IllegalArgumentException, IOException {
		return deserialize(new InputStreamReader(in), targetType);
	}

	/**
	 * <strong>Deserializes</strong> a single value.
	 * 
	 * @param <T>         target type
	 * @param reader      {@link Reader} deserialization source
	 * @param targetClass target class, must represent a <strong>single
	 *                    value</strong> or <strong>single value sequence</strong>
	 * @return <strong>deserialized</strong> single value
	 * @throws IllegalArgumentException If the value provided is not the
	 *                                  <strong>serialized form</strong> of a
	 *                                  <strong>single value or sequence</strong>.
	 * @throws IOException              if a read error occurs
	 */
	public static <T> T deserialize(Reader reader, Class<T> targetClass) throws IllegalArgumentException, IOException {
		return deserialize(reader, (Type) targetClass);
	}

	/**
	 * <strong>Deserializes</strong> a <strong>single value</strong> or
	 * <strong>single value sequence</strong>.
	 * 
	 * @param <T>        target type
	 * @param reader     {@link Reader} deserialization source
	 * @param targetType target type, must represent a <strong>single value type or
	 *                   sequence</strong>
	 * @return <strong>deserialized</strong> single value
	 * @throws IllegalArgumentException If the value provided is not the
	 *                                  <strong>serialized form</strong> of a
	 *                                  <strong>single value or sequence</strong>.
	 * @throws IOException              if a read error occurs
	 */
	public static <T> T deserialize(Reader reader, Type targetType) throws IllegalArgumentException, IOException {
		@SuppressWarnings("unchecked")
		final var targetClass = (Class<T>) getTargetClass(targetType);

		if (targetClass.isInstance(reader))
			return targetClass.cast(reader);

		if (targetClass == Writer.class) {
			final var detachedWriter = new DetachedWriter();
			IuException.unchecked(() -> IuStream.copy(reader, detachedWriter));
			return targetClass.cast(detachedWriter);
		}

		final var text = IuStream.read(reader);

		//
//		@SuppressWarnings("unchecked")
//		final var targetClass = (Class<T>) getTargetClass(targetType);
//
//		if (targetClass.isInstance(text))
//			return targetClass.cast(text);
//
//		if (targetClass == Reader.class)
//			return targetClass.cast(new StringReader(text.toString()));
//
//		if (targetClass == Writer.class) {
//			final var detachedWriter = new DetachedWriter();
//			IuException.unchecked(() -> detachedWriter.write(text.toString()));
//			return targetClass.cast(detachedWriter);
//		}
//
//		if (targetClass == Character.class)
//			if (text.length() != 1)
//				throw new IllegalArgumentException("Invalid character, expected length == 1");
//			else
//				return targetClass.cast(text.charAt(0));
//
//		if (targetClass == Boolean.class) {
//			final var textString = text.toString();
//			return targetClass.cast(textString.equals("1") //
//					|| textString.equals("-1") //
//					|| textString.equalsIgnoreCase("true") //
//					|| textString.equalsIgnoreCase("Y"));
//		}
//
//		if (targetClass == Integer.class)
//			return targetClass.cast(Integer.parseInt(text.toString()));
//		if (targetClass == Long.class)
//			return targetClass.cast(Long.parseLong(text.toString()));
//		if (targetClass == Short.class)
//			return targetClass.cast(Short.parseShort(text.toString()));
//		if (targetClass == Byte.class)
//			return targetClass.cast(Byte.parseByte(text.toString()));
//		if (targetClass == Double.class)
//			return targetClass.cast(Double.parseDouble(text.toString()));
//		if (targetClass == Float.class)
//			return targetClass.cast(Float.parseFloat(text.toString()));
//
//		if (targetClass == BigDecimal.class)
//			return targetClass.cast(new BigDecimal(text.toString()));
//		if (targetClass == BigInteger.class)
//			return targetClass.cast(new BigInteger(text.toString()));
//
//		// TODO Auto-generated method stub
//		throw new UnsupportedOperationException("TODO");

		return deserialize(deserializationSource, targetType);
	}

	/**
	 * Converts a single value.
	 * 
	 * @param <T>         target type
	 * @param sourceValue source value
	 * @param targetClass target class, must represent a <strong>single value type
	 *                    or sequence</strong>
	 * @return converted single value
	 */
	public static <T> T convert(Object sourceValue, Class<T> targetClass) {
		return convert(sourceValue, (Type) targetClass);
	}

	/**
	 * Converts a single value.
	 * 
	 * @param <T>         target type
	 * @param sourceValue source value
	 * @param targetType  target type, must represent a <strong>single value type or
	 *                    sequence</strong>
	 * @return converted single value
	 */
	public static <T> T convert(Object sourceValue, Type targetType) {
		if (sourceValue == null)
			return null;

		@SuppressWarnings("unchecked")
		final var targetClass = (Class<T>) getTargetClass(targetType);

		if (targetClass.isInstance(sourceValue))
			return targetClass.cast(sourceValue);

		// pre-process source value
		final Object preProcessedSourceValue;
		if (sourceValue instanceof DetachedWriter w)
			preProcessedSourceValue = w.text();

		else if (sourceValue instanceof StringWriter w)
			preProcessedSourceValue = w.toString();

		else if (sourceValue instanceof Reader r) {
			if (targetClass == Writer.class) {
				final var detachedWriter = new DetachedWriter();
				IuException.unchecked(() -> IuStream.copy(r, detachedWriter));
				return targetClass.cast(detachedWriter);
			} else
				try {
					preProcessedSourceValue = IuStream.read(r);
					if (targetClass.isInstance(preProcessedSourceValue))
						return targetClass.cast(preProcessedSourceValue);
				} catch (IOException e) {
					throw new IllegalArgumentException("Unreadable source value", e);
				}
		}

		else
			preProcessedSourceValue = sourceValue;

		if (targetClass == Writer.class) {
			final var detachedWriter = new DetachedWriter();
			IuException.unchecked(() -> serialize(sourceValue, detachedWriter));
			return targetClass.cast(detachedWriter);
		}

		if (Number.class.isAssignableFrom(targetClass) //
				&& (preProcessedSourceValue instanceof Number sourceNumber)) {
			final Number converted = convertNumber(sourceNumber, getNonPrimitiveClass(targetClass));
			final Number reversed = convertNumber(converted, sourceValue.getClass());
			if (!sourceValue.equals(reversed))
				throw new ArithmeticException();
			else
				return targetClass.cast(converted);
		}

		final var text = serialize(preProcessedSourceValue).toString();

		if (targetType == Reader.class)
			return targetClass.cast(new StringReader(text));

		if (targetType == CharSequence.class || targetType == String.class)
			return targetClass.cast(text);

		if (isSingleValue(targetClass))
			return deserialize(text, targetClass);

		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	private static Number convertNumber(Number sourceNumber, Class<?> targetClass) {
		if (targetClass == BigDecimal.class)
			if (sourceNumber instanceof BigInteger a)
				return new BigDecimal(a);
			else if (sourceNumber instanceof Double a)
				return new BigDecimal(a);
			else if (sourceNumber instanceof Float a)
				return new BigDecimal(a);
			else if (sourceNumber instanceof Long a)
				return new BigDecimal(a);
			else if (sourceNumber instanceof Integer a)
				return new BigDecimal(a);
			else if (sourceNumber instanceof Short a)
				return new BigDecimal(a);
			else if (sourceNumber instanceof Byte a)
				return new BigDecimal(a);
			else
				throw new IllegalArgumentException();
		else if (targetClass == BigInteger.class)
			if (sourceNumber instanceof BigDecimal b)
				return b.toBigIntegerExact();
			else
				return new BigInteger(Long.toString(sourceNumber.longValue()));
		else if (targetClass == Double.class)
			return sourceNumber.doubleValue();
		else if (targetClass == Float.class)
			return sourceNumber.floatValue();
		else if (targetClass == Long.class)
			return sourceNumber.longValue();
		else if (targetClass == Integer.class)
			return sourceNumber.intValue();
		else if (targetClass == Short.class)
			return sourceNumber.shortValue();
		else if (targetClass == Byte.class)
			return sourceNumber.byteValue();
		else
			throw new IllegalArgumentException();
	}

	private static void serializeSequence(Iterator<?> sequence, Writer writer) throws IOException {
		boolean first = true;
		while (sequence.hasNext()) {
			final var item = sequence.next();
			if (first)
				first = false;
			else
				writer.append(',');

			var serialized = serialize(item);
			if (serialized.indexOf('\\') != -1 //
					|| serialized.indexOf('\"') != -1)
				serialized = "\"" + serialized.replace("\\", "\\\\").replace("\"", "\\\"") + '\"';

			writer.append(serialized);
		}
	}

	private IuSingleValue() {
	}

}
