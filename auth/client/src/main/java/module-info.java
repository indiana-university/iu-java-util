/**
 * Authoriation client implementation module
 * 
 * @provides edu.iu.auth.spi.IuAuthClientSpi SPI implementation
 */
module iu.util.auth.client {
	requires iu.util;
	requires iu.util.auth;
	requires iu.util.auth.config;
	requires iu.util.auth.jwt;
	requires iu.util.auth.principal;
	requires iu.util.client;
	requires iu.util.crypt;

	provides edu.iu.auth.spi.IuAuthClientSpi with iu.auth.client.AuthClientSpi;
}