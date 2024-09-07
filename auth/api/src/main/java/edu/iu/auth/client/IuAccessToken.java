package edu.iu.auth.client;

import java.util.Set;

import edu.iu.auth.jwt.IuWebToken;
import edu.iu.auth.oidc.IuIdentityToken;

/**
 * Extends {@link IuWebToken} to add authorization attribute claims.
 */
public interface IuAccessToken extends IuWebToken {

	/**
	 * Gets the client ID of the
	 * <a href="https://datatracker.ietf.org/doc/html/rfc6749">OAuth 2.0</a> client
	 * that requested the token.
	 * 
	 * @return Client ID
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc8693.html#name-client_id-client-identifier">OAuth
	 *      2.0 Token Exchange Section 4.3</a>
	 */
	String getClientId();

	/**
	 * Gets the authorized scope(s) granted by this access token.
	 * 
	 * @return Scope(s)
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc6749#section-3.3">OAuth 2.0
	 *      Section 3.3</a>
	 */
	Set<String> getScope();

	/**
	 * Gets authorization details.
	 * 
	 * @param <T>  Authorization details type
	 * @param type Authorization details class; MUST be understood as configured for
	 *             the client by the type attribute described in <a href=
	 *             "https://www.rfc-editor.org/rfc/rfc9396.html#name-request-parameter-authoriza">RFC-9396
	 *             Rich Authorization Requests Section 2</a>.
	 * @return Authorization details
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc9396.html#name-jwt-based-access-tokens">RFC-9396
	 *      Rich Authorization Requests Section 9.1</a>
	 */
	<T> T getAuthorizationDetails(Class<T> type);

	/**
	 * Gets {@link IuIdentityToken} claims for a user the end-user has been
	 * authorized to impersonate.
	 * 
	 * <p>
	 * When present, actor claims MAY be used in place of end-user attributes or
	 * claims associated with this access token.
	 * </p>
	 * 
	 * @see <a href=
	 *      "https://www.rfc-editor.org/rfc/rfc8693.html#name-may_act-authorized-actor-cl">OAuth
	 *      2.0 Token Exchange</a>
	 * @return {@link IuIdentityToken}
	 */
	IuIdentityToken getAuthorizedActor();

}
