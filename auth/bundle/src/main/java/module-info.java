/**
 * Provides delegated access to authentication and authorization utilities.
 * 
 * <img src="doc-files/iu.util.auth.bundle.svg" alt="UML Deployment Diagram">
 * 
 * @uses edu.iu.auth.spi.IuBasicAuthSpi implementation module
 * @uses edu.iu.auth.spi.IuOAuthSpi implementation module
 * @uses edu.iu.auth.spi.IuOpenIdConnectSpi implementation module
 * @provides edu.iu.auth.spi.IuBasicAuthSpi delegates to implementation module
 * @provides edu.iu.auth.spi.IuOAuthSpi delegates to implementation module
 * @provides edu.iu.auth.spi.IuOpenIdConnectSpi delegates to implementation
 *           module
 */
module iu.util.auth.bundle {
	requires iu.util;
	requires iu.util.auth;
	requires iu.util.type.base;
	requires iu.util.type.loader;

	uses edu.iu.auth.spi.IuBasicAuthSpi;
	uses edu.iu.auth.spi.IuOAuthSpi;
	uses edu.iu.auth.spi.IuOpenIdConnectSpi;

	provides edu.iu.auth.spi.IuBasicAuthSpi with iu.auth.bundle.BasicAuthSpiDelegate;
	provides edu.iu.auth.spi.IuOAuthSpi with iu.auth.bundle.OAuthSpiDelegate;
	provides edu.iu.auth.spi.IuOpenIdConnectSpi with iu.auth.bundle.OidcSpiDelegate;
}
