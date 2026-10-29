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

import jakarta.json.stream.JsonParser;

/**
 * A parser scoped to one value by the conversion that controls it, which a
 * reader holding the value can prepare for before that conversion moves on.
 */
public interface ScopedParser extends JsonParser {

	/**
	 * Determines if the value is already in memory, so reading it whole costs
	 * nothing more than a reference.
	 *
	 * @return true if {@link #getObject()} and {@link #getArray()} return values
	 *         already held
	 */
	boolean isTree();

	/**
	 * Registers work to run before the controlling conversion skips what's left
	 * of the value.
	 *
	 * @param hook runs once, while the parser is still where the reader left it
	 */
	void beforeRelease(Runnable hook);

	/**
	 * Opens a parser over the rest of the object this parser is in, from the
	 * text the controlling conversion reads, without copying it.
	 *
	 * <p>
	 * Valid between properties: at the object's {@code START_OBJECT}, or at the
	 * last event of one of its values.
	 * </p>
	 *
	 * @return parser at a {@code START_OBJECT} followed by the properties not
	 *         yet read, and the object's {@code END_OBJECT}; null if the
	 *         controlling conversion doesn't read from text it holds
	 */
	JsonParser continuation();

}
