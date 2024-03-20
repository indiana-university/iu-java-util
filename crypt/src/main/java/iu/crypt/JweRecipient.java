package iu.crypt;

import java.io.OutputStream;
import java.util.Arrays;

import edu.iu.IuCrypt;
import edu.iu.IuException;
import edu.iu.crypt.WebEncryptionRecipient;
import edu.iu.crypt.WebKey;

/**
 * Represents a recipient of a {@link Jwe} encrypted message.
 */
class JweRecipient implements WebEncryptionRecipient {

	private final Jwe encryption;
	private final Jose header;
	private final byte[] encryptedKey;

	/**
	 * Constructor.
	 * 
	 * @param encryption   encrypted message
	 * @param header       header
	 * @param encryptedKey encrypted key
	 */
	JweRecipient(Jwe encryption, Jose header, byte[] encryptedKey) {
		this.encryption = encryption;
		this.header = header;
		this.encryptedKey = encryptedKey;
	}

	@Override
	public Jose getHeader() {
		return header;
	}

	@Override
	public byte[] getEncryptedKey() {
		return encryptedKey;
	}

	@Override
	public String compact() {
		return EncodingUtils.base64Url(EncodingUtils.utf8(header.toJson(this::isProtected).toString())) //
				+ '.' + EncodingUtils.base64Url(encryptedKey) //
				+ '.' + EncodingUtils.base64Url(encryption.getInitializationVector()) //
				+ '.' + EncodingUtils.base64Url(encryption.getCipherText()) //
				+ '.' + EncodingUtils.base64Url(encryption.getAuthenticationTag());
	}

	/**
	 * Gets the key to use for signing or encryption.
	 * 
	 * @param jose header
	 * @return encryption or signing key
	 */
	private static Jwk getKey(Jose jose) { // TODO: remove
		final var kid = jose.getKeyId();

		var key = jose.getKey();
		if (key != null)
			if (kid != null && !kid.equals(key.getId()))
				throw new IllegalArgumentException("kid");
			else
				return key;

		if (kid != null) {
			final var jwks = jose.getKeySetUri();
			if (jwks != null)
				return JwkBuilder.readJwks(jose.getKeySetUri()).filter(a -> kid.equals(a.getId())).findFirst().get();
		}

		var cert = jose.getCertificateChain();
		if (cert == null) {
			final var certUri = jose.getCertificateUri();
			if (certUri != null)
				cert = PemEncoded.getCertificateChain(certUri);
		}
		if (cert != null) {
			final var t = jose.getCertificateThumbprint();
			if (t != null && !Arrays.equals(t, IuCrypt.sha1(IuException.unchecked(cert[0]::getEncoded))))
				throw new IllegalArgumentException();

			final var t2 = jose.getCertificateSha256Thumbprint();
			if (t2 != null && !Arrays.equals(t2, IuCrypt.sha256(IuException.unchecked(cert[0]::getEncoded))))
				throw new IllegalArgumentException();

			final var jwkb = new JwkBuilder();
			if (kid != null)
				jwkb.id(kid);
			return jwkb.algorithm(jose.getAlgorithm()).cert(cert).build();
		}

		return null;
	}

	@Override
	public void decrypt(WebKey key, OutputStream out) {
		throw new UnsupportedOperationException("TODO");
		// TODO: REVIEW LINE
//		final var algorithm = header.getAlgorithm();
//		final var encryption = header.getEncryption();
//		final var privateKey = key.getPrivateKey();
//
//		final SecretKey cek;
//		final byte[] cekmac;
//		final Key recipientKey;
//		switch (algorithm.algorithm) {
//		case "ECDH":
//			recipientKey = IuException
//					.unchecked(() -> KeyAgreement.getInstance(algorithm.algorithm).doPhase(privateKey, true));
//			break;
//
//		case "RSA":
//			recipientKey = getKey(header).getPublicKey();
//			break;
//
//		default:
//			recipientKey = null;
//		}

//		if (cek != null)
//			encryptedKey = IuException.unchecked(() -> {
//				final var cipher = Cipher.getInstance(algorithm.keyAlgorithm);
//				if (cekmac != null) {
//					cipher.init(Cipher.ENCRYPT_MODE, recipientKey);
//					return cipher.doFinal(cekmac);
//				} else {
//					cipher.init(Cipher.WRAP_MODE, recipientKey);
//					return cipher.wrap(cek);
//				}
//			});
//		else
//			encryptedKey = null;

//		if (Type.RAW.equals(algorithm.type)) {
//			if (encryptedKey != null)
//				throw new IllegalArgumentException("shared key must be known by recipient");
//			cek = null;
//			cekmac = null;
//		} else if ("GCM".equals(encryption.cipherMode)) {
//			cek = new SecretKeySpec(Objects.requireNonNull(encryptedKey, "encrypted_key required for GCM keywrap"),
//					"AES");
//			cekmac = null;
//		} else {
//			cekmac = Objects.requireNonNull(encryptedKey, "encrypted_key required for CBC/HMAC");
//			if (cekmac.length != encryption.size / 4)
//				throw new IllegalArgumentException("Incorrect encrypted_key length for " + encryption.enc);
//			cek = new SecretKeySpec(cekmac, encryption.size / 8, cekmac.length, "AES");
//		}

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
	}

	/**
	 * Determines which if a JOSE parameter is protected for this recipient.
	 * 
	 * @param name parameter name
	 * @return true if protected; else false
	 */
	boolean isProtected(String name) {
		if (encryption.isProtected(name))
			return true;

		final var crit = header.getCriticalExtendedParameters();
		return crit.contains(name);
	}

}
