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

/**
 * Implements {@link IuJsonAdapter} for {@link Number}
 * 
 * @param <N> number type
 */
class NumberAdapter<N extends Number> implements IuJsonAdapter<N> {

	/**
	 * Adapts {@link Byte}
	 */
	static final NumberAdapter<Byte> BYTE = new NumberAdapter<>(null, a -> (byte) a.intValue(), Byte::valueOf);

	/**
	 * Adapts {@link Byte#TYPE}
	 */
	static final NumberAdapter<Byte> BYTE_PRIMITIVE = new NumberAdapter<>((byte) 0, a -> (byte) a.intValue(),
			Byte::valueOf);

	/**
	 * Adapts {@link Short}
	 */
	static final NumberAdapter<Short> SHORT = new NumberAdapter<>(null, a -> (short) a.intValue(), Short::valueOf);

	/**
	 * Adapts {@link Short#TYPE}
	 */
	static final NumberAdapter<Short> SHORT_PRIMITIVE = new NumberAdapter<>((short) 0, a -> (short) a.intValue(),
			Short::valueOf);

	/**
	 * Adapts {@link Integer}
	 */
	static final NumberAdapter<Integer> INT = new NumberAdapter<>(null, JsonNumber::intValue, Integer::valueOf);

	/**
	 * Adapts {@link Integer#TYPE}
	 */
	static final NumberAdapter<Integer> INT_PRIMITIVE = new NumberAdapter<>((int) 0, JsonNumber::intValue,
			Integer::valueOf);

	/**
	 * Adapts {@link Long}
	 */
	static final NumberAdapter<Long> LONG = new NumberAdapter<>(null, JsonNumber::longValue, Long::valueOf);

	/**
	 * Adapts {@link Long#TYPE}
	 */
	static final NumberAdapter<Long> LONG_PRIMITIVE = new NumberAdapter<>(0L, JsonNumber::longValue, Long::valueOf);

	/**
	 * Adapts {@link Float}
	 */
	static final NumberAdapter<Float> FLOAT = new NumberAdapter<>(null, a -> (float) a.doubleValue(), Float::valueOf);

	/**
	 * Adapts {@link Float#TYPE}
	 */
	static final NumberAdapter<Float> FLOAT_PRIMITIVE = new NumberAdapter<>(0.0f, a -> (float) a.doubleValue(),
			Float::valueOf);

	/**
	 * Adapts {@link Double}
	 */
	static final NumberAdapter<Double> DOUBLE = new NumberAdapter<>(null, JsonNumber::doubleValue, Double::valueOf);

	/**
	 * Adapts {@link Double#TYPE}
	 */
	static final NumberAdapter<Double> DOUBLE_PRIMITIVE = new NumberAdapter<>(0.0, JsonNumber::doubleValue,
			Double::valueOf);

	/**
	 * Adapts {@link BigDecimal}
	 */
	static final NumberAdapter<BigDecimal> BIG_DECIMAL = new NumberAdapter<>(null, JsonNumber::bigDecimalValue,
			BigDecimal::new);

	/**
	 * Adapts {@link BigDecimal}
	 */
	static final NumberAdapter<BigInteger> BIG_INTEGER = new NumberAdapter<>(null, JsonNumber::bigIntegerValue,
			BigInteger::new);

	private final N nullValue;
	private final Function<JsonNumber, N> fromJson;
	private final Function<String, N> fromString;

	private NumberAdapter(N nullValue, Function<JsonNumber, N> fromJson, Function<String, N> fromString) {
		this.nullValue = nullValue;
		this.fromJson = fromJson;
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

}
