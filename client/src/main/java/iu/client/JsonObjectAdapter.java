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

import java.util.Map;
import java.util.function.Supplier;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Adapts to/from {@link JsonObject} values.
 * 
 * @param <T> target type
 * @param <V> value type
 * @param <K> key type
 */
class JsonObjectAdapter<T extends Map<K, V>, K, V> implements IuJsonAdapter<T> {

	private final IuJsonAdapter<V> valueAdapter;
	private final IuJsonAdapter<K> keyAdapter;
	private final Supplier<T> factory;

	/**
	 * Constructor
	 * 
	 * @param keyAdapter   key adapter
	 * @param valueAdapter value adapter
	 * @param factory      supplies a new map instance
	 */
	protected JsonObjectAdapter(IuJsonAdapter<K> keyAdapter, IuJsonAdapter<V> valueAdapter, Supplier<T> factory) {
		this.valueAdapter = valueAdapter;
		this.keyAdapter = keyAdapter;
		this.factory = factory;
	}

	@Override
	public T fromJson(JsonValue jsonValue) {
		if (jsonValue == null //
				|| JsonValue.NULL.equals(jsonValue))
			return null;

		final var map = factory.get();
		for (final var e : jsonValue.asJsonObject().entrySet())
			map.put(fromString(e.getKey()), valueAdapter.fromJson(e.getValue()));
		return map;
	}

	@Override
	public JsonValue toJson(T javaValue) {
		if (javaValue == null)
			return JsonValue.NULL;

		final var a = IuJson.object();
		for (final var e : javaValue.entrySet())
			a.add(toString(e.getKey()), valueAdapter.toJson(e.getValue()));
		return a.build();
	}

	private K fromString(String key) {
		return keyAdapter.fromJson(IuJsonAdapter.of(String.class).toJson(key));
	}

	private String toString(K key) {
		return IuJsonAdapter.of(String.class).fromJson(keyAdapter.toJson(key));
	}

}
