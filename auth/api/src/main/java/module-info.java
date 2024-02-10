/**
 * API Authentication and Authorization interfaces.
 * 
 * <img src="doc-files/iu.util.auth.svg" alt="UML Class Diagram">
 * 
 * @uses edu.iu.auth.spi.IuBasicAuthSpi For access to HTTP basic auth resources
 * @uses edu.iu.auth.spi.IuOAuthSpi For access to OAuth 2.0 implementation
 *       resources
 * @uses edu.iu.auth.spi.IuOpenIdConnectSpi For access to OpenID Connect
 *       implementation resources
 */
module iu.util.auth {
	exports edu.iu.auth;
	exports edu.iu.auth.basic;
	exports edu.iu.auth.oauth;
	exports edu.iu.auth.oidc;
	exports edu.iu.auth.spi;
	
	requires transitive java.net.http;

	uses edu.iu.auth.spi.IuBasicAuthSpi;
	uses edu.iu.auth.spi.IuOAuthSpi;
	uses edu.iu.auth.spi.IuOpenIdConnectSpi;
}
