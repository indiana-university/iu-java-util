package iu.auth.config;

/**
 * Holds authorization attributes.
 */
public interface OidcPreAuthSession {

	/**
	 * Gets the state parameter value.
	 * 
	 * @return state
	 */
	String getState();

	/**
	 * Sets the state parameter value.
	 * 
	 * @param state state
	 */
	void setState(String state);

	/**
	 * Gets the nonce parameter value.
	 * 
	 * @return nonce
	 */
	String getNonce();

	/**
	 * Sets the nonce parameter value.
	 * 
	 * @param nonce nonce
	 */
	void setNonce(String nonce);

}
