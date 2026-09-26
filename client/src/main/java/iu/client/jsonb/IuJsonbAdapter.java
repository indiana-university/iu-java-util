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

import edu.iu.client.IuJsonAdapter;
import iu.client.JsonProxy;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * Converts a business object type using its {@link IuJsonbModel}, as part of
 * the call in progress: its options snapshot names the properties, its path
 * locates a failure, and its cycle check rejects a recursive reference.
 *
 * <p>
 * An interface converts from JSON as a {@link JsonProxy} reading the call's
 * property name format; a value wrapped that way converts to JSON as its
 * source object.
 * </p>
 *
 * @param <T> business object type
 */
final class IuJsonbAdapter<T> implements IuJsonAdapter<T> {

	private final Class<T> type;
	private final IuJsonb jsonb;
	private volatile IuJsonbModel model;

	/**
	 * Constructor; the model is introspected on first use.
	 *
	 * @param type  business object type
	 * @param jsonb provider
	 */
	IuJsonbAdapter(Class<T> type, IuJsonb jsonb) {
		this.type = type;
		this.jsonb = jsonb;
	}

	private IuJsonbModel model() {
		var model = this.model;
		if (model == null)
			this.model = model = jsonb.model(type);
		return model;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T fromJson(JsonValue value) {
		if (value == null || JsonValue.NULL.equals(value))
			return null;
		if (!(value instanceof JsonObject))
			throw new JsonbException("expected object for " + type.getName() + ", found " + value.getValueType());

		final var context = IuDeserializationContext.require(jsonb);
		final var format = context.format();
		final var object = value.asJsonObject();
		if (type.isInterface())
			return JsonProxy.wrap(object, type, format, jsonb::adapt);

		final var model = model();
		final var bean = (T) model.newInstance();
		for (final var entry : object.entrySet()) {
			final var key = entry.getKey();
			final var property = model.writable(format, key);
			if (property != null)
				property.fromJson(bean, key, entry.getValue(), context);
		}
		return bean;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T read(JsonParser parser) {
		var event = parser.currentEvent();
		if (event == Event.VALUE_NULL)
			return null;
		if (event != Event.START_OBJECT)
			throw new JsonbException("expected START_OBJECT for " + type.getName() + ", found " + event);

		final var context = IuDeserializationContext.require(jsonb);
		final var format = context.format();
		if (type.isInterface())
			return JsonProxy.wrap(parser.getObject(), type, format, jsonb::adapt);

		final var model = model();
		final var bean = (T) model.newInstance();
		while (parser.next() != Event.END_OBJECT) {
			final var key = parser.getString();
			final var property = model.writable(format, key);
			event = parser.next();
			if (property != null)
				property.read(bean, key, parser, context);
			else if (event == Event.START_OBJECT)
				parser.skipObject();
			else if (event == Event.START_ARRAY)
				parser.skipArray();
		}
		return bean;
	}

	@Override
	public JsonValue toJson(T value) {
		if (value == null)
			return JsonValue.NULL;
		if (IuSerializationContext.isJsonProxy(value))
			return JsonProxy.unwrap(value);

		final var context = IuSerializationContext.require(jsonb);
		context.enterBean(value);
		try {
			final var builder = jsonb.provider().createObjectBuilder();
			for (final var property : model().readable(context.format()))
				property.add(value, builder, context);
			return builder.build();
		} finally {
			context.exitBean(value);
		}
	}

	@Override
	public void write(T value, JsonGenerator generator) {
		if (value == null) {
			generator.writeNull();
			return;
		}
		if (IuSerializationContext.isJsonProxy(value)) {
			generator.write(JsonProxy.unwrap(value));
			return;
		}

		final var context = IuSerializationContext.require(jsonb);
		context.enterBean(value);
		try {
			generator.writeStartObject();
			for (final var property : model().readable(context.format()))
				property.write(value, generator, context);
			generator.writeEnd();
		} finally {
			context.exitBean(value);
		}
	}

}
