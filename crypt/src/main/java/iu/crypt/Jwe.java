package iu.crypt;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

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
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * JSON Web Encryption (JWE) implementation class.
 */
public class Jwe implements WebEncryption {
	static {
		IuObject.assertNotOpen(Jwe.class);
	}

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
					IuStream.copy(in, deflatedContent);
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
			final var macKey = Arrays.copyOf(cek, bytelen);
			final var encKey = Arrays.copyOfRange(cek, bytelen, bytelen * 2);

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
				return Arrays.copyOf(hash, bytelen);
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

//	private static class AesGcm {
//		private final SecretKey cek;
//		private final SecureRandom rand = new SecureRandom();
//		private final byte[] fixed = new byte[4];
//		private final byte[] iv = new byte[12];
//		private int c;
//
//		private AesGcm(SecretKey cek) {
//			this.cek = cek;
//			rand.nextBytes(fixed);
//		}
//
//		private GCMParameterSpec spec() {
//			if (c == -1)
//				throw new IllegalStateException();
//			else
//				c++;
//
//			// NIST 800-38D 8.2.1 Deterministic Construction
//			rand.nextBytes(iv);
//			System.arraycopy(fixed, 0, iv, 0, 4);
//			iv[4] = (byte) c;
//			iv[6] = (byte) ((c >>> 8) & 0xff);
//			iv[8] = (byte) ((c >>> 16) & 0xff);
//			iv[10] = (byte) ((c >>> 24) & 0xff);
//			return new GCMParameterSpec(128, iv);
//		}
//	}
//
//	private static SecretKey ecek(int size) {
//		final var keygen = IuException.unchecked(() -> KeyGenerator.getInstance("AES"));
//		keygen.init(size);
//		return keygen.generateKey();
//	}
//
//	private static class PendingRecipient {
//		private final Jose header;
//		private final byte[] encryptedKey;
//
//		private PendingRecipient(WebEncryptionHeader header, SecretKey cek, byte[] cekmac) {
//			this.header = new Jose(header);
//
//			final var publicKey = Jose.getKey(header).getPublicKey();
//			final var algorithm = header.getAlgorithm();
//
//			switch (algorithm.keyAlgorithm) {
//			case "AESWrap":
//				encryptedKey = IuException.unchecked(() -> {
//					if (cekmac != null) {
//						cipher.init(Cipher.ENCRYPT_MODE, publicKey);
//						return cipher.doFinal(cekmac);
//					} else {
//						cipher.init(Cipher.WRAP_MODE, publicKey);
//						return cipher.wrap(cek);
//					}
//				});
//				break;
//
//			case "RSA":
//				encryptedKey = IuException.unchecked(() -> {
//					final var cipher = Cipher.getInstance(algorithm.keyAlgorithm);
//					if (cekmac != null) {
//						cipher.init(Cipher.ENCRYPT_MODE, publicKey);
//						return cipher.doFinal(cekmac);
//					} else {
//						cipher.init(Cipher.WRAP_MODE, publicKey);
//						return cipher.wrap(cek);
//					}
//				});
//				break;
//
//			default:
//				encryptedKey = null;
//			}
//		}
//
//		private PendingRecipient(JsonObject protectedHeader, byte[] encryptedKey) {
//			header = Jose.from(protectedHeader);
//			this.encryptedKey = encryptedKey;
//			validate();
//		}
//
//		private PendingRecipient(JsonObject protectedHeader, JsonObject sharedHeader, JsonObject recipient) {
//			final JsonObject perRecipientHeader = recipient.getJsonObject("header");
//			header = Jose.from(protectedHeader, sharedHeader, perRecipientHeader);
//			encryptedKey = IuJson.text(recipient, "encrypted_key", EncodingUtils::base64Url);
//			validate();
//		}
//
//		private void validate() {
//			final var algorithm = header.getAlgorithm();
//			final var encryption = header.getEncryption();
//			if (Type.RAW.equals(algorithm.type)) {
//				if (encryptedKey != null)
//					throw new IllegalArgumentException("shared key must be known by recipient");
//			} else if ("GCM".equals(encryption.cipherMode)) {
//				if (Objects.requireNonNull(encryptedKey,
//						"encrypted_key required for GCM keywrap").length != encryption.size)
//					throw new IllegalArgumentException("invalid key size for " + encryption);
//			} else if (Objects.requireNonNull(encryptedKey,
//					"encrypted_key required for CBC/HMAC").length != encryption.size * 2)
//				throw new IllegalArgumentException("invalid key size for " + encryption);
//		}
//	}
//
//	/**
//	 * Encrypts data as JWE.
//	 * 
//	 * @param recipientHeaders    per recipient header data
//	 * @param protectedParameters set of parameters to include in the protected
//	 *                            header
//	 * @param data                data to encrypt
//	 * @param additionalData      optional additional data for AEAD authentication
//	 * @return JSON Web Encryption (JWE) encrypted message
//	 */
//	public static WebEncryption encrypt(Iterable<WebEncryptionHeader> recipientHeaders, Set<Param> protectedParameters,
//			byte[] data, byte[] additionalData) {
//		final var recipientHeaderIterator = recipientHeaders.iterator();
//		final var header = recipientHeaderIterator.next();
//		final var key = Jose.getKey(header);
//
//
//		// 5.1#3 determine agreed-upon key
//		final PublicKey epk;
//		final SecretKey agreedUponKey;
//		if (algorithm.algorithm != null) {
//			final var ka = KeyAgreement.getInstance(algorithm.algorithm);
//			ka.init(Objects.requireNonNull(key.getPrivateKey(), "private key required"));
//		} else
//			agreedUponKey = null;
//
//		final var crit = header.getCriticalExtendedParameters();
//
//		final Map<String, Object> prot = new HashMap<>();
//		if (protectedParameters == null) {
//			prot.put("alg", header.getAlgorithm().alg);
//			final var enc = header.getEncryption();
//			if (enc != null)
//				prot.put("enc", enc.enc);
//		} else
//			protectedParameters.forEach(p -> prot.put(p.name, p.get(header)));
//
//		// 5.1#11 compress content
//		final byte[] content;
//		if (header.isDeflate()) {
//			final var b = new ByteArrayOutputStream();
//			IuException.unchecked(() -> {
//				try (final var d = new DeflaterOutputStream(b)) {
//					d.write(data);
//				}
//			});
//			content = b.toByteArray();
//		} else
//			content = data;
//
//		// TODO: REVIEW LINE
//		final var encryption = header.getEncryption();
//		final GCMParameterSpec gcm;
//		final SecretKey cek, ecek;
//		final byte[] cekmac;
//		if (Type.RAW.equals(algorithm.type)) {
//			cek = new SecretKeySpec(Jose.getKey(header).getKey(), "AES");
//			cekmac = null;
//			ecek = null; // cek is shared, not ephemeral
//			if (algorithm.keyAlgorithm.equals("GCM")) {
//				final var a = new AesGcm(algorithm.size);
//				while (a.c == 0)
//					a.c = a.rand.nextInt();
//				gcm = a.spec();
//			} else
//				gcm = null;
//		} else if ("GCM".equals(encryption.cipherMode)) {
//			final var g = gcm(encryption.size);
//			gcm = g.spec();
//			ecek = cek = g.cek;
//			cekmac = null;
//		} else {
//			gcm = null;
//			ecek = cek = cek(encryption.size);
//			final byte[] mac = new byte[encryption.size / 8];
//			new SecureRandom().nextBytes(mac);
//			cekmac = new byte[encryption.size / 4];
//			System.arraycopy(mac, 0, cekmac, 0, mac.length);
//			System.arraycopy(cek.getEncoded(), 0, cekmac, mac.length, mac.length);
//		}
//
//		final var firstRecipient = new PendingRecipient(header, ecek, cekmac);
//		final Queue<PendingRecipient> pendingRecipients = new ArrayDeque<>();
//		pendingRecipients.offer(firstRecipient);
//
//		while (recipientHeaderIterator.hasNext()) {
//			final var additionalHeader = recipientHeaderIterator.next();
//
//			if (!algorithm.equals(additionalHeader.getAlgorithm()))
//				throw new IllegalArgumentException();
//			if (deflate != additionalHeader.isDeflate())
//				throw new IllegalArgumentException();
//			if (!IuObject.equals(encryption, additionalHeader.getEncryption()))
//				throw new IllegalArgumentException();
//			if (!IuObject.equals(crit, additionalHeader.getCriticalExtendedParameters()))
//				throw new IllegalArgumentException();
//
//			final Map<String, Object> checkProt = new HashMap<>();
//			protectedParameters.forEach(p -> checkProt.put(p.name, p.get(additionalHeader)));
//			if (!IuObject.equals(prot, checkProt))
//				throw new IllegalArgumentException();
//
//			pendingRecipients.offer(new PendingRecipient(additionalHeader, ecek, cekmac));
//		}
//
//		return IuException.unchecked(() -> {
//			final byte[] initializationVector;
//			if (gcm != null)
//				initializationVector = gcm.getIV();
//			else if (cekmac != null) {
//				initializationVector = new byte[16];
//				new SecureRandom().nextBytes(initializationVector);
//			} else
//				initializationVector = null;
//
//			final var encodedProtectedHeader = EncodingUtils
//					.base64Url(EncodingUtils.utf8(firstRecipient.header.toJson(p -> {
//						final var param = Param.from(p);
//						if (param == null)
//							return false;
//						else if (protectedParameters == null)
//							return param.equals(Param.ALGORITHM) || param.equals(Param.ENCRYPTION);
//						else
//							return protectedParameters.contains(Param.from(p));
//					}).toString()));
//
//			final byte[] aad;
//			if (additionalData == null)
//				aad = IuException.unchecked(() -> encodedProtectedHeader.getBytes("US-ASCII"));
//			else
//				aad = IuException
//						.unchecked(() -> (encodedProtectedHeader + '.' + EncodingUtils.base64Url(additionalData))
//								.getBytes("US-ASCII"));
//
//			final Cipher cipher;
//			switch (algorithm) {
//			case DIRECT:
//				cipher = Cipher.getInstance(algorithm.algorithm);
//				cipher.init(Cipher.ENCRYPT_MODE, cek, gcm);
//				break;
//
//			case A128KW:
//			case A192KW:
//			case A256KW:
//			case A128GCMKW:
//			case A192GCMKW:
//			case A256GCMKW:
//			case PBES2_HS256_A128KW:
//			case PBES2_HS384_A192KW:
//			case PBES2_HS512_A256KW:
//				cipher = Cipher.getInstance(algorithm.algorithm);
//				cipher.init(Cipher.WRAP_MODE, cek, gcm);
//				break;
//
//			default:
//				cipher = Cipher.getInstance(encryption.cipherAlgorithm + '/' + encryption.cipherMode + "/PKCS5Padding");
//				cipher.init(Cipher.ENCRYPT_MODE, cek, gcm);
//				break;
//			}
//
//			final byte[] cipherText, authenticationTag;
//			if (gcm != null) {
//				cipher.updateAAD(aad);
//
//				final byte[] encrypted = cipher.doFinal(content);
//				final int taglen = gcm.getTLen() / 8;
//				cipherText = Arrays.copyOfRange(encrypted, 0, encrypted.length - taglen);
//				authenticationTag = Arrays.copyOfRange(encrypted, encrypted.length - taglen, encrypted.length);
//
//			} else if (cekmac != null) {
//				cipherText = cipher.doFinal(content);
//
//				final var mac = Mac.getInstance(encryption.mac);
//				mac.init(new SecretKeySpec(cekmac, 0, encryption.size / 8, encryption.mac));
//				final var tag = new ByteArrayOutputStream();
//				tag.write(aad);
//				tag.write(initializationVector);
//				tag.write(cipherText);
//				final var al = BigInteger.valueOf(aad.length).toByteArray();
//				final var pad = 8 - al.length;
//				for (int i = 0; i < pad; i++)
//					tag.write(0);
//				tag.write(al);
//				authenticationTag = mac.doFinal(tag.toByteArray());
//			} else {
//				cipherText = cipher.doFinal(content);
//				authenticationTag = null;
//			}
//
//			return new Jwe(pendingRecipients, protectedParameters, initializationVector, cipherText, authenticationTag,
//					additionalData);
//		});
//	}
//
//	/**
//	 * Parses a {@link #compact() compact} or {@link #serialize() serialized} JWE
//	 * encrypted message.
//	 * 
//	 * @param jwe compact or serialized JWE message
//	 * @return JSON Web Encryption (JWE) encrypted message
//	 */
//	public static WebEncryption readJwe(String jwe) {
//	}
//

//
//	@Override
//	public Iterable<JweRecipient> getRecipients() {
//		return recipients;
//	}
//
//	@Override
//	public byte[] getInitializationVector() {
//		return initializationVector;
//	}
//
//	@Override
//	public byte[] getCipherText() {
//		return cipherText;
//	}
//
//	@Override
//	public byte[] getAuthenticationTag() {
//		return authenticationTag;
//	}
//
//	@Override
//	public byte[] getAdditionalData() {
//		return additionalData;
//	}
//
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
