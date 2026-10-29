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
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;

/**
 * Reports a {@link JsonValue} as {@link JsonParser} events, so a
 * {@link jakarta.json.bind.serializer.JsonbDeserializer} can run in a tree
 * conversion.
 *
 * <p>
 * Walks the value in place, as JSON-P's own parser over a
 * {@link jakarta.json.JsonStructure} does, and like that parser reports an
 * unknown {@link #getLocation() location}, since a value has no source text.
 * {@link #position()} identifies the parser state instead. For the same
 * reason, {@link #getString()} gives a number as the canonical text of its
 * value, such as {@code 2.5E+3} for {@code 2.5e3}.
 * </p>
 */
final class IuJsonbParser implements JsonParser {

	private static final JsonLocation UNKNOWN = new JsonLocation() {
		@Override
		public long getLineNumber() {
			return -1;
		}

		@Override
		public long getColumnNumber() {
			return -1;
		}

		@Override
		public long getStreamOffset() {
			return -1;
		}
	};

	/**
	 * An array or object the parser is in.
	 */
	private static final class Context {
		private final JsonValue value;
		private final Iterator<JsonValue> values;
		private final Iterator<Entry<String, JsonValue>> entries;
		private JsonValue pending;

		private Context(JsonArray array) {
			value = array;
			values = array.iterator();
			entries = null;
		}

		private Context(JsonObject object) {
			value = object;
			values = null;
			entries = object.entrySet().iterator();
		}
	}

	private final JsonValue root;
	private final JsonProvider provider;
	private final Deque<Context> contexts = new ArrayDeque<>();
	private Event event;
	private JsonValue value;
	private String key;
	private boolean done;
	private long position;

	/**
	 * Constructor.
	 *
	 * @param root     value to report
	 * @param provider creates the value of a {@link Event#KEY_NAME KEY_NAME}
	 */
	IuJsonbParser(JsonValue root, JsonProvider provider) {
		this.root = root;
		this.provider = provider;
	}

	/**
	 * Gets the number of events the parser has advanced through, which
	 * identifies its state.
	 *
	 * @return position; 0 before the first event
	 */
	long position() {
		return position;
	}

	@Override
	public boolean hasNext() {
		return !done;
	}

	@Override
	public Event next() {
		if (done)
			throw new NoSuchElementException();
		position++;

		final var context = contexts.peek();
		if (context == null)
			start(root);
		else if (context.entries == null)
			if (context.values.hasNext())
				start(context.values.next());
			else
				end(Event.END_ARRAY);
		else if (context.pending != null) {
			final var pending = context.pending;
			context.pending = null;
			start(pending);
		} else if (context.entries.hasNext()) {
			final var entry = context.entries.next();
			event = Event.KEY_NAME;
			key = entry.getKey();
			value = null;
			context.pending = entry.getValue();
		} else
			end(Event.END_OBJECT);

		return event;
	}

	/**
	 * Enters a value.
	 */
	private void start(JsonValue value) {
		this.value = value;
		switch (value.getValueType()) {
		case OBJECT:
			event = Event.START_OBJECT;
			contexts.push(new Context(value.asJsonObject()));
			return;

		case ARRAY:
			event = Event.START_ARRAY;
			contexts.push(new Context(value.asJsonArray()));
			return;

		case STRING:
			event = Event.VALUE_STRING;
			break;

		case NUMBER:
			event = Event.VALUE_NUMBER;
			break;

		case TRUE:
			event = Event.VALUE_TRUE;
			break;

		case FALSE:
			event = Event.VALUE_FALSE;
			break;

		default: // NULL
			event = Event.VALUE_NULL;
			break;
		}
		done = contexts.isEmpty();
	}

	/**
	 * Leaves the innermost array or object.
	 */
	private void end(Event end) {
		event = end;
		value = contexts.pop().value;
		done = contexts.isEmpty();
	}

	/**
	 * Leaves the innermost array or object without reading the rest of it.
	 */
	private void skip(Event end) {
		position++;
		end(end);
	}

	private IllegalStateException illegal(String method) {
		return new IllegalStateException(method + " not valid at " + event);
	}

	@Override
	public Event currentEvent() {
		return event;
	}

	@Override
	public String getString() {
		if (event == Event.KEY_NAME)
			return key;
		else if (event == Event.VALUE_STRING)
			return ((JsonString) value).getString();
		else if (event == Event.VALUE_NUMBER)
			return value.toString();
		else
			throw illegal("getString()");
	}

	private JsonNumber number(String method) {
		if (event == Event.VALUE_NUMBER)
			return (JsonNumber) value;
		else
			throw illegal(method);
	}

	@Override
	public boolean isIntegralNumber() {
		return number("isIntegralNumber()").isIntegral();
	}

	@Override
	public int getInt() {
		return number("getInt()").intValue();
	}

	@Override
	public long getLong() {
		return number("getLong()").longValue();
	}

	@Override
	public BigDecimal getBigDecimal() {
		return number("getBigDecimal()").bigDecimalValue();
	}

	@Override
	public JsonLocation getLocation() {
		return UNKNOWN;
	}

	@Override
	public JsonObject getObject() {
		if (event != Event.START_OBJECT)
			throw illegal("getObject()");
		skip(Event.END_OBJECT);
		return value.asJsonObject();
	}

	@Override
	public JsonArray getArray() {
		if (event != Event.START_ARRAY)
			throw illegal("getArray()");
		skip(Event.END_ARRAY);
		return value.asJsonArray();
	}

	@Override
	public JsonValue getValue() {
		if (event == null)
			throw illegal("getValue()");

		switch (event) {
		case START_OBJECT:
			return getObject();

		case START_ARRAY:
			return getArray();

		case KEY_NAME:
			return provider.createValue(key);

		case END_OBJECT:
		case END_ARRAY:
			throw illegal("getValue()");

		default:
			return value;
		}
	}

	/**
	 * Reads the next item of a stream by advancing the parser.
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
	 * Streams items read lazily by advancing this parser.
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
		if (event != Event.START_ARRAY)
			throw illegal("getArrayStream()");
		return stream(action -> {
			if (next() == Event.END_ARRAY)
				return false;
			action.accept(getValue());
			return true;
		});
	}

	@Override
	public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
		if (event != Event.START_OBJECT)
			throw illegal("getObjectStream()");
		return stream(action -> {
			if (next() == Event.END_OBJECT)
				return false;
			final var name = key;
			next();
			action.accept(new SimpleImmutableEntry<>(name, getValue()));
			return true;
		});
	}

	@Override
	public Stream<JsonValue> getValueStream() {
		if (!contexts.isEmpty())
			throw illegal("getValueStream()");
		return stream(action -> {
			if (done)
				return false;
			next();
			action.accept(getValue());
			return true;
		});
	}

	@Override
	public void skipArray() {
		final var context = contexts.peek();
		if (context != null && context.entries == null)
			skip(Event.END_ARRAY);
	}

	@Override
	public void skipObject() {
		final var context = contexts.peek();
		if (context != null && context.entries != null)
			skip(Event.END_OBJECT);
	}

	/**
	 * Does nothing, since the parser holds no resources.
	 */
	@Override
	public void close() {
	}

}
