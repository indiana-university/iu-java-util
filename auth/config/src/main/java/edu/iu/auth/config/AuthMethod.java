package edu.iu.auth.config;

import java.time.Duration;

import edu.iu.IuIterable;
import edu.iu.client.IuJsonAdapter;

/**
 * Enumerate token endpoint authentication methods types.
 */
public enum AuthMethod {

	/**
	 * Direct use of client secret as password at API endpoint via Authorization
	 * Basic header.
	 * 
	 * @deprecated Most container standards <em>require</em> support for basic
	 *             authentication, and Basic auth is supported by current HTTP
	 *             standards. However, password authentication is insecure and
	 *             <em>should not</em> be used in production. This
	 *             {@link AuthMethod} is useful for development and test
	 *             environments and will be retained long term, but is deprecated to
	 *             discourage direct use by enterprise applications.
	 */
	@Deprecated
	BASIC("basic", Duration.ofDays(45L), true),

	/**
	 * Direct use of an access token via Authorization Bearer header.
	 */
	BEARER("bearer", Duration.ofHours(12L), false),

	/**
	 * Bearer token w/ use of client secret as password at token endpoint via
	 * Authorization Basic header.
	 * 
	 * @deprecated OAuth 2.0 <em>requires</em> support for client_secret password,
	 *             and Basic auth is supported by current HTTP standards. However,
	 *             password authentication is insecure and <em>should not</em> be
	 *             used in production. This {@link AuthMethod} is useful for
	 *             development and test environments and will be retained long term,
	 *             but is deprecated to discourage direct use by enterprise
	 *             applications.
	 */
	@Deprecated
	CLIENT_SECRET_BASIC("client_secret_basic", Duration.ofDays(45L), true),

	/**
	 * Bearer token w/ use of client secret as password at token endpoint via POST
	 * parameter.
	 * 
	 * @deprecated OAuth 2.0 <em>requires</em> support for client_secret POST.
	 *             However, password authentication is insecure and <em>should
	 *             not</em> be used in production. This {@link AuthMethod} is useful
	 *             for development and test environments and will be retained long
	 *             term, but is deprecated to discourage direct use by enterprise
	 *             applications.
	 */
	@Deprecated
	CLIENT_SECRET_POST("client_secret_post", Duration.ofDays(45L), true),

	/**
	 * Bearer token w/ use of client secret as HMAC key for signing a JWT assertion.
	 */
	CLIENT_SECRET_JWT("client_secret_jwt", Duration.ofDays(455L), false),

	/**
	 * Bearer token w/ use of X509 certificate to demonstrate proof of private key
	 * possession via JWT assertion.
	 */
	PRIVATE_KEY_JWT("private_key_jwt", Duration.ofDays(830L), false);

	/**
	 * JSON type adapter.
	 */
	public static IuJsonAdapter<AuthMethod> JSON = IuJsonAdapter.text(AuthMethod::from, a -> a.parameterValue);

	/**
	 * Gets an authentication method from standard parameter value.
	 * 
	 * @param parameterValue standard parameter value
	 * @return {@link AuthMethod}
	 */
	public static AuthMethod from(String parameterValue) {
		return IuIterable.select(IuIterable.iter(AuthMethod.values()),
				a -> a.parameterValue.equals(parameterValue));
	}

	/**
	 * Standard parameter value.
	 */
	public final String parameterValue;

	/**
	 * Designates the minimum duration to allow for client configuration to expire
	 * with the auth method enabled.
	 */
	public final Duration ttlPolicy;

	/**
	 * True if IP restrictions are required in order to use this authentication
	 * method.
	 */
	public final boolean requiresIpAllow;

	private AuthMethod(String parameterValue, Duration ttlPolicy, boolean requiresIpAllow) {
		this.parameterValue = parameterValue;
		this.ttlPolicy = ttlPolicy;
		this.requiresIpAllow = requiresIpAllow;
	}
}