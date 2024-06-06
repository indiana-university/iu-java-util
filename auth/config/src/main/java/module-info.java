/**
 * Provides internal configuration management support for authentication and
 * authorization implementation modules.
 */
module iu.util.auth.config {
	exports edu.iu.auth.config;

	requires iu.util;
	requires transitive iu.util.auth;
	requires transitive iu.util.crypt;
}
