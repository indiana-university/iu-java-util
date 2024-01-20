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
package iu.type.jsonb;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;

/**
 * Converts directly to {@link JsonValue}.
 */
class JsonValueBuilder implements JsonStructureBuilder {

	private final JsonProvider jsonpProvider;

	/**
	 * Constructor.
	 * 
	 * @param jsonpProvider JSON-P provider to use for creating values
	 */
	JsonValueBuilder(JsonProvider jsonpProvider) {
		this.jsonpProvider = jsonpProvider;
	}

	/**
	 * Holds a non-null, non-empty reference to the value, after calling any
	 * {@code add} method.
	 * <p>
	 * Will be null until an {@code add} method is called.
	 * </p>
	 */
	Optional<JsonValue> value;

	@Override
	public void addNull() {
		this.value = Optional.of(JsonValue.NULL);
	}

	@Override
	public void add(JsonValue value) {
		this.value = Optional.of(value);
	}

	@Override
	public void add(String value) {
		this.value = Optional.of(jsonpProvider.createValue(value));
	}

	@Override
	public void add(BigDecimal value) {
		this.value = Optional.of(jsonpProvider.createValue(value));
	}

	@Override
	public void add(BigInteger value) {
		this.value = Optional.of(jsonpProvider.createValue(value));
	}

	@Override
	public void add(int value) {
		this.value = Optional.of(jsonpProvider.createValue(value));
	}

	@Override
	public void add(long value) {
		this.value = Optional.of(jsonpProvider.createValue(value));
	}

	@Override
	public void add(double value) {
		this.value = Optional.of(jsonpProvider.createValue(value));
	}

	@Override
	public void add(boolean value) {
		this.value = Optional.of(value ? JsonValue.TRUE : JsonValue.FALSE);
	}

	@Override
	public void add(JsonObjectBuilder builder) {
		this.value = Optional.of(builder.build());
	}

	@Override
	public void add(JsonArrayBuilder builder) {
		this.value = Optional.of(builder.build());
	}

}
