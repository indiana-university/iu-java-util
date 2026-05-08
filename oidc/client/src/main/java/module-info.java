/**
 * IU OIDC client utilities module.
 */
module iu.util.oidc.client {
	exports iu.oidc.client;
	exports iu.oidc.client.config;
	opens iu.oidc.client.config;

	requires iu.util;
	requires transitive iu.util.client;
	requires transitive iu.util.crypt;
	requires transitive iu.util.jwt.api;
	requires transitive iu.util.oidc;
	requires transitive iu.util.session;

}
