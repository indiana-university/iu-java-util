package iu.auth.saml;

import java.net.URI;

/**
 * SAML session details interface
 */
public interface SamlPreAuthentication {

	/**
	 * Get session id
	 * 
	 * @return session id
	 */
	String getSessionId();

	/**
	 * set session id
	 * 
	 * @param sessionId session id
	 */
	void setSessionId(String sessionId);

	/**
	 * Get return
	 * 
	 * @return return URI
	 */
	URI getReturnUri();

	/**
	 * Set return URI
	 * 
	 * @param returnUri return URI
	 */
	void setReturnUri(URI returnUri);

	/**
	 * Get relay state
	 * 
	 * @return relay state
	 */
	String getRelayState();

	/**
	 * set relay state
	 * 
	 * @param relayState relay state
	 */
	void setRelayState(String relayState);

}
