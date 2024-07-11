package iu.auth.nonce;

import edu.iu.auth.IuAuthorizationChallenge;

/**
 * Represents a one-time number that has been used for external synchronization.
 * 
 * <p>
 * When received: the nonce value is not validated; all references are
 * eliminated.
 * </p>
 */
final class UsedChallenge implements IuAuthorizationChallenge {

	private final String nonce;

	/**
	 * Creates a message that indicates the one-time number has been used.
	 * 
	 * @param nonce one-time number
	 */
	UsedChallenge(String nonce) {
		this.nonce = nonce;
	}

	@Override
	public String getNonce() {
		return nonce;
	}

	@Override
	public byte[] getClientThumbprint() {
		return null;
	}

}
