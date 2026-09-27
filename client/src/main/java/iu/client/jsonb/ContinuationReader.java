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
package iu.client.jsonb;

import java.io.Reader;

/**
 * Reads the rest of an object from the text it was parsed from, as a JSON
 * object of its own: an opening brace, then the text from where the rest
 * begins.
 *
 * <p>
 * Reads the text in place rather than copying it. Whatever follows the object
 * in the text is never reached, since a parser reading the object stops at its
 * closing brace.
 * </p>
 */
final class ContinuationReader extends Reader {

	private final String text;
	private int position;
	private boolean opened;

	/**
	 * Constructor.
	 *
	 * @param text  text the object was parsed from
	 * @param start where the rest of the object begins: its next property, or its
	 *              closing brace
	 */
	ContinuationReader(String text, int start) {
		this.text = text;
		this.position = start;
	}

	@Override
	public int read(char[] buffer, int offset, int length) {
		if (length == 0)
			return 0;

		var count = 0;
		if (!opened) {
			buffer[offset] = '{';
			opened = true;
			count = 1;
		}

		final var remaining = Math.min(length - count, text.length() - position);
		if (remaining <= 0)
			return count == 0 ? -1 : count;

		text.getChars(position, position + remaining, buffer, offset + count);
		position += remaining;
		return count + remaining;
	}

	/**
	 * Does nothing: the text isn't a resource.
	 */
	@Override
	public void close() {
	}

}
