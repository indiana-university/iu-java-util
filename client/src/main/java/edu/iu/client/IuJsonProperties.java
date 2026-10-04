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
package edu.iu.client;

import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.iu.GenericTypes;
import edu.iu.TypeValue;
import iu.client.ConversionScope;
import iu.client.JsonAdapters;
import iu.client.JsonProxy;
import iu.client.ScopedParser;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * Indexes the properties of one JSON object, converting each to Java only when
 * it's read.
 *
 * <p>
 * A property resolves from the first of three places that has it:
 * </p>
 * <ol>
 * <li>Values already resolved, which a read returns directly.</li>
 * <li>Raw {@link JsonValue}s, from a {@link JsonObject} or captured from a
 * parser, which convert on first read.</li>
 * <li>A parser inside the object. A read pulls forward: the property it asks
 * for converts straight from the parser, with no tree built, and properties
 * passed on the way are captured raw, needing no type and no conversion.</li>
 * </ol>
 *
 * <p>
 * An index reading from a parser must {@link #detach()} before whatever
 * controls the parser moves on; within a JSON-B deserialization by the IU
 * JSON-B provider, the provider detaches it. Detaching from a parser over text
 * the provider holds keeps reading the rest of the object from that text, in
 * place, and releases the text once the object is read through; otherwise the
 * rest is captured raw. Until detached, an index reading from a parser belongs
 * to the parser's thread; otherwise it is thread-safe.
 * </p>
 *
 * <p>
 * A property the reader never asks for is kept raw and written back unchanged;
 * {@link #requireOnly(Set)} checks for unexpected ones. The index is immutable:
 * {@link #builder()} creates one from Java values, and
 * {@link #with(String, Object)} copies one with a value replaced.
 * </p>
 *
 * <p>
 * An index converts by the conversions it's created with, or, created without
 * them, by the first of:
 * </p>
 * <ol>
 * <li>the IU JSON-B provider's call in progress when it converts, so values an
 * application indexes and passes to {@link jakarta.json.bind.Jsonb#toJson}, or
 * writes from a serializer, convert by that call's configuration;</li>
 * <li>the provider's call in progress when the index was created, so an index a
 * deserializer creates converts that way after the call returns;</li>
 * <li>the JSON-B instance given to {@link #builder(Jsonb)}, if built with one;
 * else the IU conversions, as
 * {@link IuJsonAdapter#adapt(Type, java.util.function.Supplier)} converts with
 * {@link IuJsonSerializationOptions#DEFAULT}.</li>
 * </ol>
 *
 * <p>
 * An index is itself a value both the IU conversions and the IU JSON-B provider
 * convert, with nothing to register: written as the object it indexes, and read
 * as an index of the object, as a top-level value, a property, or an item. Read
 * by {@link IuJsonAdapter#adapt(Type, java.util.function.Supplier)}, it
 * converts as that adapter's value types do; read by JSON-B, as described
 * above.
 * </p>
 *
 * <p>
 * The same index may so convert differently in different calls. A serializer
 * running under any JSON-B provider can write an index through the call's
 * context with
 * {@link #write(JsonGenerator, jakarta.json.bind.serializer.SerializationContext)}.
 * </p>
 */
public final class IuJsonProperties {

	/**
	 * Resolved null.
	 */
	private static final Object NULL = new Object();

	/**
	 * A property not in the object.
	 */
	private static final Object ABSENT = new Object();

	private static final TypeValue<IuJsonAdapter<?>> DEFAULT_ADAPTERS = new TypeValue<IuJsonAdapter<?>>() {
		@Override
		protected IuJsonAdapter<?> computeValue(Type type) {
			return IuJsonAdapter.adapt(type, () -> IuJsonSerializationOptions.DEFAULT);
		}
	};

	/**
	 * The IU conversions with default options, for an index converting with no
	 * JSON-B call to take conversions from.
	 */
	private static final Function<Type, IuJsonAdapter<?>> DEFAULTS = DEFAULT_ADAPTERS::get;

	/**
	 * Builds an index from Java values.
	 */
	public static final class Builder {
		private final Function<Type, IuJsonAdapter<?>> adapt;
		private final Function<Type, IuJsonAdapter<?>> captured;
		private final Function<Type, IuJsonAdapter<?>> fallback;
		private final List<String> names = new ArrayList<>();
		private final Map<String, Object> values = new ConcurrentHashMap<>();
		private final Map<String, Type> types = new ConcurrentHashMap<>();
		private final Map<String, JsonValue> raw = new ConcurrentHashMap<>();

		private Builder(Function<Type, IuJsonAdapter<?>> adapt, Function<Type, IuJsonAdapter<?>> captured,
				Function<Type, IuJsonAdapter<?>> fallback) {
			this.adapt = adapt;
			this.captured = captured;
			this.fallback = fallback;
		}

		private void name(String name) {
			if (!names.contains(Objects.requireNonNull(name, "name")))
				names.add(name);
			values.remove(name);
			types.remove(name);
			raw.remove(name);
		}

		/**
		 * Gets previously provided property.
		 *
		 * @param name JSON property name
		 * @return value provided via put, putJson, or putAll
		 */
		public Object get(String name) {
			if (values.containsKey(name))
				return unbox(values.get(name));
			else
				return raw.get(name);
		}

		/**
		 * Sets a property to a Java value, converted as its runtime type.
		 *
		 * @param name  JSON property name
		 * @param value value; null for a null property, written only where the call
		 *              writes null properties, as {@link #putJson(String, JsonValue)}
		 *              with {@link JsonValue#NULL} always is
		 * @return this
		 */
		public Builder put(String name, Object value) {
			return put(name, value, runtimeType(value));
		}

		/**
		 * Sets a property to a Java value.
		 *
		 * @param name  JSON property name
		 * @param value value; null for a null property, written only where the call
		 *              writes null properties, as {@link #putJson(String, JsonValue)}
		 *              with {@link JsonValue#NULL} always is
		 * @param type  type the value converts as
		 * @return this
		 */
		public Builder put(String name, Object value, Type type) {
			name(name);
			values.put(name, box(value));
			types.put(name, Objects.requireNonNull(type, "type"));
			return this;
		}

		/**
		 * Sets a property to a JSON value, converted to Java when read.
		 *
		 * @param name  JSON property name
		 * @param value JSON value
		 * @return this
		 */
		public Builder putJson(String name, JsonValue value) {
			name(name);
			raw.put(name, Objects.requireNonNull(value, "value"));
			return this;
		}

		/**
		 * Copies every property of an index, in order, reading it through first.
		 *
		 * @param properties index
		 * @return this
		 */
		public Builder putAll(IuJsonProperties properties) {
			// JSON as it was read, where there is some, so it writes back unchanged
			for (final var name : properties.names()) {
				final var json = properties.raw(name);
				if (json != null)
					putJson(name, json);
				else
					put(name, unbox(properties.resolved.get(name)), properties.types.get(name));
			}
			return this;
		}

		/**
		 * Determines if values have been provided.
		 * 
		 * @return true if no values have been provided, either java or raw JSON; false
		 *         if values have been provided
		 */
		public boolean isEmpty() {
			return values.isEmpty() && raw.isEmpty();
		}

		/**
		 * Copy this builder from its current state.
		 * 
		 * @return a copy of this builder that can change independently
		 */
		public Builder copy() {
			final var copy = new Builder(adapt, captured, fallback);
			copy.names.addAll(names);
			copy.raw.putAll(raw);
			copy.values.putAll(values);
			copy.types.putAll(types);
			return copy;
		}

		/**
		 * Builds the index.
		 *
		 * @return {@link IuJsonProperties}
		 */
		public IuJsonProperties build() {
			return new IuJsonProperties(this);
		}
	}

	/**
	 * Indexes a JSON object, converting as the JSON-B call in progress converts.
	 *
	 * @param object JSON object, referenced rather than copied
	 * @return {@link IuJsonProperties}
	 * @see IuJsonProperties
	 */
	public static IuJsonProperties of(JsonObject object) {
		return new IuJsonProperties(Objects.requireNonNull(object, "object"), null, null, DEFAULTS);
	}

	/**
	 * Indexes a JSON object, converting as the JSON-B call in progress converts,
	 * and otherwise as a JSON-B instance does.
	 *
	 * @param object JSON object, referenced rather than copied
	 * @param jsonb  JSON-B instance to convert as when no call is in progress
	 * @return {@link IuJsonProperties}
	 * @see #builder(Jsonb)
	 */
	@SuppressWarnings("exports")
	public static IuJsonProperties of(JsonObject object, Jsonb jsonb) {
		return new IuJsonProperties(Objects.requireNonNull(object, "object"), null, null,
				iu.client.jsonb.IuJsonb.adapters(jsonb));
	}

	/**
	 * Indexes an object as a parser reads it, converting as the JSON-B call in
	 * progress converts, and otherwise as a JSON-B instance does.
	 *
	 * @param parser parser, at the object's {@code START_OBJECT}
	 * @param jsonb  JSON-B instance to convert as when no call is in progress
	 * @return {@link IuJsonProperties}
	 * @throws IllegalArgumentException if the parser isn't at an object
	 * @see #builder(Jsonb)
	 */
	@SuppressWarnings("exports")
	public static IuJsonProperties read(JsonParser parser, Jsonb jsonb) {
		return index(parser, null, iu.client.jsonb.IuJsonb.adapters(jsonb));
	}

	/**
	 * Indexes a JSON object.
	 *
	 * @param object JSON object, referenced rather than copied
	 * @param adapt  gets the conversion for a type
	 * @return {@link IuJsonProperties}
	 */
	public static IuJsonProperties of(JsonObject object, Function<Type, IuJsonAdapter<?>> adapt) {
		return new IuJsonProperties(Objects.requireNonNull(object, "object"), null,
				Objects.requireNonNull(adapt, "adapt"), DEFAULTS);
	}

	/**
	 * Indexes an object as a parser reads it, converting as the JSON-B call in
	 * progress converts.
	 *
	 * @param parser parser, at the object's {@code START_OBJECT}
	 * @return {@link IuJsonProperties}
	 * @throws IllegalArgumentException if the parser isn't at an object
	 * @see #read(JsonParser, Function)
	 */
	public static IuJsonProperties read(JsonParser parser) {
		return index(parser, null, DEFAULTS);
	}

	/**
	 * Indexes an object as a parser reads it.
	 *
	 * <p>
	 * An object already in memory, as in a JSON-B tree conversion, is referenced
	 * rather than read. Otherwise the index reads from the parser, which it leaves
	 * between properties; call {@link #detach()} before advancing the parser past
	 * the object, unless the parser is one the IU JSON-B provider gave a
	 * deserializer, which detaches the index itself.
	 * </p>
	 *
	 * @param parser parser, at the object's {@code START_OBJECT}
	 * @param adapt  gets the conversion for a type
	 * @return {@link IuJsonProperties}
	 * @throws IllegalArgumentException if the parser isn't at an object
	 */
	public static IuJsonProperties read(JsonParser parser, Function<Type, IuJsonAdapter<?>> adapt) {
		return index(parser, Objects.requireNonNull(adapt, "adapt"), DEFAULTS);
	}

	private static IuJsonProperties index(JsonParser parser, Function<Type, IuJsonAdapter<?>> adapt,
			Function<Type, IuJsonAdapter<?>> fallback) {
		final var event = parser.currentEvent();
		if (event != Event.START_OBJECT)
			throw JsonAdapters.expected("an object", event);

		if (parser instanceof ScopedParser) {
			final var scoped = (ScopedParser) parser;
			if (scoped.isTree())
				return new IuJsonProperties(parser.getObject(), null, adapt, fallback);

			final var properties = new IuJsonProperties(null, parser, adapt, fallback);
			scoped.beforeRelease(properties::detach);
			return properties;
		} else
			return new IuJsonProperties(null, parser, adapt, fallback);
	}

	/**
	 * Indexes an object as a JSON-B deserializer reads it, converting with the
	 * conversions of the provider running the deserialization.
	 *
	 * @param parser  parser the deserializer was given, at the object's
	 *                {@code START_OBJECT}
	 * @param context deserialization context
	 * @return {@link IuJsonProperties}
	 * @throws IllegalArgumentException if the parser isn't at an object, or the
	 *                                  context isn't from the IU JSON-B provider
	 */
	@SuppressWarnings("exports")
	public static IuJsonProperties deserialize(JsonParser parser, DeserializationContext context) {
		return read(parser, iu.client.jsonb.IuJsonb.adapters(context));
	}

	/**
	 * Starts building an index from Java values, converting as the JSON-B call in
	 * progress converts.
	 *
	 * @return {@link Builder}
	 * @see IuJsonProperties
	 */
	public static Builder builder() {
		return new Builder(null, ConversionScope.current(), DEFAULTS);
	}

	/**
	 * Starts building an index from Java values, converting as the JSON-B call in
	 * progress converts, and otherwise as a JSON-B instance does.
	 *
	 * <p>
	 * Where no call is in progress, as when a value put as one type is read as
	 * another, as {@code BigInteger} put and {@code byte[]} read, the instance's
	 * configuration applies, its adapters included, in place of the IU defaults. An
	 * instance of the IU JSON-B provider converts directly; one of another provider
	 * converts through JSON text.
	 * </p>
	 *
	 * @param jsonb JSON-B instance to convert as when no call is in progress
	 * @return {@link Builder}
	 * @see IuJsonProperties
	 */
	@SuppressWarnings("exports")
	public static Builder builder(Jsonb jsonb) {
		return new Builder(null, ConversionScope.current(), iu.client.jsonb.IuJsonb.adapters(jsonb));
	}

	/**
	 * Starts building an index from Java values.
	 *
	 * @param adapt gets the conversion for a type
	 * @return {@link Builder}
	 */
	public static Builder builder(Function<Type, IuJsonAdapter<?>> adapt) {
		return new Builder(Objects.requireNonNull(adapt, "adapt"), null, DEFAULTS);
	}

	private final Function<Type, IuJsonAdapter<?>> adapt;
	private final Function<Type, IuJsonAdapter<?>> captured;
	private final Function<Type, IuJsonAdapter<?>> fallback;
	private final JsonObject source;
	private final Map<String, Object> resolved = new ConcurrentHashMap<>();
	private final Map<String, Type> types = new ConcurrentHashMap<>();
	private final Map<String, JsonValue> raw = new ConcurrentHashMap<>();
	private final Set<String> names;
	private JsonParser parser;
	private boolean owned;
	private String pending;
	private volatile JsonObject json;

	/**
	 * Constructor.
	 *
	 * @param source object indexed; null if reading from a parser
	 * @param parser parser reading the object; null if indexing an object
	 * @param adapt    conversions; null for the call's in progress
	 * @param fallback conversions when unbound and no call applies
	 */
	private IuJsonProperties(JsonObject source, JsonParser parser, Function<Type, IuJsonAdapter<?>> adapt,
			Function<Type, IuJsonAdapter<?>> fallback) {
		this.adapt = adapt;
		captured = adapt == null ? ConversionScope.current() : null;
		this.fallback = fallback;
		this.source = source;
		this.parser = parser;
		this.names = source == null ? new LinkedHashSet<>() : null;
	}

	private IuJsonProperties(Builder builder) {
		adapt = builder.adapt;
		captured = builder.captured;
		fallback = builder.fallback;
		source = null;
		names = new LinkedHashSet<>(builder.names);
		resolved.putAll(builder.values);
		types.putAll(builder.types);
		raw.putAll(builder.raw);
	}

	private static Object box(Object value) {
		return value == null ? NULL : value;
	}

	private static Object unbox(Object value) {
		return value == NULL ? null : value;
	}

	private static Type runtimeType(Object value) {
		if (value == null)
			return Object.class;
		else if (value instanceof Enum)
			return ((Enum<?>) value).getDeclaringClass();
		else if (Proxy.isProxyClass(value.getClass()) //
				&& Proxy.getInvocationHandler(value) instanceof JsonProxy)
			return value.getClass().getInterfaces()[0];
		else
			return value.getClass();
	}

	/**
	 * Gets the conversion for a type: by the conversions the index is bound to;
	 * else those of the JSON-B call in progress; else those of the call in progress
	 * when the index was created; else its fallback: the JSON-B instance it was
	 * built with, or the IU defaults.
	 */
	@SuppressWarnings("unchecked")
	private IuJsonAdapter<Object> adapter(Type type) {
		var conversions = adapt;
		if (conversions == null) {
			conversions = ConversionScope.current();
			if (conversions == null)
				conversions = captured == null ? fallback : captured;
		}
		return (IuJsonAdapter<Object>) conversions.apply(type);
	}

	private JsonValue raw(String name) {
		if (source != null)
			return source.get(name);
		else
			return raw.get(name);
	}

	/**
	 * Reads forward to a property.
	 *
	 * @param name name to stop at; null to read to the end of the object
	 * @param type type to convert the property it stops at to, straight from the
	 *             parser; null to capture it raw
	 * @return converted value, boxed; {@link #ABSENT} if the property wasn't
	 *         reached, or was captured raw
	 */
	private Object pull(String name, Type type) {
		while (parser != null) {
			final String key;
			if (pending != null) {
				key = pending;
				pending = null;
			} else if (parser.next() != Event.KEY_NAME) {
				finish();
				return ABSENT;
			} else {
				key = parser.getString();
				names.add(key);
			}

			parser.next();
			final Object value;
			if (key.equals(name) && type != null) {
				value = box(adapter(type).read(parser));
				resolved.put(key, value);
				types.put(key, type);
			} else {
				raw.put(key, parser.getValue());
				value = ABSENT;
			}

			if (owned)
				lookAhead();

			if (key.equals(name))
				return value;
		}
		return ABSENT;
	}

	/**
	 * Advances an owned continuation to the next property, or releases it at the
	 * end of the object, so the text it reads is released as soon as the last
	 * property is.
	 */
	private void lookAhead() {
		if (parser.next() == Event.KEY_NAME) {
			pending = parser.getString();
			names.add(pending);
		} else
			finish();
	}

	/**
	 * Releases the parser at the end of the object, and with an owned continuation
	 * the text it reads.
	 */
	private void finish() {
		if (owned)
			parser.close();
		parser = null;
		owned = false;
	}

	/**
	 * Determines if the object has a property.
	 *
	 * @param name JSON property name
	 * @return true if present, with any value, null included
	 */
	public boolean containsKey(String name) {
		if (source != null)
			return source.containsKey(name);

		synchronized (this) {
			if (!names.contains(name))
				pull(name, null);
			return names.contains(name);
		}
	}

	/**
	 * Reads a property.
	 *
	 * @param <T>  Java type
	 * @param name JSON property name
	 * @param type type to convert the property to
	 * @return converted value; for a property not in the object, with no conversion
	 *         looked up whatever the type, a primitive's default, an empty
	 *         optional, or null
	 */
	public <T> T get(String name, Class<T> type) {
		return get(name, (Type) type);
	}

	/**
	 * Reads a property.
	 *
	 * @param <T>  Java type
	 * @param name JSON property name
	 * @param type type to convert the property to
	 * @return converted value; for a property not in the object, with no conversion
	 *         looked up whatever the type, a primitive's default, an empty
	 *         optional, or null
	 */
	@SuppressWarnings("unchecked")
	public <T> T get(String name, Type type) {
		var value = resolved.get(name);
		if (value == null)
			synchronized (this) {
				value = resolved.get(name);
				if (value == null)
					value = resolve(name, type);
			}
		return (T) fit(name, unbox(value), type);
	}

	private Object resolve(String name, Type type) {
		var json = raw(name);
		if (json == null && parser != null) {
			final var pulled = pull(name, type);
			if (pulled != ABSENT)
				return pulled;
			json = raw(name);
		}

		// not in the object: nothing to convert, so no conversion is looked up
		if (json == null)
			return box(JsonAdapters.undefined(type));

		final var value = box(adapter(type).fromJson(json));
		resolved.put(name, value);
		types.put(name, type);
		return value;
	}

	/**
	 * Converts a resolved value to the type asked for, if it was resolved as
	 * another.
	 */
	private Object fit(String name, Object value, Type type) {
		if (value == null //
				|| ((Class<?>) GenericTypes.box(GenericTypes.erase(type))).isInstance(value))
			return value;
		else
			return adapter(type).fromJson(adapter(types.get(name)).toJson(value));
	}

	/**
	 * Reads the object through, then gets its property names.
	 *
	 * @return property names, in the order the object has them
	 */
	public Set<String> names() {
		if (source != null)
			return source.keySet();

		synchronized (this) {
			pull(null, null);
			return Collections.unmodifiableSet(new LinkedHashSet<>(names));
		}
	}

	/**
	 * Reads the object through, then gets its property names.
	 *
	 * @return property names, in the order the object has them
	 */
	public Set<String> nonNullNames() {
		return names().stream().filter(name -> !NULL.equals(resolved.get(name))).collect(Collectors.toSet());
	}

	/**
	 * Checks that the object has no property but those expected, reading it
	 * through.
	 *
	 * @param expected names of the properties expected
	 * @return this
	 * @throws IllegalArgumentException naming any unexpected properties
	 */
	public IuJsonProperties requireOnly(Set<String> expected) {
		final List<String> unexpected = new ArrayList<>();
		for (final var name : names())
			if (!expected.contains(name))
				unexpected.add(name);
		if (!unexpected.isEmpty())
			throw new IllegalArgumentException("unexpected properties " + unexpected);
		return this;
	}

	/**
	 * Copies the index with a property set to a Java value, converted as its
	 * runtime type.
	 *
	 * @param name  JSON property name
	 * @param value value; null for a null property, written only where the call
	 *              writes null properties
	 * @return new index
	 */
	public IuJsonProperties with(String name, Object value) {
		return new Builder(adapt, captured, fallback).putAll(this).put(name, value).build();
	}

	/**
	 * Releases the parser the index reads from, so whatever controls it can move
	 * on.
	 *
	 * <p>
	 * From a parser over text the IU JSON-B provider holds, the index keeps reading
	 * the rest of the object from that text, in place, releasing it once the object
	 * is read through. From any other parser it captures the rest raw now. Either
	 * way the parser is left at the object's {@code END_OBJECT}, or where a
	 * provider can skip to it. Does nothing if already detached.
	 * </p>
	 */
	public synchronized void detach() {
		if (parser == null //
				|| owned)
			return;

		if (parser instanceof ScopedParser) {
			final var continuation = ((ScopedParser) parser).continuation();
			if (continuation != null) {
				parser = continuation;
				owned = true;
				lookAhead();
				return;
			}
		}

		pull(null, null);
	}

	/**
	 * Gets the object as JSON, reading it through.
	 *
	 * @return the object indexed if it came from one; otherwise a new object with
	 *         each property in order, generated once
	 */
	public JsonObject toJsonObject() {
		if (source != null)
			return source;

		var json = this.json;
		if (json == null)
			synchronized (this) {
				json = this.json;
				if (json == null) {
					pull(null, null);
					final var builder = IuJson.object();
					final var includeNulls = ConversionScope.isIncludeNullProperties();
					for (final var name : names) {
						final var raw = this.raw.get(name);
						final var value = unbox(resolved.get(name));
						if (raw != null)
							builder.add(name, raw);
						else if (value != null)
							builder.add(name, adapter(types.get(name)).toJson(value));
						else if (includeNulls)
							builder.addNull(name);
					}
					this.json = json = builder.build();
				}
			}
		return json;
	}

	/**
	 * Writes the object, reading it through, without building it as a
	 * {@link JsonObject} first.
	 *
	 * @param generator generator, where a value is expected
	 */
	public void write(JsonGenerator generator) {
		if (source != null) {
			generator.write(source);
			return;
		}

		final List<String> names;
		synchronized (this) {
			pull(null, null);
			names = new ArrayList<>(this.names);
		}

		final var includeNulls = ConversionScope.isIncludeNullProperties();
		generator.writeStartObject();
		for (final var name : names) {
			final var json = raw.get(name);
			final var value = unbox(resolved.get(name));
			if (json != null)
				generator.write(name, json);
			else if (value != null) {
				generator.writeKey(name);
				adapter(types.get(name)).write(value, generator);
			} else if (includeNulls)
				generator.writeNull(name);
		}
		generator.writeEnd();
	}

	/**
	 * Writes the object from a JSON-B serializer, converting each Java value
	 * through the serialization context, as its runtime type, so the call's
	 * configuration applies with any JSON-B provider.
	 *
	 * <p>
	 * A property read as JSON writes back unchanged; one set to a Java null is
	 * written as the context writes a null property, so omitted unless the call
	 * writes null properties. The index is read through first.
	 * </p>
	 *
	 * @param generator generator the serializer was given, where a value is
	 *                  expected
	 * @param context   serialization context
	 */
	@SuppressWarnings("exports")
	public void write(JsonGenerator generator, SerializationContext context) {
		if (source != null) {
			generator.write(source);
			return;
		}

		final List<String> names;
		synchronized (this) {
			pull(null, null);
			names = new ArrayList<>(this.names);
		}

		generator.writeStartObject();
		for (final var name : names) {
			final var json = raw.get(name);
			if (json != null)
				generator.write(name, json);
			else {
				final var value = unbox(resolved.get(name));
				context.serialize(name, value, generator);
			}
		}
		generator.writeEnd();
	}

	@Override
	public String toString() {
		return toJsonObject().toString();
	}

}
