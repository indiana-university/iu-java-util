package iu.esas.thirdparty.auth.oidc.client;

import edu.iu.crypt.WebKey;

/**
 * Third Party OIDC client registration endpoint metadata.
 */
public interface ThirdPartyIssuer {

	/**
	 * Gets all keys available at this endpoint.
	 * 
	 * @return {@link WebKey}
	 */
	Iterable<WebKey> getKeys();

}
