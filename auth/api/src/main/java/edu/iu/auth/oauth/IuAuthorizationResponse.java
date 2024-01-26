package edu.iu.auth.oauth;

import java.security.Principal;

/**
 * Represents the response to an {@link IuAuthorizationCodeGrant}.
 */
public interface IuAuthorizationResponse {

	/**
	 * Gets the authenticated principal.
	 * 
	 * @return {@link Principal}
	 */
	Principal getPrincipal();

	/**
	 * Gets the access token.
	 * 
	 * @return access token
	 */
	String getAccessToken();

}
