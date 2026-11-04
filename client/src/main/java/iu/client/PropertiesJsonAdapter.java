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

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonProperties;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * Built-in conversion for {@link IuJsonProperties}: written as the object it
 * indexes, and read as an index of the object, whose values convert when read.
 *
 * <p>
 * Reading from a parser reads the whole object, leaving the parser at its
 * {@code END_OBJECT} as a conversion must; the properties still convert only
 * when read.
 * </p>
 */
final class PropertiesJsonAdapter implements IuJsonAdapter<IuJsonProperties> {

	private final Function<Type, IuJsonAdapter<?>> adapt;

	/**
	 * Constructor.
	 *
	 * @param adapt conversions an index read converts by; null for those of the
	 *              JSON-B call it converts in
	 */
	PropertiesJsonAdapter(Function<Type, IuJsonAdapter<?>> adapt) {
		this.adapt = adapt;
	}

	private IuJsonProperties index(JsonObject object) {
		return adapt == null ? IuJsonProperties.of(object) : IuJsonProperties.of(object, adapt);
	}

	@Override
	public IuJsonProperties fromJson(JsonValue value) {
		if (value == null || JsonValue.NULL.equals(value))
			return null;
		else if (value instanceof JsonObject)
			return index((JsonObject) value);
		else
			throw JsonAdapters.expected("an object", value.getValueType());
	}

	@Override
	public JsonValue toJson(IuJsonProperties value) {
		return value == null ? JsonValue.NULL : value.toJsonObject();
	}

	@Override
	public IuJsonProperties read(JsonParser parser) {
		final var event = parser.currentEvent();
		if (event == Event.VALUE_NULL)
			return null;
		else if (event == Event.START_OBJECT)
			return index(parser.getObject());
		else
			throw JsonAdapters.expected("an object", event);
	}

	@Override
	public void write(IuJsonProperties value, JsonGenerator generator) {
		if (value == null)
			generator.writeNull();
		else
			value.write(generator);
	}

}
