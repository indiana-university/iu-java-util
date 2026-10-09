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
package iu.client.jsonb;

import java.util.List;
import java.util.Map;

import edu.iu.client.IuJsonAdapter;
import iu.client.JsonAdapters;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * Built-in conversion for {@link Object} and other
 * {@link IuJsonb#isBroad(java.lang.reflect.Type) broad} declared types.
 *
 * <p>
 * Reads a JSON array as a {@link List}, an object as a {@link Map} with
 * {@link String} keys, a string as {@link String}, a number as
 * {@link java.math.BigDecimal}, and a boolean as {@link Boolean}. Each nested
 * item and map value reads as {@link Object} through {@link IuJsonb#adapt}, so
 * configured components apply at any depth.
 * </p>
 *
 * <p>
 * Writes only null: {@link IuJsonbValueAdapter} writes any other value of a
 * broad declared type through its runtime type, and a value reaching this
 * adapter has no conversion.
 * </p>
 */
@SuppressWarnings({ "rawtypes" })
final class IuJsonbObjectAdapter implements IuJsonAdapter<Object> {

	private static final IuJsonAdapter<Object> BASIC = IuJsonAdapter.basic();

	private final IuJsonb jsonb;
	private volatile IuJsonAdapter list;
	private volatile IuJsonAdapter map;

	/**
	 * Constructor.
	 *
	 * @param jsonb provider
	 */
	IuJsonbObjectAdapter(IuJsonb jsonb) {
		this.jsonb = jsonb;
	}

	private IuJsonAdapter list() {
		var list = this.list;
		if (list == null)
			this.list = list = JsonAdapters.adapt(List.class, jsonb::adapt, jsonb.itemScope);
		return list;
	}

	private IuJsonAdapter map() {
		var map = this.map;
		if (map == null)
			this.map = map = JsonAdapters.adapt(Map.class, jsonb::adapt, jsonb::keyAdapter, jsonb.itemScope);
		return map;
	}

	@Override
	public Object fromJson(JsonValue value) {
		if (value instanceof jakarta.json.JsonArray)
			return list().fromJson(value);
		else if (value instanceof jakarta.json.JsonObject)
			return map().fromJson(value);
		else
			return BASIC.fromJson(value);
	}

	@Override
	public Object read(JsonParser parser) {
		switch (parser.currentEvent()) {
		case START_ARRAY:
			return list().read(parser);

		case START_OBJECT:
			return map().read(parser);

		default:
			return BASIC.read(parser);
		}
	}

	@Override
	public JsonValue toJson(Object value) {
		if (value == null)
			return JsonValue.NULL;
		else
			throw unsupported(value);
	}

	@Override
	public void write(Object value, JsonGenerator generator) {
		if (value == null)
			generator.writeNull();
		else
			throw unsupported(value);
	}

	private static UnsupportedOperationException unsupported(Object value) {
		return new UnsupportedOperationException("Unsupported for JSON conversion: " + value.getClass());
	}

}
