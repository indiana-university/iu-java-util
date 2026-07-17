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

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link Long}
 */
class BooleanJsonAdapter implements IuJsonAdapter<Boolean> {

	/**
	 * Adapts {@link Boolean}
	 */
	static BooleanJsonAdapter INSTANCE = new BooleanJsonAdapter(null);
	
	/**
	 * Adapts {@link Boolean#TYPE}
	 */
	static BooleanJsonAdapter PRIMITIVE = new BooleanJsonAdapter(false);
	
	private final Boolean nullValue;
	
	private BooleanJsonAdapter(Boolean nullValue) {
		this.nullValue = nullValue;
	}

	@Override
	public Boolean fromJson(JsonValue value) {
		if (JsonValue.NULL.equals(value) //
				|| value == null)
			return nullValue;
		else if (value instanceof JsonString)
			return Boolean.valueOf(TextJsonAdapter.INSTANCE.fromJson(value));
		else if (value instanceof JsonNumber)
			return ((JsonNumber) value).intValue() != 0;
		else
			return (value instanceof JsonStructure) || JsonValue.TRUE.equals(value);
	}

	@Override
	public JsonValue toJson(Boolean value) {
		if (value == null)
			return JsonValue.NULL;
		else
			return value ? JsonValue.TRUE : JsonValue.FALSE;
	}

}
