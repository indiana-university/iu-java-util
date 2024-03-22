package iu.crypt;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
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
import edu.iu.IuObject;
import edu.iu.IuStream;
import edu.iu.client.IuJson;
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

	private final Logger LOG = Logger.getLogger(Jwe.class.getName());

	private final String protectedHeader;
	private final Encryption encryption;
	private final boolean deflate;
	private final JweRecipient[] recipients;
	private final Set<Param> protectedParameters;
	private final byte[] initializationVector;
	private final byte[] cipherText;
	private final byte[] authenticationTag;
	private final byte[] additionalData;

	/**
	 * Encrypts an outbound message.
	 * 
	 * @param builder {@link JweBuilder}
	 * @param in      provides the plaintext data to be encrypted
	 */
	Jwe(JweBuilder builder, InputStream in) {
		encryption = Objects.requireNonNull(builder.encryption(), "Content encryption algorithm is required");
		deflate = builder.deflate();

		final var protectedParameters = builder.protectedParameters();
		if (protectedParameters == null)
			if (deflate)
				this.protectedParameters = Set.of(Param.ALGORITHM, Param.ENCRYPTION, Param.ZIP);
			else
				this.protectedParameters = Set.of(Param.ALGORITHM, Param.ENCRYPTION);
		else
			this.protectedParameters = protectedParameters;

		final var cek = builder
				.cek(Objects.requireNonNull(builder.recipients().findFirst().get(), "requires at least one recipient"));

		this.recipients = builder.recipients().map(r -> r.build(this, cek)).toArray(JweRecipient[]::new);
		final var recipient = recipients[0];
		final var jose = recipient.getHeader();

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
		protectedHeader = EncodingUtils.base64Url(EncodingUtils.utf8(jose.toJson(recipient::isProtected).toString()));

		// 5.1#14 encode protected header
		this.additionalData = builder.additionalData();
		final var aad = IuException.unchecked(() -> {
			if (additionalData != null)
				return (protectedHeader + '.' + EncodingUtils.base64Url(additionalData)).getBytes("US-ASCII");
			else
				return protectedHeader.getBytes("US-ASCII");
		});

		// 5.1#15 encrypt content
		if (encryption.mac != null) {
			// AES_CBC_HMAC

			final var bytelen = encryption.size / 8;
			final var keylen = bytelen / 2;
			final var macKey = Arrays.copyOf(cek, keylen);
			final var encKey = Arrays.copyOfRange(cek, keylen, bytelen);

			initializationVector = new byte[16];
			new SecureRandom().nextBytes(initializationVector);

			cipherText = IuException.unchecked(() -> {
				final var messageCipher = Cipher.getInstance(encryption.cipherAlgorithm);
				messageCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encKey, encryption.keyAlgorithm),
						new IvParameterSpec(initializationVector));
				return messageCipher.doFinal(content);
			});

			// MAC input = aad || initializationVector || cipherText ||
			// big_endian_u64(bitlen(cipherText))
			final byte[] macInput = new byte[aad.length + initializationVector.length + cipherText.length + 8];
			var pos = 0;

			System.arraycopy(aad, 0, macInput, pos, aad.length);
			pos += aad.length;

			System.arraycopy(initializationVector, 0, macInput, pos, initializationVector.length);
			pos += initializationVector.length;

			System.arraycopy(cipherText, 0, macInput, pos, cipherText.length);
			pos += cipherText.length;

			EncodingUtils.bigEndian((long) aad.length * 8L, macInput, pos);

			authenticationTag = IuException.unchecked(() -> {
				final var mac = Mac.getInstance(encryption.mac);
				mac.init(new SecretKeySpec(macKey, encryption.mac));
				final var hash = mac.doFinal(macInput);
				return Arrays.copyOf(hash, keylen);
			});
		} else {
			// GCM w/ 96-bit initialization vector
			initializationVector = new byte[12]; // 96 bits = 12 bytes
			new SecureRandom().nextBytes(initializationVector);

			final var encryptedData = IuException.unchecked(() -> {
				final var gcmSpec = new GCMParameterSpec(128, initializationVector);
				final var messageCipher = Cipher.getInstance(encryption.cipherAlgorithm);
				messageCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, encryption.keyAlgorithm), gcmSpec);
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
	}

	/**
	 * Verifies and prepares decryption of an inbound encrypted message.
	 * 
	 * @param jwe inbound encrypted message
	 */
	public Jwe(String jwe) {
		final JsonObject parsedProtectedHeader;
		if (jwe.charAt(0) == '{') {
			final var parsed = IuJson.parse(jwe).asJsonObject();
			protectedHeader = parsed.getString("protected");
			parsedProtectedHeader = EncodingUtils.compactJson(protectedHeader);

			final var unprotectedHeader = IuJson.get(parsed, "unprotected", JsonValue::asJsonObject);

			if (parsed.containsKey("recipients")) {
				if (parsed.containsKey("header"))
					throw new IllegalArgumentException("Must not contain both header and recipients");

				recipients = parsed.getJsonArray("recipients").stream().map(JsonValue::asJsonObject) //
						.map(recipient -> new JweRecipient(this, parsedProtectedHeader, unprotectedHeader, recipient))
						.toArray(JweRecipient[]::new);
			} else
				recipients = new JweRecipient[] {
						new JweRecipient(this, parsedProtectedHeader, unprotectedHeader, parsed) };

			initializationVector = IuJson.text(parsed, "iv", EncodingUtils::base64Url);
			cipherText = EncodingUtils.base64Url(parsed.getString("ciphertext"));
			authenticationTag = IuJson.text(parsed, "tag", EncodingUtils::base64Url);
			additionalData = IuJson.text(parsed, "aad", EncodingUtils::base64Url);

		} else {
			final var i = EncodingUtils.compact(jwe);
			protectedHeader = i.next();
			parsedProtectedHeader = EncodingUtils.compactJson(protectedHeader);
			recipients = new JweRecipient[] {
					new JweRecipient(this, Jose.from(parsedProtectedHeader), EncodingUtils.base64Url(i.next())) };
			initializationVector = EncodingUtils.base64Url(i.next());
			cipherText = EncodingUtils.base64Url(i.next());
			authenticationTag = EncodingUtils.base64Url(i.next());

			if (i.hasNext())
				throw new IllegalArgumentException();
			additionalData = null;
		}

		final var ext = recipients[0].getHeader().getExtendedParameters();
		encryption = Encryption.from(Objects.requireNonNull((String) ext.get("enc"), "Missing enc header parameter"));
		deflate = "DEF".equals(ext.get("zip"));

		protectedParameters = parsedProtectedHeader.keySet().stream() //
				.map(Param::from).filter(Objects::nonNull) //
				.collect(Collectors.toUnmodifiableSet());
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
				cek = recipients[i].decryptCek(jwk);

				// 5.2#12 record CEK decryption success
				LOG.fine("CEK decryption successful for " + wellKnown);
			} catch (Throwable e) {
				// 5.2#12 record CEK decryption failure
				LOG.log(Level.FINE, e, () -> "CEK decryption failed");
			}

		if (cek == null) {
			// see: https://datatracker.ietf.org/doc/html/rfc7516#section-11.5
			// -> proceed with a random key that will not work
			cek = new byte[encryption.size];
			new SecureRandom().nextBytes(cek);
		}

		// 5.2#15 encode protected header
		final var aad = IuException.unchecked(() -> {
			if (additionalData != null)
				return (protectedHeader + '.' + EncodingUtils.base64Url(additionalData)).getBytes("US-ASCII");
			else
				return protectedHeader.getBytes("US-ASCII");
		});

		// 5.2#15 decrypt content
		final byte[] content;
		if (encryption.mac != null) {
			// AES_CBC_HMAC

			final var bytelen = encryption.size / 8;
			final var keylen = bytelen / 2;
			final var macKey = Arrays.copyOf(cek, keylen);
			final var encKey = Arrays.copyOfRange(cek, keylen, bytelen);

			// MAC input = aad || initializationVector || cipherText ||
			// big_endian_u64(bitlen(cipherText))
			final byte[] macInput = new byte[aad.length + initializationVector.length + cipherText.length + 8];
			var pos = 0;

			System.arraycopy(aad, 0, macInput, pos, aad.length);
			pos += aad.length;

			System.arraycopy(initializationVector, 0, macInput, pos, initializationVector.length);
			pos += initializationVector.length;

			System.arraycopy(cipherText, 0, macInput, pos, cipherText.length);
			pos += cipherText.length;

			EncodingUtils.bigEndian((long) aad.length * 8L, macInput, pos);

			if (!Arrays.equals(authenticationTag, IuException.unchecked(() -> {
				final var mac = Mac.getInstance(encryption.mac);
				mac.init(new SecretKeySpec(macKey, encryption.mac));
				final var hash = mac.doFinal(macInput);
				return Arrays.copyOf(hash, keylen);
			})))
				throw new IllegalStateException(encryption + " failure");

			content = IuException.unchecked(() -> {
				final var messageCipher = Cipher.getInstance(encryption.cipherAlgorithm);
				messageCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encKey, encryption.keyAlgorithm),
						new IvParameterSpec(initializationVector));
				return messageCipher.doFinal(cipherText);
			});

		} else {
			// GCM Cipher.doFinal() returns (cipherText || authenticationTag)
			// 16 = 128-bit authenticationTag (from GCMParameterSpec), in bytes
			final var startOfTag = cipherText.length;
			final var endOfTag = cipherText.length + authenticationTag.length;
			final var encryptedData = Arrays.copyOf(cipherText, endOfTag);
			System.arraycopy(authenticationTag, 0, encryptedData, startOfTag, authenticationTag.length);

			final var secretKey = new SecretKeySpec(cek, encryption.keyAlgorithm);
			content = IuException.unchecked(() -> {
				final var gcmSpec = new GCMParameterSpec(128, initializationVector);
				final var messageCipher = Cipher.getInstance(encryption.cipherAlgorithm);
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

	/**
	 * Determines if a header parameter should be included in the protected header.
	 * 
	 * @param paramName parameter name
	 * @return true if the parameter should be included in the protected header;
	 *         else false
	 */
	boolean isProtected(String paramName) {
		final var param = Param.from(paramName);
		if (param == null)
			return false;
		else
			return protectedParameters.contains(param);
	}

	@Override
	public String toString() {
		final var b = IuJson.object();

		final var recipient = recipients[0];
		final var header = recipients[0].getHeader();
		final var protectedHeader = header.toJson(recipients[0]::isProtected);

		final Map<String, Object> sharedParams = new LinkedHashMap<>();
		for (final var p : Param.values()) {
			if (protectedParameters.contains(p))
				continue;

			final var v = p.get(header);
			if (v != null)
				sharedParams.put(p.name, v);
		}

		for (final var e : header.getExtendedParameters().entrySet()) {
			final var paramName = e.getKey();
			if (!recipient.isProtected(paramName))
				sharedParams.put(paramName, e.getValue());
		}

		// 5.1#13 encode protected header
		if (!protectedHeader.isEmpty())
			b.add("protected", EncodingUtils.base64Url(EncodingUtils.utf8(protectedHeader.toString())));

		if (recipients.length > 1) {
			for (int i = 1; i < recipients.length; i++) {
				final var additionalRecipient = recipients[i];
				final var additionalHeader = additionalRecipient.getHeader();

				final var sharedEntries = sharedParams.entrySet().iterator();
				final var additionalExt = additionalHeader.getExtendedParameters();
				while (sharedEntries.hasNext()) {
					final var sharedEntry = sharedEntries.next();
					final var paramName = sharedEntry.getKey();
					final var param = Param.from(paramName);
					final var value = sharedEntry.getValue();
					if (param != null) {
						if (!IuObject.equals(param.get(additionalHeader), value))
							sharedEntries.remove();
					} else if (!IuObject.equals(additionalExt.get(paramName), value))
						sharedEntries.remove();
				}

				for (final var p : Param.values())
					if (!protectedParameters.contains(p) //
							&& !IuObject.equals(p.get(header), sharedParams.get(p.name)))
						sharedParams.remove(p.name);
			}

			if (!sharedParams.isEmpty()) {
				final var unprotectedHeader = header.toJson(sharedParams::containsKey);
				b.add("unprotected", unprotectedHeader);
			}

			final var serializedRecipients = IuJson.array();
			for (final var additionalRecipient : this.recipients) {
				final var recipientBuilder = IuJson.object();
				final var perRecipientHeader = additionalRecipient.getHeader()
						.toJson(a -> !sharedParams.containsKey(a));
				if (!perRecipientHeader.isEmpty())
					recipientBuilder.add("header", perRecipientHeader);
				IuJson.add(recipientBuilder, a -> true, "encrypted_key",
						() -> EncodingUtils.base64Url(additionalRecipient.getEncryptedKey()));
				serializedRecipients.add(recipientBuilder);
			}
		} else {
			final var perRecipientHeader = header.toJson(sharedParams::containsKey);
			if (!perRecipientHeader.isEmpty())
				b.add("header", perRecipientHeader);
			IuJson.add(b, a -> true, "encrypted_key", () -> EncodingUtils.base64Url(recipients[0].getEncryptedKey()));
		}

		IuJson.add(b, "iv", () -> EncodingUtils.base64Url(initializationVector));
		IuJson.add(b, "ciphertext", () -> EncodingUtils.base64Url(cipherText));
		IuJson.add(b, "tag", () -> EncodingUtils.base64Url(authenticationTag));
		if (additionalData != null)
			IuJson.add(b, "aad", () -> EncodingUtils.base64Url(additionalData));

		return b.build().toString();
	}

}
