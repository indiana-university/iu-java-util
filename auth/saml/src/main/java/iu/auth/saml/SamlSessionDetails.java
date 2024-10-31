package iu.auth.saml;

import java.net.URI;
import java.time.Instant;

import edu.iu.auth.saml.IuSamlAssertion;

/**
 * SAML session details interface
 */
public interface SamlSessionDetails {

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
	 * Get entry point
	 * 
	 * @return entry point URI
	 */
	URI getEntryPointUri();

	/**
	 * Set entry point URI
	 * 
	 * @param entryPointUri entry point URI
	 */
	void setEntryPointUri(URI entryPointUri);

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

	/**
	 * Get invalid status
	 * 
	 * @return invalid status
	 */
	boolean isInvalid();

	/**
	 * Set invalid status
	 * 
	 * @param invalid invalid status
	 */
	void setInvalid(boolean invalid);

	/**
	 * Get authentication realm
	 * 
	 * @return authentication realm
	 */
	String getRealm();

	/**
	 * Set authentication realm
	 * 
	 * @param realm authentication realm
	 */
	void setRealm(String realm);

	/**
	 * Get name
	 * 
	 * @return name
	 */
	String getName();

	/**
	 * Set name
	 * 
	 * @param name name
	 */
	void setName(String name);

	/**
	 * Get issue time
	 * 
	 * @return issue time
	 */
	Instant getIssueTime();

	/**
	 * Set issue time
	 * 
	 * @param issueTime issue time
	 */
	void setIssueTime(Instant issueTime);

	/**
	 * Get authentication time
	 * 
	 * @return authentication time
	 */
	Instant getAuthTime();

	/**
	 * Set authentication time
	 * 
	 * @param authTime authentication time
	 */
	void setAuthTime(Instant authTime);

	/**
	 * Get expires
	 * 
	 * @return expires
	 */
	Instant getExpires();

	/**
	 * Set expires
	 * 
	 * @param expires expires
	 */
	void setExpires(Instant expires);

	/**
	 * Get SAAML assertions
	 * 
	 * @return SAML assertions
	 */
	Iterable<IuSamlAssertion> getAssertions();

	/**
	 * Set SAML assertions
	 * 
	 * @param assertions SAML assertions
	 */
	void setAssertions(Iterable<IuSamlAssertion> assertions);

}
