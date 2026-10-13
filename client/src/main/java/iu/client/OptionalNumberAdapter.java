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

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.function.Predicate;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * Implements {@link IuJsonAdapter} for {@link OptionalInt},
 * {@link OptionalLong}, and {@link OptionalDouble}; empty converts as JSON
 * null.
 *
 * @param <O> optional type
 * @param <N> number type
 */
class OptionalNumberAdapter<O, N extends Number> implements IuJsonAdapter<O> {

	/**
	 * Adapts {@link OptionalInt}
	 */
	static final OptionalNumberAdapter<OptionalInt, Integer> INT = new OptionalNumberAdapter<>(NumberAdapter.INT,
			OptionalInt.empty(), OptionalInt::of, OptionalInt::isPresent, OptionalInt::getAsInt);

	/**
	 * Adapts {@link OptionalLong}
	 */
	static final OptionalNumberAdapter<OptionalLong, Long> LONG = new OptionalNumberAdapter<>(NumberAdapter.LONG,
			OptionalLong.empty(), OptionalLong::of, OptionalLong::isPresent, OptionalLong::getAsLong);

	/**
	 * Adapts {@link OptionalDouble}
	 */
	static final OptionalNumberAdapter<OptionalDouble, Double> DOUBLE = new OptionalNumberAdapter<>(
			NumberAdapter.DOUBLE, OptionalDouble.empty(), OptionalDouble::of, OptionalDouble::isPresent,
			OptionalDouble::getAsDouble);

	private final NumberAdapter<N> number;
	private final O empty;
	private final Function<N, O> of;
	private final Predicate<O> isPresent;
	private final Function<O, N> get;

	private OptionalNumberAdapter(NumberAdapter<N> number, O empty, Function<N, O> of, Predicate<O> isPresent,
			Function<O, N> get) {
		this.number = number;
		this.empty = empty;
		this.of = of;
		this.isPresent = isPresent;
		this.get = get;
	}

	@Override
	public O fromJson(JsonValue value) {
		if (value == null //
				|| JsonValue.NULL.equals(value))
			return empty;
		else
			return of.apply(number.fromJson(value));
	}

	@Override
	public JsonValue toJson(O value) {
		if (value == null //
				|| !isPresent.test(value))
			return JsonValue.NULL;
		else
			return number.toJson(get.apply(value));
	}

	@Override
	public O read(JsonParser parser) {
		if (parser.currentEvent() == Event.VALUE_NULL)
			return empty;
		else
			return of.apply(number.read(parser));
	}

	@Override
	public void write(O value, JsonGenerator generator) {
		if (value == null //
				|| !isPresent.test(value))
			generator.writeNull();
		else
			number.write(get.apply(value), generator);
	}

}
