/**
 * Utilities for supporting
 * <a href="https://datatracker.ietf.org/doc/html/rfc7617">HTTP Basic
 * Authentication</a>.
 */
module iu.util.auth.basic {
	exports iu.auth.basic to iu.util.auth.oidc;

	requires iu.util;
	requires iu.util.auth;
	requires java.net.http;
}
