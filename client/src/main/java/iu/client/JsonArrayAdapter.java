/*
 * Copyright © 2024 Indiana University
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

import java.util.Iterator;

import edu.iu.IuIterable;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonArray;
import jakarta.json.JsonValue;

/**
 * Adapts to/from {@link JsonArray} values.
 * 
 * @param <T> target type
 * @param <E> element type
 */
abstract class JsonArrayAdapter<T, E> implements IuJsonAdapter<T> {

	/**
	 * Extracts an iterator from a Java value.
	 * 
	 * @param value value
	 * @return iterator
	 */
	abstract protected Iterator<E> iterator(T value);

	/**
	 * Collects items into the target type.
	 * 
	 * @param items items
	 * @return target value
	 */
	abstract protected T collect(Iterator<E> items);

	private final IuJsonAdapter<E> itemAdapter;

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
		else if (itemAdapter != null)
			return collect(IuIterable.map(jsonValue.asJsonArray(), itemAdapter::fromJson).iterator());
		else
			return collect(IuIterable.map(jsonValue.asJsonArray(), IuJsonAdapter.<E>basic()::fromJson).iterator());
	}

	@Override
	public JsonValue toJson(T javaValue) {
		if (javaValue == null)
			return JsonValue.NULL;

		final var a = IuJson.array();
		iterator(javaValue).forEachRemaining(i -> {
			if (itemAdapter == null)
				a.add(IuJsonAdapter.of(i).toJson(i));
			else
				a.add(itemAdapter.toJson(i));
		});
		return a.build();
	}

}
