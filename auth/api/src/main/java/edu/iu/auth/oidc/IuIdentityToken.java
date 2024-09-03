package edu.iu.auth.oidc;

import java.time.Instant;

import edu.iu.auth.IuPrincipalIdentity;

/**
 * Defines token claims commonly required to support authenticated user
 * interactions and application layer token verification logic.
 * 
 */
public interface IuIdentityToken extends IuPrincipalIdentity {

	/**
	 * {@inheritDoc}
	 * 
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenID
	 *      Connect ID Token</a>
	 */
	@Override
	Instant getAuthTime();

	/**
	 * Gets the end-user's full name in displayable form including all name parts,
	 * possibly including titles and suffixes, ordered according to the End-User's
	 * locale and preferences.
	 * 
	 * @return Full name
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">OpenID
	 *      Connect Standard Claims</a>
	 */
	@Override
	String getName();

	/**
	 * Gets the end-user's preferred email address.
	 * 
	 * @return Email address
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">OpenID
	 *      Connect Standard Claims</a>
	 */
	String getEmail();

	/**
	 * Gets the given name(s) or first name(s) of the end-user.
	 * 
	 * @return Given name
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">OpenID
	 *      Connect Standard Claims</a>
	 */
	String getGivenName();

	/**
	 * Gets the middle name(s) of the end-user.
	 * 
	 * @return Middle name
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">OpenID
	 *      Connect Standard Claims</a>
	 */
	String getMiddleName();

	/**
	 * Gets the surname(s) or last name(s) of the end-user.
	 * 
	 * @return Surname
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">OpenID
	 *      Connect Standard Claims</a>
	 */
	String getFamilyName();

	/**
	 * Gets the party to which the ID token was issued.
	 * 
	 * <p>
	 * SHOULD match the client ID associated with the application that verified the
	 * ID token.
	 * </p>
	 * 
	 * @return Client ID
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenID
	 *      Connect ID Token</a>
	 */
	String getAuthorizedParty();

	/**
	 * Gets {@link IuIdentityToken} claims for a user the end-user has been
	 * authorized to impersonate.
	 * 
	 * <p>
	 * When present, actor claims MAY be used in place of the end-user's claims.
	 * </p>
	 * 
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc8693.html#name-act-actor-claim">OAuth
	 *      2.0 Token Exchange</a>
	 * @return {@link IuIdentityToken}
	 */
	IuIdentityToken getActor();

}
