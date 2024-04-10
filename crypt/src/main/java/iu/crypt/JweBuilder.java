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

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import edu.iu.IuObject;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Builder;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;

/**
 * Collects inputs for {@link Jwe} encrypted messages.
 */
public class JweBuilder implements Builder {
	static {
		IuObject.assertNotOpen(JweBuilder.class);
	}

	private final Encryption encryption;
	private final boolean deflate;
	private final Queue<JweRecipientBuilder> pendingRecipients = new ArrayDeque<>();
	private final Set<String> protectedParameters = new LinkedHashSet<>();
	private boolean compact;
	private byte[] additionalData;

	/**
	 * Constructor.
	 * 
	 * @param encryption {@link Encryption content encryption algorithm}
	 * @param deflate    true to compress content; false to encrypt without
	 *                   compression
	 */
	public JweBuilder(Encryption encryption, boolean deflate) {
		this.encryption = encryption;
		this.deflate = deflate;
	}

	@Override
	public Builder compact() {
		compact = true;
		return this;
	}

	@Override
	public Builder aad(byte[] additionalData) {
		this.additionalData = IuObject.once(this.additionalData, additionalData);
		return this;
	}

	@Override
	public Builder protect(Param... params) {
		for (final var param : params)
			protectedParameters.add(param.name);
		return this;
	}

	@Override
	public Builder protect(String... params) {
		for (final var param : params)
			protectedParameters.add(param);
		return this;
	}

	@Override
	public JweRecipientBuilder addRecipient(Algorithm algorithm) {
		final var builder = new JweRecipientBuilder(this, algorithm);
		builder.param(Param.ENCRYPTION, encryption());
		if (deflate)
			builder.param(Param.ZIP, "DEF");
		this.pendingRecipients.offer(builder);
		return builder;
	}

	@Override
	public WebEncryption encrypt(InputStream in) {
		byte[] contentEncryptionKey = null;
		final Queue<JweRecipient> recipients = new ArrayDeque<>();
		for (final var pendingRecipient : pendingRecipients) {
			final var builder = pendingRecipient.encryptedKeyBuilder();
			final var algorithm = Objects.requireNonNull(builder.algorithm(), "Missing algorithm");
			if (algorithm.equals(Algorithm.DIRECT)) {
				// 5.1#6 use shared key as CEK for direct encryption
				final var jwk = Objects.requireNonNull(builder.key(), "recipient must provide a key");
				final var cek = Objects.requireNonNull(jwk.getKey(), "DIRECT requires a secret key");
				if (cek.length != encryption.size / 8)
					throw new IllegalArgumentException("Invalid key size for " + encryption + " " + cek.length);
				contentEncryptionKey = IuObject.once(contentEncryptionKey, cek, "cek already set");
			} else if (algorithm.equals(Algorithm.ECDH_ES))
				contentEncryptionKey = IuObject.once(contentEncryptionKey, builder.agreedUponKey(encryption),
						"cek already set");
			else if (!algorithm.use.equals(Use.ENCRYPT))
				throw new IllegalArgumentException("Not an encryption algorithm " + algorithm);
			else if (contentEncryptionKey == null)
				contentEncryptionKey = WebKey.ephemeral(encryption).getKey();

			// encrypt before processing header to ensure all headers are populated
			final var recipient = builder.encrypt(encryption, contentEncryptionKey);

			final var header = recipient.getHeader();
			final var serializedHeader = header.toJson(a -> true);

			if (!compact //
					&& !serializedHeader.keySet().containsAll(protectedParameters))
				throw new IllegalArgumentException(
						"Protected parameters " + protectedParameters + " are required " + serializedHeader.keySet());

			recipients.add(recipient);
		}

		return new Jwe(encryption, deflate, compact, protectedParameters, recipients, contentEncryptionKey,
				additionalData, in);
	}

	/**
	 * Gets the encryption.
	 * 
	 * @return encryption
	 */
	Encryption encryption() {
		return encryption;
	}

}
