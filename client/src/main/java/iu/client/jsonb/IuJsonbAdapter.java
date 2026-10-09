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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import edu.iu.GenericTypes;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonProperties;
import iu.client.BindingMetadata;
import iu.client.JsonProxy;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
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

	private final Type declared;
	private final Class<T> type;
	private final IuJsonb jsonb;
	private volatile IuJsonbModel model;

	/**
	 * Constructor; the model is introspected on first use.
	 *
	 * @param type  business object type, or a parameterized type of one, whose
	 *              arguments its property types resolve against
	 * @param jsonb provider
	 */
	@SuppressWarnings("unchecked")
	IuJsonbAdapter(Type type, IuJsonb jsonb) {
		this.declared = type;
		this.type = (Class<T>) GenericTypes.erase(type);
		this.jsonb = jsonb;
	}

	private IuJsonbModel model() {
		var model = this.model;
		if (model == null)
			this.model = model = jsonb.model(declared);
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
		final var naming = context.naming();
		final var object = value.asJsonObject();

		// type information picks the subtype
		final var dispatch = model().dispatch();
		if (dispatch != null) {
			final var alias = object.get(dispatch.key());
			if (alias != null) {
				if (!(alias instanceof JsonString))
					throw new JsonbException(
							"expected an alias for " + dispatch.key() + ", found " + alias.getValueType());
				final var subtype = model().subtype(((JsonString) alias).getString());
				if (subtype != type)
					return (T) jsonb.adapt(subtype).fromJson(object);
			}
		}

		if (type.isInterface())
			return JsonProxy.wrap(IuJsonProperties.of(object, jsonb::adapt), type, naming::name,
					naming.ignoresCase(), jsonb::declared);

		final var model = model();
		final var creator = model.creator();
		if (creator != null) {
			final var arguments = new Object[creator.size()];
			final var read = new boolean[arguments.length];
			final List<Assignment> assignments = new ArrayList<>();
			for (final var entry : object.entrySet()) {
				final var key = entry.getKey();
				final var index = creator.index(naming, key);
				if (index >= 0) {
					arguments[index] = creator.fromJson(index, key, entry.getValue(), context);
					read[index] = true;
				} else {
					final var property = model.writable(naming, key);
					if (property != null)
						assignments.add(new Assignment(property, key, property.fromJson(key, entry.getValue(), context)));
				}
			}
			return create(creator, arguments, read, assignments, context);
		}

		final var bean = (T) model.newInstance();
		for (final var entry : object.entrySet()) {
			final var key = entry.getKey();
			final var property = model.writable(naming, key);
			if (property != null)
				property.fromJson(bean, key, entry.getValue(), context);
		}
		return bean;
	}

	@Override
	public T read(JsonParser parser) {
		var event = parser.currentEvent();
		if (event == Event.VALUE_NULL)
			return null;
		if (event != Event.START_OBJECT)
			throw new JsonbException("expected START_OBJECT for " + type.getName() + ", found " + event);

		final var context = IuDeserializationContext.require(jsonb);
		final var dispatch = model().dispatch();
		if (dispatch != null)
			return readPolymorphic(parser, context, dispatch);
		else
			return readObject(parser, context);
	}

	/**
	 * Reads an object whose type information picks the subtype to read it as.
	 *
	 * <p>
	 * The key comes first, as written, so the subtype usually reads the rest of
	 * the object straight from the parser. Where the key doesn't come first, or
	 * the subtype has components of its own or is an interface, the object is
	 * read through and converted from the tree.
	 * </p>
	 *
	 * @param parser   parser, between the object's properties
	 * @param context  call in progress
	 * @param dispatch type information
	 * @return value
	 */
	@SuppressWarnings("unchecked")
	private T readPolymorphic(JsonParser parser, IuDeserializationContext context, BindingMetadata.TypeInfo dispatch) {
		var event = parser.next();
		final var object = jsonb.provider().createObjectBuilder();
		if (event == Event.KEY_NAME && dispatch.key().equals(parser.getString())) {
			event = parser.next();
			if (event != Event.VALUE_STRING)
				throw new JsonbException("expected an alias for " + dispatch.key() + ", found " + event);
			final var alias = parser.getString();
			final var subtype = model().subtype(alias);
			if (subtype == type)
				return readObject(parser, context);

			final var subtypeAdapter = jsonb.adapt(subtype);
			if (!subtype.isInterface() && subtypeAdapter.isBuiltInRead())
				return (T) ((IuJsonbAdapter<?>) subtypeAdapter.builtInAdapter()).continueRead(parser, context);

			object.add(dispatch.key(), alias);
			event = parser.next();
		}

		// read through, then convert from the tree
		while (event != Event.END_OBJECT) {
			final var key = parser.getString();
			parser.next();
			object.add(key, parser.getValue());
			event = parser.next();
		}
		return fromJson(object.build());
	}

	/**
	 * Reads the rest of an object, whose type information another type read.
	 *
	 * @param parser  parser, between the object's properties
	 * @param context call in progress
	 * @return value
	 */
	private T continueRead(JsonParser parser, IuDeserializationContext context) {
		// a subtype always has type information; its own may pick a further one
		final var dispatch = model().dispatch();
		if (dispatch.type() == type)
			return readPolymorphic(parser, context, dispatch);
		else
			return readObject(parser, context);
	}

	/**
	 * Reads an object as this type.
	 *
	 * @param parser  parser, at the object's {@code START_OBJECT}, or between its
	 *                properties unless this type is an interface
	 * @param context call in progress
	 * @return value
	 */
	@SuppressWarnings("unchecked")
	private T readObject(JsonParser parser, IuDeserializationContext context) {
		Event event;
		final var naming = context.naming();
		if (type.isInterface()) {
			// the proxy converts each property when its getter is first called: from
			// the text this call reads, in place, when there is some; otherwise from
			// the tree, or from the rest captured raw as the parser moves on
			final var view = new IuJsonbBoundedParser(parser, context);
			final var properties = IuJsonProperties.read(view, jsonb::adapt);
			view.release();
			return JsonProxy.wrap(properties, type, naming::name, naming.ignoresCase(), jsonb::declared);
		}

		final var model = model();
		final var creator = model.creator();
		if (creator != null) {
			// values are held until the object is read through, then the creator
			// runs and the other properties are set
			final var arguments = new Object[creator.size()];
			final var read = new boolean[arguments.length];
			final List<Assignment> assignments = new ArrayList<>();
			while (parser.next() != Event.END_OBJECT) {
				final var key = parser.getString();
				event = parser.next();
				final var index = creator.index(naming, key);
				if (index >= 0) {
					arguments[index] = creator.read(index, key, parser, context);
					read[index] = true;
				} else {
					final var property = model.writable(naming, key);
					if (property != null)
						assignments.add(new Assignment(property, key, property.read(key, parser, context)));
					else
						skip(parser, event);
				}
			}
			return create(creator, arguments, read, assignments, context);
		}

		final var bean = (T) model.newInstance();
		while (parser.next() != Event.END_OBJECT) {
			final var key = parser.getString();
			final var property = model.writable(naming, key);
			event = parser.next();
			if (property != null)
				property.read(bean, key, parser, context);
			else
				skip(parser, event);
		}
		return bean;
	}

	/**
	 * Skips a value no property reads.
	 */
	private static void skip(JsonParser parser, Event event) {
		if (event == Event.START_OBJECT)
			parser.skipObject();
		else if (event == Event.START_ARRAY)
			parser.skipArray();
	}

	/**
	 * A property value read before its bean exists.
	 */
	private static final class Assignment {
		private final IuJsonbModel.Property property;
		private final String key;
		private final Object value;

		private Assignment(IuJsonbModel.Property property, String key, Object value) {
			this.property = property;
			this.key = key;
			this.value = value;
		}
	}

	/**
	 * Creates a bean, then sets the properties read before it existed.
	 */
	@SuppressWarnings("unchecked")
	private T create(IuJsonbModel.Creator creator, Object[] arguments, boolean[] read,
			List<Assignment> assignments, IuDeserializationContext context) {
		final var bean = (T) creator.create(arguments, read);
		for (final var assignment : assignments)
			assignment.property.set(bean, assignment.key, assignment.value, context);
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
			final var model = model();
			final var properties = model.readable(context.naming());
			final var builder = jsonb.provider().createObjectBuilder();
			// type information first, outermost first
			for (final var typeKey : model.typeKeys().entrySet())
				builder.add(typeKey.getKey(), typeKey.getValue());
			for (final var property : properties)
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
			final var model = model();
			final var properties = model.readable(context.naming());
			generator.writeStartObject();
			for (final var typeKey : model.typeKeys().entrySet())
				generator.write(typeKey.getKey(), typeKey.getValue());
			for (final var property : properties)
				property.write(value, generator, context);
			generator.writeEnd();
		} finally {
			context.exitBean(value);
		}
	}

}
