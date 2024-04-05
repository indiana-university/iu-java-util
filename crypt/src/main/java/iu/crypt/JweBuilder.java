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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Builder;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

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
	private final Queue<JweRecipient> recipients = new ArrayDeque<>();
	private final Set<String> protectedParameters = new LinkedHashSet<>();
	private Map<String, JsonValue> sharedHeader;
	private boolean compact;
	private byte[] contentEncryptionKey;
	private byte[] additionalData;

	/**
	 * Constructor.
	 * 
	 * @param encryption {@link Encryption content encryption algorithm}
	 * @param deflate    true to compress content; false to encrypt without
	 *                   compression
	 */
	public JweBuilder(Encryption encryption, boolean deflate) {
		this.encryption = Objects.requireNonNull(encryption, "encryption");
		this.deflate = deflate;
	}

	@Override
	public Builder compact() {
		if (pendingRecipients.size() + recipients.size() > 1)
			throw new IllegalStateException("Compact only allows one receipient");
		compact = true;
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
			if (isPerRecipient(param.name))
				throw new IllegalArgumentException("Cannot protect per-recipient " + param);
			else
				protectedParameters.add(param.name);
		return this;
	}

	@Override
	public Builder protect(String... params) {
		for (final var param : params)
			if (isPerRecipient(param))
				throw new IllegalArgumentException("Cannot protect per-recipient " + param);
			else
				protectedParameters.add(param);
		return this;
	}

	@Override
	public JweRecipientBuilder addRecipient(Algorithm algorithm) {
		if (compact && recipients.size() + pendingRecipients.size() > 0)
			throw new IllegalStateException("Compact only allows one receipient");
		final var builder = new JweRecipientBuilder(this, algorithm);
		builder.enc("enc", IuJson.string(encryption.enc));
		if (deflate)
			builder.enc("zip", IuJson.string("DEF"));
		this.pendingRecipients.offer(builder);
		return builder;
	}

	@Override
	public WebEncryption encrypt(InputStream in) {
		while (!pendingRecipients.isEmpty())
			addRecipient(pendingRecipients.poll());
		return new Jwe(this, in);
	}

	/**
	 * Returns true if at least 2 recipients have different header parameter values.
	 * 
	 * @param paramName parameter name
	 * @return true if at least 2 recipients have values in the header parameter;
	 *         else false
	 */
	boolean isPerRecipient(String paramName) {
		if (protectedParameters.contains(paramName) //
				|| recipients.size() < 2)
			return false;

		final var param = Param.from(paramName);
		Optional<Object> value = null;
		for (final var recipient : recipients) {
			final var header = recipient.getHeader();
			final Object recipientValue;
			if (param == null)
				recipientValue = header.getExtendedParameter(paramName);
			else
				recipientValue = param.get(header);

			if (value == null)
				value = Optional.ofNullable(recipientValue);
			else
				return !IuObject.equals(recipientValue, value.orElse(null));
		}

		return false;
	}

	/**
	 * Verifies protected header values, computes shared headers, and enqueues a
	 * message recipient.
	 * 
	 * @param builder completed recipient builder
	 */
	void addRecipient(JweRecipientBuilder builder) {
		pendingRecipients.remove(builder);
		assert !compact || recipients.isEmpty(); // TODO: prove unreachable

		final var algorithm = Objects.requireNonNull(builder.algorithm(), "Missing algorithm");
		if (algorithm.equals(Algorithm.DIRECT)) {
			// 5.1#6 use shared key as CEK for direct encryption
			final var jwk = Objects.requireNonNull(builder.key(), "recipient must provide a key");
			final var cek = Objects.requireNonNull(jwk.getKey(), "DIRECT requires a secret key");
			if (cek.length != encryption.size / 8)
				throw new IllegalArgumentException("Invalid key size for " + encryption + " " + cek.length);
			if (contentEncryptionKey != null && !Arrays.equals(contentEncryptionKey, cek))
				throw new IllegalArgumentException(
						"Cannot specify different content encryption keys for multiple recipients");
			contentEncryptionKey = cek;
		} else if (algorithm.equals(Algorithm.ECDH_ES)) {
			final var cek = builder.agreedUponKey(encryption);
			if (contentEncryptionKey != null && !Arrays.equals(contentEncryptionKey, cek))
				throw new IllegalArgumentException(
						"Cannot specify different content encryption keys for multiple recipients");
			contentEncryptionKey = cek;
		} else if (contentEncryptionKey == null)
			contentEncryptionKey = WebKey.builder().ephemeral(encryption).build().getKey();

		// encrypt before processing header to ensure all headers are populated
		final var encryptedKey = builder.encrypt(encryption, contentEncryptionKey);

		final var header = new Jose(builder);
		final var serializedHeader = header.toJson(a -> true);

		if (!compact && !serializedHeader.keySet().containsAll(protectedParameters))
			throw new IllegalStateException(
					"Protected parameters " + protectedParameters + " are required " + serializedHeader.keySet());

		if (sharedHeader == null)
			sharedHeader = new LinkedHashMap<>(serializedHeader);
		else {
			final var sharedParamIterator = sharedHeader.entrySet().iterator();
			while (sharedParamIterator.hasNext()) {
				final var sharedParamEntry = sharedParamIterator.next();
				final var paramName = sharedParamEntry.getKey();
				if (!serializedHeader.containsKey(paramName)
						|| !sharedParamEntry.getValue().equals(serializedHeader.get(paramName)))
					sharedParamIterator.remove();
			}
		}
		for (final var paramEntry : serializedHeader.entrySet())
			sharedHeader.computeIfPresent(paramEntry.getKey(),
					(a, value) -> IuObject.equals(value, paramEntry.getValue()) ? value : null);

		recipients.add(new JweRecipient(header, encryptedKey));
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
	Iterable<JweRecipient> recipients() {
		return recipients;
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
	 * Creates the protected header from shared header and parameter protection
	 * rules.
	 * 
	 * @return {@link JsonObject} of serialized protected header values
	 */
	JsonObject protectedHeader() {
		Objects.requireNonNull(sharedHeader, "at least one recipient required");
		final var protectedHeaderBuilder = IuJson.object();
		if (compact)
			sharedHeader.forEach(protectedHeaderBuilder::add);
		else if (protectedParameters.isEmpty())
			return null;
		else
			for (final var paramName : protectedParameters)
				protectedHeaderBuilder.add(paramName, Objects.requireNonNull(sharedHeader.get(paramName), paramName));
		return protectedHeaderBuilder.build();
	}

	/**
	 * Creates the unprotected header from shared header and parameter protection
	 * rules.
	 * 
	 * @return {@link JsonObject} of serialized protected header values
	 */
	JsonObject unprotected() {
		Objects.requireNonNull(sharedHeader, "at least one recipient required");
		if (compact || recipients.size() == 1)
			return null;

		final var params = new HashSet<>(sharedHeader.keySet());
		params.removeAll(protectedParameters);
		if (params.isEmpty())
			return null;

		final var unprotectedBuilder = IuJson.object();
		for (final var paramName : params)
			unprotectedBuilder.add(paramName, sharedHeader.get(paramName));
		return unprotectedBuilder.build();
	}

	/**
	 * Gets the content encryption key, calculated/verified by
	 * {@link #addRecipient(JweRecipientBuilder)}.
	 * 
	 * @return content encryption key
	 */
	byte[] contentEncryptionKey() {
		return Objects.requireNonNull(contentEncryptionKey, "at least one recipient required");
	}

}
