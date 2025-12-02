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
package iu.crypt;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

import javax.crypto.AEADBadTagException;
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
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * JSON Web Encryption (JWE) implementation class.
 */
public class Jwe implements WebEncryption {
	static {
		IuObject.assertNotOpen(Jwe.class);
	}

	private static final Logger LOG = Logger.getLogger(Jwe.class.getName());

	/** {@link IuJsonAdapter} */
	public static final IuJsonAdapter<WebEncryption> JSON = IuJsonAdapter.from(v -> {
		if (v instanceof JsonString)
			return new Jwe(((JsonString) v).getString());
		else
			return IuObject.convert(v, a -> new Jwe(a.asJsonObject().toString()));
	}, h -> {
		if (h == null)
			return null;
		final var jwe = (Jwe) h;
		if (jwe.recipients.length != 1 //
				|| jwe.additionalData != null)
			return IuJson.parse(jwe.toString());
		else
			return IuJson.string(jwe.compact());
	});

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
				throw new IllegalStateException("Invalid authentication tag",
						new AEADBadTagException("AES/CBC/HMAC verification failure"));

			this.initializationVector = initializationVector;
			this.cipherText = cipherText;
			this.authenticationTag = authenticationTag;

			content = IuException.unchecked(() -> {
				final var messageCipher = Cipher.getInstance(encryption.algorithm);

				if (initializationVector.length != messageCipher.getBlockSize())
					throw new IllegalArgumentException("invalid initialization vector");

				messageCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encKey, "AES"),
						new IvParameterSpec(initializationVector));
				return messageCipher.doFinal(cipherText);
			});
		}

		private AesCbcHmac(Encryption encryption, byte[] content, byte[] cek, byte[] aad) {
			this.content = content;
			macKey = macKey(cek);
			encKey = encKey(cek);

			final var messageCipher = IuException.unchecked(() -> Cipher.getInstance(encryption.algorithm));
			initializationVector = new byte[messageCipher.getBlockSize()];
			new SecureRandom().nextBytes(initializationVector);

			cipherText = IuException.unchecked(() -> {
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

	private static Map<String, JsonValue> createSharedHeader(Iterable<JweRecipient> recipients) {
		Map<String, JsonValue> sharedHeader = null;
		for (final var recipient : recipients) {
			final var serializedHeader = recipient.getHeader().toJson(a -> true);
			if (sharedHeader == null)
				sharedHeader = new LinkedHashMap<>(serializedHeader);
			else
				for (final var paramEntry : serializedHeader.entrySet())
					sharedHeader.computeIfPresent(paramEntry.getKey(),
							(a, value) -> IuObject.equals(value, paramEntry.getValue()) ? value : null);
		}
		return sharedHeader;
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
	 * @param encryption           content encryption algorithm
	 * @param deflate              true to compress content before encryption; false
	 *                             to encrypt uncompressed
	 * @param compact              true to verify as compact serializable
	 * @param protectedParameters  parameter names to enforce as shared and include
	 *                             in the protected header, ignored when compact =
	 *                             true
	 * @param recipients           message recipients
	 * @param contentEncryptionKey content encryption key
	 * @param additionalData       AEAD additional authentication data
	 * @param in                   provides the plain text data to be encrypted
	 */
	Jwe(Encryption encryption, boolean deflate, boolean compact, Set<String> protectedParameters,
			Iterable<JweRecipient> recipients, byte[] contentEncryptionKey, byte[] additionalData, InputStream in) {
		this.encryption = encryption;
		this.deflate = deflate;
		this.additionalData = additionalData;

		final var sharedHeader = Objects.requireNonNull(createSharedHeader(recipients),
				"at least one recipient required");

		if (compact) {
			final var recipientIterator = recipients.iterator();
			recipientIterator.next();
			if (recipientIterator.hasNext())
				throw new IllegalArgumentException("cannot specifiy compact for more than one recipient");
			IuObject.require(additionalData, Objects::isNull, () -> "cannot specify compact with additionalData");

			if (!sharedHeader.keySet().containsAll(protectedParameters))
				throw new IllegalArgumentException("protected parameters " + protectedParameters + " are required");

			final var protectedHeaderBuilder = IuJson.object();
			sharedHeader.forEach(protectedHeaderBuilder::add);
			protectedHeader = protectedHeaderBuilder.build();
			unprotected = null;

		} else {
			if (!protectedParameters.isEmpty()) {
				final var protectedHeaderBuilder = IuJson.object();
				for (final var paramName : protectedParameters)
					protectedHeaderBuilder.add(paramName,
							Objects.requireNonNull(sharedHeader.remove(paramName), paramName));
				protectedHeader = protectedHeaderBuilder.build();
			} else
				protectedHeader = null;

			if (sharedHeader.isEmpty())
				unprotected = null;
			else {
				final var unprotectedBuilder = IuJson.object();
				sharedHeader.forEach(unprotectedBuilder::add);
				unprotected = unprotectedBuilder.build();
			}
		}

		this.recipients = IuIterable.stream(recipients).toArray(JweRecipient[]::new);

		// 5.1#11 compress content if requested
		final var content = IuException.unchecked(() -> {
			if (deflate) {
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
			aadBuilder.append(IuText.base64Url(IuText.utf8(protectedHeader.toString())));

		// 5.1#14 calculate additional data for AEAD
		if (additionalData != null)
			aadBuilder.append('.').append(IuText.base64Url(additionalData));
		final var aad = IuText.ascii(aadBuilder.toString());

		// 5.1#15 encrypt content
		if (encryption.mac != null) {
			final var aesCbcHmac = new AesCbcHmac(encryption, content, contentEncryptionKey, aad);
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
				messageCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentEncryptionKey, "AES"), gcmSpec);
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
			protectedHeader = IuJson.get(parsed, "protected", IuJsonAdapter
					.from(v -> IuJson.parse(IuText.utf8(CryptJsonAdapters.B64URL.fromJson(v))).asJsonObject()));
			unprotected = IuJson.get(parsed, "unprotected", IuJsonAdapter.from(JsonValue::asJsonObject));

			if (parsed.containsKey("recipients")) {
				if (parsed.containsKey("header"))
					throw new IllegalArgumentException("Must not contain both header and recipients");
				recipients = IuJson.get(parsed, "recipients", IuJsonAdapter.of(JweRecipient[].class, IuJsonAdapter
						.from(recipient -> new JweRecipient(protectedHeader, unprotected, recipient.asJsonObject()))));
			} else
				recipients = new JweRecipient[] { new JweRecipient(protectedHeader, unprotected, parsed) };

			initializationVector = IuJson.get(parsed, "iv", CryptJsonAdapters.B64URL);
			cipherText = IuJson.nonNull(parsed, "cipher_text", CryptJsonAdapters.B64URL);
			authenticationTag = IuJson.get(parsed, "tag", CryptJsonAdapters.B64URL);
			additionalData = IuJson.get(parsed, "aad", CryptJsonAdapters.B64URL);

		} else {
			final var i = CompactEncoded.compact(jwe);
			protectedHeader = (JsonObject) IuJson.parse(IuText.utf8(IuText.base64(i.next())));
			unprotected = null;
			recipients = new JweRecipient[] {
					new JweRecipient(IuObject.convert(protectedHeader, Jose::new), IuText.base64Url(i.next())) };
			initializationVector = IuText.base64Url(i.next());
			cipherText = IuText.base64Url(i.next());
			authenticationTag = IuText.base64Url(i.next());

			if (i.hasNext())
				throw new IllegalArgumentException("Invalid compact format, found more than 5 segments");
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
	public Iterable<JweRecipient> getRecipients() {
		return IuIterable.iter(recipients);
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

		if (cek == null)
			// see: https://datatracker.ietf.org/doc/html/rfc7516#section-11.5
			// -> proceed with a random key that will not work
			cek = WebKey.ephemeral(encryption).getKey();

		StringBuilder aadBuilder = new StringBuilder();
		if (protectedHeader != null)
			aadBuilder.append(IuText.base64Url(IuText.utf8(protectedHeader.toString())));

		// 5.2#14 calculate additional data for AEAD
		if (additionalData != null)
			aadBuilder.append('.').append(IuText.base64Url(additionalData));

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

			if (initializationVector.length != 12)
				throw new IllegalArgumentException("invalid initialization vector");

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
		if (recipients.length != 1 //
				|| additionalData != null)
			throw new IllegalStateException(
					"Must have exactly one recipient with no additional authentication data to use JWE compact serialization");
		final var recipient = recipients[0];
		return IuText.base64Url(IuText.utf8(Objects.requireNonNullElse(protectedHeader, "").toString())) //
				+ '.' + Objects.requireNonNullElse(IuText.base64Url(recipient.getEncryptedKey()), "") //
				+ '.' + IuText.base64Url(initializationVector) //
				+ '.' + IuText.base64Url(cipherText) //
				+ '.' + IuText.base64Url(authenticationTag);
	}

	@Override
	public String toString() {
		final var serializedHeaderBuilder = IuJson.object();

		// 5.1#13 encode protected header
		IuJson.add(serializedHeaderBuilder, "protected", () -> protectedHeader,
				IuJsonAdapter.to(v -> IuJson.string(IuText.base64Url(IuText.utf8(v.toString())))));

		if (recipients.length > 1) {
			IuJson.add(serializedHeaderBuilder, "unprotected", unprotected);

			final var serializedRecipients = IuJson.array();
			for (final var additionalRecipient : this.recipients) {
				final var recipientBuilder = IuJson.object();
				final var perRecipientHeader = additionalRecipient.getHeader().toJson(a -> isPerRecipient(a));
				if (perRecipientHeader != null)
					recipientBuilder.add("header", perRecipientHeader);
				IuJson.add(recipientBuilder, "encrypted_key", additionalRecipient::getEncryptedKey,
						CryptJsonAdapters.B64URL);
				serializedRecipients.add(recipientBuilder);
			}

			IuJson.add(serializedHeaderBuilder, "recipients", serializedRecipients);
		} else {
			if (unprotected != null)
				serializedHeaderBuilder.add("header", unprotected);
			IuJson.add(serializedHeaderBuilder, "encrypted_key", recipients[0]::getEncryptedKey,
					CryptJsonAdapters.B64URL);
		}

		IuJson.add(serializedHeaderBuilder, "iv", () -> initializationVector, CryptJsonAdapters.B64URL);
		IuJson.add(serializedHeaderBuilder, "cipher_text", () -> cipherText, CryptJsonAdapters.B64URL);
		IuJson.add(serializedHeaderBuilder, "tag", () -> authenticationTag, CryptJsonAdapters.B64URL);
		IuJson.add(serializedHeaderBuilder, "aad", () -> additionalData, CryptJsonAdapters.B64URL);

		return serializedHeaderBuilder.build().toString();
	}

	private boolean isUnprotected(String paramName) {
		return protectedHeader == null //
				|| !protectedHeader.containsKey(paramName);
	}

	private boolean isPerRecipient(String paramName) {
		return isUnprotected(paramName) //
				&& (unprotected == null //
						|| !unprotected.containsKey(paramName));
	}

}
