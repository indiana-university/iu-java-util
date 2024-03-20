package iu.crypt;

import java.util.Objects;

import edu.iu.crypt.WebKey.Algorithm;

/**
 * Common base class for JSON web security object builders.
 * 
 * @param <B> builder type
 */
abstract class WebKeyReferenceBuilder<B extends WebKeyReferenceBuilder<B>> extends CertificateReferenceBuilder<B> {

	private String id;
	private Algorithm algorithm;

	/**
	 * Sets key ID
	 * 
	 * @param id key ID
	 * @return this
	 */
	public B id(String id) {
		Objects.requireNonNull(id);

		if (this.id == null)
			this.id = id;
		else if (!id.equals(this.id))
			throw new IllegalStateException("ID already set");

		return next();
	}

	/**
	 * Sets algorithm
	 * 
	 * @param algorithm
	 * @return this
	 */
	public B algorithm(Algorithm algorithm) {
		Objects.requireNonNull(algorithm);

		if (this.algorithm == null)
			this.algorithm = algorithm;
		else if (!algorithm.equals(this.algorithm))
			throw new IllegalStateException("Algorithm already set to " + this.algorithm);

		return next();
	}

	/**
	 * Gets the key ID
	 * 
	 * @return key ID
	 */
	String id() {
		return id;
	}

	/**
	 * Gets the algorithm
	 * 
	 * @return algorithm
	 */
	Algorithm algorithm() {
		return algorithm;
	}
}
