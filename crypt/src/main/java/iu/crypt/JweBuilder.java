package iu.crypt;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Builder;
import edu.iu.crypt.WebEncryptionHeader.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.WebSignatureHeader.Param;

/**
 * Collects inputs for {@link Jwe} encrypted messages.
 */
public class JweBuilder implements Builder {
	static {
		IuObject.assertNotOpen(JweBuilder.class);
	}

	/**
	 * Parses a compact or serialized JWE.
	 * 
	 * @param jwe compact or serialized JWE
	 * @return {@link WebEncryption}
	 */
	public static WebEncryption parse(String jwe) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	private final Algorithm algorithm;
	private final Encryption encryption;
	private final Queue<JweRecipientBuilder> recipients = new ArrayDeque<>();
	private Set<Param> protectedParameters;
	private boolean deflate = true;

	/**
	 * Constructor.
	 * 
	 * @param algorithm  message encryption algorithm
	 * @param encryption key encryption algorithm
	 */
	public JweBuilder(Algorithm algorithm, Encryption encryption) {
		if (!Use.ENCRYPT.equals(Objects.requireNonNull(algorithm, "Message encryption algorithm is required").use))
			throw new IllegalArgumentException("Not an encryption algorithm " + algorithm);

		if (!algorithm.equals(Algorithm.DIRECT))
			Objects.requireNonNull(encryption,
					"Message encryption algorithm " + algorithm + " requires key encryption");
		else if (encryption != null)
			throw new IllegalArgumentException(
					"Direct agreement algorithm " + algorithm + " doesn't perform key encryption");

		this.algorithm = algorithm;
		this.encryption = encryption;
	}

	@Override
	public JweBuilder deflate(boolean deflate) {
		this.deflate = deflate;
		return this;
	}

	@Override
	public Builder protect(Param... params) {
		Objects.requireNonNull(params);

		final var paramSet = Set.of(params);
		if (!paramSet.contains(Param.ALGORITHM))
			throw new IllegalArgumentException("alg must be protected");
		if (encryption != null && !paramSet.contains(Param.ENCRYPTION))
			throw new IllegalArgumentException("enc must be protected");

		if (this.protectedParameters == null)
			this.protectedParameters = paramSet;
		else if (!paramSet.equals(this.protectedParameters))
			throw new IllegalStateException("Protected parameters already set to " + this.protectedParameters);

		return this;
	}

	@Override
	public JweRecipientBuilder add() {
		final var recipient = new JweRecipientBuilder(algorithm, encryption, deflate, this);
		recipients.add(recipient);
		return recipient;
	}

	@Override
	public WebEncryption encrypt(InputStream in) {
		return new Jwe(this, in);
	}

	/**
	 * Gets the algorithm.
	 * 
	 * @return algorithm
	 */
	Algorithm algorithm() {
		return algorithm;
	}

	/**
	 * Gets the encryption.
	 * 
	 * @return encryption
	 */
	Encryption encryption() {
		return encryption;
	}

	/**
	 * Gets recipients.
	 * 
	 * @return recipients
	 */
	Stream<JweRecipientBuilder> recipients() {
		return recipients.stream();
	}

	/**
	 * Gets protected parameters.
	 * 
	 * @return protected parameters
	 */
	Set<Param> protectedParameters() {
		return protectedParameters;
	}

	/**
	 * Gets the deflate flag
	 * 
	 * @return deflate flag
	 */
	boolean deflate() {
		return deflate;
	}

	/**
	 * Generates content encryption key (CEK)
	 * 
	 * @return content encryption key
	 */
	byte[] cek() {
		final var recipient = Objects.requireNonNull(recipients.peek(), "requires at least one recipient");
		final var jwk = Objects.requireNonNull(recipient.key(), "recipient must provide a key");

		switch (algorithm) {
		case DIRECT:
			return Objects.requireNonNull(jwk.getKey(), "DIRECT requires a secret key");

		case ECDH_ES:
			return recipient.agreedUponKey();

		default:
			return IuException.unchecked(() -> {
				final var keygen = KeyGenerator.getInstance(encryption.cipherAlgorithm);
				keygen.init(encryption.size);
				return keygen.generateKey().getEncoded();
			});
		}
	}

}
