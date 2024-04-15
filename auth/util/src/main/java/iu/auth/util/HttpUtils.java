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
package iu.auth.util;

import java.util.Map;

/**
 * Provides minimal utilities for internal HTTP-based interactions specific to
 * authorization server interactions.
 */
public class HttpUtils {

	/**
	 * Creates an authentication challenge sending to a client via the
	 * <strong>WWW-Authenticate</strong> header.
	 * 
	 * @param scheme     authentication scheme to request
	 * @param attributes challenge attributes for informing the client of how to
	 *                   authenticate
	 * @return authentication challenge
	 */
	public static String createChallenge(String scheme, Map<String, String> attributes) {
		final var sb = new StringBuilder();
		sb.append(scheme);
		if (attributes != null && !attributes.isEmpty()) {
			sb.append(' ');
			var first = true;
			for (final var attributeEntry : attributes.entrySet()) {
				if (first)
					first = false;
				else
					sb.append(" ");
				sb.append(attributeEntry.getKey()).append("=\"")
						.append(attributeEntry.getValue().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
			}
		}
		return sb.toString();
	}

	private HttpUtils() {
	}

}
