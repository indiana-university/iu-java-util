package iu.auth.saml;

import java.time.Instant;

/**
 * SAML session details interface
 */
public interface SamlPostAuthentication {

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
	Iterable<StoredSamlAssertion> getAssertions();

	/**
	 * Set SAML assertions
	 * 
	 * @param assertions SAML assertions
	 */
	void setAssertions(Iterable<StoredSamlAssertion> assertions);

}
