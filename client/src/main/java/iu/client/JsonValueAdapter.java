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

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * Implements {@link IuJsonAdapter} for a JSON-P value type, such as
 * {@link JsonObject}, which converts as itself.
 *
 * <p>
 * A JSON null reads as {@link JsonValue#NULL} for {@link JsonValue}, which it
 * is an instance of, and as null for any narrower type. A value of the wrong
 * type fails.
 * </p>
 *
 * @param <T> JSON-P value type
 */
class JsonValueAdapter<T extends JsonValue> implements IuJsonAdapter<T> {

	private final Class<T> type;
	private final String expected;

	/**
	 * Constructor.
	 *
	 * @param type JSON-P value type
	 */
	JsonValueAdapter(Class<T> type) {
		this.type = type;
		if (JsonObject.class.isAssignableFrom(type))
			expected = "an object";
		else if (JsonArray.class.isAssignableFrom(type))
			expected = "an array";
		else if (JsonStructure.class.isAssignableFrom(type))
			expected = "an object or array";
		else if (JsonString.class.isAssignableFrom(type))
			expected = "a string";
		else if (JsonNumber.class.isAssignableFrom(type))
			expected = "a number";
		else
			expected = type.getSimpleName();
	}

	@Override
	public T fromJson(JsonValue value) {
		if (value == null)
			return null;
		else if (type.isInstance(value))
			return type.cast(value);
		else if (JsonValue.NULL.equals(value))
			return null;
		else
			throw JsonAdapters.expected(expected, value.getValueType());
	}

	@Override
	public JsonValue toJson(T value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return value;
	}

	/**
	 * Reads the whole value, leaving the parser at its last event.
	 */
	@Override
	public T read(JsonParser parser) {
		return fromJson(parser.getValue());
	}

	@Override
	public void write(T value, JsonGenerator generator) {
		if (value == null)
			generator.writeNull();
		else
			generator.write(value);
	}

}
