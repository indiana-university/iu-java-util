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
package iu.crypt;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
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
import edu.iu.client.IuJsonProperties;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebKey;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

/**
 * JSON Web Encryption (JWE) implementation class.
 */
public class Jwe implements WebEncryption {
	static {
		IuObject.assertNotOpen(Jwe.class);
	}

	private static final Logger LOG = Logger.getLogger(Jwe.class.getName());

	private static final Set<String> GENERAL = Set.of("protected", "unprotected", "recipients", "iv", "ciphertext",
			"tag", "aad");

	private static final Set<String> FLATTENED = Set.of("protected", "unprotected", "header", "encrypted_key", "iv",
			"ciphertext", "tag", "aad");

	private static final Set<String> RECIPIENT = Set.of("header", "encrypted_key");

//	/** {@link IuJsonAdapter} */
//	public static final IuJsonAdapter<WebEncryption> JSON = IuJsonAdapter.from(v -> {
//		if (v instanceof JsonString)
//			return new Jwe(((JsonString) v).getString());
//		else
//			return IuObject.convert(v, a -> new Jwe(a.asJsonObject().toString()));
//	}, h -> {
//		if (h == null)
//			return null;
//		final var jwe = (Jwe) h;
//		if (jwe.recipients.length != 1 //
//				|| jwe.additionalData != null)
//			return IuJson.parse(jwe.toString());
//		else
//			return IuJson.string(jwe.compact());
//	});

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

	private static Map<String, Object> createSharedHeader(Iterable<JweRecipient> recipients) {
		final Map<String, Object> values = new LinkedHashMap<>();

		var first = true;
		for (final var recipient : recipients) {

			final var serializedHeader = recipient.getHeader().values(a -> true);
			for (final var key : serializedHeader.nonNullNames()) {
				final var param = Param.from(key);
				final var type = param == null ? Jose.getExtension(key).type() : param.type;
				final var value = serializedHeader.get(key, type);
				if (first)
					values.put(key, value);
				else if (!IuObject.equals(value, values.get(key)))
					values.remove(key);
			}

			first = false;
		}

		if (first)
			return null;
		else
			return values;
	}

	private final Encryption encryption;
	private final boolean deflate;
	private final String encodedProtectedHeader;
	private final IuJsonProperties protectedHeader;
	private final IuJsonProperties unprotected;
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

			// one recipient, whose header the builder verified has the protected
			// parameters: all are shared, and protected

			final var builder = CryptJsonAdapters.builder();
			sharedHeader.forEach(builder::put);
			protectedHeader = builder.build();

			unprotected = null;

		} else {
			if (!protectedParameters.isEmpty()) {

				final var protectedHeader = CryptJsonAdapters.builder();
				for (final var paramName : protectedParameters) {
					final var value = sharedHeader.remove(paramName);
					if (value == null)
						throw new IllegalArgumentException("header missing protected parameter " + paramName);
					protectedHeader.put(paramName, value);
				}

				this.protectedHeader = protectedHeader.build();
			} else
				protectedHeader = null;

			if (sharedHeader.isEmpty())
				unprotected = null;
			else {
				final var builder = CryptJsonAdapters.builder();
				sharedHeader.forEach(builder::put);
				unprotected = builder.build();
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
		encodedProtectedHeader = CompactEncoded.encodeHeader(protectedHeader);

		// 5.1#14 calculate additional data for AEAD
		final var aad = aad();

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

	private Jwe(String encodedProtectedHeader, IuJsonProperties protectedHeader, IuJsonProperties unprotected,
			JweRecipient[] recipients, byte[] initializationVector, byte[] cipherText, byte[] authenticationTag,
			byte[] additionalData) {
		this.encodedProtectedHeader = encodedProtectedHeader;
		this.protectedHeader = protectedHeader;
		this.unprotected = unprotected;
		this.recipients = recipients;
		this.initializationVector = initializationVector;
		this.cipherText = cipherText;
		this.authenticationTag = authenticationTag;
		this.additionalData = additionalData;

		final var header = recipients[0].getHeader();
		encryption = Objects.requireNonNull(header.getExtendedParameter("enc"), "Missing enc header parameter");
		deflate = "DEF".equals(header.getExtendedParameter("zip"));

		verifyExtensions();
	}

	/**
	 * {@link JsonbDeserializer} handle method.
	 *
	 * <p>
	 * Reads either JSON serialization: general, with a "recipients" array, or
	 * flattened, with a single recipient's members at the top level.
	 * </p>
	 *
	 * @param parser  {@link JsonParser}
	 * @param context {@link DeserializationContext}
	 * @param type    {@link Type}
	 * @return parsed JWE
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7516#section-7.2">RFC-7516
	 *      JWE Section 7.2</a>
	 */
	static Jwe deserialize(JsonParser parser, DeserializationContext context, Type type) {
		if (Event.VALUE_NULL.equals(parser.currentEvent()))
			return null;

		final var jwe = context.deserialize(IuJsonProperties.class, parser);
		final var encodedProtectedHeader = jwe.get("protected", String.class);
		final var protectedHeader = CompactEncoded.decodeHeader(encodedProtectedHeader);
		final var unprotected = jwe.get("unprotected", IuJsonProperties.class);

		// the flattened form's recipient members are at the top level
		final IuJsonProperties[] serializedRecipients;
		if (jwe.containsKey("recipients")) {
			serializedRecipients = Objects.requireNonNull(
					jwe.requireOnly(GENERAL).get("recipients", IuJsonProperties[].class), "recipients");
			if (serializedRecipients.length == 0)
				throw new JsonbException("at least one recipient is required");
			for (final var serializedRecipient : serializedRecipients)
				serializedRecipient.requireOnly(RECIPIENT);
		} else
			serializedRecipients = new IuJsonProperties[] { jwe.requireOnly(FLATTENED) };

		final var recipients = new JweRecipient[serializedRecipients.length];
		for (var i = 0; i < recipients.length; i++)
			recipients[i] = new JweRecipient(protectedHeader, unprotected,
					serializedRecipients[i].get("header", IuJsonProperties.class),
					serializedRecipients[i].get("encrypted_key", byte[].class));

		return new Jwe(encodedProtectedHeader, protectedHeader, unprotected, recipients,
				jwe.get("iv", byte[].class), jwe.get("ciphertext", byte[].class), jwe.get("tag", byte[].class),
				jwe.get("aad", byte[].class));
	}

	/**
	 * Verifies and prepares decryption of an inbound encrypted message.
	 * 
	 * @param jwe inbound encrypted message, compact or JSON serialized
	 * @return parsed JWE
	 */
	public static Jwe parse(String jwe) {
		if (jwe.charAt(0) == '{') {
			return (Jwe) CryptJsonAdapters.JSONB.fromJson(jwe, WebEncryption.class);

		} else {
			final var i = CompactEncoded.compact(jwe);
			final var encodedProtectedHeader = Objects.requireNonNull(i.next(), "protected header required");
			final var protectedHeader = CompactEncoded.decodeHeader(encodedProtectedHeader);
			final var recipients = new JweRecipient[] {
					new JweRecipient(new Jose(protectedHeader), IuText.base64Url(i.next())) };
			final var initializationVector = IuText.base64Url(i.next());
			final var cipherText = IuText.base64Url(i.next());
			final var authenticationTag = IuText.base64Url(i.next());

			if (i.hasNext())
				throw new IllegalArgumentException("Invalid compact format, found more than 5 segments");

			return new Jwe(encodedProtectedHeader, protectedHeader, null, recipients, initializationVector, cipherText,
					authenticationTag, null);
		}

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

		// 5.2#14 calculate additional data for AEAD
		final var aad = aad();

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
		return Objects.requireNonNullElse(encodedProtectedHeader, "") //
				+ '.' +Objects.requireNonNullElse(IuText.base64Url(recipient.getEncryptedKey()), "") //
				+ '.' + IuText.base64Url(initializationVector) //
				+ '.' + IuText.base64Url(cipherText) //
				+ '.' + IuText.base64Url(authenticationTag);
	}

	@Override
	public String toString() {
		return CryptJsonAdapters.JSONB.toJson(this);
	}

	/**
	 * {@link JsonbSerializer} handle method.
	 *
	 * <p>
	 * Writes flattened for a single recipient, general otherwise.
	 * </p>
	 *
	 * @param generator {@link JsonGenerator}
	 * @param context   {@link SerializationContext}
	 */
	void serialize(JsonGenerator generator, SerializationContext context) {
		BiConsumer<String, Object> write = (key, value) -> {
			if (value != null) {
				generator.writeKey(key);
				context.serialize(value, generator);
			}
		};

		generator.writeStartObject();

		write.accept("protected", encodedProtectedHeader);

		if (recipients.length > 1) {
			write.accept("unprotected", unprotected);

			generator.writeKey("recipients");
			generator.writeStartArray();
			for (final var additionalRecipient : this.recipients) {
				generator.writeStartObject();
				write.accept("header", additionalRecipient.getHeader().values(this::isPerRecipient));

				write.accept("encrypted_key", additionalRecipient.getEncryptedKey());
				generator.writeEnd();
			}
			generator.writeEnd();

		} else {
			write.accept("header", unprotected);
			write.accept("encrypted_key", recipients[0].getEncryptedKey());
		}

		write.accept("iv", initializationVector);
		write.accept("ciphertext", cipherText);
		write.accept("tag", authenticationTag);
		write.accept("aad", additionalData);

		generator.writeEnd();
	}

	/**
	 * Computes the additional authenticated data from the encoded protected
	 * header, as it was received or will be sent.
	 *
	 * @return additional authenticated data
	 */
	private byte[] aad() {
		final var aadBuilder = new StringBuilder();
		if (encodedProtectedHeader != null)
			aadBuilder.append(encodedProtectedHeader);
		if (additionalData != null)
			aadBuilder.append('.').append(IuText.base64Url(additionalData));
		return IuText.ascii(aadBuilder.toString());
	}

	private boolean isUnprotected(String paramName) {
		return protectedHeader == null //
				|| !protectedHeader.nonNullNames().contains(paramName);
	}

	private boolean isPerRecipient(String paramName) {
		return isUnprotected(paramName) //
				&& (unprotected == null //
						|| !unprotected.nonNullNames().contains(paramName));
	}

}
