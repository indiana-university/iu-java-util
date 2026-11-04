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
package edu.iu.crypt;

import java.util.Arrays;

import edu.iu.IuException;

/**
 * Encapsulates signed data.
 */
public interface WebSignedPayload {

	/**
	 * Parses JWS signed payload from serialized form.
	 * 
	 * @param jws serialized JWS
	 * @return parsed JWS signed payload
	 */
	static WebSignedPayload parse(String jws) {
		return Init.SPI.parseJws(jws);
	}

	/**
	 * Gets the signed message payload.
	 * 
	 * @return signed message payload
	 */
	byte[] getPayload();

	/**
	 * Gets one or more signatures for verifying the payload.
	 * 
	 * @return {@link WebSignature}s
	 */
	Iterable<? extends WebSignature> getSignatures();

	/**
	 * Gets the signed payload in compact serialized form.
	 * 
	 * @return compact serialized form
	 */
	String compact();

	/**
	 * Verifies at least one signature using a public or shared key.
	 *
	 * <p>
	 * A signature whose algorithm doesn't support the key's type is skipped. The
	 * first signature that verifies returns; otherwise the first failure is
	 * thrown, with later ones suppressed.
	 * </p>
	 *
	 * @param key public or shared key
	 * @throws IllegalArgumentException if no signature's algorithm supports the
	 *                                  key's type
	 */
	default void verify(WebKey key) {
		Throwable error = null;
		final var payload = getPayload();

		for (final var signature : getSignatures())
			try {
				if (Arrays.asList(signature.getHeader().getAlgorithm().type).contains(key.getType())) {
					signature.verify(payload, key);
					return;
				}
			} catch (Throwable e) {
				if (error == null)
					error = e;
				else
					error.addSuppressed(e);
			}

		if (error == null)
			error = new IllegalArgumentException("No signature algorithm supports key type " + key.getType());

		throw IuException.unchecked(error);
	}

	/**
	 * Gets the signed payload in JSON serialized form.
	 * 
	 * @return JSON serialized form
	 */
	@Override
	String toString();

}
