package iu.crypt;

import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuCrypt;
import edu.iu.IuException;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebEncryptionRecipient.Builder;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * Builds JWE recipients for {@link JweBuilder}
 */
class JweRecipientBuilder extends JoseBuilder<JweRecipientBuilder> implements Builder<JweRecipientBuilder> {

	/**
	 * Computes the agreed-upon key for ECDH key agreement with NIST.SP.800.56C
	 * Concat KDF using SHA-256 as the key derivation formula.
	 * 
	 * @param privateKey JCE private key
	 * @param publicKey  JCE public key
	 * @param algorithm  JCE key agreement algorithm name
	 * @param algId      JWA algorithm ID
	 * @param uinfo      PartyUInfo value for Concat KDF
	 * @param vinfo      PartyVInfo value for Concat KDF
	 * @param keyDataLen bit length of desired output key, SuppPubInfo for Concat
	 *                   KDF
	 * @return derived key data
	 * @see <a href=
	 *      "https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-56Ar3.pdf">NIST.SP.800-56Ar3</a>
	 * @see <a href=
	 *      "https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-56Cr2.pdf">NIST.SP.800-56Cr2</a>
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6.2">RFC-7518
	 *      JSON Web Algorithms (JWA) 4.6.2</a>
	 */
	static byte[] agreedUponKey(ECPrivateKey privateKey, ECPublicKey publicKey, String algorithm, String algId,
			String uinfo, String vinfo, int keyDataLen) {
		// NIST.SP.800-56Ar3 C.3: bitlen(z) = 2^bits(ceil(log2 q))
		// . q = bitlen(epk.encoded); P-256 => 65, P-521 => 133
		// . log2(q) in [7..8] => 2^8 = 256 => len(z) = 32
		final var z = IuException.unchecked(() -> {
			final var ka = KeyAgreement.getInstance(algorithm);
			ka.init(privateKey);
			ka.doPhase(publicKey, true);
			return ka.generateSecret();
		});

		// JWA: H = SHA-256
		// L in [256,384,512] for AES-CBC-HMAC, [128,192,512] for AES-GCM and ECDH+KW
		// => 1 or 2 rounds
		final var reps = keyDataLen <= 256 ? 1 : 2;
		final var keyData = new byte[32 * reps];

		// NIST.SP.800-56Cr2 5.8.2.1.1:
		// R(0) = []
		// K = for n in [1..r]: R(n-1) || R(n)
		for (var i = 0; i < reps; i++) {
			final var n = i + 1;
			// R(n) = H(n || Z || FixedInfo)
			System.arraycopy(
					IuCrypt.sha256(EncodingUtils.concatKdf(n, z, /* FixedInfo = */ algId, uinfo, vinfo, keyDataLen)), 0,
					keyData, i * 32, 32);
		}

		final var keylen = keyDataLen / 8;
		if (keyData.length == keylen)
			return keyData;
		else
			return Arrays.copyOf(keyData, keylen);
	}

	private final JweBuilder jweBuilder;

	/**
	 * Constructor
	 * 
	 * @param jweBuilder JWE builder
	 */
	JweRecipientBuilder(JweBuilder jweBuilder) {
		this.jweBuilder = jweBuilder;
	}

	@Override
	public JweBuilder then() {
		return jweBuilder;
	}

	@Override
	protected JweRecipientBuilder next() {
		return this;
	}

	/**
	 * Computes the agreed-upon key for the Elliptic Curve Diffie-Hellman algorithm.
	 * 
	 * @param encryption encryption algorithm
	 * @param epk        key to use as the ephemeral public key; <em>must</em>
	 *                   contain an EC public/private key, when serialized with only
	 *                   kid, or jwk (if kid is null); <em>may</em> be null to
	 *                   generate an epk
	 * 
	 * @return agreed-upon key
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
	 *      Section 4.6</a>
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7516#section-5.1">RFC-7516 JWE
	 *      Section 5.1 #3</a>
	 */
	byte[] agreedUponKey(Encryption encryption) {
		final var algorithm = algorithm();

		final var epk = new JwkBuilder().algorithm(algorithm).ephemeral().build();
		final var serializedEpk = IuJson.object();
		epk.wellKnown().serializeTo(serializedEpk);
		crit("epk", serializedEpk.build());

		final var uinfo = EncodingUtils.base64Url(IuCrypt.sha256(((ECPublicKey) epk.getPublicKey()).getEncoded()));
		crit("apu", uinfo);

		final var publicKey = Objects.requireNonNull((ECPublicKey) key().getPublicKey());
		final var vinfo = EncodingUtils.base64Url(IuCrypt.sha256(publicKey.getEncoded()));
		crit("apv", vinfo);

		final int keyDataLen;
		final String algId;
		if (algorithm.equals(Algorithm.ECDH_ES)) {
			keyDataLen = encryption.size;
			algId = encryption.enc;
		} else {
			keyDataLen = algorithm.size;
			algId = algorithm.alg;
		}

		return agreedUponKey((ECPrivateKey) epk.getPrivateKey(), publicKey, algorithm().algorithm, algId, uinfo, vinfo,
				keyDataLen);
	}

	/**
	 * Generates the encrypted key and creates the recipient.
	 * 
	 * @param jwe  partially initialized JWE
	 * @param cek  supplies an ephemeral content encryption key if needed
	 * @param from message originator key, if known
	 * 
	 * @return recipient
	 */
	@SuppressWarnings("deprecation")
	JweRecipient build(Jwe jwe, byte[] cek) {
		final var algorithm = algorithm();

		// 5.1#4 encrypt CEK to the recipient
		final byte[] encryptedKey;
		switch (algorithm) {
		case A128KW:
		case A192KW:
		case A256KW:
			// key wrapping
			encryptedKey = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(key().getKey(), "AES");
				final var cipher = Cipher.getInstance(algorithm.keyAlgorithm);
				cipher.init(Cipher.WRAP_MODE, key);
				return cipher.wrap(new SecretKeySpec(cek, "AES"));
			});
			break;

		case A128GCMKW:
		case A192GCMKW:
		case A256GCMKW:
			// key wrapping w/ GCM
			encryptedKey = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(key().getKey(), "AES");
				final var iv = new byte[12];
				new SecureRandom().nextBytes(iv);
				crit("iv", EncodingUtils.base64Url(iv));

				final var cipher = Cipher.getInstance(algorithm.keyAlgorithm);
				cipher.init(Cipher.WRAP_MODE, key, new GCMParameterSpec(128, iv));
				final var wrappedKey = cipher.wrap(new SecretKeySpec(cek, "AES"));

				crit("tag", EncodingUtils
						.base64Url(Arrays.copyOfRange(wrappedKey, wrappedKey.length - 16, wrappedKey.length)));

				return Arrays.copyOf(wrappedKey, wrappedKey.length - 16);
			});
			break;

		case RSA1_5:
		case RSA_OAEP:
		case RSA_OAEP_256:
			// key encryption
			encryptedKey = IuException.unchecked(() -> {
				final var keyCipher = Cipher.getInstance(algorithm.keyAlgorithm);
				keyCipher.init(Cipher.ENCRYPT_MODE, key().getPublicKey());
				return keyCipher.doFinal(cek);
			});
			break;

		case ECDH_ES_A128KW:
		case ECDH_ES_A192KW:
		case ECDH_ES_A256KW:
			// key agreement with key wrapping
			encryptedKey = IuException.unchecked(() -> {
				final var key = new SecretKeySpec(agreedUponKey(jwe.getEncryption()), "AES");
				final var cipher = Cipher.getInstance(algorithm.keyAlgorithm);
				cipher.init(Cipher.WRAP_MODE, key);
				return cipher.wrap(new SecretKeySpec(cek, "AES"));
			});
			break;

		default:
			// 5.1#5 don't populate encrypted key for direct key agreement or encryption
			encryptedKey = null;
			break;
		}

		return new JweRecipient(jwe, new Jose(this), encryptedKey);
	}

}
