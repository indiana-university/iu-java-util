package iu.auth.saml;

import java.net.URI;

import edu.iu.auth.saml.IuSamlAssertion;

/**
 * SAML session details interface
 */
public interface IuSamlSessionDetails {
	/**
	 * Get session id
	 * @return session id
	 */
	String getSessionId();
	
	/**
     * set session id
	 * @param sessionId session id
     */
	void setSessionId(String sessionId);
	
	/**
	 * Get entry point
	 * @return entry point URI
	 */
	URI getEntryPointUri();
	
	/**
	 * Get relay state
	 * @return relay state
	 */
	String getRelayState();
	
	/**
	 * set relay state
	 * @param relayState relay state
	 */
	void setRelayState(String relayState);
	
	/**
	 * Get	principal name
	 * @return principal name
     */
	String getPrincipalName();
	
	/**
	 * Get SAAML assertions
	 * @return SAML assertions
	 */
	Iterable<IuSamlAssertion> getAssertion();

}
