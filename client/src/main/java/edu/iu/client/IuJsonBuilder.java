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
package edu.iu.client;

import java.util.LinkedHashMap;
import java.util.Map;

import edu.iu.IuObject;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

/**
 * Functional base class for implementing JSON-based builder pattern behavior
 * components.
 * 
 * @param <B> builder type
 */
public class IuJsonBuilder<B extends IuJsonBuilder<B>> {

	private Map<String, JsonValue> pendingParameters = new LinkedHashMap<>();

	/**
	 * Default constructor.
	 */
	protected IuJsonBuilder() {
	}

	/**
	 * Creates a {@link JsonObject} from all pending parameters.
	 * 
	 * @return {@link JsonObject}
	 */
	protected JsonObject toJson() {
		return build(IuJson.object()).build();
	}

	/**
	 * Merges all parameters defined for another builder into this builder.
	 * 
	 * @param builder builder to merge parameters from
	 * @see IuObject#once(Object, Object)
	 */
	protected void merge(IuJsonBuilder<?> builder) {
		for (final var parameterEntry : builder.pendingParameters.entrySet())
			pendingParameters.compute(parameterEntry.getKey(),
					(name, value) -> IuObject.once(value, parameterEntry.getValue()));
	}

	/**
	 * Adds all pending parameters to a {@link JsonObjectBuilder}.
	 * 
	 * @param builder {@link JsonObjectBuilder}
	 * @return builder
	 */
	protected JsonObjectBuilder build(JsonObjectBuilder builder) {
		pendingParameters.forEach(builder::add);
		return builder;
	}

	/**
	 * Gets a pending parameter value.
	 * 
	 * @param name parameter name
	 * @return parameter value
	 */
	protected JsonValue param(String name) {
		return pendingParameters.get(name);
	}

	/**
	 * Copies all values from another builder to this one
	 * 
	 * @param <S>     source builder type
	 * @param builder source builder
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	protected <S extends IuJsonBuilder<S>> B copy(S builder) {
		for (final var sourceEntry : ((IuJsonBuilder<S>) builder).pendingParameters.entrySet())
			pendingParameters.compute(sourceEntry.getKey(),
					(name, value) -> IuObject.once(value, sourceEntry.getValue()));
		return (B) this;
	}

	/**
	 * Sets a parameter value.
	 * 
	 * @param <T>     value type
	 * @param name    parameter name
	 * @param value   parameter value
	 * @param adapter JSON type adapter
	 * @return this
	 */
	protected <T> B param(String name, T value) {
		return param(name, value, IuJsonAdapter.basic());
	}

	/**
	 * Sets a parameter value.
	 * 
	 * @param <T>     value type
	 * @param name    parameter name
	 * @param value   parameter value
	 * @param adapter JSON type adapter
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	protected <T> B param(String name, T value, IuJsonAdapter<T> adapter) {
		pendingParameters.compute(name, (key, current) -> IuObject.once(current, adapter.toJson(value)));
		return (B) this;
	}

}
