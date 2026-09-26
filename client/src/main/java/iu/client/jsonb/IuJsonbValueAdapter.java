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

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.client.IuJsonAdapter;
import iu.client.GenericTypes;
import iu.client.JsonAdapters;
import iu.client.jsonb.IuJsonb.AdapterReference;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * Converts one Java type according to {@link IuJsonb} configuration.
 *
 * <p>
 * Every nested type, including a collection item, map value, array component,
 * or {@link java.util.Optional} value, resolves back through
 * {@link IuJsonb#adapt(Type)}, so configuration applies at any depth in both
 * tree and streaming conversion. Precedence, for a value:
 * </p>
 * <ol>
 * <li>A configured {@link jakarta.json.bind.adapter.JsonbAdapter} converts to
 * and from its adapted type. Each adapter applies at most once to a value: the
 * adapted value converts through its type's components, less the adapters
 * already applied, so an adapter can't loop through its own output.</li>
 * <li>A configured {@link JsonbSerializer} or {@link JsonbDeserializer}.</li>
 * <li>The built-in conversion for the declared type: {@link IuJsonbEnumAdapter}
 * for an enum, {@link IuJsonbAdapter} for a non-platform business object,
 * otherwise {@link JsonAdapters#adapt(Type, java.util.function.Function)}.</li>
 * </ol>
 *
 * <p>
 * Components form chains, most specific first; see {@link IuJsonb}. Writing
 * selects components by the value's runtime type, so the order is the same
 * wherever the value is declared, while the built-in conversion stays with the
 * declared type. Writing uses the first adapter; reading uses the first adapter
 * whose adapted type accepts the shape of the JSON value (string, number,
 * boolean, object, or array), or the first adapter if none does, so adapters
 * can accept several formats. Components registered for {@link Object} are left
 * out of the chain for a {@link IuJsonb#isScalar(Type) scalar} type, null
 * included, and so for a value written with a scalar runtime type; they are
 * also left out when reading a JSON string, number, or boolean as any type.
 * </p>
 *
 * <p>
 * A serializer or deserializer passes control down its chain by passing the
 * same value, or the parser at the same position, back to its context: the
 * next component not already in progress for it runs, and then the built-in
 * conversion. Since a component registered for a supertype may read a value of
 * another subtype, what an adapter or deserializer reads is checked against
 * the type being read, and a value that doesn't fit fails the conversion.
 * </p>
 *
 * <p>
 * Null reaches user code like any other value: an adapter adapts it in both
 * directions, a serializer is given it, and a deserializer is given a parser at
 * {@link JsonParser.Event#VALUE_NULL VALUE_NULL}. An undefined value, converted
 * from a null {@link JsonValue} in tree mode, has no parser event, so it
 * bypasses deserializers.
 * </p>
 *
 * <p>
 * Each conversion joins the call in progress on the current thread, or starts
 * one, so a tree conversion nested in a streaming call shares its options
 * snapshot, property path, and cycle checks.
 * </p>
 *
 * @param <T> Java type
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
final class IuJsonbValueAdapter<T> implements IuJsonAdapter<T> {

	private final Type type;
	private final Class<?> erased;
	private final Class<?> readType;
	private final IuJsonb jsonb;
	private final List<AdapterReference> adapters;
	private final List<AdapterReference> scalarAdapters;
	private final List<JsonbSerializer> serializers;
	private final List<JsonbDeserializer> deserializers;
	private final List<JsonbDeserializer> scalarDeserializers;
	private final boolean runtimeDispatch;
	private volatile IuJsonAdapter builtIn;

	/**
	 * Resolves the components that apply to {@code type}.
	 *
	 * @param type  Java type
	 * @param jsonb provider
	 */
	IuJsonbValueAdapter(Type type, IuJsonb jsonb) {
		this.type = type;
		this.jsonb = jsonb;
		erased = JsonAdapters.erase(type);
		readType = (Class<?>) GenericTypes.box(erased);
		// Object components leave a scalar type alone, null included; a value of
		// any other type may still read from a JSON scalar, which they leave alone
		final var scalar = IuJsonb.isScalar(erased);
		adapters = jsonb.adapters(type, scalar);
		scalarAdapters = scalar ? adapters : jsonb.adapters(type, true);
		serializers = jsonb.serializers(type, scalar);
		deserializers = jsonb.deserializers(type, scalar);
		scalarDeserializers = scalar ? deserializers : jsonb.deserializers(type, true);

		// a value of a final type is always of that type
		runtimeDispatch = jsonb.hasWriteComponents() && !Modifier.isFinal(erased.getModifiers());
	}

	/**
	 * Determines if an adapted type reads a JSON value of a given shape.
	 *
	 * <p>
	 * Judged from the type alone: a number type reads numbers, a map or business
	 * object reads objects, an array or collection type reads arrays, an enum
	 * reads text or objects, and other platform types, such as text, dates, and
	 * URIs, read text. {@link Object} and JSON-P types read anything.
	 * </p>
	 *
	 * @param adapted adapted type
	 * @param shape   JSON value type
	 * @return true if {@code adapted} reads values of that shape
	 */
	static boolean accepts(Type adapted, ValueType shape) {
		final var c = (Class<?>) GenericTypes.box(JsonAdapters.erase(adapted));
		if (c == Object.class || JsonValue.class.isAssignableFrom(c))
			return true;
		if (c == Boolean.class)
			return shape == ValueType.TRUE || shape == ValueType.FALSE;
		if (Number.class.isAssignableFrom(c))
			return shape == ValueType.NUMBER;
		if (Map.class.isAssignableFrom(c))
			return shape == ValueType.OBJECT;
		if (c == byte[].class)
			return shape == ValueType.STRING;
		if (isArrayLike(c))
			return shape == ValueType.ARRAY;
		if (c.isEnum())
			return shape == ValueType.STRING || shape == ValueType.OBJECT;
		if (IuObject.isPlatformName(c.getName()))
			return shape == ValueType.STRING;
		return shape == ValueType.OBJECT;
	}

	private static boolean isArrayLike(Class<?> c) {
		return c.isArray() //
				|| Iterable.class.isAssignableFrom(c) //
				|| c == Iterator.class //
				|| c == Enumeration.class //
				|| c == Stream.class;
	}

	/**
	 * Gets the shape of the value a parser is positioned at.
	 *
	 * @param event parser's current event, a value's first event
	 * @return JSON value type
	 */
	static ValueType shape(Event event) {
		switch (event) {
		case START_OBJECT:
			return ValueType.OBJECT;

		case START_ARRAY:
			return ValueType.ARRAY;

		case VALUE_STRING:
			return ValueType.STRING;

		case VALUE_NUMBER:
			return ValueType.NUMBER;

		case VALUE_TRUE:
			return ValueType.TRUE;

		case VALUE_FALSE:
			return ValueType.FALSE;

		default: // VALUE_NULL
			return ValueType.NULL;
		}
	}

	/**
	 * Determines if a JSON value is scalar, so components registered for
	 * {@link Object} leave it alone.
	 *
	 * @param shape JSON value type; null if undefined
	 * @return true for a string, number, or boolean
	 */
	static boolean isScalar(ValueType shape) {
		return shape == ValueType.STRING //
				|| shape == ValueType.NUMBER //
				|| shape == ValueType.TRUE //
				|| shape == ValueType.FALSE;
	}

	private static List<AdapterReference> with(List<AdapterReference> applied, AdapterReference adapter) {
		final List<AdapterReference> next = new ArrayList<>(applied.size() + 1);
		next.addAll(applied);
		next.add(adapter);
		return next;
	}

	/**
	 * Resolves the built-in conversion on first use, so a self-referential type,
	 * or a collection of one, doesn't recurse while adapters are being created.
	 */
	private IuJsonAdapter builtIn() {
		var builtIn = this.builtIn;
		if (builtIn == null)
			this.builtIn = builtIn = resolveBuiltIn();
		return builtIn;
	}

	private IuJsonAdapter resolveBuiltIn() {
		if (erased.isEnum())
			return new IuJsonbEnumAdapter(erased, jsonb);

		if (!IuObject.isPlatformName(erased.getName()) //
				&& !erased.isPrimitive() //
				&& !erased.isArray())
			return new IuJsonbAdapter<>(erased, jsonb);

		return JsonAdapters.adapt(type, jsonb::adapt);
	}

	/**
	 * Gets the adapter whose components apply to a value being written: this
	 * one, or the one for the value's runtime type.
	 */
	private IuJsonbValueAdapter<?> components(Object value) {
		if (value == null || !runtimeDispatch)
			return this;

		final var runtimeType = IuSerializationContext.runtimeType(value);
		if (runtimeType == erased)
			return this;
		else
			return jsonb.adapt(runtimeType);
	}

	private static AdapterReference writeAdapter(List<AdapterReference> adapters, List<AdapterReference> applied) {
		for (final var adapter : adapters)
			if (!applied.contains(adapter))
				return adapter;
		return null;
	}

	private static AdapterReference readAdapter(List<AdapterReference> adapters, ValueType shape,
			List<AdapterReference> applied) {
		AdapterReference first = null;
		for (final var adapter : adapters)
			if (!applied.contains(adapter)) {
				if (shape == null || shape == ValueType.NULL || accepts(adapter.adapted, shape))
					return adapter;
				if (first == null)
					first = adapter;
			}
		return first;
	}

	private Object adaptToJson(AdapterReference adapter, Object value) {
		try {
			return adapter.adapter.adaptToJson(value);
		} catch (Exception e) {
			throw new JsonbException("adapter failed to convert " + type + " to JSON", e);
		}
	}

	private T adaptFromJson(AdapterReference adapter, Object adapted) {
		final Object value;
		try {
			value = adapter.adapter.adaptFromJson(adapted);
		} catch (Exception e) {
			throw new JsonbException("adapter failed to convert " + type + " from JSON", e);
		}
		return fits(value, "adapter for " + adapter.original.getTypeName());
	}

	private T deserialize(JsonbDeserializer deserializer, JsonParser parser, IuDeserializationContext context) {
		return fits(deserializer.deserialize(parser, context, type), "deserializer " + deserializer.getClass().getName());
	}

	/**
	 * Checks that a value read by user code is a value of this type.
	 *
	 * @param value     value read
	 * @param component names the component that read it, for messages
	 * @return value
	 * @throws JsonbException if the value is not null and not an instance of this
	 *                        type
	 */
	private T fits(Object value, String component) {
		if (value == null || readType.isInstance(value))
			return (T) value;
		throw new JsonbException(
				component + " read " + value.getClass().getName() + ", which is not a " + type.getTypeName());
	}

	@Override
	public T fromJson(JsonValue value) {
		final var context = IuDeserializationContext.current(jsonb);
		if (context == null)
			return IuDeserializationContext.start(jsonb, type, null, c -> fromJson(value, c, List.of()));
		else
			return fromJson(value, context, List.of());
	}

	/**
	 * Converts a JSON value as part of the call in progress.
	 *
	 * @param value   JSON value; null if undefined
	 * @param context call in progress
	 * @param applied adapters already applied to the value
	 * @return Java value
	 */
	T fromJson(JsonValue value, IuDeserializationContext context, List<AdapterReference> applied) {
		if (adapters.isEmpty() && deserializers.isEmpty())
			return (T) builtIn().fromJson(value);

		final var shape = value == null ? null : value.getValueType();
		final var scalar = isScalar(shape);
		final var adapter = readAdapter(scalar ? scalarAdapters : adapters, shape, applied);
		if (adapter != null)
			return adaptFromJson(adapter,
					jsonb.adapt(adapter.adapted).fromJson(value, context, with(applied, adapter)));

		final var deserializers = scalar ? scalarDeserializers : this.deserializers;
		if (!deserializers.isEmpty() && value != null) {
			final var parser = new IuJsonbParser(value);
			parser.next();
			// a new parser has nothing in progress, so the chain starts at the top
			final var deserializer = deserializers.get(0);
			context.enterDeserializer(deserializer, parser, parser.getLocation().getStreamOffset(), applied);
			try {
				return deserialize(deserializer, parser, context);
			} finally {
				context.exitDeserializer();
			}
		}

		return (T) builtIn().fromJson(value);
	}

	@Override
	public T read(JsonParser parser) {
		final var context = IuDeserializationContext.current(jsonb);
		if (context == null)
			return IuDeserializationContext.start(jsonb, type, parser, c -> read(parser, c, List.of()));
		else
			return read(parser, context, List.of());
	}

	/**
	 * Reads a value as part of the call in progress.
	 *
	 * @param parser  parser, positioned at the value's first event
	 * @param context call in progress
	 * @param applied adapters already applied to the value
	 * @return Java value
	 */
	T read(JsonParser parser, IuDeserializationContext context, List<AdapterReference> applied) {
		if (adapters.isEmpty() && deserializers.isEmpty())
			return (T) builtIn().read(parser);

		final var shape = shape(parser.currentEvent());
		final var scalar = isScalar(shape);
		final var adapter = readAdapter(scalar ? scalarAdapters : adapters, shape, applied);
		if (adapter != null)
			return adaptFromJson(adapter, jsonb.adapt(adapter.adapted).read(parser, context, with(applied, adapter)));

		final var deserializers = scalar ? scalarDeserializers : this.deserializers;
		if (!deserializers.isEmpty()) {
			final var offset = parser.getLocation().getStreamOffset();
			for (final var deserializer : deserializers)
				if (context.enterDeserializer(deserializer, parser, offset, applied))
					try {
						return deserialize(deserializer, parser, context);
					} finally {
						context.exitDeserializer();
					}
		}

		return (T) builtIn().read(parser);
	}

	@Override
	public JsonValue toJson(T value) {
		final var context = IuSerializationContext.current(jsonb);
		if (context == null)
			return IuSerializationContext.start(jsonb, type, c -> toJson(value, c, List.of()));
		else
			return toJson(value, context, List.of());
	}

	/**
	 * Converts a value as part of the call in progress.
	 *
	 * @param value   value
	 * @param context call in progress
	 * @param applied adapters already applied to the value
	 * @return JSON value
	 */
	JsonValue toJson(Object value, IuSerializationContext context, List<AdapterReference> applied) {
		final var components = components(value);
		final var adapter = writeAdapter(components.adapters, applied);
		if (adapter != null)
			return jsonb.adapt(adapter.adapted).toJson(adaptToJson(adapter, value), context, with(applied, adapter));

		for (final var serializer : components.serializers)
			if (context.enterSerializer(serializer, value, this, applied)) {
				final var writer = new IuJsonbWriter();
				try {
					serializer.serialize(value, new IuJsonbGenerator(writer, jsonb.provider()), context);
				} finally {
					context.exitSerializer();
				}
				return IuIterable.single(writer);
			}

		return builtIn().toJson(value);
	}

	@Override
	public void write(T value, JsonGenerator generator) {
		final var context = IuSerializationContext.current(jsonb);
		if (context == null)
			IuSerializationContext.start(jsonb, type, c -> {
				write(value, generator, c, List.of());
				return null;
			});
		else
			write(value, generator, context, List.of());
	}

	/**
	 * Writes a value as part of the call in progress.
	 *
	 * @param value     value
	 * @param generator generator
	 * @param context   call in progress
	 * @param applied   adapters already applied to the value
	 */
	void write(Object value, JsonGenerator generator, IuSerializationContext context,
			List<AdapterReference> applied) {
		final var components = components(value);
		final var adapter = writeAdapter(components.adapters, applied);
		if (adapter != null) {
			jsonb.adapt(adapter.adapted).write(adaptToJson(adapter, value), generator, context, with(applied, adapter));
			return;
		}

		for (final var serializer : components.serializers)
			if (context.enterSerializer(serializer, value, this, applied))
				try {
					serializer.serialize(value, generator, context);
					return;
				} finally {
					context.exitSerializer();
				}

		builtIn().write(value, generator);
	}

	/**
	 * Converts a null property value when null properties are omitted.
	 *
	 * <p>
	 * Only an adapter is consulted. When it adapts the null to a value, that value
	 * converts through the adapted type; when it adapts the null to null, the
	 * adapted type's own adapters are consulted in turn.
	 * </p>
	 *
	 * @param context call in progress
	 * @param applied adapters already applied
	 * @return JSON value to include; null to omit the property
	 */
	JsonValue nullProperty(IuSerializationContext context, List<AdapterReference> applied) {
		final var adapter = writeAdapter(adapters, applied);
		if (adapter == null)
			return null;

		final var adapted = adaptToJson(adapter, null);
		final var next = jsonb.adapt(adapter.adapted);
		if (adapted == null)
			return next.nullProperty(context, with(applied, adapter));
		else
			return next.toJson(adapted, context, with(applied, adapter));
	}

	/**
	 * Writes a null property value when null properties are omitted, as
	 * {@link #nullProperty(IuSerializationContext, List)} converts it.
	 *
	 * @param name      JSON property name
	 * @param generator generator, in an object context
	 * @param context   call in progress
	 * @param applied   adapters already applied
	 * @return true if written; false if omitted
	 */
	boolean writeNullProperty(String name, JsonGenerator generator, IuSerializationContext context,
			List<AdapterReference> applied) {
		final var adapter = writeAdapter(adapters, applied);
		if (adapter == null)
			return false;

		final var adapted = adaptToJson(adapter, null);
		final var next = jsonb.adapt(adapter.adapted);
		if (adapted == null)
			return next.writeNullProperty(name, generator, context, with(applied, adapter));

		generator.writeKey(name);
		next.write(adapted, generator, context, with(applied, adapter));
		return true;
	}

}
