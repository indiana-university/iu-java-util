/**
 * Provides client-side resources defined by the
 * <a href= "https://openid.net/specs/openid-connect-core-1_0.html">OpenID
 * Connect Core 1.0 Specification</a>
 * 
 * @provides edu.iu.auth.spi.IuOpenIdConnectSpi OIDC SPI implementation
 */

module iu.util.auth.oidc {
	requires iu.util.auth;
	requires iu.util.auth.util;

	provides edu.iu.auth.spi.IuOpenIdConnectSpi with iu.auth.oidc.OpenIdConnectSpi;
}
