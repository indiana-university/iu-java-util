package edu.iu.auth.client;

import java.net.URI;

/**
 * Encapsulates a response from an attribute release endpoint involved in an
 * authorization decision.
 * 
 * <p>
 * Response types are:
 * </p>
 * <ul>
 * <li>Authorized: {@link #getAttributeToken()} is returned with a valid
 * non-null JWT</li>
 * <li>Indeterminate: {@link #getInterruptUri()} and {@link #getState()} are
 * returned to indicate additional information MUST be collected from the user
 * before attributes can be released.</li>
 * <li>Denied: Both {@link #getAttributeToken()} and {@link #getInterruptUri()}
 * return null. The user should be presented a 403 FORBIDDEN error page.</li>
 * </ul>
 */
public interface IuAuthorizationAttributeResponse {

	/**
	 * Gets a signed JWT with attributes to include as {@code authorization_details}
	 * in the access token.
	 * 
	 * <p>
	 * A valid non-null JWT issued by a party trusted by the authorization server
	 * MUST be returned if authorization is allowed. The attribute token SHOULD
	 * include a {@link scope} claim indicating the scopes satisfied by the claim.
	 * </p>
	 * 
	 * @return signed JWT
	 */
	String getAttributeToken();

	/**
	 * Gets a {@link URI} to direct the user to, via form POST, to collect
	 * additional information from the user to complete an authorization decision.
	 * 
	 * <p>
	 * POST parameters:
	 * </p>
	 * <ul>
	 * <li>{@code authorization_code}: opaque value to be sent back to the token
	 * endpoint along with {@code state} to retrieve interim ID and access
	 * tokens</li>
	 * <li>{@code state}: opaque value sent to the token endpoint, from
	 * {@link #getState()}</li>
	 * <li>{@code return_uri}: POST {@link URI} to direct the user to after the
	 * authorization decision has been completed. The POST request SHOULD only
	 * include the {@code attribute_token} form parameter.</li>
	 * <li>{@code nonce}: One-time number to include as a JWT claim in the resulting
	 * {@code attribute_token}</li>
	 * </ul>
	 * 
	 * @return {@link URI}
	 */
	URI getInterruptUri();

	/**
	 * Gets an opaque state value to include in the POST request
	 * {@link #getInterruptUri()}.
	 * 
	 * @return opaque state value; REQUIRED when {@link #getInterruptUri()} is
	 *         non-null
	 */
	String getState();

}
