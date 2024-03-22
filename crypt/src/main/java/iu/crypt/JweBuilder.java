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
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Builder;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * Collects inputs for {@link Jwe} encrypted messages.
 */
public class JweBuilder implements Builder {
	static {
		IuObject.assertNotOpen(JweBuilder.class);
	}

	private final Queue<JweRecipientBuilder> recipients = new ArrayDeque<>();
	private Set<Param> protectedParameters;
	private Encryption encryption;
	private boolean deflate = true;
	private byte[] additionalData;

	/**
	 * Constructor.
	 */
	public JweBuilder() {
	}

	@Override
	public Builder enc(Encryption encryption) {
		Objects.requireNonNull(encryption);

		if (this.encryption == null)
			this.encryption = encryption;
		else if (!encryption.equals(this.encryption))
			throw new IllegalStateException("encryption already set to " + encryption);

		return this;
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
		if (this.protectedParameters == null)
			this.protectedParameters = paramSet;
		else if (!paramSet.equals(this.protectedParameters))
			throw new IllegalStateException("Protected parameters already set to " + this.protectedParameters);

		return this;
	}

	@Override
	public JweRecipientBuilder addRecipient() {
		this.encryption = Objects.requireNonNull(encryption, "Content encryption algorithm is required");

		final var recipient = new JweRecipientBuilder(this);
		recipient.ext("enc", encryption.enc);
		if (deflate)
			recipient.ext("zip", "DEF");

		recipients.add(recipient);
		return recipient;
	}

	@Override
	public WebEncryption encrypt(InputStream in) {
		return new Jwe(this, in);
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
	 * 
	 * @return additionalData
	 */
	byte[] additionalData() {
		return additionalData;
	}

	/**
	 * Generates content encryption key (CEK)
	 * 
	 * @param recipient in-progress recipient builder
	 * @param from      message originator private key
	 * @return content encryption key
	 */
	byte[] cek(JweRecipientBuilder recipient) {
		final var algorithm = recipient.algorithm();

		if (algorithm.equals(Algorithm.DIRECT)) {
			// 5.1#6 use shared key as CEK for direct encryption
			final var jwk = Objects.requireNonNull(recipient.key(), "recipient must provide a key");
			final var cek = Objects.requireNonNull(jwk.getKey(), "DIRECT requires a secret key");
			if (cek.length != encryption.size / 8)
				throw new IllegalArgumentException("Invalid key size for " + encryption);

		} else if (algorithm.equals(Algorithm.ECDH_ES))
			return recipient.agreedUponKey(encryption);

		final byte[] key = IuException.unchecked(() -> {
			// 5.1#2 generate CEK if ephemeral
			final var keygen = KeyGenerator.getInstance(encryption.keyAlgorithm);
			keygen.init(encryption.size);
			return keygen.generateKey().getEncoded();
		});

		if (encryption == null || encryption.mac == null)
			return key;
		else
			return IuException.unchecked(() -> {
				final var keygen = KeyGenerator.getInstance(encryption.mac);
				keygen.init(encryption.size);
				final var mac = keygen.generateKey().getEncoded();
				final var maccek = new byte[mac.length + key.length];
				System.arraycopy(mac, 0, maccek, 0, mac.length);
				System.arraycopy(key, 0, maccek, mac.length, key.length);
				return maccek;
			});
	}

}
