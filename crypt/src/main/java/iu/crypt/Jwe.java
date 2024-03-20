package iu.crypt;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Stream;

import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebSignatureHeader.Param;

/**
 * JSON Web Encryption (JWE) implementation class.
 */
class Jwe implements WebEncryption {

	private final JweRecipient[] recipients;
	private final Set<Param> protectedParameters;
	private final byte[] initializationVector;
	private final byte[] cipherText;
	private final byte[] authenticationTag;
	private final byte[] additionalData;

	/**
	 * Encrypts an incoming message.
	 * 
	 * @param builder {@link JweBuilder}
	 * @param in      stream that provides the encrypted message
	 */
	Jwe(JweBuilder builder, InputStream in) {
		this.protectedParameters = builder.protectedParameters();

		// 5.1#1 determine key management mode
		final var algorithm = builder.algorithm();

		// 5.1#2 generate CEK if ephermeral
		final var ecek = builder.cek();

		// TODO: pass CEK to recipient
		this.recipients = builder.recipients().map(r -> r.build(ecek)).toArray(JweRecipient[]::new);

		this.initializationVector = null; // TODO
		this.cipherText = null;
		this.authenticationTag = null;
		this.additionalData = null;
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
	 * @param param parameter name
	 * @return true if the parameter should be included in the protected header;
	 *         else false
	 */
	boolean isProtected(String param) {
		final var p = Param.from(param);
		if (p == null)
			return false;
		else if (protectedParameters == null)
			return p.equals(Param.ALGORITHM) || p.equals(Param.ENCRYPTION);
		else
			return protectedParameters.contains(p);
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
//		final byte[] initializationVector, cipherText, authenticationTag, additionalData;
//		final Queue<PendingRecipient> pendingRecipients = new ArrayDeque<>();
//		final Set<Param> protectedParameters;
//		if (jwe.charAt(0) == '{') {
//			final var parsed = IuJson.parse(jwe).asJsonObject();
//
//			final var protectedHeader = IuJson.text(parsed, "protected", EncodingUtils::compactJson);
//			protectedParameters = protectedHeader.keySet().stream().map(Param::from).filter(Objects::nonNull)
//					.collect(Collectors.toUnmodifiableSet());
//
//			final var sharedHeader = IuJson.get(parsed, "unprotected", JsonValue::asJsonObject);
//
//			if (parsed.containsKey("header"))
//				if (parsed.containsKey("recipients"))
//					throw new IllegalArgumentException();
//				else
//					pendingRecipients.offer(new PendingRecipient(protectedHeader, sharedHeader, parsed));
//			else if (parsed.containsKey("recipients")) {
//				final var recipients = parsed.getJsonArray("recipients").iterator();
//
//				final var recipient = new PendingRecipient(protectedHeader, sharedHeader,
//						recipients.next().asJsonObject());
//				pendingRecipients.offer(recipient);
//
//				final var header = recipient.header;
//				final var algorithm = header.getAlgorithm();
//				final var encryption = header.getEncryption();
//				final var deflate = header.isDeflate();
//				final var crit = header.getCriticalExtendedParameters();
//
//				while (recipients.hasNext()) {
//					final var additionalRecipient = new PendingRecipient(protectedHeader, sharedHeader,
//							recipients.next().asJsonObject());
//
//					final var additionalHeader = additionalRecipient.header;
//					if (!algorithm.equals(additionalHeader.getAlgorithm()))
//						throw new IllegalArgumentException();
//					if (deflate != additionalHeader.isDeflate())
//						throw new IllegalArgumentException();
//					if (!IuObject.equals(encryption, additionalHeader.getEncryption()))
//						throw new IllegalArgumentException();
//					if (!IuObject.equals(crit, additionalHeader.getCriticalExtendedParameters()))
//						throw new IllegalArgumentException();
//
//					pendingRecipients.offer(additionalRecipient);
//				}
//			}
//
//			initializationVector = IuJson.text(parsed, "iv", EncodingUtils::base64Url);
//			cipherText = EncodingUtils.base64Url(parsed.getString("ciphertext"));
//			authenticationTag = IuJson.text(parsed, "tag", EncodingUtils::base64Url);
//			additionalData = IuJson.text(parsed, "aad", EncodingUtils::base64Url);
//		} else {
//			final var i = EncodingUtils.compact(jwe);
//			final var header = IuJson.parse(EncodingUtils.utf8(i.next())).asJsonObject();
//			protectedParameters = header.keySet().stream().map(Param::from).filter(Objects::nonNull)
//					.collect(Collectors.toUnmodifiableSet());
//			final var encryptedKey = i.next();
//			pendingRecipients.offer(new PendingRecipient(header, encryptedKey));
//			initializationVector = i.next();
//			cipherText = i.next();
//			authenticationTag = i.next();
//			additionalData = null;
//		}
//		return new Jwe(pendingRecipients, protectedParameters, initializationVector, cipherText, authenticationTag,
//				additionalData);
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
//	@Override
//	public String toString() {
//		final var b = IuJson.object();
//
//		final var recipients = this.recipients.iterator();
//		final var recipient = (JweRecipient) recipients.next();
//
//		final var header = recipient.getHeader();
//		final var protectedHeader = header.toJson(recipient::isProtected);
//		final Map<String, Object> sharedParams = new HashMap<>();
//		for (final var p : Param.values()) {
//			if (protectedParameters == null) {
//				if (p.equals(Param.ALGORITHM) //
//						|| p.equals(Param.ENCRYPTION))
//					continue;
//			} else if (protectedParameters.contains(p))
//				continue;
//
//			final var v = p.get(header);
//			if (v != null)
//				sharedParams.put(p.name, v);
//		}
//
//		final var crit = header.getCriticalExtendedParameters();
//		final var ext = header.getExtendedParameters();
//		if (ext != null)
//			for (final var e : ext.entrySet())
//				if (!crit.contains(e.getKey()))
//					sharedParams.put(e.getKey(), e.getValue());
//
//		b.add("protected", EncodingUtils.base64Url(EncodingUtils.utf8(protectedHeader.toString())));
//		if (recipients.hasNext()) {
//			while (recipients.hasNext()) {
//				final var additionalRecipient = (JweRecipient) recipients.next();
//				final var additionalHeader = additionalRecipient.getHeader();
//				final var sharedEntries = sharedParams.entrySet().iterator();
//				final var additionalExt = additionalHeader.getExtendedParameters();
//				while (sharedEntries.hasNext()) {
//					final var e = sharedEntries.next();
//					final var p = Param.from(e.getKey());
//					if (p != null) {
//						if (!IuObject.equals(p.get(additionalHeader), e.getValue()))
//							sharedEntries.remove();
//					} else if (additionalExt == null || !IuObject.equals(additionalExt.get(e.getKey()), e.getValue()))
//						sharedEntries.remove();
//				}
//				for (final var p : Param.values())
//					if (!protectedParameters.contains(p)) {
//						if (!IuObject.equals(p.get(header), sharedParams.get(p.name)))
//							sharedParams.remove(p.name);
//					}
//			}
//
//			if (!sharedParams.isEmpty()) {
//				final var unprotectedHeader = header.toJson(sharedParams::containsKey);
//				b.add("unprotected", unprotectedHeader);
//			}
//
//			final var serializedRecipients = IuJson.array();
//			for (final var additionalRecipient : this.recipients) {
//				final var recipientBuilder = IuJson.object();
//				final var perRecipientHeader = ((JweRecipient) additionalRecipient).getHeader()
//						.toJson(a -> !sharedParams.containsKey(a));
//				if (!perRecipientHeader.isEmpty())
//					recipientBuilder.add("header", perRecipientHeader);
//				IuJson.add(recipientBuilder, a -> true, "encrypted_key",
//						() -> EncodingUtils.base64Url(additionalRecipient.getEncryptedKey()));
//				serializedRecipients.add(recipientBuilder);
//			}
//		} else {
//			final var perRecipientHeader = header.toJson(sharedParams::containsKey);
//			if (!perRecipientHeader.isEmpty())
//				b.add("header", perRecipientHeader);
//			IuJson.add(b, a -> true, "encrypted_key", () -> EncodingUtils.base64Url(recipient.getEncryptedKey()));
//
//		}
//
//		if (initializationVector.length > 0)
//			b.add("iv", EncodingUtils.base64Url(initializationVector));
//
//		b.add("ciphertext", EncodingUtils.base64Url(cipherText));
//
//		if (authenticationTag.length > 0)
//			b.add("tag", EncodingUtils.base64Url(authenticationTag));
//
//		if (additionalData != null)
//			b.add("aad", EncodingUtils.base64Url(additionalData));
//
//		return b.build().toString();
//	}
//

}
