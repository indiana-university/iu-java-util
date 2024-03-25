package iu.auth.saml;

import java.time.Instant;

import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;

import edu.iu.auth.saml.IuSamlClient;
import iu.auth.util.XmlDomUtil;

public class SamlClient implements IuSamlClient {

	@Override
	public void authorize() {
		AuthnRequest authnRequest = (AuthnRequest) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(AuthnRequest.DEFAULT_ELEMENT_NAME).buildObject(AuthnRequest.DEFAULT_ELEMENT_NAME);
		// TODO set return url
		authnRequest.setAssertionConsumerServiceURL();
		// TODO set ldp url
		authnRequest.setDestination("");
		// TODO create new session and maintain it
		// authnRequest.setID(id);
		authnRequest.setIssueInstant(Instant.now());
		authnRequest.setProtocolBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
		authnRequest.setVersion(SAMLVersion.VERSION_20);

		Issuer issuer = (Issuer) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(Issuer.DEFAULT_ELEMENT_NAME).buildObject(Issuer.DEFAULT_ELEMENT_NAME);

		// TODO set issuer
		issuer.setValue("");
		authnRequest.setIssuer(issuer);

		NameIDPolicy nameIdPolicy = (NameIDPolicy) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(NameIDPolicy.DEFAULT_ELEMENT_NAME).buildObject(NameIDPolicy.DEFAULT_ELEMENT_NAME);
		nameIdPolicy.setAllowCreate(true);
		authnRequest.setNameIDPolicy(nameIdPolicy);

		try {
			String s = XmlDomUtil.getContent(XMLObjectProviderRegistrySupport.getMarshallerFactory()
					.getMarshaller(authnRequest).marshall(authnRequest));
			if (s.startsWith("<?xml")) {
				StringBuilder sb = new StringBuilder(s);
				int i = sb.indexOf("?>\n", 4);
				if (i != -1)
					sb.delete(0, i + 3);
				s = sb.toString();
			}
		} catch (MarshallingException e) {
			throw new IllegalStateException(e);
		}
	}

}
