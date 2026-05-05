/**
 * IU SAML 2.0 Service Provider implementation module.
 */
module iu.util.saml.impl {
	exports iu.saml;
	exports iu.saml.config;
	opens iu.saml.config;
	
	requires iu.util;
	requires iu.util.client;
	requires transitive iu.util.crypt;
	requires iu.util.crypt.impl;
	requires transitive iu.util.saml;
	requires transitive iu.util.session;
	requires java.xml;
	requires jakarta.json;

	requires static org.opensaml.core;
	requires static org.opensaml.saml;
	requires static org.opensaml.xmlsec;
	requires static net.shibboleth.shared.support;
	requires static org.opensaml.saml.impl;
	requires static org.opensaml.security;
	requires static org.opensaml.security.impl;
	requires static org.opensaml.xmlsec.impl;

}
