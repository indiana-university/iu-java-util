/**
 * IU OIDC client utilities module.
 */
module iu.util.oidc.client {

	exports iu.oidc.client.config;

	requires iu.util;
	requires iu.util.client;
	requires transitive iu.util.crypt;
	requires iu.util.jwt.api;
	requires transitive iu.util.oidc;
	requires transitive iu.util.session;

}
