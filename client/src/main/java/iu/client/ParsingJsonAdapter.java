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

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for types that provide a mechanism for
 * parsing the value returned by {@link Object#toString()}
 * 
 * @param <T> Java type
 */
public class ParsingJsonAdapter<T> implements IuJsonAdapter<T> {

	private static final Map<Class<?>, ParsingJsonAdapter<?>> INSTANCES = new WeakHashMap<>();

	/**
	 * Gets a singleton instance by target type.
	 * 
	 * @param <T>    target type
	 * @param type   target type
	 * @param parser parsing function
	 * @return {@link ParsingJsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	static <T> ParsingJsonAdapter<T> of(Class<T> type, Function<String, T> parser) {
		var instance = INSTANCES.get(type);
		if (instance == null) {
			instance = new ParsingJsonAdapter<T>(parser, T::toString);
			synchronized (INSTANCES) {
				INSTANCES.put(type, instance);
			}
		}
		return (ParsingJsonAdapter<T>) instance;
	}

	private final Function<String, T> parser;
	private final Function<T, String> print;

	/**
	 * Constructor.
	 * 
	 * @param parser parsing function
	 * @param print  printing function
	 */
	public ParsingJsonAdapter(Function<String, T> parser, Function<T, String> print) {
		this.parser = parser;
		this.print = print;
	}

	@Override
	public T fromJson(JsonValue value) {
		final var text = TextJsonAdapter.INSTANCE.fromJson(value);
		if (text == null)
			return null;
		else
			return parser.apply(text);
	}

	@Override
	public JsonValue toJson(T value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return TextJsonAdapter.INSTANCE.toJson(print.apply(value));
	}

}
