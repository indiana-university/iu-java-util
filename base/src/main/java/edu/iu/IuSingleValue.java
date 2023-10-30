/*
 * Copyright © 2023 Indiana University
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

import java.io.ByteArrayInputStream;
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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
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
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Provides consistent <strong>serialization</strong>,
 * <strong>deserialization</strong>, and <strong>conversion</strong> behavior
 * for <strong>single value</strong> types and sequences.
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
 * <strong>Serialization</strong> <em>must</em> result in text that
 * <strong>deserializes</strong> to a value {@link #equals(Object) equal to} the
 * original value.
 * </p>
 * <p>
 * <strong>Conversion</strong> to any type <em>must</em> result in a value that
 * is {@link #equals(Object) equal to} the original value when
 * <strong>converted</strong> back to the original type.
 * </p>
 * <p>
 * Note that <strong>deserialization</strong> <em>may</em> lose precision or
 * context; it is <em>not required</em> for <strong>serializing</strong> a
 * <strong>deserialized value</strong> to result in the original
 * <strong>serialized form</strong>.
 * </p>
 * 
 * <h2>Character Sequences</h2>
 * <p>
 * {@link CharSequence} uses <a href=
 * "https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.1">identity
 * conversion</a> for both <strong>serialization</strong> and
 * <strong>deserialization</strong>. <strong>Conversion</strong> to
 * {@link CharSequence} is <strong>serialization</strong>;
 * <strong>conversion</strong> from {@link CharSequence} to any other type is
 * <strong>deserialization</strong>.
 * </p>
 * 
 * <h2>Character Streams</h2>
 * <p>
 * {@link Reader} is a viable <strong>deserialization</strong> source and
 * <strong>serialization</strong> or <strong>conversion</strong> source value;
 * it will be read fully when used in these scenarios. When used as a target
 * type for <strong>deserialization</strong> or <strong>conversion</strong>, a
 * {@link StringReader} wrapping all buffered content is returned.
 * </p>
 * <p>
 * {@link Writer} is a viable <strong>serialization</strong> target; all
 * <strong>serialized text</strong> will be {@link Writer#append(char) written},
 * then {@link Writer#flush() flushed}. When used as a target type for
 * <strong>deserialization</strong> or <strong>conversion</strong>, a
 * {@link DetachedWriter} wrapping all buffered content is returned.
 * {@link StringWriter} or {@link DetachedWriter} may be used as source value
 * for <strong>serialization</strong> or <strong>conversion</strong>.
 * </p>
 * 
 * <h2>Null Values</h2>
 * <p>
 * When <strong>serializing</strong> {@code null} to a {@link CharSequence},
 * {@code null} is returned. A {@code null} value <em>must not</em> be
 * <strong>serialized</strong> to a {@link Writer}; applications requiring the
 * ability to <strong>serialize</strong> a {@code null} value over a stream
 * <em>should</em> should use a higher-level abstraction (i.e., JSON-B with null
 * values enabled).
 * </p>
 * <p>
 * When <strong>converting</strong> or <strong>deserializing</strong>
 * {@code null} from a {@link CharSequence}, {@code null} is returned. When
 * <strong>deserializing</strong> from a {@link Reader}, {@code null} is only
 * returned when the target type is {@link Void Void or void} and reading fully
 * resulted in the {@link String#isEmpty() empty string}.
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
 * <li>{@link Void} ~= {@link Void#TYPE void} (null only)</li>
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
 * <li>{@link String}, with {@link CharSequence#toString()}; used first when
 * {@link String} is needed for parsing <strong>serialized text</strong></li>
 * <li>{@link StringBuilder}, with
 * {@link StringBuilder#StringBuilder(CharSequence)}</li>
 * <li>{@link StringBuffer}, with
 * {@link StringBuffer#StringBuffer(CharSequence)}</li>
 * <li>{@link CharBuffer}, with {@link CharBuffer#wrap(CharSequence)}</li>
 * <li>{@code char[]}, with {@link CharBuffer#wrap(CharSequence)}</li>
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
 * <li>{@link Void}, by enforcing {@code null}</li>
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
 * <p>
 * <strong>Binary types</strong> are:
 * </p>
 * <ul>
 * <li>{@code byte[]}</li>
 * <li>{@link ByteBuffer}</li>
 * <li>
 * </ul>
 * 
 * <h3>Binary Streams</h3>
 * <p>
 * {@link InputStream} is a viable <strong>deserialization</strong> source via
 * {@code UTF-8} {@link InputStreamReader}, and <strong>serialization</strong>
 * or <strong>conversion</strong> source value; it will be read fully when used
 * in these scenarios. When used as a target type for
 * <strong>deserialization</strong> or <strong>conversion</strong>, a
 * {@link ByteArrayInputStream} wrapping all buffered content is returned.
 * </p>
 * <p>
 * {@link OutputStream} is a viable <strong>serialization</strong> target via
 * {@code UTF-8} {@link OutputStreamWriter}; all <strong>serialized
 * data</strong> will be written, then {@link OutputStream#flush() flushed}.
 * When used as a target type for <strong>deserialization</strong> or
 * <strong>conversion</strong>, a {@link DetachedOutputStream} wrapping all
 * buffered data is returned. {@link ByteArrayOutputStream} or
 * {@link DetachedOutputStream} may be used as source values for
 * <strong>serialization</strong> or <strong>conversion</strong>.
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
 * </ul>
 *
 * <h3>Date, Calendar, and Long</h3>
 * <p>
 * {@link Date#toInstant()} and {@link Calendar#toInstant()} are used to
 * <strong>convert</strong> to {@link Instant} before
 * <strong>serializing</strong>.
 * </p>
 * <p>
 * {@link Date#from(Instant)} is used to <strong>convert</strong> to
 * {@link Date} after first <strong>deserializing</strong> as or
 * <strong>converting</strong> to {@link Instant}.
 * </p>
 * <p>
 * {@link Calendar#getInstance()}, then {@link Calendar#setTime(Date)}, is used
 * to <strong>convert</strong> to {@link Calendar} after first
 * <strong>deserializing</strong> as or <strong>converting</strong> to
 * {@link Date}.
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
 * <li><strong>Serialized</strong> to
 * <a href="https://datatracker.ietf.org/doc/html/rfc4180">RFC-4180 CSV
 * Format</a></li>
 * <li><strong>Deserialized</strong> from
 * <a href="https://datatracker.ietf.org/doc/html/rfc4180">RFC-4180 CSV
 * Format</a> by passing one of the following {@link Class} values as target
 * type.
 * <ul>
 * <li>Any {@link Class#isArray() array class} with a <strong>single
 * value</strong> {@link Class#getComponentType() component type}, except for
 * {@code byte[]} and {@code char[]}</li>
 * <li>{@link IntStream}</li>
 * <li>{@link LongStream}</li>
 * <li>{@link DoubleStream}</li>
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
 * <li>{@link Queue}</li>
 * <li>{@link Deque}</li>
 * <li>{@link Stream}</li>
 * </ul>
 * </li>
 * <li><strong>Converted</strong> between the <strong>single value
 * sequence</strong> types listed above by <strong>converting</strong> each
 * <strong>single value</strong> in the <strong>sequence</strong>.</li>
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
		if (CharSequence.class.isAssignableFrom(type) //
				|| type.isPrimitive() //
				|| type == Character.class //
				|| type == Boolean.class //
				|| type == Integer.class //
				|| type == Long.class //
				|| type == Short.class //
				|| type == Byte.class //
				|| type == Double.class //
				|| type == Float.class //
		)
			return true;

		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
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
	public static CharSequence serialize(Object sourceValue) throws IllegalArgumentException {
		if (sourceValue == null)
			return null;

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

		if (sourceValue instanceof CharSequence s)
			writer.append(s);

		else if (sourceValue instanceof Reader r)
			IuStream.copy(r, writer);

		else if (sourceValue instanceof InputStream i)
			IuStream.copy(new InputStreamReader(i), writer);

		else if (sourceValue instanceof DetachedWriter w)
			writer.write(w.text().toString());

		else if (sourceValue instanceof StringWriter w)
			writer.write(w.toString());

		// TODO non-parseable above isSingleValue() check

		else if (isSingleValue(sourceValue.getClass()))
			writer.write(sourceValue.toString());

		else
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("TODO");

		writer.flush();
//		if (value == null || (value instanceof CharSequence))
//			return (CharSequence) value;
//
//		if (value instanceof char[])
//			return new String((char[]) value);
//
//		if ((value instanceof Number) //
//				|| (value instanceof Character) //
//				|| (value instanceof Boolean) //
//				|| (value instanceof Instant) //
//				|| (value instanceof Year) //
//				|| (value instanceof YearMonth) //
//				|| (value instanceof LocalDate) //
//				|| (value instanceof LocalDateTime) //
//				|| (value instanceof LocalTime) //
//				|| (value instanceof ZonedDateTime) //
//				|| (value instanceof OffsetDateTime) //
//				|| (value instanceof URL) //
//				|| (value instanceof URI) //
//				|| (value instanceof Pattern))
//			return value.toString();
//
//		if (value instanceof Date date)
//			return date.toInstant().toString();
//
//		if (value instanceof Calendar calendar)
//			return calendar.toInstant().toString();
//
//		if (value.getClass().isEnum())
//			return ((Enum<?>) value).name();
//
//		throw new IllegalArgumentException("Invalid single value");
	}

	/**
	 * <strong>Deserializes</strong> a single value.
	 * 
	 * @param <T>         target type
	 * @param text        serialized text
	 * @param targetClass target class, must represent a <strong>single value type
	 *                    or sequence</strong>
	 * @return <strong>deserialized</strong> single value
	 * @throws IllegalArgumentException If the value provided is not the
	 *                                  <strong>serialized form</strong> of a
	 *                                  <strong>single value or sequence</strong>.
	 */
	public static <T> T deserialize(CharSequence text, Class<T> targetClass) throws IllegalArgumentException {
		return deserialize(text, (Type) targetClass);
	}

	/**
	 * <strong>Deserializes</strong> a single value.
	 * 
	 * @param <T>        target type
	 * @param text       serialized text
	 * @param targetType target type, must represent a <strong>single value type or
	 *                   sequence</strong>
	 * @return <strong>deserialized</strong> single value
	 * @throws IllegalArgumentException If the value provided is not the
	 *                                  <strong>serialized form</strong> of a
	 *                                  <strong>single value or sequence</strong>.
	 */
	public static <T> T deserialize(CharSequence text, Type targetType) throws IllegalArgumentException {
		if (text == null)
			return null;

		@SuppressWarnings("unchecked")
		final var targetClass = (Class<T>) getTargetClass(targetType);

		if (targetClass.isInstance(text))
			return targetClass.cast(text);

		if (targetClass == Reader.class)
			return targetClass.cast(new StringReader(text.toString()));

		if (targetClass == Writer.class) {
			final var detachedWriter = new DetachedWriter();
			IuException.unchecked(() -> detachedWriter.write(text.toString()));
			return targetClass.cast(detachedWriter);
		}

		if (targetClass == Character.class)
			if (text.length() != 1)
				throw new IllegalArgumentException("Invalid character, expected length == 1");
			else
				return targetClass.cast(text.charAt(0));

		if (targetClass == Boolean.class) {
			final var textString = text.toString();
			return targetClass.cast(textString.equals("1") //
					|| textString.equals("-1") //
					|| textString.equalsIgnoreCase("true") //
					|| textString.equalsIgnoreCase("Y"));
		}

		if (targetClass == Integer.class)
			return targetClass.cast(Integer.parseInt(text.toString()));
		if (targetClass == Long.class)
			return targetClass.cast(Long.parseLong(text.toString()));
		if (targetClass == Short.class)
			return targetClass.cast(Short.parseShort(text.toString()));
		if (targetClass == Byte.class)
			return targetClass.cast(Byte.parseByte(text.toString()));
		if (targetClass == Double.class)
			return targetClass.cast(Double.parseDouble(text.toString()));
		if (targetClass == Float.class)
			return targetClass.cast(Float.parseFloat(text.toString()));
		
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * <strong>Deserializes</strong> a single value.
	 * 
	 * @param <T>         target type
	 * @param in          {@link InputStream} deserialization source
	 * @param targetClass target class, must represent a <strong>single value type
	 *                    or sequence</strong>
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
	 * @param targetType target type, must represent a <strong>single value type or
	 *                   sequence</strong>
	 * @return <strong>deserialized</strong> single value
	 * @throws IllegalArgumentException If the value provided is not the
	 *                                  <strong>serialized form</strong> of a
	 *                                  <strong>single value or sequence</strong>.
	 * @throws IOException              if a read error occurs
	 */
	public static <T> T deserialize(InputStream in, Type targetType) throws IllegalArgumentException, IOException {
		@SuppressWarnings("unchecked")
		final var targetClass = (Class<T>) getTargetClass(targetType);

		if (targetClass == Reader.class)
			return targetClass.cast(new InputStreamReader(in, "UTF-8"));

		if (targetClass == Writer.class) {
			final var detachedWriter = new DetachedWriter();
			IuException.unchecked(() -> IuStream.copy(new InputStreamReader(in, "UTF-8"), detachedWriter));
			return targetClass.cast(detachedWriter);
		}

		return deserialize(new InputStreamReader(in), targetType);
	}

	/**
	 * <strong>Deserializes</strong> a single value.
	 * 
	 * @param <T>         target type
	 * @param reader      {@link Reader} deserialization source
	 * @param targetClass target type, must represent a <strong>single value type or
	 *                    sequence</strong>
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
	 * <strong>Deserializes</strong> a single value.
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

		if (targetClass == Reader.class)
			return targetClass.cast(reader);

		if (targetClass == Writer.class) {
			final var detachedWriter = new DetachedWriter();
			IuException.unchecked(() -> IuStream.copy(reader, detachedWriter));
			return targetClass.cast(detachedWriter);
		}

		final var deserializationSource = IuStream.read(reader);
		if ("".equals(deserializationSource) && getNonPrimitiveClass(targetClass) == Void.class)
			return null;

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

		if (sourceValue instanceof Number sourceNumber) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("TODO");
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

	private IuSingleValue() {
	}

}
