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
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * Implements {@link IuJsonAdapter} for {@link Number}
 * 
 * @param <N> number type
 */
class NumberAdapter<N extends Number> implements IuJsonAdapter<N> {

	/**
	 * Adapts {@link Byte}
	 */
	static final NumberAdapter<Byte> BYTE = new NumberAdapter<>(null, a -> (byte) a.intValue(),
			p -> (byte) p.getInt(), Byte::valueOf);

	/**
	 * Adapts {@link Byte#TYPE}
	 */
	static final NumberAdapter<Byte> BYTE_PRIMITIVE = new NumberAdapter<>((byte) 0, a -> (byte) a.intValue(),
			p -> (byte) p.getInt(), Byte::valueOf);

	/**
	 * Adapts {@link Short}
	 */
	static final NumberAdapter<Short> SHORT = new NumberAdapter<>(null, a -> (short) a.intValue(),
			p -> (short) p.getInt(), Short::valueOf);

	/**
	 * Adapts {@link Short#TYPE}
	 */
	static final NumberAdapter<Short> SHORT_PRIMITIVE = new NumberAdapter<>((short) 0, a -> (short) a.intValue(),
			p -> (short) p.getInt(), Short::valueOf);

	/**
	 * Adapts {@link Integer}
	 */
	static final NumberAdapter<Integer> INT = new NumberAdapter<>(null, JsonNumber::intValue, JsonParser::getInt,
			Integer::valueOf);

	/**
	 * Adapts {@link Integer#TYPE}
	 */
	static final NumberAdapter<Integer> INT_PRIMITIVE = new NumberAdapter<>((int) 0, JsonNumber::intValue,
			JsonParser::getInt, Integer::valueOf);

	/**
	 * Adapts {@link Long}
	 */
	static final NumberAdapter<Long> LONG = new NumberAdapter<>(null, JsonNumber::longValue, JsonParser::getLong,
			Long::valueOf);

	/**
	 * Adapts {@link Long#TYPE}
	 */
	static final NumberAdapter<Long> LONG_PRIMITIVE = new NumberAdapter<>(0L, JsonNumber::longValue,
			JsonParser::getLong, Long::valueOf);

	/**
	 * Adapts {@link Float}
	 */
	static final NumberAdapter<Float> FLOAT = new NumberAdapter<>(null, a -> (float) a.doubleValue(),
			p -> (float) p.getBigDecimal().doubleValue(), Float::valueOf);

	/**
	 * Adapts {@link Float#TYPE}
	 */
	static final NumberAdapter<Float> FLOAT_PRIMITIVE = new NumberAdapter<>(0.0f, a -> (float) a.doubleValue(),
			p -> (float) p.getBigDecimal().doubleValue(), Float::valueOf);

	/**
	 * Adapts {@link Double}
	 */
	static final NumberAdapter<Double> DOUBLE = new NumberAdapter<>(null, JsonNumber::doubleValue,
			p -> p.getBigDecimal().doubleValue(), Double::valueOf);

	/**
	 * Adapts {@link Double#TYPE}
	 */
	static final NumberAdapter<Double> DOUBLE_PRIMITIVE = new NumberAdapter<>(0.0, JsonNumber::doubleValue,
			p -> p.getBigDecimal().doubleValue(), Double::valueOf);

	/**
	 * Adapts {@link BigDecimal}
	 */
	static final NumberAdapter<BigDecimal> BIG_DECIMAL = new NumberAdapter<>(null, JsonNumber::bigDecimalValue,
			JsonParser::getBigDecimal, BigDecimal::new);

	/**
	 * Adapts {@link BigDecimal}
	 *
	 * <p>
	 * Reads through {@link JsonNumber#bigIntegerValue()} even when streaming, so
	 * the provider's limit on the scale of a number converted to an integer still
	 * applies; a short number with a large exponent would otherwise expand to an
	 * arbitrarily large value.
	 * </p>
	 */
	static final NumberAdapter<BigInteger> BIG_INTEGER = new NumberAdapter<>(null, JsonNumber::bigIntegerValue,
			p -> ((JsonNumber) p.getValue()).bigIntegerValue(), BigInteger::new);

	private final N nullValue;
	private final Function<JsonNumber, N> fromJson;
	private final Function<JsonParser, N> fromParser;
	private final Function<String, N> fromString;

	private NumberAdapter(N nullValue, Function<JsonNumber, N> fromJson, Function<JsonParser, N> fromParser,
			Function<String, N> fromString) {
		this.nullValue = nullValue;
		this.fromJson = fromJson;
		this.fromParser = fromParser;
		this.fromString = fromString;
	}

	@Override
	public N fromJson(JsonValue value) {
		if (JsonValue.NULL.equals(value) //
				|| value == null)
			return nullValue;
		else if (value instanceof JsonString)
			return fromString.apply(TextJsonAdapter.INSTANCE.fromJson(value));
		else
			return fromJson.apply((JsonNumber) value);
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
		switch (parser.currentEvent()) {
		case VALUE_NUMBER:
			return fromParser.apply(parser);

		case VALUE_STRING:
			return fromString.apply(parser.getString());

		case VALUE_NULL:
			return nullValue;

		default:
			// as fromJson(JsonValue) fails for any other value
			throw new ClassCastException("expected a number, found " + parser.currentEvent());
		}
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
