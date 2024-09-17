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
package edu.iu;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Provides a utility for generating short cryptographically secure unique
 * identifiers.
 * 
 * <p>
 * Identifiers generated by this utility are smaller and less likely to collide
 * than the random (v4) UUID values from {@link java.util.UUID#randomUUID()}.
 * </p>
 *
 * <ul>
 * <li>Generates 24 bytes of pseudorandom data, interposed with
 * <ul>
 * <li>The 4 byte seconds since epoch timestamp</li>
 * <li>The lower 3 bytes of a hash checksum seeded by the system timestamp</li>
 * </ul>
 * </li>
 * <li>Uses a modified Base64 transform to convert to an id-safe 32 character
 * string
 * <ul>
 * <li>Includes only upper and lower case letters, digits, hyphen and underline
 * characters</li>
 * <li>May be verified as having been generated by the same algorithm,
 * optionally with a millisecond time to live</li>
 * </ul>
 * </li>
 * </ul>
 */
public class IdGenerator {

	/**
	 * Generates a new unique identifier.
	 * 
	 * @return new unique identifier
	 */
	public static String generateId() {
		byte[] rawId = new byte[24];
		new SecureRandom().nextBytes(rawId);

		final var now = Instant.now().getEpochSecond();
		rawId[3] = (byte) now;
		rawId[9] = (byte) ((now >>> 8) & 0xff);
		rawId[15] = (byte) ((now >>> 16) & 0xff);
		rawId[6] = (byte) ((now >>> 24) & 0xff);
		
		int hash = (int) now;
		for (int i = 1; i < 24; i++)
			if (i != 11 && i != 20)
				hash = 47 * hash + rawId[i];
		rawId[11] = (byte) hash;
		rawId[20] = (byte) ((hash >>> 8) & 0xff);
		rawId[0] = (byte) ((hash >>> 16) & 0xff);

		return IuText.base64Url(rawId);
	}
	
	/**
	 * Validates that a new unique identifier was created by the same algorithm used
	 * in this utility, and has not expired.
	 * 
	 * @param id  id to verify
	 * @param ttl millisecond time to live for determining the expiration time for
	 *            the id; 0 for no expiration time
	 */
	public static void verifyId(String id, long ttl) {
		byte[] decoded = IuText.base64Url(id);
		if (decoded.length != 24)
			throw new IllegalArgumentException("Invalid length");

		long s = ((((long) decoded[6]) << 24) & 0xff000000L) //
				| ((((long) decoded[15]) << 16) & 0xff0000L) //
				| ((((long) decoded[9]) << 8) & 0xff00L) //
				| (((long) decoded[3]) & 0xffL);

		long now = Instant.now().getEpochSecond();
		if (now - s < -1L)
			throw new IllegalArgumentException("Invalid time signature");

		long ttls = (ttl + 999L) / 1000L;
		if (ttls > 0 && now - s > ttls)
			throw new IllegalArgumentException("Expired time signature");

		int h = ((decoded[0] << 16) & 0xff0000) | ((decoded[20] << 8) & 0xff00) | (decoded[11] & 0xff);
		int hash = (int) s;
		for (int i = 1; i < 24; i++)
			if (i != 11 && i != 20)
				hash = 47 * hash + decoded[i];
		if (h != (hash & 0xffffff))
			throw new IllegalArgumentException("Invalid checksum");
	}

	private IdGenerator() {
	}
}
