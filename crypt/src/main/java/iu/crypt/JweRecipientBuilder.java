package iu.crypt;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuCrypt;
import edu.iu.IuException;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryptionHeader.Encryption;
import edu.iu.crypt.WebEncryptionRecipient.Builder;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * Builds JWE recipients for {@link JweBuilder}
 */
class JweRecipientBuilder extends JoseBuilder<JweRecipientBuilder> implements Builder<JweRecipientBuilder> {

	private final JweBuilder jweBuilder;

	/**
	 * Constructor
	 * 
	 * @param algorithm  algorithm
	 * @param encryption encryption
	 * @param deflate    deflate
	 * @param jweBuilder JWE builder
	 */
	JweRecipientBuilder(Algorithm algorithm, Encryption encryption, boolean deflate, JweBuilder jweBuilder) {
		super(algorithm, encryption, deflate);
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
	 * @return agreed-upon key
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7518#section-4.6">RFC-7518 JWA
	 *      Section 4.6</a>
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc7516#section-5.1">RFC-7516 JWE
	 *      Section 5.1 #3</a>
	 */
	byte[] agreedUponKey() {
		final var algorithm = algorithm();
		final var encryption = encryption();

		final var epk = new JwkBuilder().algorithm(algorithm).ephemeral().build();
		final var serializedEpk = IuJson.object();
		epk.wellKnown().serializeTo(serializedEpk);
		crit("epk", serializedEpk.build());

		final var uinfo = EncodingUtils.base64Url(IuCrypt.sha256(epk.getPublicKey().getEncoded()));
		crit("apu", uinfo);

		final var pub = key().getPublicKey();
		final var vinfo = EncodingUtils.base64Url(IuCrypt.sha256(pub.getEncoded()));
		crit("apv", vinfo);

		final var z = IuException.unchecked(() -> {
			final var ka = KeyAgreement.getInstance(algorithm().algorithm);
			ka.init(epk.getPrivateKey());
			ka.doPhase(key().getPublicKey(), true);
			return ka.generateSecret();
		});

		final int keyDataLen;
		final String algId;
		if (algorithm.equals(Algorithm.ECDH_ES)) {
			keyDataLen = encryption.size;
			algId = encryption.enc;
		} else {
			keyDataLen = algorithm.size;
			algId = algorithm.alg;
		}

		return EncodingUtils.concatKdf(1, z, algId, uinfo, vinfo, keyDataLen);
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

	/**
	 * Generates the encrypted key and creates the recipient.
	 * 
	 * @param jwe partially initialized JWE
	 * @param cek ephemeral content encryption key, null if not ephemeral
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
				final var key = new SecretKeySpec(agreedUponKey(), "AES");
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
