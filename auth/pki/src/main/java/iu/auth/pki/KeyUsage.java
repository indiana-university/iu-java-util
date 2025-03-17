/*
 * Copyright © 2025 Indiana University
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
package iu.auth.pki;

import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;

import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Use;

/**
 * Processes {@link X509Certificate#getKeyUsage() X509 Key Usage Extensions}.
 */
final class KeyUsage {

	private final int pathLen;
	private final boolean digitalSignature;
	private final boolean keyEncipherment;
	private final boolean keyAgreement;
	private final boolean keyCertSign;
	private final boolean cRLSign;

	/**
	 * Constructor.
	 * 
	 * @param certificate {@link X509Certificate}
	 */
	KeyUsage(X509Certificate certificate) {
		pathLen = certificate.getBasicConstraints();
		final var keyUsage = certificate.getKeyUsage();
		if (keyUsage == null) {
			digitalSignature = false;
			keyEncipherment = false;
			keyAgreement = false;
			keyCertSign = false;
			cRLSign = false;
		} else {
			digitalSignature = keyUsage[0];
			keyEncipherment = keyUsage[2];
			keyAgreement = keyUsage[4];
			keyCertSign = keyUsage[5];
			cRLSign = keyUsage[6];
		}
	}

	/**
	 * Determines if key usage permits public key use as a CA signing certificate.
	 * 
	 * @return true if X509 key usage flags allow CA certificate and CRL signing
	 */
	boolean isCA() {
		return pathLen >= 0 //
				&& keyCertSign //
				&& cRLSign;
	}

	/**
	 * Determines if key usage flags match {@link Use public key use}.
	 * 
	 * @param use {@link Use public key use}
	 * @return true if X509 key usage flags allow the public key use, else false
	 */
	boolean matches(Use use) {
		switch (use) {
		case ENCRYPT:
			return keyAgreement || keyEncipherment;

		case SIGN:
		default:
			return digitalSignature;
		}
	}

	/**
	 * Determines if key usage flags match (@link Operation Web Crypto key
	 * operations}.
	 * 
	 * @param ops {@link Operation} {@link Set}
	 * @return true if X509 key usage flags allow all key operations, else false
	 */
	boolean matches(Set<Operation> ops) {
		for (final var op : ops)
			switch (op) {
			case DERIVE_BITS:
			case DECRYPT:
			case ENCRYPT:
				return false;

			case DERIVE_KEY:
				if (!keyAgreement)
					return false;
				else
					continue;

			case UNWRAP:
			case WRAP:
				if (!keyEncipherment)
					return false;
				else
					continue;

			case SIGN:
				if (!digitalSignature)
					return false;
				else
					continue;

			case VERIFY:
			default:
				if (!digitalSignature)
					return false;
			}
		return true;
	}

	/**
	 * Gets {@link Operation Web Crypto key operations} allowed by key usage.
	 * 
	 * @param verify        true to get key ops for digitial signature verification;
	 *                      false for data encryption
	 * @param hasPrivateKey true if the private key is available; false for public
	 *                      key operations only
	 * @return {@link Operation}[]
	 */
	Operation[] ops(boolean verify, boolean hasPrivateKey) {
		final Queue<Operation> ops = new ArrayDeque<>();
		if (verify) {
			if (digitalSignature) {
				ops.add(Operation.VERIFY);
				if (hasPrivateKey)
					ops.add(Operation.SIGN);
			}
		} else { // encrypt
			if (keyEncipherment) {
				ops.add(Operation.WRAP);
				if (hasPrivateKey)
					ops.add(Operation.UNWRAP);
			}
			if (keyAgreement)
				ops.add(Operation.DERIVE_KEY);
		}
		return ops.toArray(Operation[]::new);
	}
}
