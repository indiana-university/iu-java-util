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
package iu.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Identifies one cached read in the process-wide read cache.
 *
 * <p>
 * The parameter map is copied and wrapped on construction so that a caller
 * mutating the map it passed to a search cannot corrupt the key's hash code
 * after the entry has been stored.
 * </p>
 *
 * <p>
 * {@code maxResults} participates in equality because a capped search and an
 * uncapped one over the same parameters are different results. A single-entity
 * load uses {@link #LOAD}, and a generated query uses {@link #QUERY}, so neither
 * can collide with a search and every non-load read can publish the rows it read
 * as loads of their own.
 * </p>
 *
 * @param type       cached entity type
 * @param parameters key values the read was performed with; null for a query
 *                   with caller-supplied where fragments
 * @param where      where clause fragments, or null for a mapped-key query or
 *                   a search
 * @param order      order clause fragments, or null
 * @param args       where clause args, or null
 * @param maxResults row cap the read was performed with, or {@link #LOAD} for a
 *                   single-entity load
 */
record DaoKey(Class<?> type, Map<String, ?> parameters, Iterable<String> where, Iterable<String> order,
		Iterable<?> args, int maxResults) {

	/** {@link #maxResults()} of a single-entity load. */
	static final int LOAD = -1;

	/** {@link #maxResults()} of a generated bean query. */
	static final int QUERY = -2;

	/**
	 * Canonical constructor.
	 *
 * @param type       cached entity type
 * @param parameters key values the read was performed with
 * @param where      where clause fragments
 * @param order      order clause fragments
 * @param args       where clause args
 * @param maxResults row cap, {@link #LOAD}, or {@link #QUERY}
	 */
	DaoKey {
		if (parameters != null)
			parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
		where = copyOf(where);
		order = copyOf(order);
		args = copyOf(args);
	}

	/**
	 * Copies an iterable so that a caller cannot mutate a cache key after it has
	 * been stored.
	 *
	 * @param values values to copy
	 * @return an immutable copy, or null when {@code values} is null
	 */
	private static <T> List<T> copyOf(Iterable<T> values) {
		if (values == null)
			return null;

		final var copy = new ArrayList<T>();
		values.forEach(copy::add);
		return List.copyOf(copy);
	}

	/**
	 * Determines whether this key identifies a single-entity load.
	 *
	 * @return true for a load; false for a search
	 */
	boolean isLoad() {
		return maxResults == LOAD;
	}

	/**
	 * Determines whether this key identifies a generated bean query.
	 *
	 * @return true for a generated query; false for a load or search
	 */
	boolean isQuery() {
		return maxResults == QUERY;
	}

	@Override
	public String toString() {
		return (isLoad() ? "load:" : isQuery() ? "query:" : "search:") + type.getName()
				+ (where == null ? parameters : where) + (order == null ? "" : order) + (args == null ? "" : args);
	}
}
