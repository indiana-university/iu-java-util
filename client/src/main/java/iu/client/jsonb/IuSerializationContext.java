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

import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import iu.client.JsonProxy;
import iu.client.jsonb.IuJsonb.AdapterReference;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;

/**
 * One serialization call: the options snapshot and property path from
 * {@link IuJsonbContext}, plus the values in progress for re-entry and cycle
 * checks.
 *
 * <p>
 * The call in progress is held by {@link IuJsonb} per thread, so adapters that
 * only see a {@link JsonGenerator}, or no generator at all, still convert as
 * part of it.
 * </p>
 */
final class IuSerializationContext extends IuJsonbContext implements SerializationContext {

	/**
	 * Gets the call in progress on this thread.
	 *
	 * @param jsonb provider
	 * @return call in progress; null if none
	 */
	static IuSerializationContext current(IuJsonb jsonb) {
		return jsonb.serialization.get();
	}

	/**
	 * Gets the call in progress on this thread, which an adapter delegated to by
	 * {@link IuJsonbValueAdapter} can rely on.
	 *
	 * @param jsonb provider
	 * @return call in progress
	 */
	static IuSerializationContext require(IuJsonb jsonb) {
		return Objects.requireNonNull(current(jsonb), "no serialization in progress");
	}

	/**
	 * Runs a conversion as a new call.
	 *
	 * @param <R>        result type
	 * @param jsonb      provider
	 * @param root       type converted by the call
	 * @param conversion conversion
	 * @return result
	 * @throws JsonbException describing where the call failed
	 */
	static <R> R start(IuJsonb jsonb, Type root, Function<IuSerializationContext, R> conversion) {
		final var context = new IuSerializationContext(jsonb, root);
		try {
			return context.within(conversion);
		} catch (RuntimeException e) {
			throw context.failed("write", e, null);
		}
	}

	/**
	 * Determines if a value is a {@link JsonProxy} wrapper.
	 *
	 * @param value value
	 * @return true if value wraps a JsonObject
	 */
	static boolean isJsonProxy(Object value) {
		return Proxy.isProxyClass(value.getClass()) //
				&& Proxy.getInvocationHandler(value) instanceof JsonProxy;
	}

	/**
	 * Gets the type to convert a value as when no declared type is available.
	 *
	 * @param value value
	 * @return {@link Enum#getDeclaringClass()} for an enum constant, including one
	 *         with a class body; the wrapped interface for a {@link JsonProxy};
	 *         otherwise the value's class, or {@link Object} for null
	 */
	static Type runtimeType(Object value) {
		if (value == null)
			return Object.class;
		else if (value instanceof Enum)
			return ((Enum<?>) value).getDeclaringClass();
		else if (isJsonProxy(value))
			return value.getClass().getInterfaces()[0];
		else
			return value.getClass();
	}

	/**
	 * A serializer in progress for a value, and the conversion it is part of.
	 */
	private static final class Frame {
		private final JsonbSerializer<?> serializer;
		private final Object value;
		private final IuJsonbValueAdapter<?> owner;
		private final List<AdapterReference> applied;

		private Frame(JsonbSerializer<?> serializer, Object value, IuJsonbValueAdapter<?> owner,
				List<AdapterReference> applied) {
			this.serializer = serializer;
			this.value = value;
			this.owner = owner;
			this.applied = applied;
		}
	}

	private final Deque<Frame> serializing = new ArrayDeque<>();
	private Set<Object> beans;

	private IuSerializationContext(IuJsonb jsonb, Type root) {
		super(jsonb, root);
	}

	/**
	 * Runs a conversion with this call in progress on the current thread.
	 */
	private <R> R within(Function<IuSerializationContext, R> conversion) {
		final var local = jsonb.serialization;
		final var previous = local.get();
		if (previous == this)
			return conversion.apply(this);

		local.set(this);
		try {
			return conversion.apply(this);
		} finally {
			if (previous == null)
				local.remove();
			else
				local.set(previous);
		}
	}

	/**
	 * Marks a serializer as in progress for a value.
	 *
	 * <p>
	 * A serializer that passes the same value back to this context continues the
	 * conversion it is part of, with the next serializer in the chain that isn't
	 * already in progress for the value, and finally the built-in conversion for
	 * the type {@code owner} converts.
	 * </p>
	 *
	 * @param serializer serializer
	 * @param value      value; may be null
	 * @param owner      adapter for the declared type of the conversion
	 * @param applied    adapters already applied in the conversion
	 * @return true if marked; false if the serializer is already in progress for
	 *         the value
	 */
	boolean enterSerializer(JsonbSerializer<?> serializer, Object value, IuJsonbValueAdapter<?> owner,
			List<AdapterReference> applied) {
		for (final var frame : serializing)
			if (frame.serializer == serializer && frame.value == value)
				return false;
		serializing.push(new Frame(serializer, value, owner, applied));
		return true;
	}

	/**
	 * Unmarks the serializer most recently marked by
	 * {@link #enterSerializer(JsonbSerializer, Object, IuJsonbValueAdapter, List)}.
	 */
	void exitSerializer() {
		serializing.pop();
	}

	/**
	 * Writes a value passed to this context, continuing its conversion if a
	 * serializer is already in progress for it.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void write(Object value, JsonGenerator generator) {
		for (final var frame : serializing)
			if (frame.value == value) {
				((IuJsonbValueAdapter) frame.owner).write(value, generator, this, frame.applied);
				return;
			}
		jsonb.adapt(runtimeType(value)).write(value, generator);
	}

	/**
	 * Marks a value as in progress by the built-in business object or enum
	 * object conversion.
	 *
	 * @param value value
	 * @throws JsonbException if the value is already in progress
	 */
	void enterBean(Object value) {
		if (beans == null)
			beans = Collections.newSetFromMap(new IdentityHashMap<>());
		if (!beans.add(value))
			throw new JsonbException("recursive reference to " + value.getClass().getName());
	}

	/**
	 * Unmarks a value marked by {@link #enterBean(Object)}.
	 *
	 * @param value value
	 */
	void exitBean(Object value) {
		beans.remove(value);
	}

	@Override
	public <T> void serialize(String key, T object, JsonGenerator generator) {
		within(c -> {
			push(key);
			try {
				generator.writeKey(key);
				write(object, generator);
			} catch (RuntimeException e) {
				throw fail(e);
			} finally {
				pop();
			}
			return null;
		});
	}

	@Override
	public <T> void serialize(T object, JsonGenerator generator) {
		within(c -> {
			write(object, generator);
			return null;
		});
	}

}
