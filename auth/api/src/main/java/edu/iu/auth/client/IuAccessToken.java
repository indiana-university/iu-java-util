package edu.iu.auth.client;

import edu.iu.crypt.WebToken;

/**
 * Represents the claims of an OAuth 2.0 access token.
 */
public interface IuAccessToken extends WebToken {

	/**
	 * Gets the "client_id" claim value.
	 * 
	 * @return client_id
	 */
	String getClientId();

	/**
	 * Gets the "scope" claim value, parsed as space separated.
	 * 
	 * @return scope
	 */
	Iterable<String> getScope();

	/**
	 * Gets the "may_act" claim value.
	 * 
	 * @return {@link IuIdentityToken}
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc8693#name-may_act-authorized-actor-cl">OAuth
	 *      2.0 Token Exchange Section 4.3</a>
	 */
	IuIdentityToken getAuthorizedActor();

	/**
	 * Gets an entry from the "authorization_details" claim value.
	 * 
	 * @param <T>  details type
	 * @param type details class; SHOULD match the type property of the entry by a
	 *             name mapped from the application environment
	 * @return typed authorization details
	 */
	<T> T getAuthorizationDetails(Class<T> type);

}
