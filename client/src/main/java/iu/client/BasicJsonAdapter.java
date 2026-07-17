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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter#basic()}
 */
class BasicJsonAdapter implements IuJsonAdapter<Object> {

	/**
	 * Singleton instance.
	 */
	static BasicJsonAdapter INSTANCE = new BasicJsonAdapter();

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private final IuJsonAdapter<List<?>> listAdapter = new CollectionAdapter(this, ArrayList::new);
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private final IuJsonAdapter<Iterable<?>> iterableAdapter = new IterableAdapter(this);
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private final IuJsonAdapter<Iterable<?>> collectionAdapter = new CollectionAdapter(this, ArrayDeque::new);
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private final IuJsonAdapter<Iterator<?>> iteratorAdapter = new IteratorAdapter(this);
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private final IuJsonAdapter<Enumeration<?>> enumerationAdapter = new EnumerationAdapter(this);
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private final IuJsonAdapter<Stream<?>> streamAdapter = new StreamAdapter(this);
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private final IuJsonAdapter<Map<?, ?>> mapAdapter = new JsonObjectAdapter(this, this, LinkedHashMap::new);

	@Override
	public Object fromJson(JsonValue value) {
		if (value instanceof JsonArray)
			return listAdapter.fromJson(value);
		else if (value instanceof JsonObject)
			return mapAdapter.fromJson(value);
		else if (value instanceof JsonString)
			return ((JsonString) value).getString();
		else if (value instanceof JsonNumber)
			return ((JsonNumber) value).bigDecimalValue();
		else if (JsonValue.TRUE.equals(value))
			return Boolean.TRUE;
		else if (JsonValue.FALSE.equals(value))
			return Boolean.FALSE;
		else // (JsonValue.NULL.equals(value) || value == null)
			return null;
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public JsonValue toJson(Object value) {
		if (value == null)
			return JsonValue.NULL;
		else if (value instanceof JsonValue)
			return (JsonValue) value;
		else if (value instanceof JsonObjectBuilder)
			return ((JsonObjectBuilder) value).build();
		else if (value instanceof JsonArrayBuilder)
			return ((JsonArrayBuilder) value).build();
		else if (value instanceof Map)
			return mapAdapter.toJson((Map<?, ?>) value);
		else if (value.getClass().isArray())
			return new ArrayAdapter(this, null).toJson(value);
		else if (value instanceof List)
			return listAdapter.toJson((List<?>) value);
		else if (value instanceof Collection)
			return collectionAdapter.toJson((Collection<?>) value);
		else if (value instanceof Iterable)
			return iterableAdapter.toJson((Iterable<?>) value);
		else if (value instanceof Iterator)
			return iteratorAdapter.toJson((Iterator<?>) value);
		else if (value instanceof Enumeration)
			return enumerationAdapter.toJson((Enumeration<?>) value);
		else if (value instanceof Stream)
			return streamAdapter.toJson((Stream<?>) value);
		else if (value instanceof String)
			return IuJson.PROVIDER.createValue((String) value);
		else if (value instanceof Number)
			return IuJson.PROVIDER.createValue((Number) value);
		else if (Boolean.TRUE.equals(value))
			return JsonValue.TRUE;
		else if (Boolean.FALSE.equals(value))
			return JsonValue.FALSE;
		else
			throw new IllegalArgumentException();
	}

	private BasicJsonAdapter() {
	}
}
