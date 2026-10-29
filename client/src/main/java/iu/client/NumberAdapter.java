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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.Function;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonNumber;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * Implements {@link IuJsonAdapter} for {@link Number}
 *
 * <p>
 * Reads only JSON numbers, and an integer type only a number it holds exactly:
 * a fractional part or a value out of range fails rather than truncating. A
 * floating-point type reads any number, to the nearest value it can hold.
 * </p>
 *
 * <p>
 * The text form, for a map key, is the number's {@link Object#toString()},
 * read back with the type's own parser.
 * </p>
 *
 * @param <N> number type
 */
class NumberAdapter<N extends Number> implements IuJsonAdapter<N>, TextForm<N> {

	/**
	 * Adapts {@link Byte}
	 */
	static final NumberAdapter<Byte> BYTE = new NumberAdapter<>("a byte", null,
			a -> (byte) exact(a.intValueExact(), Byte.MIN_VALUE, Byte.MAX_VALUE), Byte::valueOf);

	/**
	 * Adapts {@link Byte#TYPE}
	 */
	static final NumberAdapter<Byte> BYTE_PRIMITIVE = new NumberAdapter<>("a byte", (byte) 0,
			a -> (byte) exact(a.intValueExact(), Byte.MIN_VALUE, Byte.MAX_VALUE), Byte::valueOf);

	/**
	 * Adapts {@link Short}
	 */
	static final NumberAdapter<Short> SHORT = new NumberAdapter<>("a short", null,
			a -> (short) exact(a.intValueExact(), Short.MIN_VALUE, Short.MAX_VALUE), Short::valueOf);

	/**
	 * Adapts {@link Short#TYPE}
	 */
	static final NumberAdapter<Short> SHORT_PRIMITIVE = new NumberAdapter<>("a short", (short) 0,
			a -> (short) exact(a.intValueExact(), Short.MIN_VALUE, Short.MAX_VALUE), Short::valueOf);

	/**
	 * Adapts {@link Integer}
	 */
	static final NumberAdapter<Integer> INT = new NumberAdapter<>("an int", null, JsonNumber::intValueExact,
			Integer::valueOf);

	/**
	 * Adapts {@link Integer#TYPE}
	 */
	static final NumberAdapter<Integer> INT_PRIMITIVE = new NumberAdapter<>("an int", 0, JsonNumber::intValueExact,
			Integer::valueOf);

	/**
	 * Adapts {@link Long}
	 */
	static final NumberAdapter<Long> LONG = new NumberAdapter<>("a long", null, JsonNumber::longValueExact,
			Long::valueOf);

	/**
	 * Adapts {@link Long#TYPE}
	 */
	static final NumberAdapter<Long> LONG_PRIMITIVE = new NumberAdapter<>("a long", 0L, JsonNumber::longValueExact,
			Long::valueOf);

	/**
	 * Adapts {@link Float}
	 */
	static final NumberAdapter<Float> FLOAT = new NumberAdapter<>("a float", null, a -> (float) a.doubleValue(),
			Float::valueOf);

	/**
	 * Adapts {@link Float#TYPE}
	 */
	static final NumberAdapter<Float> FLOAT_PRIMITIVE = new NumberAdapter<>("a float", 0.0f,
			a -> (float) a.doubleValue(), Float::valueOf);

	/**
	 * Adapts {@link Double}
	 */
	static final NumberAdapter<Double> DOUBLE = new NumberAdapter<>("a double", null, JsonNumber::doubleValue,
			Double::valueOf);

	/**
	 * Adapts {@link Double#TYPE}
	 */
	static final NumberAdapter<Double> DOUBLE_PRIMITIVE = new NumberAdapter<>("a double", 0.0,
			JsonNumber::doubleValue, Double::valueOf);

	/**
	 * Adapts {@link BigDecimal}
	 */
	static final NumberAdapter<BigDecimal> BIG_DECIMAL = new NumberAdapter<>("a number", null,
			JsonNumber::bigDecimalValue, BigDecimal::new);

	/**
	 * Adapts {@link BigInteger}
	 *
	 * <p>
	 * Reads through {@link JsonNumber#bigIntegerValueExact()}, so the provider's
	 * limit on the scale of a number converted to an integer still applies; a
	 * short number with a large exponent would otherwise expand to an arbitrarily
	 * large value.
	 * </p>
	 */
	static final NumberAdapter<BigInteger> BIG_INTEGER = new NumberAdapter<>("an integer", null,
			JsonNumber::bigIntegerValueExact, BigInteger::new);

	/**
	 * Checks that an integer is within the range of a narrower type.
	 *
	 * @param value value
	 * @param min   narrower type's minimum
	 * @param max   narrower type's maximum
	 * @return value
	 * @throws ArithmeticException if out of range
	 */
	static int exact(int value, int min, int max) {
		if (value < min || value > max)
			throw new ArithmeticException("out of range");
		return value;
	}

	private final String expected;
	private final N nullValue;
	private final Function<JsonNumber, N> fromJson;
	private final Function<String, N> fromString;

	private NumberAdapter(String expected, N nullValue, Function<JsonNumber, N> fromJson,
			Function<String, N> fromString) {
		this.expected = expected;
		this.nullValue = nullValue;
		this.fromJson = fromJson;
		this.fromString = fromString;
	}

	@Override
	public N fromJson(JsonValue value) {
		if (JsonValue.NULL.equals(value) //
				|| value == null)
			return nullValue;
		else if (value instanceof JsonNumber)
			return exact((JsonNumber) value);
		else
			throw JsonAdapters.expected(expected, value.getValueType());
	}

	private N exact(JsonNumber value) {
		try {
			return fromJson.apply(value);
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("expected " + expected + ", found " + value, e);
		}
	}

	@Override
	public N fromText(String text) {
		return fromString.apply(text);
	}

	@Override
	public String toText(N value) {
		return value.toString();
	}

	@Override
	public JsonValue toJson(N value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return IuJson.PROVIDER.createValue(value);
	}

	@Override
	public N read(JsonParser parser) {
		final var event = parser.currentEvent();
		if (event == Event.VALUE_NUMBER)
			// a provider represents a small integer compactly, so an exact
			// conversion costs no more than reading it as a primitive
			return exact((JsonNumber) parser.getValue());
		else if (event == Event.VALUE_NULL)
			return nullValue;
		else
			throw JsonAdapters.expected(expected, event);
	}

	/**
	 * Writes {@link Integer} and {@link Long} directly, which have the same text
	 * as a {@link JsonNumber}; other types convert through
	 * {@link #toJson(Number)}, whose text for a floating-point value may differ
	 * from the generator's.
	 */
	@Override
	public void write(N value, JsonGenerator generator) {
		if (value == null)
			generator.writeNull();
		else if (value instanceof Integer)
			generator.write(value.intValue());
		else if (value instanceof Long)
			generator.write(value.longValue());
		else
			generator.write(toJson(value));
	}

}
