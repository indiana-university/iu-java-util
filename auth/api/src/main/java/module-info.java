/**
 * API Authentication and Authorization interfaces.
 * 
 * @uses edu.iu.auth.oidc.spi.OpenIDConnectSpi For access to OpenID Connect
 *       implementation resources
 */
module iu.util.auth {
	exports edu.iu.auth;
	exports edu.iu.auth.basic;
	exports edu.iu.auth.oauth;
	exports edu.iu.auth.oidc;
	exports edu.iu.auth.oidc.spi;

	uses edu.iu.auth.oidc.spi.OpenIDConnectSpi;
}
