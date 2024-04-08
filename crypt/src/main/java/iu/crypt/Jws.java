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
package iu.crypt;

import java.security.Signature;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebSignature;
import jakarta.json.JsonObject;

/**
 * JSON implementation of {@link WebSignature}.
 */
class Jws implements WebSignature {

	private final JsonObject protectedHeader;
	private final Jose header;
	private final byte[] signature;

	/**
	 * Creates a new signed message;
	 * 
	 * @param protectedHeader JWS protected header
	 * @param header          JWS unprotected header
	 * @param signature       signature
	 */
	Jws(JsonObject protectedHeader, Jose header, byte[] signature) {
		this.protectedHeader = protectedHeader;
		this.header = header;
		this.signature = signature;
		verify();
	}

	private void verify() {
		for (final var protectedEntry : protectedHeader.entrySet()) {
			final var name = protectedEntry.getKey();
			final var param = Param.from(name);

			if (param == null) {
				final var ext = Jose.getExtension(name);
				IuObject.once(ext.fromJson(protectedHeader.get(name)), header.getExtendedParameter(name));
			}
		}
		if (!header.getAlgorithm().equals(Param.ALGORITHM.json().fromJson(protectedHeader.get(Param.ALGORITHM.name))))
			throw new IllegalArgumentException("Algorithm must be present match protected header");
	}

	@Override
	public WebCryptoHeader getHeader() {
		return header;
	}

	@Override
	public byte[] getSignature() {
		return signature;
	}

	@Override
	public void verify(byte[] payload, WebKey key) {
		final var algorithm = header.getAlgorithm();
		final var signingInput = getSignatureInput(payload);
		final var dataToSign = IuText.utf8(signingInput);

		if (algorithm.algorithm.startsWith("Hmac")) {
			if (!Arrays.equals(signature, IuException.unchecked(() -> {
				final var mac = Mac.getInstance(algorithm.algorithm);
				mac.init(new SecretKeySpec(key.getKey(), "Hmac"));
				return mac.doFinal(dataToSign);
			})))
				throw new IllegalArgumentException(algorithm.algorithm + " verification failed");
		} else
			IuException.unchecked(() -> {
				final var sig = Signature.getInstance(algorithm.algorithm);
				sig.initVerify(key.getPublicKey());
				sig.update(dataToSign);
				if (!sig.verify(signature))
					throw new IllegalArgumentException(algorithm.algorithm + " verification failed");
			});
	}

	/**
	 * Gets the signature input.
	 * 
	 * @param payload payload
	 * @return signature input
	 */
	String getSignatureInput(byte[] payload) {
		return UnpaddedBinary.base64Url(IuText.utf8(Objects.requireNonNullElse(protectedHeader, "").toString())) + '.'
				+ UnpaddedBinary.base64Url(payload);
	}

}
