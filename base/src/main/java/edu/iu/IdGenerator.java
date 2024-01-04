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

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

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
 * <li>Once per thread, creates a SHA1PRNG pseudo random number generator seeded
 * with 1024 bytes of entropy from the system default mechanism. <em>The system
 * default entropy source can be exceedingly slow on a Linux server so is only
 * used to seed a (much faster) hash-based algorithm.</em></li>
 * <li>Generates 12 bytes of pseudorandom data, interposed with
 * <ul>
 * <li>The lower 6 bytes of the system millisecond timestamp</li>
 * <li>The lower 3 bytes of a hash checksum seeded by the system timestamp</li>
 * </ul>
 * </li>
 * <li>Uses a modified Base64 transform to convert to an id-safe 28 character
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

	private static final Random SEED;
	private static final byte[] SEED_BUF = new byte[1024];
	static {
		try {
			SEED = SecureRandom.getInstanceStrong();
		} catch (NoSuchAlgorithmException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private static final ThreadLocal<SecureRandom> RAND = new ThreadLocal<SecureRandom>() {
		@Override
		protected SecureRandom initialValue() {
			try {
				SecureRandom rand = SecureRandom.getInstance("SHA1PRNG");
				synchronized (SEED) {
					SEED.nextBytes(SEED_BUF);
					rand.setSeed(SEED_BUF);
				}
				return rand;
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException(e);
			}
		}
	};

	private static char getEncodedChar(int v) {
		if (v == 0)
			return '_';
		else if (v == 1)
			return '-';
		else if (v < 12)
			return (char) ('0' + (v - 2));
		else if (v < 38)
			return (char) ('A' + (v - 12));
		else
			return (char) ('a' + (v - 38));
	}

	private static String encodeId(byte[] raw) {
		StringBuilder sb = new StringBuilder();
		int p = 0;
		for (int i = 0; i < raw.length; i++) {
			int b0 = raw[i];
			if (p <= 2)
				sb.append(getEncodedChar((b0 >> (2 - p)) & 0x3f));
			int p1 = (p + 4) % 6;
			if (p != 2) {
				byte b1 = raw[i + 1];
				sb.append(getEncodedChar(((b0 << p1) & 0x3f) + ((b1 >> (8 - p1)) & ((int) Math.pow(2, p1) - 1))));
			}
			p = p1;
		}
		return sb.toString();
	}

	private static byte getDecodedValue(char c) {
		if (c == '_')
			return (byte) 0;
		if (c == '-')
			return (byte) 1;
		if (c >= '0' && c <= '9')
			return (byte) (2 + (c - '0'));
		if (c >= 'A' && c <= 'Z')
			return (byte) (12 + (c - 'A'));
		if (c >= 'a' && c <= 'z')
			return (byte) (38 + (c - 'a'));
		throw new IllegalArgumentException("Invalid encoding");
	}

	private static byte[] decodeId(String id) {
		if (id.length() % 4 != 0)
			throw new IllegalArgumentException("Invalid length");
		byte[] raw = new byte[id.length() * 3 / 4];
		Arrays.fill(raw, (byte) 0);
		int p = 0, i = 0;
		for (int j = 0; j < id.length(); j++) {
			byte v = getDecodedValue(id.charAt(j));
			int d = (int) (v * Math.pow(2, 2 - p));
			if (d > 127)
				d -= 256;
			raw[i] |= (byte) d;
			p += 6;
			if (p >= 8) {
				p -= 8;
				i++;
				if (p > 0)
					raw[i] |= (byte) (v * Math.pow(2, 8 - p));
			}
		}
		return raw;
	}

	/**
	 * Generates a new unique identifier.
	 * 
	 * @return new unique identifier
	 */
	public static String generateId() {
		byte[] rawId = new byte[24];
		RAND.get().nextBytes(rawId);

		long now = System.currentTimeMillis();
		rawId[3] = (byte) now;
		rawId[9] = (byte) ((now >>> 8) & 0xff);
		rawId[15] = (byte) ((now >>> 16) & 0xff);
		rawId[6] = (byte) ((now >>> 24) & 0xff);
		rawId[12] = (byte) ((now >>> 32) & 0xff);
		rawId[18] = (byte) ((now >>> 40) & 0xff);

		int hash = (int) now;
		for (int i = 1; i < 20; i++)
			if (i != 11)
				hash = 47 * hash + rawId[i];
		rawId[11] = (byte) hash;
		rawId[20] = (byte) ((hash >>> 8) & 0xff);
		rawId[0] = (byte) ((hash >>> 16) & 0xff);

		return encodeId(rawId);
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
		byte[] decoded = decodeId(id);
		if (decoded.length != 24)
			throw new IllegalArgumentException("Invalid length");

		long s = ((((long) decoded[18]) << 40) & 0xff0000000000L) //
				| ((((long) decoded[12]) << 32) & 0xff00000000L) //
				| ((((long) decoded[6]) << 24) & 0xff000000L) //
				| ((((long) decoded[15]) << 16) & 0xff0000L) //
				| ((((long) decoded[9]) << 8) & 0xff00L) //
				| (((long) decoded[3]) & 0xffL);

		long now = System.currentTimeMillis();
		if (now - s < -1000L)
			throw new IllegalArgumentException("Invalid time signature");

		if (ttl > 0 && now - s > ttl)
			throw new IllegalArgumentException("Expired time signature");

		int h = ((decoded[0] << 16) & 0xff0000) | ((decoded[20] << 8) & 0xff00) | (decoded[11] & 0xff);
		int hash = (int) s;
		for (int i = 1; i < 20; i++)
			if (i != 11)
				hash = 47 * hash + decoded[i];
		if (h != (hash & 0xffffff))
			throw new IllegalArgumentException("Invalid checksum");
	}

	private IdGenerator() {
	}
}
