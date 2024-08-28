/**
 * JWT Tokenization utility module.
 */
module iu.util.auth.jwt {
	exports iu.auth.jwt;

	requires iu.util;
	requires transitive iu.util.auth;
	requires transitive iu.util.client;
	requires transitive iu.util.crypt;
}
