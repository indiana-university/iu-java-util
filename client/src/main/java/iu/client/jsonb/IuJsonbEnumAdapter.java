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

import java.util.Objects;

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import iu.client.JsonSerializer;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * Converts an enum type as text, or as an object when
 * {@link edu.iu.client.IuJsonSerializationOptions#isEnumAsObject()}.
 *
 * <p>
 * The object form writes the {@link JsonSerializer#NAME name} property first,
 * followed by the enum type's other readable properties from its
 * {@link IuJsonbModel}. An enum that declares its own name property supplies
 * that value, falling back to {@link Enum#name()} when it's omitted. Reading
 * accepts either form, finding the name property by the call's property name
 * format.
 * </p>
 *
 * @param <E> enum type
 */
final class IuJsonbEnumAdapter<E extends Enum<E>> implements IuJsonAdapter<E> {

	private static final IuJsonAdapter<String> TEXT = IuJsonAdapter.of(String.class);

	private final Class<E> type;
	private final IuJsonb jsonb;
	private volatile IuJsonbModel model;

	/**
	 * Constructor; the model is introspected on first use.
	 *
	 * @param type  enum type
	 * @param jsonb provider
	 */
	IuJsonbEnumAdapter(Class<E> type, IuJsonb jsonb) {
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
	public E fromJson(JsonValue value) {
		if (value == null || JsonValue.NULL.equals(value))
			return null;

		final String name;
		if (value instanceof JsonObject) {
			final var format = IuDeserializationContext.require(jsonb).format();
			name = TEXT.fromJson(((JsonObject) value).get(JsonSerializer.formatPropertyName(JsonSerializer.NAME, format)));
		} else
			name = TEXT.fromJson(value);

		return Enum.valueOf(type, Objects.requireNonNull(name, JsonSerializer.NAME));
	}

	/**
	 * Reads the text form directly, and the object form by scanning for its name
	 * property, skipping every other property.
	 */
	@Override
	public E read(JsonParser parser) {
		switch (parser.currentEvent()) {
		case VALUE_NULL:
			return null;

		case VALUE_STRING:
			return Enum.valueOf(type, parser.getString());

		case START_OBJECT: {
			final var nameKey = JsonSerializer.formatPropertyName(JsonSerializer.NAME,
					IuDeserializationContext.require(jsonb).format());

			String name = null;
			while (parser.next() != Event.END_OBJECT) {
				final var key = parser.getString();
				final var event = parser.next();
				if (nameKey.equals(key))
					name = TEXT.read(parser);
				else if (event == Event.START_OBJECT)
					parser.skipObject();
				else if (event == Event.START_ARRAY)
					parser.skipArray();
			}
			return Enum.valueOf(type, Objects.requireNonNull(name, JsonSerializer.NAME));
		}

		default:
			return fromJson(parser.getValue());
		}
	}

	@Override
	public JsonValue toJson(E value) {
		if (value == null)
			return JsonValue.NULL;

		final var context = IuSerializationContext.require(jsonb);
		if (!context.isEnumAsObject())
			return jsonb.provider().createValue(value.toString());

		final var format = context.format();
		final var nameKey = JsonSerializer.formatPropertyName(JsonSerializer.NAME, format);
		final var properties = model().readable(format);
		final var nameProperty = nameProperty(properties, nameKey, format);

		context.enterBean(value);
		try {
			final var builder = jsonb.provider().createObjectBuilder();
			if (nameProperty == null || !nameProperty.add(value, builder, context))
				builder.add(nameKey, value.name());

			for (final var property : properties)
				if (property != nameProperty)
					property.add(value, builder, context);

			return builder.build();
		} finally {
			context.exitBean(value);
		}
	}

	@Override
	public void write(E value, JsonGenerator generator) {
		if (value == null) {
			generator.writeNull();
			return;
		}

		final var context = IuSerializationContext.require(jsonb);
		if (!context.isEnumAsObject()) {
			generator.write(value.toString());
			return;
		}

		final var format = context.format();
		final var nameKey = JsonSerializer.formatPropertyName(JsonSerializer.NAME, format);
		final var properties = model().readable(format);
		final var nameProperty = nameProperty(properties, nameKey, format);

		context.enterBean(value);
		try {
			generator.writeStartObject();
			if (nameProperty == null || !nameProperty.write(value, generator, context))
				generator.write(nameKey, value.name());

			for (final var property : properties)
				if (property != nameProperty)
					property.write(value, generator, context);

			generator.writeEnd();
		} finally {
			context.exitBean(value);
		}
	}

	private static IuJsonbModel.Property nameProperty(IuJsonbModel.Property[] properties, String nameKey,
			IuJsonPropertyNameFormat format) {
		for (final var property : properties)
			if (nameKey.equals(property.jsonName(format)))
				return property;
		return null;
	}

}
