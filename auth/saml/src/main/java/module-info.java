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
	
	requires org.opensaml.core;
	requires org.opensaml.saml;
	requires org.opensaml.xmlsec;
	requires net.shibboleth.shared.support;
	requires org.opensaml.saml.impl;
	requires org.opensaml.security;
	requires org.opensaml.security.impl;
	requires org.opensaml.xmlsec.impl;

	provides edu.iu.auth.spi.IuSamlSpi with iu.auth.saml.SamlConnectSpi;
}
