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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Stream;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

/**
 * Reports a {@link JsonValue} as {@link JsonParser} events, so a
 * {@link jakarta.json.bind.serializer.JsonbDeserializer} can run in a tree
 * conversion.
 */
public class IuJsonbParser implements JsonParser {

	private static class State {
		private String name;
		private JsonValue value;
		private Event event;

		private State(String name) {
			this.name = name;
		}

		private State(JsonValue value) {
			this.value = value;
		}

		private State(Event event) {
			this.event = event;
		}

		private Event event() {
			if (name != null)
				return Event.KEY_NAME;
			else if (value instanceof JsonArray)
				return Event.START_ARRAY;
			else if (value instanceof JsonObject)
				return Event.START_OBJECT;
			else if (value instanceof JsonString)
				return Event.VALUE_STRING;
			else if (value instanceof JsonNumber)
				return Event.VALUE_NUMBER;
			else if (value.equals(JsonValue.TRUE))
				return Event.VALUE_TRUE;
			else if (value.equals(JsonValue.FALSE))
				return Event.VALUE_FALSE;
			else if (value.equals(JsonValue.NULL))
				return Event.VALUE_NULL;
			else
				return event;
		}

		private JsonValue value() {
			return Objects.requireNonNull(value, "unexpected " + event());
		}
	}

	private static class Location implements JsonLocation {
		// tracks "pretty-printed" parse position without printing
		// 2-space indent, LF line endings

		private long line;
		private long column;
		private long streamOffset;

		@Override
		public long getLineNumber() {
			return line;
		}

		@Override
		public long getColumnNumber() {
			return column;
		}

		@Override
		public long getStreamOffset() {
			return streamOffset;
		}

		private void advance(long n) {
			column += n;
			streamOffset += n;
		}

		private void advance(String name) {
			final var len = name.length();
			advance(len + 2L); // + 2 quotes
			for (var i = 0; i < len; i++) {
				final var c = name.charAt(i);
				if (c == '\\' || c == '\"')
					advance(1L); // esc char
			}
		}

		private void line(long d) {
			column = 0;
			line++;
			streamOffset += d * 2 + 1;
		}
	}

	private final JsonValue value;
	private Deque<State> stack = new ArrayDeque<>();
	private Location location = new Location();
	private State current;
	private int depth;

	/**
	 * Constructor.
	 *
	 * @param value value to report
	 */
	public IuJsonbParser(JsonValue value) {
		this.value = value;
	}

	private State current() {
		return Objects.requireNonNull(current, "before start");
	}

	@Override
	public boolean hasNext() {
		return value != null && (current == null || !stack.isEmpty());
	}

	@Override
	public Event next() {
		if (value == null)
			throw new JsonParsingException(null, location);

		if (current == null) {
			final var init = new State(value);
			stack.push(init);
			current = init;
		} else {
			final var next = stack.pop();

			if (next.value instanceof JsonObject) {
				final var o = next.value.asJsonObject();
				stack.push(new State(Event.END_OBJECT));

				final Deque<Map.Entry<String, JsonValue>> entries = new ArrayDeque<>();
				o.entrySet().forEach(entries::push);
				for (final var e : entries) {
					stack.push(new State(e.getValue()));
					stack.push(new State(e.getKey()));
				}

				location.advance(1);
				location.line(++depth);

			} else if (next.value instanceof JsonArray) {
				final var a = next.value.asJsonArray();
				stack.push(new State(Event.END_ARRAY));

				final var size = a.size();
				for (var i = 0; i < size; i++)
					stack.push(new State(a.get(size - (i + 1))));

				location.advance(1);
				location.line(++depth);
			} else if (next.value != null) {
				location.advance(next.value.toString().length());

				final var n = stack.peek();
				if (n == null || Event.END_ARRAY.equals(n.event) || Event.END_OBJECT.equals(n.event))
					location.line(Long.max(0, depth - 1));
				else {
					location.advance(1L); // comma
					location.line(depth);
				}

			} else if (next.name != null) {
				location.advance(next.name);
				location.advance(2L); // colon + space

			} else if (Event.END_ARRAY.equals(next.event) || Event.END_OBJECT.equals(next.event)) {
				location.advance(1L); // close brace or bracket

				final var n = stack.peek();
				if (n != null && (Event.END_ARRAY.equals(n.event) || Event.END_OBJECT.equals(n.event)))
					location.advance(1L); // comma

				location.line(--depth);
			}

			current = next;
		}

		return currentEvent();
	}

	@Override
	public String getString() {
		final var current = current();
		switch (current.event()) {
		case KEY_NAME:
			return current.name;

		case VALUE_NUMBER:
		case VALUE_STRING:
			return current.value().toString();

		default:
			throw new JsonParsingException("expected name, string, or number", location);
		}
	}

	@Override
	public boolean isIntegralNumber() {
		final var value = current().value();
		return (value instanceof JsonNumber) && ((JsonNumber) value).isIntegral();
	}

	@Override
	public int getInt() {
		return ((JsonNumber) current().value()).intValue();
	}

	@Override
	public long getLong() {
		return ((JsonNumber) current().value()).longValue();
	}

	@Override
	public BigDecimal getBigDecimal() {
		return ((JsonNumber) current().value()).bigDecimalValue();
	}

	@Override
	public JsonLocation getLocation() {
		return location;
	}

	@Override
	public Event currentEvent() {
		return current().event();
	}

	@Override
	public JsonObject getObject() {
		return getValue().asJsonObject();
	}

	@Override
	public JsonValue getValue() {
		return current().value();
	}

	@Override
	public JsonArray getArray() {
		return getValue().asJsonArray();
	}

	@Override
	public Stream<JsonValue> getArrayStream() {
		return getArray().stream();
	}

	@Override
	public Stream<Entry<String, JsonValue>> getObjectStream() {
		return getObject().entrySet().stream();
	}

	@Override
	public Stream<JsonValue> getValueStream() {
		final var value = getValue();
		if (value instanceof JsonArray)
			return getArrayStream();
		else if (value instanceof JsonObject)
			return getObject().values().stream();
		else
			return Stream.of(value);
	}

	private void skipTo(Event eventToReach) {
		var depth = 0;
		var count = 0;

		boolean found = false;
		scan: for (final var s : stack) {
			count++;

			final var event = s.event();
			switch (event) {
			case START_ARRAY:
			case START_OBJECT:
				depth++;
				break;

			case END_ARRAY:
			case END_OBJECT:
				if (depth > 0) {
					depth--;
					break;
				}

			default:
				if (event.equals(eventToReach)) {
					found = true;
					break scan;
				}
			}
		}

		if (!found)
			return;
		else
			for (var i = 0; i < count; i++)
				stack.pop();
	}

	@Override
	public void skipArray() {
		skipTo(Event.END_ARRAY);
	}

	@Override
	public void skipObject() {
		skipTo(Event.END_OBJECT);
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub

	}

}
