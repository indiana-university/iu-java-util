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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import iu.client.jsonb.IuJsonb.AdapterReference;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * One deserialization call: the options snapshot and property path from
 * {@link IuJsonbContext}, plus the
 * {@link jakarta.json.bind.serializer.JsonbDeserializer} invocations in
 * progress for re-entry checks.
 *
 * <p>
 * The call in progress is held by {@link IuJsonb} per thread, so adapters that
 * only see a {@link JsonParser}, or no parser at all, still convert as part of
 * it.
 * </p>
 */
final class IuDeserializationContext extends IuJsonbContext implements DeserializationContext {

	/**
	 * A deserializer in progress at a parser position, and the adapters already
	 * applied in the conversion it is part of.
	 */
	private static final class Frame {
		private final JsonbDeserializer<?> deserializer;
		private final JsonParser parser;
		private final long offset;
		private final List<AdapterReference> applied;

		private Frame(JsonbDeserializer<?> deserializer, JsonParser parser, long offset,
				List<AdapterReference> applied) {
			this.deserializer = deserializer;
			this.parser = parser;
			this.offset = offset;
			this.applied = applied;
		}
	}

	/**
	 * Gets the call in progress on this thread.
	 *
	 * @param jsonb provider
	 * @return call in progress; null if none
	 */
	static IuDeserializationContext current(IuJsonb jsonb) {
		return jsonb.deserialization.get();
	}

	/**
	 * Gets the call in progress on this thread, which an adapter delegated to by
	 * {@link IuJsonbValueAdapter} can rely on.
	 *
	 * @param jsonb provider
	 * @return call in progress
	 */
	static IuDeserializationContext require(IuJsonb jsonb) {
		return Objects.requireNonNull(current(jsonb), "no deserialization in progress");
	}

	/**
	 * Runs a conversion as a new call.
	 *
	 * @param <R>        result type
	 * @param jsonb      provider
	 * @param root       type converted by the call
	 * @param parser     parser the call reads from, for locating a failure; null
	 *                   if converting from a {@link jakarta.json.JsonValue}
	 * @param conversion conversion
	 * @return result
	 * @throws JsonbException describing where the call failed
	 */
	static <R> R start(IuJsonb jsonb, Type root, JsonParser parser,
			Function<IuDeserializationContext, R> conversion) {
		final var context = new IuDeserializationContext(jsonb, root);
		try {
			return context.within(conversion);
		} catch (RuntimeException e) {
			throw context.failed("read", e, parser == null ? null : parser.getLocation());
		}
	}

	private final Deque<Frame> deserializing = new ArrayDeque<>();

	private IuDeserializationContext(IuJsonb jsonb, Type root) {
		super(jsonb, root);
	}

	/**
	 * Runs a conversion with this call in progress on the current thread.
	 */
	private <R> R within(Function<IuDeserializationContext, R> conversion) {
		final var local = jsonb.deserialization;
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
	 * Marks a {@link jakarta.json.bind.serializer.JsonbDeserializer} as in
	 * progress at a parser position.
	 *
	 * <p>
	 * A deserializer that passes the parser back to this context before advancing
	 * it continues the conversion, with the next deserializer in the requested
	 * type's chain that isn't already in progress at that position, and finally
	 * the requested type's built-in conversion. A nested value at a later
	 * position starts a chain of its own. A parser that can't report its position
	 * reads as never advancing.
	 * </p>
	 *
	 * @param deserializer deserializer
	 * @param parser       parser, positioned at the value's first event
	 * @param offset       the parser's stream offset
	 * @param applied      adapters already applied in the conversion
	 * @return true if marked; false if the deserializer is already in progress on
	 *         the same parser at the same position
	 */
	boolean enterDeserializer(JsonbDeserializer<?> deserializer, JsonParser parser, long offset,
			List<AdapterReference> applied) {
		for (final var frame : deserializing)
			if (frame.parser == parser //
					&& frame.offset == offset //
					&& frame.deserializer == deserializer)
				return false;

		deserializing.push(new Frame(deserializer, parser, offset, applied));
		return true;
	}

	/**
	 * Unmarks the deserializer most recently marked by
	 * {@link #enterDeserializer(JsonbDeserializer, JsonParser, long, List)}.
	 */
	void exitDeserializer() {
		deserializing.pop();
	}

	/**
	 * Reads a value from a parser passed to this context, continuing its
	 * conversion if a deserializer is already in progress at the same position.
	 */
	private Object read(Type type, JsonParser parser) {
		final var offset = parser.getLocation().getStreamOffset();
		for (final var frame : deserializing)
			if (frame.parser == parser && frame.offset == offset)
				return jsonb.adapt(type).read(parser, this, frame.applied);
		return jsonb.adapt(type).read(parser, this, List.of());
	}

	@Override
	public <T> T deserialize(Class<T> clazz, JsonParser parser) {
		return deserialize((Type) clazz, parser);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T deserialize(Type type, JsonParser parser) {
		return within(c -> {
			// JsonbDeserializer implementations are documented to call from KEY_NAME
			final var event = parser.currentEvent();
			if (event != Event.KEY_NAME) {
				if (event == null)
					parser.next();
				return (T) read(type, parser);
			}

			final var key = parser.getString();
			push(key);
			try {
				parser.next();
				return (T) read(type, parser);
			} catch (RuntimeException e) {
				throw fail(e);
			} finally {
				pop();
			}
		});
	}

}
