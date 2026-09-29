package iu.crypt.model;

import edu.iu.crypt.WebKey;

/**
 * JSON proxy interface for a JSON Web Key Set (JWKS).
 */
public interface Jwks {

	/**
	 * Returns the keys in the set.
	 * 
	 * @return keys
	 */
	Iterable<? extends WebKey> getKeys();

}
