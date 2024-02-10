package edu.iu.auth.oauth;

import java.util.Map;
import java.util.Set;

import edu.iu.auth.IuApiCredentials;

/**
 * Represents a successful response to an {@link IuAuthorizationGrant}.
 */
public interface IuAuthorizationResponse {

	/**
	 * Gets the client ID.
	 * 
	 * @return client ID
	 */
	String getClientId();

	/**
	 * Gets the token type.
	 * 
	 * @return token type
	 */
	String getTokenType();

	/**
	 * Gets the authorized scopes.
	 * 
	 * @return authorized scopes
	 */
	Set<String> getScope();

	/**
	 * Gets additional attributes sent with the initial authorization request.
	 * 
	 * @return attributes request attributes
	 */
	Map<String, ?> getRequestAttributes();

	/**
	 * Gets additional attributes received with the token response.
	 * 
	 * @return attributes token attributes
	 */
	Map<String, ?> getTokenAttributes();

	/**
	 * Gets API credentials established through authorization.
	 * 
	 * <p>
	 * Although the token response providing the credentials <em>must</em> be
	 * verified before this response is created, the client <em>must</em>
	 * independently verify the credentials provided as valid for the authentication
	 * realm.
	 * </p>
	 * 
	 * @return {@link IuApiCredentials}
	 */
	IuApiCredentials getCredentials();

}
