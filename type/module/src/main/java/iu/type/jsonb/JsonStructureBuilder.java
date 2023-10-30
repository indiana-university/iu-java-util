/*
 * Copyright © 2023 Indiana University
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
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

/**
 * Encapsulates add methods for adding a single attribute or item to a
 * {@link JsonStructure} or {@link JsonValueBuilder}.
 */
interface JsonStructureBuilder {
	
	/**
	 * Adds a null value.
	 * 
	 * @see JsonArrayBuilder#addNull()
	 * @see JsonObjectBuilder#addNull(String)
	 */
	void addNull();

	/**
	 * Adds a JSON value.
	 * 
	 * @param value {@link JsonValue}
	 * @see JsonArrayBuilder#add(JsonValue)
	 * @see JsonObjectBuilder#add(String, JsonValue)
	 */
	void add(JsonValue value);

	/**
	 * Adds a {@link String}.
	 * 
	 * @param value {@link String}
	 * @see JsonArrayBuilder#add(String)
	 * @see JsonObjectBuilder#add(String, String)
	 */
	void add(String value);

	/**
	 * Adds a {@link BigDecimal}.
	 * 
	 * @param value {@link BigDecimal}
	 * @see JsonArrayBuilder#add(BigDecimal)
	 * @see JsonObjectBuilder#add(String, BigDecimal)
	 */
	void add(BigDecimal value);

	/**
	 * Adds a {@link BigInteger}.
	 * 
	 * @param value {@link BigInteger}
	 * @see JsonArrayBuilder#add(BigInteger)
	 * @see JsonObjectBuilder#add(String, BigInteger)
	 */
	void add(BigInteger value);

	/**
	 * Adds a {@code int}.
	 * 
	 * @param value integer value
	 * @see JsonArrayBuilder#add(int)
	 * @see JsonObjectBuilder#add(String, int)
	 */
	void add(int value);

	/**
	 * Adds a {@code long}.
	 * 
	 * @param value long value
	 * @see JsonArrayBuilder#add(long)
	 * @see JsonObjectBuilder#add(String, long)
	 */
	void add(long value);

	/**
	 * Adds a {@code double}.
	 * 
	 * @param value double value
	 * @see JsonArrayBuilder#add(double)
	 * @see JsonObjectBuilder#add(String, double)
	 */
	void add(double value);

	/**
	 * Adds a {@code boolean}.
	 * 
	 * @param value boolean value
	 * @see JsonArrayBuilder#add(boolean)
	 * @see JsonObjectBuilder#add(String, boolean)
	 */
	void add(boolean value);

	/**
	 * Adds a JSON object.
	 * 
	 * @param value {@link JsonObjectBuilder}
	 * @see JsonArrayBuilder#add(JsonObjectBuilder)
	 * @see JsonObjectBuilder#add(String, JsonObjectBuilder)
	 */
	void add(JsonObjectBuilder value);

	/**
	 * Adds a JSON array.
	 * 
	 * @param value {@link JsonArrayBuilder}
	 * @see JsonArrayBuilder#add(JsonArrayBuilder)
	 * @see JsonObjectBuilder#add(String, JsonArrayBuilder)
	 */
	void add(JsonArrayBuilder value);

}
