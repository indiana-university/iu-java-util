/**
 * Provides client-side resources
 * 
 * @provides edu.iu.auth.spi.IuSamlSpi
 */
module iu.util.auth.saml {
	requires iu.util;
	requires iu.util.auth;
	requires iu.util.auth.config;
	requires iu.util.auth.principal;
	requires iu.util.client;
	requires iu.util.crypt;
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

	provides edu.iu.auth.spi.IuSamlSpi with iu.auth.saml.SamlSpi;
}
