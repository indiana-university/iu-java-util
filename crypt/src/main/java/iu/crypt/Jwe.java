package iu.crypt;

import static iu.crypt.JsonP.object;
import static iu.crypt.JsonP.parse;
import static iu.crypt.JsonP.string;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.Key;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.DeflaterOutputStream;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryptionHeader;
import edu.iu.crypt.WebEncryptionRecipient;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebSignatureHeader.Param;
import jakarta.json.JsonObject;

/**
 * JSON Web Encryption (JWE) implementation class.
 */
public class Jwe implements WebEncryption {

	private static class AesGcm {
		private final int size;
		private final SecureRandom rand = new SecureRandom();
		private final byte[] fixed = new byte[4];
		private final byte[] iv = new byte[12];
		private SecretKey cek;
		private int c;

		private AesGcm(int size) {
			this.size = size;
			rand.nextBytes(fixed);
		}

		private GCMParameterSpec spec() {
			if (c++ == 0)
				cek = cek(size);

			// NIST 800-38D 8.2.1 Deterministic Construction
			rand.nextBytes(iv);
			System.arraycopy(fixed, 0, iv, 0, 4);
			iv[4] = (byte) c;
			iv[6] = (byte) ((c >>> 8) & 0xff);
			iv[8] = (byte) ((c >>> 16) & 0xff);
			iv[10] = (byte) ((c >>> 24) & 0xff);
			return new GCMParameterSpec(128, iv);
		}
	}

	private static ThreadLocal<Map<Integer, AesGcm>> GCM = new ThreadLocal<>() {
		@Override
		protected Map<Integer, AesGcm> initialValue() {
			return new HashMap<>();
		}
	};

	private static SecretKey cek(int size) {
		final var keygen = IuException.unchecked(() -> KeyGenerator.getInstance("AES"));
		keygen.init(size);
		return keygen.generateKey();
	}

	private static AesGcm gcm(int size) {
		final var gcm = GCM.get();
		var aesGcm = gcm.get(size);
		if (aesGcm == null)
			gcm.put(size, aesGcm = new AesGcm(size));
		return aesGcm;
	}

	private static class PendingRecipient {
		private final Jose header;
		private final byte[] encryptedKey;

		private PendingRecipient(WebEncryptionHeader header, SecretKey cek, byte[] cekmac) {
			this.header = new Jose(header);

			final var publicKey = Jose.getKey(header).getPublicKey();
			final var algorithm = header.getAlgorithm();
			final Key recipientKey;
			switch (algorithm.algorithm) {
			case "ECDH":
				recipientKey = IuException
						.unchecked(() -> KeyAgreement.getInstance(algorithm.algorithm).doPhase(publicKey, false));
				break;

			case "RSA":
				recipientKey = Jose.getKey(header).getPublicKey();
				break;

			default:
				recipientKey = null;
			}

			if (cek != null)
				encryptedKey = IuException.unchecked(() -> {
					final var cipher = Cipher.getInstance(algorithm.auxiliaryAlgorithm);
					if (cekmac != null) {
						cipher.init(Cipher.ENCRYPT_MODE, recipientKey);
						return cipher.doFinal(cekmac);
					} else {
						cipher.init(Cipher.WRAP_MODE, recipientKey);
						return cipher.wrap(cek);
					}
				});
			else
				encryptedKey = null;
		}

		private PendingRecipient(JsonObject protectedHeader, byte[] encryptedKey) {
			header = Jose.from(protectedHeader);
			this.encryptedKey = encryptedKey;
			validate();
		}

		private PendingRecipient(JsonObject protectedHeader, JsonObject sharedHeader, JsonObject recipient) {
			final JsonObject perRecipientHeader = recipient.getJsonObject("header");
			header = Jose.from(protectedHeader, sharedHeader, perRecipientHeader);
			encryptedKey = string(recipient, "encrypted_key", EncodingUtils::base64Url);
			validate();
		}

		private void validate() {
			final var algorithm = header.getAlgorithm();
			final var encryption = header.getEncryption();
			if (Type.RAW.equals(algorithm.type)) {
				if (encryptedKey != null)
					throw new IllegalArgumentException("shared key must be known by recipient");
			} else if ("GCM".equals(encryption.cipherMode)) {
				if (Objects.requireNonNull(encryptedKey,
						"encrypted_key required for GCM keywrap").length != encryption.size)
					throw new IllegalArgumentException("invalid key size for " + encryption);
			} else if (Objects.requireNonNull(encryptedKey,
					"encrypted_key required for CBC/HMAC").length != encryption.size * 2)
				throw new IllegalArgumentException("invalid key size for " + encryption);
		}
	}

	/**
	 * Encrypts data as JWE.
	 * 
	 * @param recipientHeaders    per recipient header data
	 * @param protectedParameters set of parameters to include in the protected
	 *                            header
	 * @param data                data to encrypt
	 * @param additionalData      optional additional data for AEAD authentication
	 * @return JSON Web Encryption (JWE) encrypted message
	 */
	public static WebEncryption encrypt(Iterable<WebEncryptionHeader> recipientHeaders, Set<Param> protectedParameters,
			byte[] data, byte[] additionalData) {

		final var recipientHeaderIterator = recipientHeaders.iterator();

		final var header = recipientHeaderIterator.next();
		final var algorithm = header.getAlgorithm();
		final var encryption = header.getEncryption();
		final var deflate = header.isDeflate();
		final var crit = header.getCriticalExtendedParameters();

		final Map<String, Object> prot = new HashMap<>();
		protectedParameters.forEach(p -> prot.put(p.name, p.get(header)));

		final GCMParameterSpec gcm;
		final SecretKey cek, ecek;
		final byte[] cekmac;
		if (Type.RAW.equals(algorithm.type)) {
			cek = new SecretKeySpec(Jose.getKey(header).getKey(), "AES");
			cekmac = null;
			ecek = null; // cek is shared, not ephemeral
			if (algorithm.auxiliaryAlgorithm.equals("GCM")) {
				final var a = new AesGcm(algorithm.size);
				while (a.c == 0)
					a.c = a.rand.nextInt();
				gcm = a.spec();
			} else
				gcm = null;
		} else if ("GCM".equals(encryption.cipherMode)) {
			final var g = gcm(encryption.size);
			gcm = g.spec();
			ecek = cek = g.cek;
			cekmac = null;
		} else {
			gcm = null;
			ecek = cek = cek(encryption.size);
			final byte[] mac = new byte[algorithm.size];
			new SecureRandom().nextBytes(mac);
			cekmac = new byte[encryption.size * 2];
			System.arraycopy(mac, 0, cekmac, 0, encryption.size);
			System.arraycopy(cek.getEncoded(), 0, cekmac, encryption.size, encryption.size);
		}

		final var firstRecipient = new PendingRecipient(header, ecek, cekmac);
		final Queue<PendingRecipient> pendingRecipients = new ArrayDeque<>();
		pendingRecipients.offer(firstRecipient);

		while (recipientHeaderIterator.hasNext()) {
			final var additionalHeader = recipientHeaderIterator.next();

			if (!algorithm.equals(additionalHeader.getAlgorithm()))
				throw new IllegalArgumentException();
			if (deflate != additionalHeader.isDeflate())
				throw new IllegalArgumentException();
			if (!IuObject.equals(encryption, additionalHeader.getEncryption()))
				throw new IllegalArgumentException();
			if (!IuObject.equals(crit, additionalHeader.getCriticalExtendedParameters()))
				throw new IllegalArgumentException();

			final Map<String, Object> checkProt = new HashMap<>();
			protectedParameters.forEach(p -> checkProt.put(p.name, p.get(additionalHeader)));
			if (!IuObject.equals(prot, checkProt))
				throw new IllegalArgumentException();

			pendingRecipients.offer(new PendingRecipient(additionalHeader, ecek, cekmac));
		}

		final byte[] content;
		if (deflate) {
			final var b = new ByteArrayOutputStream();
			IuException.unchecked(() -> {
				try (final var d = new DeflaterOutputStream(b)) {
					d.write(data);
				}
			});
			content = b.toByteArray();
		} else
			content = data;

		return IuException.unchecked(() -> {
			final byte[] initializationVector;
			if (gcm != null)
				initializationVector = gcm.getIV();
			else if (cekmac != null) {
				initializationVector = new byte[16];
				new SecureRandom().nextBytes(initializationVector);
			} else
				initializationVector = null;

			final var encodedProtectedHeader = EncodingUtils.base64Url(EncodingUtils
					.utf8(firstRecipient.header.toJson(p -> protectedParameters.contains(Param.from(p))).toString()));
			final byte[] aad;
			if (additionalData == null)
				aad = IuException.unchecked(() -> encodedProtectedHeader.getBytes("US-ASCII"));
			else
				aad = IuException
						.unchecked(() -> (encodedProtectedHeader + '.' + EncodingUtils.base64Url(additionalData))
								.getBytes("US-ASCII"));

			final Cipher cipher;
			switch (algorithm) {
			case DIRECT:
				cipher = Cipher.getInstance(algorithm.algorithm);
				cipher.init(Cipher.ENCRYPT_MODE, cek, gcm);
				break;

			case A128KW:
			case A192KW:
			case A256KW:
			case A128GCMKW:
			case A192GCMKW:
			case A256GCMKW:
			case PBES2_HS256_A128KW:
			case PBES2_HS384_A192KW:
			case PBES2_HS512_A256KW:
				cipher = Cipher.getInstance(algorithm.algorithm);
				cipher.init(Cipher.WRAP_MODE, cek, gcm);
				break;

			default:
				cipher = Cipher.getInstance(encryption.cipherAlgorithm + '/' + encryption.cipherMode + "/PKCS5Padding");
				cipher.init(Cipher.ENCRYPT_MODE, cek, gcm);
				break;
			}

			final byte[] cipherText, authenticationTag;
			if (gcm != null) {
				cipher.updateAAD(aad);

				final byte[] encrypted = cipher.doFinal(content);
				final int taglen = gcm.getTLen() / 8;
				cipherText = Arrays.copyOfRange(encrypted, 0, encrypted.length - taglen);
				authenticationTag = Arrays.copyOfRange(encrypted, encrypted.length - taglen, encrypted.length);

			} else if (cekmac != null) {
				cipherText = cipher.doFinal(content);

				final var mac = Mac.getInstance(encryption.mac);
				mac.init(new SecretKeySpec(cekmac, 0, encryption.size, encryption.mac));
				final var tag = new ByteArrayOutputStream();
				tag.write(aad);
				tag.write(initializationVector);
				tag.write(cipherText);
				final var al = BigInteger.valueOf(aad.length).toByteArray();
				final var pad = 8 - al.length;
				for (int i = 0; i < pad; i++)
					tag.write(0);
				tag.write(al);
				authenticationTag = mac.doFinal(tag.toByteArray());
			} else {
				cipherText = cipher.doFinal(content);
				authenticationTag = null;
			}

			return new Jwe(pendingRecipients, protectedParameters, initializationVector, cipherText, authenticationTag,
					additionalData);
		});
	}

	/**
	 * Parses a {@link #compact() compact} or {@link #serialize() serialized} JWE
	 * encrypted message.
	 * 
	 * @param jwe compact or serialized JWE message
	 * @return JSON Web Encryption (JWE) encrypted message
	 */
	public static WebEncryption readJwe(String jwe) {
		final byte[] initializationVector, cipherText, authenticationTag, additionalData;
		final Queue<PendingRecipient> pendingRecipients = new ArrayDeque<>();
		final Set<Param> protectedParameters;
		if (jwe.charAt(0) == '{') {
			final var parsed = parse(jwe);

			final var protectedHeader = string(parsed, "protected", JsonP::parseBase64Url);
			protectedParameters = protectedHeader.keySet().stream().map(Param::from).filter(Objects::nonNull)
					.collect(Collectors.toUnmodifiableSet());

			final var sharedHeader = object(parsed, "unprotected");

			if (parsed.containsKey("header"))
				if (parsed.containsKey("recipients"))
					throw new IllegalArgumentException();
				else
					pendingRecipients.offer(new PendingRecipient(protectedHeader, sharedHeader, parsed));
			else if (parsed.containsKey("recipients")) {
				final var recipients = parsed.getJsonArray("recipients").iterator();

				final var recipient = new PendingRecipient(protectedHeader, sharedHeader,
						recipients.next().asJsonObject());
				pendingRecipients.offer(recipient);

				final var header = recipient.header;
				final var algorithm = header.getAlgorithm();
				final var encryption = header.getEncryption();
				final var deflate = header.isDeflate();
				final var crit = header.getCriticalExtendedParameters();

				while (recipients.hasNext()) {
					final var additionalRecipient = new PendingRecipient(protectedHeader, sharedHeader,
							recipients.next().asJsonObject());

					final var additionalHeader = additionalRecipient.header;
					if (!algorithm.equals(additionalHeader.getAlgorithm()))
						throw new IllegalArgumentException();
					if (deflate != additionalHeader.isDeflate())
						throw new IllegalArgumentException();
					if (!IuObject.equals(encryption, additionalHeader.getEncryption()))
						throw new IllegalArgumentException();
					if (!IuObject.equals(crit, additionalHeader.getCriticalExtendedParameters()))
						throw new IllegalArgumentException();

					pendingRecipients.offer(additionalRecipient);
				}
			}

			initializationVector = string(parsed, "iv", EncodingUtils::base64);
			cipherText = EncodingUtils.base64Url(parsed.getString("ciphertext"));
			authenticationTag = string(parsed, "tag", EncodingUtils::base64);
			additionalData = string(parsed, "aad", EncodingUtils::base64);
		} else {
			final var i = EncodingUtils.compact(jwe);
			final var header = JsonP.parse(EncodingUtils.utf8(i.next()));
			protectedParameters = header.keySet().stream().map(Param::from).filter(Objects::nonNull)
					.collect(Collectors.toUnmodifiableSet());
			final var encryptedKey = i.next();
			pendingRecipients.offer(new PendingRecipient(header, encryptedKey));
			initializationVector = i.next();
			cipherText = i.next();
			authenticationTag = i.next();
			additionalData = null;
		}
		return new Jwe(pendingRecipients, protectedParameters, initializationVector, cipherText, authenticationTag,
				additionalData);
	}

	private final Iterable<WebEncryptionRecipient> recipients;
	private final Set<Param> protectedParameters;
	private final byte[] initializationVector;
	private final byte[] cipherText;
	private final byte[] authenticationTag;
	private final byte[] additionalData;

	private Jwe(Iterable<PendingRecipient> pendingRecipients, Set<Param> protectedParameters,
			byte[] initializationVector, byte[] cipherText, byte[] authenticationTag, byte[] additionalData) {
		final Queue<WebEncryptionRecipient> recipients = new ArrayDeque<>();
		pendingRecipients.forEach(r -> recipients.offer(new JweRecipient(this, r.header, r.encryptedKey)));
		this.recipients = recipients;
		this.protectedParameters = protectedParameters;
		this.initializationVector = initializationVector;
		this.cipherText = cipherText;
		this.authenticationTag = authenticationTag;
		this.additionalData = additionalData;
	}

	@Override
	public Iterable<WebEncryptionRecipient> getRecipients() {
		return recipients;
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
	public int hashCode() {
		return IuObject.hashCode(recipients, initializationVector, cipherText, authenticationTag);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;

		final var other = (Jwe) obj;
		return IuObject.equals(recipients, other.recipients) //
				&& IuObject.equals(initializationVector, other.initializationVector) //
				&& IuObject.equals(cipherText, other.cipherText) //
				&& IuObject.equals(authenticationTag, other.authenticationTag);
	}

	@Override
	public String toString() {
		final var b = JsonP.PROVIDER.createObjectBuilder();
		b.add("protected", EncodingUtils.base64Url(EncodingUtils.utf8(Jose.getProtected(header).toString())));

		final var shared = Jose.getShared(header);
		if (!shared.isEmpty())
			b.add("unprotected", shared);

		final var perRecipient = Jose.getPerRecipient(header);
		if (!perRecipient.isEmpty())
			b.add("header", perRecipient);

		if (encryptedKey.length > 0)
			b.add("encrypted_key", EncodingUtils.base64Url(encryptedKey));

		if (initializationVector.length > 0)
			b.add("iv", EncodingUtils.base64Url(initializationVector));

		b.add("ciphertext", EncodingUtils.base64Url(cipherText));

		if (authenticationTag.length > 0)
			b.add("tag", EncodingUtils.base64Url(authenticationTag));

		if (additionalData != null)
			b.add("aad", EncodingUtils.base64Url(additionalData));

		return b.build().toString();
	}

	@Override
	public byte[] decrypt(WebKey key) {
		final var algorithm = header.getAlgorithm();

		if (algorithm.algorithm.equals("ECDH")) {

		}

		final var algorithm = header.getAlgorithm();
		final var encryption = header.getEncryption();
		encryptedKey = string(recipient, "encrypted_key", EncodingUtils::base64Url);

		final SecretKey cek;
		final byte[] cekmac;
		if (Type.RAW.equals(algorithm.type)) {
			if (encryptedKey != null)
				throw new IllegalArgumentException("shared key must be known by recipient");
			cek = null;
			cekmac = null;
		} else if ("GCM".equals(encryption.cipherMode)) {
			cek = new SecretKeySpec(Objects.requireNonNull(encryptedKey, "encrypted_key required for GCM keywrap"),
					"AES");
			cekmac = null;
		} else {
			cekmac = Objects.requireNonNull(encryptedKey, "encrypted_key required for CBC/HMAC");
			cek = new SecretKeySpec(cekmac, encryption.size, cekmac.length, "AES");
		}

//		   8.   When Direct Key Agreement or Key Agreement with Key Wrapping are
//		        employed, use the key agreement algorithm to compute the value
//		        of the agreed upon key.  When Direct Key Agreement is employed,
//		        let the CEK be the agreed upon key.  When Key Agreement with Key
//		        Wrapping is employed, the agreed upon key will be used to
//		        decrypt the JWE Encrypted Key.
//
//		   9.   When Key Wrapping, Key Encryption, or Key Agreement with Key
//		        Wrapping are employed, decrypt the JWE Encrypted Key to produce
//		        the CEK.  The CEK MUST have a length equal to that required for
//		        the content encryption algorithm.  Note that when there are
//		        multiple recipients, each recipient will only be able to decrypt
//		        JWE Encrypted Key values that were encrypted to a key in that
//		        recipient's possession.  It is therefore normal to only be able
//		        to decrypt one of the per-recipient JWE Encrypted Key values to
//		        obtain the CEK value.  Also, see Section 11.5 for security
//		        considerations on mitigating timing attacks.
//
//		   10.  When Direct Key Agreement or Direct Encryption are employed,
//		        verify that the JWE Encrypted Key value is an empty octet
//		        sequence.
//
//		   11.  When Direct Encryption is employed, let the CEK be the shared
//		        symmetric key.
//
//		   12.  Record whether the CEK could be successfully determined for this
//		        recipient or not.
//
//		   13.  If the JWE JSON Serialization is being used, repeat this process
//		        (steps 4-12) for each recipient contained in the representation.
//
//		   14.  Compute the Encoded Protected Header value BASE64URL(UTF8(JWE
//		        Protected Header)).  If the JWE Protected Header is not present
//		        (which can only happen when using the JWE JSON Serialization and
//		        no "protected" member is present), let this value be the empty
//		        string.
//
//		   15.  Let the Additional Authenticated Data encryption parameter be
//		        ASCII(Encoded Protected Header).  However, if a JWE AAD value is
//		        present (which can only be the case when using the JWE JSON
//		        Serialization), instead let the Additional Authenticated Data
//		        encryption parameter be ASCII(Encoded Protected Header || '.' ||
//		        BASE64URL(JWE AAD)).
//
//		   16.  Decrypt the JWE Ciphertext using the CEK, the JWE Initialization
//		        Vector, the Additional Authenticated Data value, and the JWE
//		        Authentication Tag (which is the Authentication Tag input to the
//		        calculation) using the specified content encryption algorithm,
//		        returning the decrypted plaintext and validating the JWE
//		        Authentication Tag in the manner specified for the algorithm,
//		        rejecting the input without emitting any decrypted output if the
//		        JWE Authentication Tag is incorrect.
//
//		   17.  If a "zip" parameter was included, uncompress the decrypted
//		        plaintext using the specified compression algorithm.
//
//		   18.  If there was no recipient for which all of the decryption steps
//		        succeeded, then the JWE MUST be considered invalid.  Otherwise,
//		        output the plaintext.  In the JWE JSON Serialization case, also
//		        return a result to the application indicating for which of the
//		        recipients the decryption succeeded and failed.
//
//
//
//
//		Jones & Hildebrand           Standards Track                   [Page 19]
//
//		RFC 7516                JSON Web Encryption (JWE)               May 2015
//
//
//		   Finally, note that it is an application decision which algorithms may
//		   be used in a given context.  Even if a JWE can be successfully
//		   decrypted, unless the algorithms used in the JWE are acceptable to
//		   the application, it SHOULD consider the JWE to be invalid.

		throw new UnsupportedOperationException("TODO");
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
		return p != null && protectedParameters.contains(param);
	}

}
