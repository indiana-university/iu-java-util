/**
 * OpenID Connect provider module.
 */
module iu.util.auth.oidc {
	requires iu.util;
	requires iu.util.auth;
	requires iu.util.auth.config;
	requires iu.util.auth.jwt;
	requires iu.util.auth.pki;
	requires iu.util.client;
	requires iu.util.crypt;
	
	requires java.logging;
}
