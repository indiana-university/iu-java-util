package iu.auth.saml;

import java.io.Serializable;
import java.net.URI;

import edu.iu.auth.saml.IuSamlSession;

/**
 * SAML session implementation
 */
public class SamlSession implements IuSamlSession, Serializable {

	private static final long serialVersionUID = 1L;
	
	/**
	 * TODO
	 */
	private final String realm;
	
	/**
	 * TODO
	 */
	private final URI entryPoint;

	/**
	 * Constructor.
	 * 
	 * @param realm      authentication realm
	 * @param entryPoint {@link URI} to redirect the user agent to in order restart
	 *                   the authentication process.
	 */
	public SamlSession(String realm, URI entryPoint) {
		this.realm = realm;
		this.entryPoint = entryPoint;
	}

}
