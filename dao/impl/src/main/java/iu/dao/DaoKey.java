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

import java.util.Collections;
import java.util.LinkedHashMap;
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
 * load uses {@link #LOAD}, so its entries can never collide with a search, and
 * so a search is able to publish the rows it read as loads of their own.
 * </p>
 *
 * @param type       cached entity type
 * @param parameters key values the read was performed with
 * @param maxResults row cap the read was performed with, or {@link #LOAD} for a
 *                   single-entity load
 */
record DaoKey(Class<?> type, Map<String, ?> parameters, int maxResults) {

	/** {@link #maxResults()} of a single-entity load. */
	static final int LOAD = -1;

	/**
	 * Canonical constructor.
	 *
	 * @param type       cached entity type
	 * @param parameters key values the read was performed with
	 * @param maxResults row cap, or {@link #LOAD} for a single-entity load
	 */
	DaoKey {
		parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
	}

	/**
	 * Determines whether this key identifies a single-entity load.
	 *
	 * @return true for a load; false for a search
	 */
	boolean isLoad() {
		return maxResults == LOAD;
	}

	@Override
	public String toString() {
		return (isLoad() ? "load:" : "search:") + type.getName() + parameters;
	}
}
