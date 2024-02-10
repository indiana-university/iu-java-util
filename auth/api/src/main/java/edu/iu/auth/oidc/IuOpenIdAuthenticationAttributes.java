package edu.iu.auth.oidc;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;

import javax.security.auth.Subject;

/**
 * Represents verified attributes following successful authentication by an
 * {@link IuOpenIdProvider OIDC Provider}.
 */
public interface IuOpenIdAuthenticationAttributes {

	/**
	 * Gets the authenticated principal.
	 * 
	 * @return authenticated principal
	 */
	Principal getPrincipal();

	/**
	 * Gets the authorized subject.
	 * 
	 * @return authorized subject
	 */
	Subject getSubject();

	/**
	 * Gets the point in time when the principal's credentials were verified.
	 * 
	 * @return authentication time
	 */
	Instant getAuthenticationTime();

	/**
	 * Gets the point in time when the verified ID token was issued.
	 * 
	 * @return authentication time
	 */
	Instant getIdTokenIssuedAt();

	/**
	 * Gets the point in time when verified the ID token expires.
	 * 
	 * @return authentication time
	 */
	Instant getIdTokenExpires();

	/**
	 * Gets the authorized scope.
	 * 
	 * @return authorized scope
	 */
	String getScope();

	/**
	 * Gets claims included with the verified ID token, excluding those involved in
	 * the verification process, and attributes released through the OIDC userinfo
	 * endpoint.
	 * 
	 * @return authentication attributes
	 */
	Map<String, ?> getAttributes();

}
