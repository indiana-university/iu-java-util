/**
 * IU Session Management implementation module
 */
module iu.util.session.impl {
	exports iu.session;
	exports iu.session.config;
	opens iu.session.config;
	
	requires transitive iu.util;
	requires transitive iu.util.client;
	requires iu.util.crypt;
	requires iu.util.crypt.impl;
	requires transitive iu.util.session;
}
