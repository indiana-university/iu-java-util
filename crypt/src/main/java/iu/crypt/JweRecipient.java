package iu.crypt;

import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuCrypt;
import edu.iu.IuException;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryptionRecipient;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Represents a recipient of a {@link Jwe} encrypted message.
 */
class JweRecipient implements WebEncryptionRecipient {

	private final Logger LOG = Logger.getLogger(Jwe.class.getName());

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

	/**
	 * Constructor.
	 * 
	 * @param encryption      encrypted message
	 * @param protectedHeader protected header parameters
	 * @param sharedHeader    shared header parameters
	 * @param recipient       recipient parameters
	 */
	JweRecipient(Jwe encryption, JsonObject protectedHeader, JsonObject sharedHeader, JsonObject recipient) {
		this(encryption,
				Jose.from(protectedHeader, sharedHeader, IuJson.get(recipient, "header", JsonValue::asJsonObject)),
				IuJson.text(recipient, "encrypted_key", EncodingUtils::base64Url));
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
	private static Jwk getKey(Jose jose) {
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

	/**
	 * Computes the agreed-upon key for the Elliptic Curve Diffie-Hellman algorithm.
	 * 
	 * @param recipientPrivateKey recipient's private key
	 * 
	 * @return agreed-upon key
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
	 *      Section 4.6</a>
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7516#section-5.1">RFC-7516 JWE
	 *      Section 5.1 #3</a>
	 */
	byte[] agreedUponKey(WebKey recipientPrivateKey) {
		final var ext = header.getExtendedParameters();
		final var epk = JwkBuilder.parse(IuJson.toJson(ext.get("epk")));
		final var uinfo = (String) ext.get("apu");
		final var vinfo = (String) ext.get("apv");
		final var algorithm = header.getAlgorithm();
		final var z = IuException.unchecked(() -> {
			final var ka = KeyAgreement.getInstance(header.getAlgorithm().algorithm);
			ka.init(recipientPrivateKey.getPrivateKey());
			ka.doPhase(epk.getPublicKey(), true);
			return ka.generateSecret();
		});

		final int keyDataLen;
		final String algId;
		if (algorithm.equals(Algorithm.ECDH_ES)) {
			final var encryption = this.encryption.getEncryption();
			keyDataLen = encryption.size;
			algId = encryption.enc;
		} else {
			keyDataLen = algorithm.size;
			algId = algorithm.alg;
		}

		return EncodingUtils.concatKdf(1, z, algId, uinfo, vinfo, keyDataLen);
	}

	/**
	 * Decrypts the content encryption key (CEK)
	 * 
	 * @param recipient  in-progress recipient builder
	 * @param privateKey private key
	 * @return content encryption key
	 */
	@SuppressWarnings("deprecation")
	byte[] decryptCek(Jwk privateKey) {
		// 5.2#7 Verify that the JWE uses a key known to the recipient.
		final var wellKnown = getKey(header).wellKnown();
		if (wellKnown != null && !wellKnown.represents(privateKey))
			throw new IllegalArgumentException("Key is not valid for recipient");

		final var algorithm = header.getAlgorithm();
		if (algorithm.equals(Algorithm.DIRECT)) {
			if (encryptedKey != null)
				// 5.2#10 verify that the JWE Encrypted Key value is an empty
				throw new IllegalArgumentException("encrypted key must be empty for " + algorithm);

			// 5.2#11 use shared key as CEK for direct encryption
			final var cek = Objects.requireNonNull(privateKey.getKey(), "DIRECT requires a secret key");
			final var enc = encryption.getEncryption();
			if (cek.length != enc.size / 8)
				throw new IllegalArgumentException("Invalid key size for " + enc);
		} else if (algorithm.equals(Algorithm.ECDH_ES))
			// 5.2#10 verify that the JWE Encrypted Key value is an empty
			if (encryptedKey != null)
				throw new IllegalArgumentException("encrypted key must be empty for " + algorithm);
			else
				// 5.2#8 use agreed upon key as CEK for direct encryption
				return agreedUponKey(privateKey);

		// 5.2#9 encrypt CEK to the recipient
		Objects.requireNonNull(encryptedKey, "encrypted key required for " + algorithm);

		final byte[] cek;
		switch (algorithm) {
		case A128KW:
		case A192KW:
		case A256KW:
			// key wrapping
			cek = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(privateKey.getKey(), "AES");
				final var cipher = Cipher.getInstance(algorithm.keyAlgorithm);
				cipher.init(Cipher.UNWRAP_MODE, key);
				return ((SecretKey) cipher.unwrap(encryptedKey, "AES", Cipher.SECRET_KEY)).getEncoded();
			});
			break;

		case A128GCMKW:
		case A192GCMKW:
		case A256GCMKW:
			// key wrapping w/ GCM
			cek = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(privateKey.getKey(), "AES");

				final var ext = header.getExtendedParameters();
				final var iv = EncodingUtils
						.base64((String) Objects.requireNonNull(ext.get("iv"), "Missing iv header parameter"));
				final var tag = EncodingUtils
						.base64((String) Objects.requireNonNull(ext.get("tag"), "Missing tag header parameter"));

				final var wrappedKey = Arrays.copyOf(encryptedKey, encryptedKey.length + 16);
				System.arraycopy(tag, 0, wrappedKey, encryptedKey.length, 16);

				final var cipher = Cipher.getInstance(algorithm.keyAlgorithm);
				cipher.init(Cipher.UNWRAP_MODE, key, new GCMParameterSpec(128, iv));

				return ((SecretKey) cipher.unwrap(encryptedKey, "AES", Cipher.SECRET_KEY)).getEncoded();
			});
			break;

		case RSA1_5:
		case RSA_OAEP:
		case RSA_OAEP_256:
			// key encryption
			cek = IuException.unchecked(() -> {
				final var keyCipher = Cipher.getInstance(algorithm.keyAlgorithm);
				keyCipher.init(Cipher.DECRYPT_MODE, privateKey.getPrivateKey());
				return keyCipher.doFinal(encryptedKey);
			});
			break;

		case ECDH_ES_A128KW:
		case ECDH_ES_A192KW:
		case ECDH_ES_A256KW:
			// key agreement with key wrapping
			cek = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(agreedUponKey(privateKey), "AES");
				final var cipher = Cipher.getInstance(algorithm.keyAlgorithm);
				cipher.init(Cipher.UNWRAP_MODE, key);
				return ((SecretKey) cipher.unwrap(encryptedKey, "AES", Cipher.SECRET_KEY)).getEncoded();
			});
			break;

		default:
			throw new UnsupportedOperationException();
		}

		// 5.2#12 record CEK decryption success
		LOG.fine("CEK decryption successful for " + wellKnown);
		return cek;
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
