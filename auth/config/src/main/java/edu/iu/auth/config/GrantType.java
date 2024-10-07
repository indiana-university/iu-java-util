package edu.iu.auth.config;

import edu.iu.IuIterable;
import edu.iu.client.IuJsonAdapter;

/**
 * Enumerates grant types.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7591#section-2">OAuth
 *      2.0 Dynamic Client Registration</a>
 */
public enum GrantType {

	/**
	 * Authorization code.
	 * 
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#CodeFlowAuth">OIDC
	 *      Core 1.0 Section 3.1</a>
	 */
	AUTHORIZATION_CODE("code"),

	/**
	 * Client credentials.
	 * 
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc6749#section-4.4">OAuth 2.0
	 *      Section 4.4</a>
	 */
	CLIENT_CREDENTIALS("client_credentials"),

	/**
	 * Resource owner password.
	 * 
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc6749#section-4.3">OAuth 2.0
	 *      Section 4.3</a>
	 */
	PASSWORD("password"),

	/**
	 * Refresh token.
	 * 
	 * @see <a href=
	 *      "https://openid.net/specs/openid-connect-core-1_0.html#OfflineAccess">OIDC
	 *      Core 1.0 Section 11</a>
	 */
	REFRESH_TOKEN("refresh_token"),

	/**
	 * JWT bearer assertion.
	 * 
	 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523">OAuth 2.0 JWT
	 *      Bearer Token Profiles</a>
	 */
	JWT_BEARER("urn:ietf:params:oauth:grant-type:jwt-bearer");

	/**
	 * JSON type adapter.
	 */
	public static IuJsonAdapter<GrantType> JSON = IuJsonAdapter.text(GrantType::from, a -> a.parameterValue);

	/**
	 * Gets a grant type from parameter value.
	 * 
	 * @param parameterValue standard parameter value
	 * @return {@link GrantType}
	 */
	public static GrantType from(String parameterValue) {
		return IuIterable.select(IuIterable.iter(GrantType.values()), a -> a.parameterValue.equals(parameterValue));
	}

	/**
	 * Standard parameter value.
	 */
	public final String parameterValue;

	private GrantType(String parameterValue) {
		this.parameterValue = parameterValue;
	}

}