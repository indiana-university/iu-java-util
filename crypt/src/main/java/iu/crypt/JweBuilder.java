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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

import javax.crypto.KeyGenerator;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Builder;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * Collects inputs for {@link Jwe} encrypted messages.
 */
public class JweBuilder implements Builder {
	static {
		IuObject.assertNotOpen(JweBuilder.class);
	}

	private final Queue<JweRecipientBuilder> recipients = new ArrayDeque<>();
	private Set<String> protectedParameters = new LinkedHashSet<>();
	private Encryption encryption;
	private boolean deflate = true;
	private byte[] additionalData;

	/**
	 * Constructor.
	 */
	public JweBuilder() {
	}

	@Override
	public Builder enc(Encryption encryption) {
		Objects.requireNonNull(encryption);

		if (this.encryption == null)
			this.encryption = encryption;
		else if (!encryption.equals(this.encryption))
			throw new IllegalStateException("encryption already set to " + encryption);

		return this;
	}

	@Override
	public JweBuilder deflate(boolean deflate) {
		this.deflate = deflate;
		return this;
	}

	@Override
	public Builder aad(byte[] additionalData) {
		Objects.requireNonNull(additionalData);

		if (this.additionalData == null)
			this.additionalData = additionalData;
		else if (!Arrays.equals(additionalData, this.additionalData))
			throw new IllegalStateException("additionalData already set");

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
		return null;
	}

	@Override
	public JweRecipientBuilder addRecipient() {
		this.encryption = Objects.requireNonNull(encryption, "Content encryption algorithm is required");

		final var recipient = new JweRecipientBuilder(this);
		recipient.enc("enc", IuJson.toJson(encryption.enc));
		if (deflate)
			recipient.ext("zip", IuJson.toJson("DEF"));

		recipients.add(recipient);
		return recipient;
	}

	@Override
	public WebEncryption encrypt(InputStream in) {
		return new Jwe(this, in);
	}

	/**
	 * Gets the encryption.
	 * 
	 * @return encryption
	 */
	Encryption encryption() {
		return encryption;
	}

	/**
	 * Gets recipients.
	 * 
	 * @return recipients
	 */
	Stream<JweRecipientBuilder> recipients() {
		return recipients.stream();
	}

	/**
	 * Gets protected parameters.
	 * 
	 * @return protected parameters
	 */
	Set<String> protectedParameters() {
		return protectedParameters;
	}

	/**
	 * Gets the deflate flag
	 * 
	 * @return deflate flag
	 */
	boolean deflate() {
		return deflate;
	}

	/**
	 * Get additionalData
	 * 
	 * @return additionalData
	 */
	byte[] additionalData() {
		return additionalData;
	}

	/**
	 * Generates content encryption key (CEK)
	 * 
	 * @param recipient in-progress recipient builder
	 * @param from      message originator private key
	 * @return content encryption key
	 */
	byte[] cek(JweRecipientBuilder recipient) {
		final var algorithm = recipient.algorithm();

		if (algorithm.equals(Algorithm.DIRECT)) {
			// 5.1#6 use shared key as CEK for direct encryption
			final var jwk = Objects.requireNonNull(recipient.key(), "recipient must provide a key");
			final var cek = Objects.requireNonNull(jwk.getKey(), "DIRECT requires a secret key");
			if (cek.length != encryption.size / 8)
				throw new IllegalArgumentException("Invalid key size for " + encryption);

		} else if (algorithm.equals(Algorithm.ECDH_ES))
			return recipient.agreedUponKey(encryption);

		final byte[] key = IuException.unchecked(() -> {
			// 5.1#2 generate CEK if ephemeral
			final var keygen = KeyGenerator.getInstance("AES");
			keygen.init(encryption.size);
			return keygen.generateKey().getEncoded();
		});

		if (encryption == null || encryption.mac == null)
			return key;
		else
			return IuException.unchecked(() -> {
				final var keygen = KeyGenerator.getInstance(encryption.mac);
				keygen.init(encryption.size);
				final var mac = keygen.generateKey().getEncoded();
				final var maccek = new byte[mac.length + key.length];
				System.arraycopy(mac, 0, maccek, 0, mac.length);
				System.arraycopy(key, 0, maccek, mac.length, key.length);
				return maccek;
			});
	}

}
