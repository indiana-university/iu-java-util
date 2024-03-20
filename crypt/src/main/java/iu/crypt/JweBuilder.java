package iu.crypt;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

import javax.crypto.KeyGenerator;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Builder;
import edu.iu.crypt.WebEncryptionHeader.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
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
	private byte[] additionalData;

	/**
	 * Constructor.
	 * 
	 * @param algorithm  message encryption algorithm
	 * @param encryption key encryption algorithm
	 */
	public JweBuilder(Algorithm algorithm, Encryption encryption) {
		this.algorithm = Objects.requireNonNull(algorithm, "Encryption algorithm is required");
		if (!Use.ENCRYPT.equals(algorithm.use))
			throw new IllegalArgumentException("Not an encryption algorithm " + algorithm);

		this.encryption = Objects.requireNonNull(encryption, "Content encryption algorithm is required");
	}

	@Override
	public JweBuilder deflate(boolean deflate) {
		this.deflate = deflate;
		return this;
	}

	@Override
	public Builder aad(byte[] additionalData) {
		Objects.requireNonNull(additionalData);

		if (this.additionalData == null)
			this.additionalData = additionalData;
		else if (!Arrays.equals(additionalData, this.additionalData))
			throw new IllegalStateException("additionalData already set");

		return this;
	}

	@Override
	public Builder protect(Param... params) {
		Objects.requireNonNull(params);

		final var paramSet = Set.of(params);
		if (!paramSet.contains(Param.ALGORITHM) //
				|| !paramSet.contains(Param.ENCRYPTION))
			throw new IllegalArgumentException("alg and enc must be protected");

		if (this.protectedParameters == null)
			this.protectedParameters = paramSet;
		else if (!paramSet.equals(this.protectedParameters))
			throw new IllegalStateException("Protected parameters already set to " + this.protectedParameters);

		return this;
	}

	@Override
	public JweRecipientBuilder addRecipient() {
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
	 * Get additionalData
	 * @return additionalData
	 */
	byte[] additionalData() {
		return additionalData;
	}

	/**
	 * Generates content encryption key (CEK)
	 * 
	 * @return content encryption key
	 */
	byte[] cek() {
		final var recipient = Objects.requireNonNull(recipients.peek(), "requires at least one recipient");
		final var jwk = Objects.requireNonNull(recipient.key(), "recipient must provide a key");

		final byte[] cek;
		if (algorithm.equals(Algorithm.DIRECT))
			// 5.1#6 use shared key as CEK for direct encryption
			cek = Objects.requireNonNull(jwk.getKey(), "DIRECT requires a secret key");
		else if (algorithm.equals(Algorithm.ECDH_ES))
			cek = recipient.agreedUponKey();
		else
			cek = IuException.unchecked(() -> {
				// 5.1#2 generate CEK if ephemeral
				final var keygen = KeyGenerator.getInstance(encryption.keyAlgorithm);
				keygen.init(encryption.size);
				return keygen.generateKey().getEncoded();
			});

		final var encryption = encryption();
		if (encryption == null || encryption.mac == null)
			return cek;
		else
			return IuException.unchecked(() -> {
				final var keygen = KeyGenerator.getInstance(encryption.mac);
				keygen.init(encryption.size);
				final var mac = keygen.generateKey().getEncoded();
				final var maccek = new byte[mac.length + cek.length];
				System.arraycopy(mac, 0, maccek, 0, mac.length);
				System.arraycopy(cek, 0, maccek, mac.length, cek.length);
				return maccek;
			});
	}

}
