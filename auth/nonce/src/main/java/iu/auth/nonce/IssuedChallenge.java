package iu.auth.nonce;

import edu.iu.IdGenerator;
import edu.iu.IuDigest;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthorizationChallenge;

/**
 * Broadcasts a one-time number that has been issued to a client.
 * 
 * <p>
 * When received: the nonce value is validated and queued for later
 * verification.
 * </p>
 */
final class IssuedChallenge implements IuAuthorizationChallenge {

	/**
	 * Validates remote address and user agent, and creates a client footprint.
	 * 
	 * @param remoteAddress remote address
	 * @param userAgent     user agent
	 * @return client thumbprint
	 */
	static byte[] thumbprint(String remoteAddress, String userAgent) {
		IuWebUtils.validateUserAgent(userAgent);
		return IuDigest.sha256(IuText.utf8(IuWebUtils.getInetAddress(remoteAddress).getHostAddress() + userAgent));
	}

	private final String nonce = IdGenerator.generateId();
	private final byte[] clientThumbprint;

	/**
	 * Creates a message that indicates the one-time number has been created.
	 * 
	 * @param remoteAddress remote address
	 * @param userAgent     user agent
	 */
	IssuedChallenge(String remoteAddress, String userAgent) {
		IuWebUtils.validateUserAgent(userAgent);
		this.clientThumbprint = thumbprint(remoteAddress, userAgent);
	}

	@Override
	public String getNonce() {
		return nonce;
	}

	@Override
	public byte[] getClientThumbprint() {
		return clientThumbprint;
	}

}
