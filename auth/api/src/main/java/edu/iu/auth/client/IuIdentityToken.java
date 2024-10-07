package edu.iu.auth.client;

import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.crypt.WebToken;

/**
 * Represents an OpenID Connect identity token.
 * 
 * @see <a href=
 *      "https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenID
 *      Connect Core 1.0, ID Token</a>
 */
public interface IuIdentityToken extends IuPrincipalIdentity, WebToken {

	/**
	 * The "name" claim.
	 * 
	 * @return the name
	 */
	String getName();

	/**
	 * The "given_name" claim.
	 * 
	 * @return the given name
	 */
	String getGivenName();

	/**
	 * The "family_name" claim.
	 * 
	 * @return the family name
	 */
	String getFamilyName();

	/**
	 * The "middle_name" claim.
	 * 
	 * @return the middle name
	 */
	String getMiddleName();

	/**
	 * The "email" claim.
	 * 
	 * @return the email
	 */
	String getEmail();

	/**
	 * The "azp" claim.
	 * 
	 * @return authorized party (client_id)
	 */
	String getAuthorizedParty();

	/**
	 * The "act" claim.
	 * 
	 * @return the actor
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc8693#name-act-actor-claim">RFC-8693
	 *      OAuth 2.0 Token Exchange Section 4.1</a>
	 */
	IuIdentityToken getActor();

}
