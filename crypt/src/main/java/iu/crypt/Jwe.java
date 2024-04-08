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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebKey;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * JSON Web Encryption (JWE) implementation class.
 */
public class Jwe implements WebEncryption {
	static {
		IuObject.assertNotOpen(Jwe.class);
	}

	private static final Logger LOG = Logger.getLogger(Jwe.class.getName());

	private static class AesCbcHmac {
		private static byte[] macKey(byte[] cek) {
			return Arrays.copyOf(cek, cek.length / 2);
		}

		private static byte[] encKey(byte[] cek) {
			return Arrays.copyOfRange(cek, cek.length / 2, cek.length);
		}

		private final byte[] initializationVector;
		private final byte[] content;
		private final byte[] cipherText;
		private final byte[] authenticationTag;
		private final byte[] macKey;
		private final byte[] encKey;

		private AesCbcHmac(Encryption encryption, byte[] initializationVector, byte[] cipherText,
				byte[] authenticationTag, byte[] aad, byte[] cek) {
			macKey = macKey(cek);
			encKey = encKey(cek);

			final var macInput = ByteBuffer
					.wrap(new byte[aad.length + initializationVector.length + cipherText.length + 8]);
			macInput.put(aad);
			macInput.put(initializationVector);
			macInput.put(cipherText);
			EncodingUtils.bigEndian((long) aad.length * 8L, macInput);

			if (!Arrays.equals(authenticationTag, 0, authenticationTag.length, IuException.unchecked(() -> {
				final var mac = Mac.getInstance(encryption.mac);
				mac.init(new SecretKeySpec(macKey, encryption.mac));
				return mac.doFinal(macInput.array());
			}), 0, cek.length / 2))
				throw new IllegalArgumentException("Invalid authentication tag");

			this.initializationVector = initializationVector;
			this.cipherText = cipherText;
			this.authenticationTag = authenticationTag;

			content = IuException.unchecked(() -> {
				final var messageCipher = Cipher.getInstance(encryption.algorithm);
				messageCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encKey, "AES"),
						new IvParameterSpec(initializationVector));
				return messageCipher.doFinal(cipherText);
			});
		}

		private AesCbcHmac(Encryption encryption, byte[] content, byte[] cek, byte[] aad) {
			this.content = content;
			macKey = macKey(cek);
			encKey = encKey(cek);

			initializationVector = new byte[16];
			new SecureRandom().nextBytes(initializationVector);

			cipherText = IuException.unchecked(() -> {
				final var messageCipher = Cipher.getInstance(encryption.algorithm);
				messageCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encKey, "AES"),
						new IvParameterSpec(initializationVector));
				return messageCipher.doFinal(content);
			});

			final var macInput = ByteBuffer
					.wrap(new byte[aad.length + initializationVector.length + cipherText.length + 8]);
			macInput.put(aad);
			macInput.put(initializationVector);
			macInput.put(cipherText);
			EncodingUtils.bigEndian((long) aad.length * 8L, macInput);

			authenticationTag = IuException.unchecked(() -> {
				final var mac = Mac.getInstance(encryption.mac);
				mac.init(new SecretKeySpec(macKey, encryption.mac));
				final var hash = mac.doFinal(macInput.array());
				return Arrays.copyOf(hash, cek.length / 2);
			});
		}
	}

	private final Encryption encryption;
	private final boolean deflate;
	private final JsonObject protectedHeader;
	private final JsonObject unprotected;
	private final JweRecipient[] recipients;
	private final byte[] initializationVector;
	private final byte[] cipherText;
	private final byte[] authenticationTag;
	private final byte[] additionalData;

	/**
	 * Encrypts an outbound message.
	 * 
	 * @param builder {@link JweBuilder}
	 * @param in      provides the plain text data to be encrypted
	 */
	Jwe(JweBuilder builder, InputStream in) {
		encryption = builder.encryption();
		deflate = builder.deflate();
		protectedHeader = builder.protectedHeader();
		unprotected = builder.unprotected();
		recipients = IuIterable.stream(builder.recipients()).toArray(JweRecipient[]::new);

		final var cek = builder.contentEncryptionKey();

		// 5.1#11 compress content if requested
		final var content = IuException.unchecked(() -> {
			if (builder.deflate()) {
				final var deflatedContent = new ByteArrayOutputStream();
				try (final var d = new DeflaterOutputStream(deflatedContent,
						new Deflater(Deflater.DEFAULT_COMPRESSION, true /* <= RFC-1951 compliant */))) {
					IuStream.copy(in, d);
				}
				return deflatedContent.toByteArray();
			} else
				return IuStream.read(in);
		});

		// 5.1#13 encode protected header
		final var aadBuilder = new StringBuilder();
		if (protectedHeader != null)
			aadBuilder.append(UnpaddedBinary.base64Url(IuText.utf8(protectedHeader.toString())));

		// 5.1#14 calculate additional data for AEAD
		additionalData = builder.additionalData();
		if (additionalData != null)
			aadBuilder.append('.').append(UnpaddedBinary.base64Url(additionalData));
		final var aad = IuText.ascii(aadBuilder.toString());

		// 5.1#15 encrypt content
		if (encryption.mac != null) {
			final var aesCbcHmac = new AesCbcHmac(encryption, content, cek, aad);
			initializationVector = aesCbcHmac.initializationVector;
			cipherText = aesCbcHmac.cipherText;
			authenticationTag = aesCbcHmac.authenticationTag;
		} else {
			// GCM w/ 96-bit initialization vector
			initializationVector = new byte[12]; // 96 bits = 12 bytes
			new SecureRandom().nextBytes(initializationVector);

			final var encryptedData = IuException.unchecked(() -> {
				final var gcmSpec = new GCMParameterSpec(128, initializationVector);
				final var messageCipher = Cipher.getInstance(encryption.algorithm);
				messageCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), gcmSpec);
				messageCipher.updateAAD(aad);
				return messageCipher.doFinal(content);
			});

			// GCM Cipher.doFinal() returns (cipherText || authenticationTag)
			// 16 = 128-bit authenticationTag (from GCMParameterSpec), in bytes
			final var endOfTag = encryptedData.length;
			final var startOfTag = endOfTag - 16;
			cipherText = Arrays.copyOf(encryptedData, startOfTag);
			authenticationTag = Arrays.copyOfRange(encryptedData, startOfTag, endOfTag);
		}

		verifyExtensions();
	}

	/**
	 * Verifies and prepares decryption of an inbound encrypted message.
	 * 
	 * @param jwe inbound encrypted message
	 */
	public Jwe(String jwe) {
		if (jwe.charAt(0) == '{') {
			final var parsed = IuJson.parse(jwe).asJsonObject();
			protectedHeader = IuJson.get(parsed, "protected",
					IuJsonAdapter.from(v -> IuJson.parse(IuText.utf8(UnpaddedBinary.JSON.fromJson(v))).asJsonObject()));
			unprotected = IuJson.get(parsed, "unprotected", IuJsonAdapter.from(JsonValue::asJsonObject));

			if (parsed.containsKey("recipients")) {
				if (parsed.containsKey("header"))
					throw new IllegalArgumentException("Must not contain both header and recipients");
				recipients = IuJson.get(parsed, "recipients", IuJsonAdapter.of(JweRecipient[].class, IuJsonAdapter
						.from(recipient -> new JweRecipient(protectedHeader, unprotected, recipient.asJsonObject()))));
			} else
				recipients = new JweRecipient[] { new JweRecipient(protectedHeader, unprotected, parsed) };

			initializationVector = IuJson.get(parsed, "iv", UnpaddedBinary.JSON);
			cipherText = IuJson.nonNull(parsed, "cipher_text", UnpaddedBinary.JSON);
			authenticationTag = IuJson.get(parsed, "tag", UnpaddedBinary.JSON);
			additionalData = IuJson.get(parsed, "aad", UnpaddedBinary.JSON);

		} else {
			final var i = UnpaddedBinary.compact(jwe);
			protectedHeader = (JsonObject) UnpaddedBinary.compactJson(i.next());
			unprotected = null;
			recipients = new JweRecipient[] {
					new JweRecipient(new Jose(protectedHeader), UnpaddedBinary.base64Url(i.next())) };
			initializationVector = UnpaddedBinary.base64Url(i.next());
			cipherText = UnpaddedBinary.base64Url(i.next());
			authenticationTag = UnpaddedBinary.base64Url(i.next());

			if (i.hasNext())
				throw new IllegalArgumentException();
			additionalData = null;
		}

		final var header = recipients[0].getHeader();
		encryption = Objects.requireNonNull(header.getExtendedParameter("enc"), "Missing enc header parameter");
		deflate = "DEF".equals(header.getExtendedParameter("zip"));

		verifyExtensions();
	}

	private void verifyExtensions() {
		for (final var recipient : recipients)
			for (final var paramName : recipient.getHeader().extendedParameters().keySet())
				if (Param.from(paramName) == null)
					Jose.getExtension(paramName).verify(this, recipient);
	}

	@Override
	public Encryption getEncryption() {
		return encryption;
	}

	@Override
	public boolean isDeflate() {
		return deflate;
	}

	@Override
	public Stream<JweRecipient> getRecipients() {
		return Stream.of(recipients);
	}

	@Override
	public byte[] getInitializationVector() {
		return initializationVector;
	}

	@Override
	public byte[] getCipherText() {
		return cipherText;
	}

	@Override
	public byte[] getAuthenticationTag() {
		return authenticationTag;
	}

	@Override
	public byte[] getAdditionalData() {
		return additionalData;
	}

	@Override
	public void decrypt(WebKey key, OutputStream out) {
		byte[] cek = null;

		final var jwk = (Jwk) key;
		final var wellKnown = jwk.wellKnown();
		for (var i = 0; cek == null && i < recipients.length; i++)
			try {
				cek = recipients[i].decryptCek(encryption, jwk);

				// 5.2#12 record CEK decryption success
				LOG.fine("CEK decryption successful for " + wellKnown);
			} catch (Throwable e) {
				// 5.2#12 record CEK decryption failure
				LOG.log(Level.FINE, e, () -> "CEK decryption failed");
			}

		if (cek == null) {
			// see: https://datatracker.ietf.org/doc/html/rfc7516#section-11.5
			// -> proceed with a random key that will not work
			cek = new byte[encryption.size / 8];
			new SecureRandom().nextBytes(cek);
		}

		StringBuilder aadBuilder = new StringBuilder();
		if (protectedHeader != null)
			aadBuilder.append(UnpaddedBinary.base64Url(IuText.utf8(protectedHeader.toString())));

		// 5.2#14 calculate additional data for AEAD
		if (additionalData != null)
			aadBuilder.append('.').append(UnpaddedBinary.base64Url(additionalData));

		final var aad = IuText.ascii(aadBuilder.toString());

		// 5.2#15 decrypt content
		final byte[] content;
		if (encryption.mac != null)
			content = new AesCbcHmac(encryption, initializationVector, cipherText, authenticationTag, aad, cek).content;
		else {
			// GCM Cipher.doFinal() returns (cipherText || authenticationTag)
			// 16 = 128-bit authenticationTag (from GCMParameterSpec), in bytes
			final var startOfTag = cipherText.length;
			final var endOfTag = cipherText.length + authenticationTag.length;
			final var encryptedData = Arrays.copyOf(cipherText, endOfTag);
			System.arraycopy(authenticationTag, 0, encryptedData, startOfTag, authenticationTag.length);

			final var secretKey = new SecretKeySpec(cek, "AES");
			content = IuException.unchecked(() -> {
				final var gcmSpec = new GCMParameterSpec(128, initializationVector);
				final var messageCipher = Cipher.getInstance(encryption.algorithm);
				messageCipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
				messageCipher.updateAAD(aad);
				return messageCipher.doFinal(encryptedData);
			});
		}

		// 5.2#16 decompress content if requested
		final var plaintext = IuException.unchecked(() -> {
			if (deflate) {
				final var inflatedContent = new ByteArrayOutputStream();
				try (final var d = new InflaterOutputStream(inflatedContent,
						new Inflater(true /* <= RFC-1951 compliant */))) {
					d.write(content);
				}
				return inflatedContent.toByteArray();
			} else
				return content;
		});

		IuException.unchecked(() -> out.write(plaintext));
	}

	@Override
	public String compact() {
		if (recipients.length != 1 || unprotected != null || additionalData != null)
			throw new IllegalStateException(
					"Must have exactly one recipient with no unprotected header parameters, and no additional authentication data to use JWE compact serialization");
		final var recipient = recipients[0];
		return UnpaddedBinary.base64Url(IuText.utf8(Objects.requireNonNullElse(protectedHeader, "").toString())) //
				+ '.' + Objects.requireNonNullElse(UnpaddedBinary.base64Url(recipient.getEncryptedKey()), "") //
				+ '.' + UnpaddedBinary.base64Url(initializationVector) //
				+ '.' + UnpaddedBinary.base64Url(cipherText) //
				+ '.' + UnpaddedBinary.base64Url(authenticationTag);
	}

	@Override
	public String toString() {
		final var serializedHeaderBuilder = IuJson.object();

		// 5.1#13 encode protected header
		IuJson.add(serializedHeaderBuilder, "protected", () -> protectedHeader,
				IuJsonAdapter.to(v -> IuJson.string(UnpaddedBinary.base64Url(IuText.utf8(v.toString())))));
		IuJson.add(serializedHeaderBuilder, "unprotected", unprotected);

		if (recipients.length > 1) {
			final var serializedRecipients = IuJson.array();
			for (final var additionalRecipient : this.recipients) {
				final var recipientBuilder = IuJson.object();
				final var perRecipientHeader = additionalRecipient.getHeader().toJson(a -> isPerRecipient(a));
				if (!perRecipientHeader.isEmpty())
					recipientBuilder.add("header", perRecipientHeader);
				IuJson.add(recipientBuilder, "encrypted_key", additionalRecipient::getEncryptedKey,
						UnpaddedBinary.JSON);
				serializedRecipients.add(recipientBuilder);
			}

			IuJson.add(serializedHeaderBuilder, "recipients", serializedRecipients);
		} else {
			final var recipient = recipients[0];
			final var header = recipient.getHeader();
			if (header != null) {
				final var perRecipient = header.toJson(this::isUnprotected);
				if (perRecipient != null)
					serializedHeaderBuilder.add("header", perRecipient);
			}
			IuJson.add(serializedHeaderBuilder, "encrypted_key", recipients[0]::getEncryptedKey, UnpaddedBinary.JSON);
		}

		IuJson.add(serializedHeaderBuilder, "iv", () -> initializationVector, UnpaddedBinary.JSON);
		IuJson.add(serializedHeaderBuilder, "cipher_text", () -> cipherText, UnpaddedBinary.JSON);
		IuJson.add(serializedHeaderBuilder, "tag", () -> authenticationTag, UnpaddedBinary.JSON);
		IuJson.add(serializedHeaderBuilder, "aad", () -> additionalData, UnpaddedBinary.JSON);

		return serializedHeaderBuilder.build().toString();
	}

	/**
	 * Returns true a parameter name is not present in the protected or unprotected
	 * shared header.
	 * 
	 * @param paramName parameter name
	 * @return false if present in the protected or unprotected shared header; else
	 *         true
	 */
	boolean isUnprotected(String paramName) {
		return protectedHeader == null //
				|| !protectedHeader.containsKey(paramName);
	}

	/**
	 * Returns true a parameter name is not present in the protected or unprotected
	 * shared header.
	 * 
	 * @param paramName parameter name
	 * @return false if present in the protected or unprotected shared header; else
	 *         true
	 */
	boolean isPerRecipient(String paramName) {
		return isUnprotected(paramName) //
				&& (unprotected == null //
						|| !unprotected.containsKey(paramName));
	}

}
