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
import java.util.function.Function;

import edu.iu.IuObject;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * Converts a value of a {@link JsonAdapters#isBroad(Type) broad} declared
 * type, such as {@link Object}, by its runtime type.
 *
 * <p>
 * Reads as {@link IuJsonAdapter#basic()} does: a JSON array as a
 * {@link java.util.List}, an object as a {@link java.util.Map}, a string as
 * {@link String}, a number as {@link java.math.BigDecimal}, and a boolean as
 * {@link Boolean}.
 * </p>
 *
 * <p>
 * Writes a {@link JsonValue}, {@link JsonObjectBuilder}, or
 * {@link JsonArrayBuilder} as {@link IuJsonAdapter#basic()} does, and any other
 * value through the value adapter for its
 * {@link JsonAdapters#runtimeType(Object) runtime type}; a platform class
 * converts as its {@link JsonAdapters#conversionType(Class) conversion type}.
 * </p>
 */
final class RuntimeTypeAdapter implements IuJsonAdapter<Object> {

	private final Function<Type, IuJsonAdapter<?>> valueAdapter;

	/**
	 * Constructor.
	 *
	 * @param valueAdapter value adapter function
	 */
	RuntimeTypeAdapter(Function<Type, IuJsonAdapter<?>> valueAdapter) {
		this.valueAdapter = valueAdapter;
	}

	@Override
	public Object fromJson(JsonValue value) {
		return BasicJsonAdapter.INSTANCE.fromJson(value);
	}

	@Override
	public Object read(JsonParser parser) {
		return BasicJsonAdapter.INSTANCE.read(parser);
	}

	@Override
	public JsonValue toJson(Object value) {
		return adapter(value).toJson(value);
	}

	@Override
	public void write(Object value, JsonGenerator generator) {
		adapter(value).write(value, generator);
	}

	@SuppressWarnings("unchecked")
	private IuJsonAdapter<Object> adapter(Object value) {
		if (value == null //
				|| value instanceof JsonValue //
				|| value instanceof JsonObjectBuilder //
				|| value instanceof JsonArrayBuilder)
			return BasicJsonAdapter.INSTANCE;

		final var runtimeType = JsonAdapters.runtimeType(value);
		final Type type;
		if (runtimeType.isArray() || !IuObject.isPlatformName(runtimeType.getName()))
			type = runtimeType;
		else {
			type = JsonAdapters.conversionType(runtimeType);
			if (type == Object.class)
				throw new UnsupportedOperationException("Unsupported for JSON conversion: " + runtimeType);
		}

		return (IuJsonAdapter<Object>) valueAdapter.apply(type);
	}

}
