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

import java.math.BigDecimal;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import iu.client.ScopedParser;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;

/**
 * The view of a parser a {@link jakarta.json.bind.serializer.JsonbDeserializer}
 * reads through, limited to the value it deserializes.
 *
 * <p>
 * Created at the value's first event. {@link #hasNext()} turns false once the
 * value ends, so a deserializer can loop until then, as the
 * {@link jakarta.json.bind.serializer.JsonbDeserializer} documentation shows;
 * a scalar value is its only event. Structure methods act only within the
 * value. After the deserializer returns, {@link #release()} skips whatever it
 * left unread, so the conversion in control of the parser continues after the
 * value however much of it the deserializer read.
 * </p>
 *
 * <p>
 * The view doesn't close the parser it reads from, which the conversion in
 * control of it still needs.
 * </p>
 */
final class IuJsonbBoundedParser implements ScopedParser {

	/**
	 * Gets the parser a view reads from, through any views nested in it.
	 *
	 * @param parser parser or view
	 * @return underlying parser
	 */
	static JsonParser underlying(JsonParser parser) {
		while (parser instanceof IuJsonbBoundedParser)
			parser = ((IuJsonbBoundedParser) parser).parser;
		return parser;
	}

	private final JsonParser parser;
	private final IuDeserializationContext context;
	private final Deque<Event> open = new ArrayDeque<>();
	private final List<Runnable> releaseHooks = new ArrayList<>(0);

	/**
	 * Constructor.
	 *
	 * @param parser  parser, at the first event of the value to bound the view to
	 * @param context call in control of the parser, which may continue an object
	 *                from the text it reads; null if none
	 */
	IuJsonbBoundedParser(JsonParser parser, IuDeserializationContext context) {
		this.parser = parser;
		this.context = context;
		final var event = parser.currentEvent();
		if (event == Event.START_OBJECT //
				|| event == Event.START_ARRAY)
			open.push(event);
	}

	@Override
	public boolean isTree() {
		return underlying(this) instanceof IuJsonbParser;
	}

	@Override
	public JsonParser continuation() {
		if (context == null)
			return null;
		else
			return context.continuation(parser);
	}

	/**
	 * Registers work to run before {@link #release()} skips what's unread, such
	 * as capturing the rest of an object a reader holds.
	 */
	@Override
	public void beforeRelease(Runnable hook) {
		releaseHooks.add(hook);
	}

	/**
	 * Runs the release hooks, then skips the rest of the value, leaving the
	 * parser at its last event.
	 */
	void release() {
		for (final var hook : releaseHooks)
			hook.run();
		releaseHooks.clear();

		while (!open.isEmpty())
			if (open.pop() == Event.START_ARRAY)
				parser.skipArray();
			else
				parser.skipObject();
	}

	@Override
	public boolean hasNext() {
		return !open.isEmpty();
	}

	@Override
	public Event next() {
		if (open.isEmpty())
			throw new NoSuchElementException();

		final var event = parser.next();
		switch (event) {
		case START_OBJECT:
		case START_ARRAY:
			open.push(event);
			break;

		case END_OBJECT:
		case END_ARRAY:
			open.pop();
			break;

		default:
			break;
		}
		return event;
	}

	@Override
	public Event currentEvent() {
		return parser.currentEvent();
	}

	@Override
	public String getString() {
		return parser.getString();
	}

	@Override
	public boolean isIntegralNumber() {
		return parser.isIntegralNumber();
	}

	@Override
	public int getInt() {
		return parser.getInt();
	}

	@Override
	public long getLong() {
		return parser.getLong();
	}

	@Override
	public BigDecimal getBigDecimal() {
		return parser.getBigDecimal();
	}

	@Override
	public JsonLocation getLocation() {
		return parser.getLocation();
	}

	@Override
	public JsonObject getObject() {
		final var object = parser.getObject();
		open.pop();
		return object;
	}

	@Override
	public JsonArray getArray() {
		final var array = parser.getArray();
		open.pop();
		return array;
	}

	@Override
	public JsonValue getValue() {
		final var event = parser.currentEvent();
		if (event == Event.START_OBJECT)
			return getObject();
		else if (event == Event.START_ARRAY)
			return getArray();
		else
			return parser.getValue();
	}

	/**
	 * Reads the next item of a stream by advancing the view.
	 *
	 * @param <T> item type
	 */
	@FunctionalInterface
	private interface Advance<T> {
		/**
		 * Reads the next item.
		 *
		 * @param action receives the item
		 * @return false if there are no more items
		 */
		boolean tryAdvance(Consumer<? super T> action);
	}

	/**
	 * Streams items read lazily by advancing this view, so its bounds hold.
	 */
	private <T> Stream<T> stream(Advance<T> advance) {
		return StreamSupport.stream(new AbstractSpliterator<T>(Long.MAX_VALUE, Spliterator.ORDERED) {
			@Override
			public boolean tryAdvance(Consumer<? super T> action) {
				return advance.tryAdvance(action);
			}
		}, false);
	}

	@Override
	public Stream<JsonValue> getArrayStream() {
		if (parser.currentEvent() != Event.START_ARRAY)
			throw new IllegalStateException("getArrayStream() not valid at " + parser.currentEvent());
		return stream(action -> {
			if (next() == Event.END_ARRAY)
				return false;
			action.accept(getValue());
			return true;
		});
	}

	@Override
	public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
		if (parser.currentEvent() != Event.START_OBJECT)
			throw new IllegalStateException("getObjectStream() not valid at " + parser.currentEvent());
		return stream(action -> {
			if (next() == Event.END_OBJECT)
				return false;
			final var name = getString();
			next();
			action.accept(new SimpleImmutableEntry<>(name, getValue()));
			return true;
		});
	}

	/**
	 * Fails: a deserializer reads one value, never a sequence of top-level
	 * values.
	 */
	@Override
	public Stream<JsonValue> getValueStream() {
		throw new IllegalStateException("getValueStream() not valid within a value");
	}

	@Override
	public void skipArray() {
		if (open.peek() == Event.START_ARRAY) {
			parser.skipArray();
			open.pop();
		}
	}

	@Override
	public void skipObject() {
		if (open.peek() == Event.START_OBJECT) {
			parser.skipObject();
			open.pop();
		}
	}

	/**
	 * Does nothing: the conversion in control of the parser closes it.
	 */
	@Override
	public void close() {
	}

}
