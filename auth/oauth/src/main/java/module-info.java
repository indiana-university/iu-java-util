/**
 * Utilities for supporting
 * <a href="https://datatracker.ietf.org/doc/html/rfc6749">OAuth 2.0
 * Authorization Framework</a>.
 * 
 * @provides edu.iu.auth.spi.IuOAuthSpi Service provider implementation
 */
module iu.util.auth.oauth {
	requires iu.util.auth;
	requires iu.util.auth.util;

	provides edu.iu.auth.spi.IuOAuthSpi with iu.auth.oauth.OAuthSpi;
}
