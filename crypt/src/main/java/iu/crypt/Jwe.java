package iu.crypt;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
import edu.iu.crypt.WebEncryptionHeader.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import jakarta.json.JsonObject;

/**
 * JSON Web Encryption (JWE) implementation class.
 */
public class Jwe implements WebEncryption {

	private static final byte[] b0 = new byte[0];

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

	/**
	 * Encrypts data as JWE.
	 * 
	 * @param header         header data
	 * @param data           data to encrypt
	 * @param additionalData optional additional data for AEAD authentication
	 * @return JSON Web Encryption (JWE) encrypted message
	 */
	public static WebEncryption encrypt(WebEncryptionHeader header, byte[] data, byte[] additionalData) {
		final var algorithm = header.getAlgorithm();
		if (!Use.ENCRYPT.equals(algorithm.use))
			throw new IllegalArgumentException("alg");

		final byte[] content;
		if (header.isDeflate()) {
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
			final Encryption encryption = header.getEncryption();
			final GCMParameterSpec gcm;
			final SecretKey cek;

			if (Type.RAW.equals(algorithm.type)) {
				cek = new SecretKeySpec(Jose.getKey(header).getKey(), "AES");
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
				cek = g.cek;
			} else {
				gcm = null;
				cek = cek(encryption.size);
			}

			final Key recipientKey;
			switch (algorithm.algorithm) {
			case "ECDH":
				recipientKey = IuException.unchecked(() -> KeyAgreement.getInstance(algorithm.algorithm)
						.doPhase(Jose.getKey(header).getPublicKey(), false));
				break;

			case "RSA":
				recipientKey = Jose.getKey(header).getPublicKey();
				break;

			default:
				recipientKey = null;
			}

			final byte[] encryptedKey;
			if (recipientKey != null) {
				final var cipher = Cipher.getInstance(algorithm.auxiliaryAlgorithm);
				if (gcm == null) {
					cipher.init(Cipher.ENCRYPT_MODE, recipientKey);
					final byte[] mac = new byte[algorithm.size];
					new SecureRandom().nextBytes(mac);
					final byte[] k = new byte[algorithm.size * 2];
					System.arraycopy(mac, 0, k, 0, algorithm.size);
					System.arraycopy(cek.getEncoded(), 0, k, algorithm.size, algorithm.size);
					encryptedKey = cipher.doFinal(k);
				} else {
					cipher.init(Cipher.WRAP_MODE, recipientKey);
					encryptedKey = cipher.wrap(cek);
				}
			} else
				encryptedKey = b0;

			final byte[] initializationVector;
			if (gcm != null)
				initializationVector = gcm.getIV();
			else if (recipientKey != null) {
				initializationVector = new byte[16];
				new SecureRandom().nextBytes(initializationVector);
			} else
				initializationVector = b0;

			final var encodedProtectedHeader = EncodingUtils
					.base64Url(EncodingUtils.utf8(Jose.getProtected(header).toString()));
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

			} else if (recipientKey != null) {
				cipherText = cipher.doFinal(content);

				final var mac = Mac.getInstance(encryption.mac);
				mac.init(new SecretKeySpec(encryptedKey, 0, encryption.size, encryption.mac));
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
				authenticationTag = b0;
			}

			return new Jwe(header, encryptedKey, initializationVector, cipherText, authenticationTag, additionalData);
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
		final WebEncryptionHeader header;
		final byte[] encryptedKey, initializationVector, cipherText, authenticationTag, additionalData;

		if (jwe.charAt(0) == '{') {
			final var parsed = JsonP.PROVIDER.createReader(new StringReader(jwe)).readObject();

			final var protectedHeader = JsonP.PROVIDER.createReader(
					new StringReader(EncodingUtils.utf8(EncodingUtils.base64Url(parsed.getString("protected")))))
					.readObject();

			final JsonObject sharedHeader;
			if (parsed.containsKey("unprotected"))
				sharedHeader = parsed.getJsonObject("unprotected");
			else
				sharedHeader = null;
			final JsonObject perRecipientHeader;
			if (parsed.containsKey("header"))
				perRecipientHeader = parsed.getJsonObject("header");
			else
				perRecipientHeader = null;

			if (parsed.containsKey("encrypted_key"))
				encryptedKey = EncodingUtils.base64Url(parsed.getString("encrypted_key"));
			else
				encryptedKey = b0;

			if (parsed.containsKey("iv"))
				initializationVector = EncodingUtils.base64Url(parsed.getString("iv"));
			else
				initializationVector = b0;

			cipherText = EncodingUtils.base64Url(parsed.getString("ciphertext"));

			if (parsed.containsKey("tag"))
				authenticationTag = EncodingUtils.base64Url(parsed.getString("tag"));
			else
				authenticationTag = b0;

			if (parsed.containsKey("aad"))
				additionalData = EncodingUtils.base64Url(parsed.getString("aad"));
			else
				additionalData = null;

			header = Jose.from(protectedHeader, sharedHeader, perRecipientHeader);
		} else {
			final var i = EncodingUtils.compact(jwe);
			header = Jose
					.from(JsonP.PROVIDER.createReader(new StringReader(EncodingUtils.utf8(i.next()))).readObject());
			encryptedKey = i.next();
			initializationVector = i.next();
			cipherText = i.next();
			authenticationTag = i.next();
			additionalData = null;
		}
		return new Jwe(header, encryptedKey, initializationVector, cipherText, authenticationTag, additionalData);
	}

	private final WebEncryptionHeader header;
	private final byte[] encryptedKey;
	private final byte[] initializationVector;
	private final byte[] cipherText;
	private final byte[] authenticationTag;
	private final byte[] additionalData;

	private Jwe(WebEncryptionHeader header, byte[] encryptedKey, byte[] initializationVector, byte[] cipherText,
			byte[] authenticationTag, byte[] additionalData) {
		this.header = header;
		this.encryptedKey = encryptedKey;
		this.initializationVector = initializationVector;
		this.cipherText = cipherText;
		this.authenticationTag = authenticationTag;
		this.additionalData = additionalData;
	}

	@Override
	public WebEncryptionHeader getHeader() {
		return header;
	}

	@Override
	public byte[] getEncryptedKey() {
		return encryptedKey;
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
	public String compact() {
		return EncodingUtils.base64Url(EncodingUtils.utf8(Jose.getProtected(header).toString())) //
				+ '.' + EncodingUtils.base64Url(encryptedKey) //
				+ '.' + EncodingUtils.base64Url(initializationVector) //
				+ '.' + EncodingUtils.base64Url(cipherText) //
				+ '.' + EncodingUtils.base64Url(authenticationTag);
	}

	@Override
	public int hashCode() {
		return IuObject.hashCodeSuper(Jose.hashCode(header), encryptedKey, initializationVector, cipherText,
				authenticationTag);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;

		final var other = (Jwe) obj;
		return Jose.equals(header, other.header) //
				&& IuObject.equals(encryptedKey, other.encryptedKey) //
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
		// TODO Auto-generated method stub

//		   2.   Base64url decode the encoded representations of the JWE
//		        Protected Header, the JWE Encrypted Key, the JWE Initialization
//		        Vector, the JWE Ciphertext, the JWE Authentication Tag, and the
//		        JWE AAD, following the restriction that no line breaks,
//		        whitespace, or other additional characters have been used.
//
//		   3.   Verify that the octet sequence resulting from decoding the
//		        encoded JWE Protected Header is a UTF-8-encoded representation
//		        of a completely valid JSON object conforming to RFC 7159
//		        [RFC7159]; let the JWE Protected Header be this JSON object.
//
//		   4.   If using the JWE Compact Serialization, let the JOSE Header be
//		        the JWE Protected Header.  Otherwise, when using the JWE JSON
//		        Serialization, let the JOSE Header be the union of the members
//		        of the JWE Protected Header, the JWE Shared Unprotected Header
//		        and the corresponding JWE Per-Recipient Unprotected Header, all
//		        of which must be completely valid JSON objects.  During this
//		        step, verify that the resulting JOSE Header does not contain
//		        duplicate Header Parameter names.  When using the JWE JSON
//		        Serialization, this restriction includes that the same Header
//		        Parameter name also MUST NOT occur in distinct JSON object
//		        values that together comprise the JOSE Header.
//
//		   5.   Verify that the implementation understands and can process all
//		        fields that it is required to support, whether required by this
//		        specification, by the algorithms being used, or by the "crit"
//		        Header Parameter value, and that the values of those parameters
//		        are also understood and supported.
//
//		   6.   Determine the Key Management Mode employed by the algorithm
//		        specified by the "alg" (algorithm) Header Parameter.
//
//		   7.   Verify that the JWE uses a key known to the recipient.
//
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
//
//
//
//		Jones & Hildebrand           Standards Track                   [Page 18]
//
//		RFC 7516                JSON Web Encryption (JWE)               May 2015
//
//
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

}
