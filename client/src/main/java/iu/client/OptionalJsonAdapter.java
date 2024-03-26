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

import java.util.Date;
import java.util.Optional;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Date}
 * 
 * @param <T> value type
 */
class OptionalJsonAdapter<T> implements IuJsonAdapter<Optional<T>> {

	/**
	 * Singleton instance.
	 */
	static final OptionalJsonAdapter<?> INSTANCE = new OptionalJsonAdapter<>(BasicJsonAdapter.INSTANCE);

	private final IuJsonAdapter<T> adapter;

	/**
	 * Constructor.
	 * 
	 * @param adapter value adapter
	 */
	OptionalJsonAdapter(IuJsonAdapter<T> adapter) {
		this.adapter = adapter;
	}

	@Override
	public Optional<T> fromJson(JsonValue value) {
		return Optional.ofNullable(adapter.fromJson(value));
	}

	@Override
	public JsonValue toJson(Optional<T> value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return adapter.toJson(value.orElse(null));
	}

}
