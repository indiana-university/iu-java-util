package edu.iu.auth.config;

import java.net.URI;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.client.IuAuthorizationAttributeResponse;

/**
 * Provides configuration properties for a locally hosted OpenID Connect
 * provider.
 */
public interface IuOpenIdProviderEndpoint {

	/**
	 * Gets the token locally hosted endpoint {@link URI}
	 * 
	 * @return {@link URI}
	 */
	URI getTokenEndpoint();

	/**
	 * Gets the authentication realm that MAY be used to verifying client assertions
	 * issued by a private key holder other than the client registered by the
	 * client_id value found in the assertion {@code sub} claim.
	 * 
	 * <p>
	 * MAY be null to REQUIRE the token to be issued by the private or MAC key
	 * holder associated with a public key registered via
	 * {@link IuAuthorizationClient#getCredentials()}
	 * </p>
	 * 
	 * @return assertion issuer realm
	 */
	String getAssertionIssuerRealm();

	/**
	 * Requests attributes from a URI
	 * 
	 * @param attributeUri Attribute URI
	 * @param credentials  {@link IuApiCredentials}
	 * @return {@link IuAuthorizationAttributeResponse}
	 */
	IuAuthorizationAttributeResponse getAttributes(URI attributeUri, IuApiCredentials credentials);

}
