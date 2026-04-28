package iu.esas.thirdparty.auth.oidc.client;

import edu.iu.thirdparty.auth.oidc.api.ThirdPartyAuthorizeRequest;

/**
 * Binds session attributes.
 */
public interface ThirdPartySession {

	/**
	 * Gets authorization request details
	 * 
	 * @return authorization request details
	 */
	ThirdPartyAuthorizeRequest getAuthorizeRequest();

	/**
	 * Sets authorization request details
	 * 
	 * @param authRequest authorization request details
	 */
	void setAuthorizeRequest(ThirdPartyAuthorizeRequest authRequest);

}
