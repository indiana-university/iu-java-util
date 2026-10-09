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
package iu.client;

import java.util.ArrayList;
import java.util.List;

import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonArrayAdapter;
import jakarta.json.JsonArray;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * Adapts to/from {@link JsonArray} values.
 *
 * <p>
 * Each item converts within the adapter's {@link ItemScope}, so a failure can
 * name its position.
 * </p>
 * 
 * @param <T> target type
 * @param <E> element type
 */
abstract class JsonArrayAdapter<T, E> implements IuJsonArrayAdapter<T, E> {

	private final IuJsonAdapter<E> itemAdapter;

	/**
	 * Tracks the item converting; set by {@link JsonAdapters} before the adapter
	 * is shared.
	 */
	ItemScope scope = ItemScope.NONE;

	/**
	 * Constructor
	 * 
	 * @param itemAdapter item adapter
	 */
	protected JsonArrayAdapter(IuJsonAdapter<E> itemAdapter) {
		this.itemAdapter = itemAdapter;
	}

	@Override
	public T fromJson(JsonValue jsonValue) {
		if (jsonValue == null //
				|| JsonValue.NULL.equals(jsonValue))
			return null;
		else if (jsonValue instanceof JsonArray) {
			final var index = new int[1];
			return collect(IuIterable.map(jsonValue.asJsonArray(), item -> fromJson(index[0]++, item)));
		} else
			throw JsonAdapters.expected("an array", jsonValue.getValueType());
	}

	private E fromJson(int index, JsonValue item) {
		scope.enterIndex(false, index);
		try {
			return itemAdapter.fromJson(item);
		} catch (RuntimeException e) {
			throw scope.fail(false, e);
		} finally {
			scope.exit(false);
		}
	}

	@Override
	public JsonValue toJson(T javaValue) {
		if (javaValue == null)
			return JsonValue.NULL;

		final var a = IuJson.array();
		final var items = iterator(javaValue);
		for (var index = 0; items.hasNext(); index++) {
			final var item = items.next();
			scope.enterIndex(true, index);
			try {
				a.add(itemAdapter.toJson(item));
			} catch (RuntimeException e) {
				throw scope.fail(true, e);
			} finally {
				scope.exit(true);
			}
		}
		return a.build();
	}

	/**
	 * Reads items as the parser reaches them, collected eagerly since the parser
	 * moves on.
	 */
	@Override
	public T read(JsonParser parser) {
		final var event = parser.currentEvent();
		if (event == Event.VALUE_NULL)
			return null;
		if (event != Event.START_ARRAY)
			throw JsonAdapters.expected("an array", event);

		final List<E> items = new ArrayList<>();
		for (var index = 0; parser.next() != Event.END_ARRAY; index++) {
			scope.enterIndex(false, index);
			try {
				items.add(itemAdapter.read(parser));
			} catch (RuntimeException e) {
				throw scope.fail(false, e);
			} finally {
				scope.exit(false);
			}
		}
		return collect(items);
	}

	@Override
	public void write(T javaValue, JsonGenerator generator) {
		if (javaValue == null) {
			generator.writeNull();
			return;
		}

		generator.writeStartArray();
		final var items = iterator(javaValue);
		for (var index = 0; items.hasNext(); index++) {
			final var item = items.next();
			scope.enterIndex(true, index);
			try {
				itemAdapter.write(item, generator);
			} catch (RuntimeException e) {
				throw scope.fail(true, e);
			} finally {
				scope.exit(true);
			}
		}
		generator.writeEnd();
	}

}
