package iu.auth.util;

import com.auth0.jwt.algorithms.Algorithm;

/**
 * Encapsulates deferred initialization of a JWT algorithm factory.
 */
public interface AlgorithmFactory {

	/**
	 * Gets an initialized JWT algorithm.
	 * 
	 * @param kid JWT kid claim value
	 * @param alg JWT alg claim value
	 * @return algorithm
	 */
	@SuppressWarnings("exports")
	Algorithm getAlgorithm(String kid, String alg);

}
