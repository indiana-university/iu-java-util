/**
 * Utilities for supporting
 * <a href="https://datatracker.ietf.org/doc/html/rfc7617">HTTP Basic
 * Authentication</a>.
 * 
 * @provides edu.iu.auth.spi.IuBasicAuthSpi Service provider implementation
 */
module iu.util.auth.basic {
	requires iu.util.auth;
	requires iu.util.auth.util;

	provides edu.iu.auth.spi.IuBasicAuthSpi with iu.auth.basic.BasicAuthSpi;
}
