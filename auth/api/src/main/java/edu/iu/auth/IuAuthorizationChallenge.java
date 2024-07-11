package edu.iu.auth;

/**
 * Represents an authorization challenge.
 */
public interface IuAuthorizationChallenge {

	/**
	 * One-time number value.
	 * 
	 * @return one-time number
	 */
	String getNonce();

	/**
	 * Client thumbprint.
	 * 
	 * @return client thumbprint
	 */
	byte[] getClientThumbprint();

}
