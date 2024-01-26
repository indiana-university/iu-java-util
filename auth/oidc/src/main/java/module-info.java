/**
 * Utilities supporting
 * <a href="https://openid.net/specs/openid-connect-core-1_0.html">OpenID
 * Connect Core 1.0 Specification</a>.
 */
module iu.util.auth.oidc {
	requires iu.util;
	requires iu.util.auth;
	requires iu.util.auth.oauth;
	requires jakarta.json;
	requires java.net.http;
}
