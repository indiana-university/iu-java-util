/**
 * Utilities for supporting
 * <a href="https://datatracker.ietf.org/doc/html/rfc6749">OAuth 2.0
 * Authorization Framework</a>.
 */
module iu.util.auth.oauth {
	exports iu.auth.oauth to iu.util.auth.oidc;

	requires transitive com.auth0.jwt;
	requires iu.util;
	requires iu.util.auth;
	requires iu.util.auth.basic;
	requires transitive jakarta.json;
	requires java.net.http;
}
