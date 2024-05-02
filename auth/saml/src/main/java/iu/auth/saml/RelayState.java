package iu.auth.saml;

import java.net.URI;

import edu.iu.IdGenerator;

/**
 *  Relay state to support session and SAML authentication
 */
public class RelayState {
	private final String session;
	private final URI applicationUri;

	/**
	 * constructor
	 * @param applicationUri  The root resource URI.
	 * 
	 */
	public RelayState(URI applicationUri) {
		this.session = IdGenerator.generateId();
		this.applicationUri = applicationUri;
	}
	
	/**
	 * return session value
	 * @return session
	 */
	public String getSession() {
		return session;
	}

}
