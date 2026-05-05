/**
 * IU JSON Web Token (JWT) implementation module.
 * @provides iu.jwt.spi.IuJwtSpi static implementation resources
 */
module iu.util.jwt.impl {
	requires iu.util.jwt.api;

	requires iu.util;
	requires iu.util.client;
	requires iu.util.config;
	requires iu.util.crypt;
	
	provides iu.jwt.spi.IuJwtSpi with iu.jwt.JwtSpi;
}
