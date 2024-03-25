
module iu.util.auth.saml {
	requires iu.util;
	requires iu.util.auth;
	requires java.xml;
	requires transitive iu.util.auth.util;
	requires static org.opensaml.core;
	requires static org.opensaml.saml;
	requires static org.opensaml.xmlsec;
	requires static net.shibboleth.shared.support;
	requires org.opensaml.saml.impl;

	provides edu.iu.auth.spi.IuSamlSpi with iu.auth.saml.SamlConnectSpi;
}
