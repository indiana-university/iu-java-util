/**
 * Provides common utilities to API authorization implementation modules.
 */
module iu.util.auth.util {
	exports iu.auth.util to iu.util.auth.basic, iu.util.auth.oauth, iu.util.auth.oidc;

	requires transitive com.auth0.jwt;
	requires transitive iu.util;
	requires transitive jakarta.json;
	requires transitive java.net.http;
}