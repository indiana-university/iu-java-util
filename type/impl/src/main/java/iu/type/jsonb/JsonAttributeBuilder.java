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

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

/**
 * Encapsulates add a single attribute value to a {@link JsonObjectBuilder}.
 */
class JsonAttributeBuilder implements JsonStructureBuilder {

	private final JsonObjectBuilder builder;
	private final String name;

	/**
	 * Constructor.
	 * 
	 * @param builder {@link JsonObjectBuilder}
	 * @param name    attribute name
	 */
	JsonAttributeBuilder(JsonObjectBuilder builder, String name) {
		this.builder = builder;
		this.name = name;
	}

	@Override
	public void addNull() {
		builder.addNull(name);
	}

	@Override
	public void add(JsonArrayBuilder value) {
		builder.add(name, value);
	}

	@Override
	public void add(JsonObjectBuilder value) {
		builder.add(name, value);
	}

	@Override
	public void add(boolean value) {
		builder.add(name, value);
	}

	@Override
	public void add(double value) {
		builder.add(name, value);
	}

	@Override
	public void add(long value) {
		builder.add(name, value);
	}

	@Override
	public void add(int value) {
		builder.add(name, value);
	}

	@Override
	public void add(BigInteger value) {
		builder.add(name, value);
	}

	@Override
	public void add(BigDecimal value) {
		builder.add(name, value);
	}

	@Override
	public void add(String value) {
		builder.add(name, value);
	}

	@Override
	public void add(JsonValue value) {
		builder.add(name, value);
	}

}
