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
package iu.crypt;

import java.util.Base64;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import jakarta.json.JsonObject;

/**
 * Encodes {@link byte[]} values for inclusion in JWS and JWE serialized forms
 * as unpadded Base64 URL encoded strings.
 */
public final class CompactEncoded {

	private CompactEncoded() {
	}

	/**
	 * Iterates over segments in a JSON compact serialized structure.
	 * 
	 * @param data compact serialized data
	 * @return {@link Iterator} over data segments
	 */
	public static Iterator<String> compact(final String data) {
		return new Iterator<String>() {
			private int start;
			private int end = -1;

			@Override
			public boolean hasNext() {
				if (end < start) {
					end = data.indexOf('.', start);
					if (end == -1)
						end = data.length();
				}
				return start < data.length();
			}

			@Override
			public String next() {
				if (!hasNext())
					throw new NoSuchElementException();

				final var next = start == end ? null : data.substring(start, end);
				start = end + 1;
				return next;
			}
		};
	}

	/**
	 * Returns the protected header of a compact serialized JWS or JWE.
	 * 
	 * @param compactSerialized compact serialized JWS or JWE
	 * @return protected header
	 */
	public static JsonObject getProtectedHeader(String compactSerialized) {
		final var dot = IuObject.require(//
				Objects.requireNonNull(compactSerialized, "Missing token").indexOf('.'), //
				i -> i != -1, "Invalid compact serialized data");

		final var encodedProtectedHeader = compactSerialized.substring(0, dot);
		return IuJson.parse(IuText.utf8(Base64.getUrlDecoder().decode(encodedProtectedHeader))).asJsonObject();
	}

}