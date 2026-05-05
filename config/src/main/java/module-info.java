/**
 * IU configuration utilities module.
 */
module iu.util.config {
	exports edu.iu.config;
	
	requires iu.util;
	requires transitive iu.util.client;
	requires transitive iu.util.crypt;
}
