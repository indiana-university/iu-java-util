package iu.auth.saml;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.ServiceConfigurationError;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.xml.config.XMLConfigurator;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.saml.config.SAMLConfiguration;
import org.opensaml.saml.metadata.resolver.ChainingMetadataResolver;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.AttributeConsumingService;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.RequestedAttribute;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.xmlsec.config.DecryptionParserPool;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.X509Certificate;
import org.opensaml.xmlsec.signature.X509Data;
import org.w3c.dom.Element;
import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.auth.saml.IuSamlClient;
import net.shibboleth.shared.xml.ParserPool;


/**
 * A SAML builder
 *  
 */
public class SamlBuilder {
	
	/** class logger  */
	private final Logger LOG = Logger.getLogger(SamlBuilder.class.getName());

	/** metadata URIs */
	private List<URI> metadataUris;
	
	/** metadata resolver object */
	private MetadataResolver metadataResolver;
	
	/** metadata resolver last updated time */
	private Instant lastMetadataUpdate;
	
	/** maximum duration of time metadata resolver is valid */
	private Duration metadataTtl = Duration.ofMinutes(5L);
	
	/** allowed list of assertion consumer {@link URI}  */
	private List<URI> acsUris;

	/** unique service provider id that register with identity provider */
	String serviceProviderEntityId;

	/** X.509 certificate */
	java.security.cert.X509Certificate certificate;
	
	
	static {

		Thread current = Thread.currentThread();
		ClassLoader currentLoader = current.getContextClassLoader();
		IuException.unchecked(() -> {
			ClassLoader scl = SamlBuilder.class.getClassLoader();
			current.setContextClassLoader(scl);
			ConfigurationService.register(XMLObjectProviderRegistry.class, new XMLObjectProviderRegistry());
			ConfigurationService.register(SAMLConfiguration.class, new SAMLConfiguration());
			ConfigurationService.register(DecryptionParserPool.class, new DecryptionParserPool(new SamlParserPool()));

			XMLConfigurator config = new XMLConfigurator();
			config.load(scl.getResourceAsStream("default-config.xml"));
			config.load(scl.getResourceAsStream("schema-config.xml"));
			config.load(scl.getResourceAsStream("saml-ec-gss-config.xml"));
			config.load(scl.getResourceAsStream("saml1-assertion-config.xml"));
			config.load(scl.getResourceAsStream("saml1-metadata-config.xml"));
			config.load(scl.getResourceAsStream("saml1-protocol-config.xml"));
			config.load(scl.getResourceAsStream("saml2-assertion-config.xml"));
			config.load(scl.getResourceAsStream("saml2-assertion-delegation-restriction-config.xml"));
			config.load(scl.getResourceAsStream("saml2-channel-binding-config.xml"));
			config.load(scl.getResourceAsStream("saml2-ecp-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-algorithm-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-attr-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-idp-discovery-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-query-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-reqinit-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-rpi-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-ui-config.xml"));
			config.load(scl.getResourceAsStream("saml2-protocol-aslo-config.xml"));
			config.load(scl.getResourceAsStream("saml2-protocol-config.xml"));
			config.load(scl.getResourceAsStream("saml2-protocol-thirdparty-config.xml"));
			config.load(scl.getResourceAsStream("signature-config.xml"));
			config.load(scl.getResourceAsStream("encryption-config.xml"));

			XMLObjectProviderRegistrySupport.setParserPool(new SamlParserPool());
		} );
		current.setContextClassLoader(currentLoader);
	}
	
	/**
	 * Constructor
	 * @param client client configuration {@see IuSamlClient}
	 *
	 */
	public SamlBuilder(IuSamlClient client) {
		this.metadataUris = client.getMetaDataUris();
		this.metadataTtl = client.getMetadataTtl();
		this.acsUris = client.getAcsUris();
		this.certificate = client.getCertificate();
		this.serviceProviderEntityId = client.getServiceProviderEntityId();
	}
	
	/**
	 * Get metadata resolver {@link MetadataResolver} for given metadata URIs
	 * @return {@link MetadataResolver} metadata resolver
	 */
	synchronized MetadataResolver getMetadata() {
		if (metadataResolver != null && lastMetadataUpdate.isAfter(Instant.now().minus(metadataTtl))) 
			return metadataResolver;
		
		Queue<Throwable> failures = new ArrayDeque<>();
		List<MetadataResolver> resolvers = new ArrayList<>();

		ParserPool pp = XMLObjectProviderRegistrySupport.getParserPool();
		for (URI metadataUri : metadataUris)
			try {
				Element md;
				try (InputStream in = metadataUri.toURL().openStream()) {
					md = pp.parse(in).getDocumentElement();
				}

				DOMMetadataResolver mdr = new DOMMetadataResolver(md);
				mdr.setId(IdGenerator.generateId());
				mdr.setRequireValidMetadata(true);
				mdr.initialize();

				resolvers.add(mdr);

			} catch (Throwable e) {
				failures.add(new ServiceConfigurationError(metadataUri.toString(), e));
			}

		if (!failures.isEmpty()) {
			ServiceConfigurationError error = new ServiceConfigurationError(
					"Failed to load SAML metadata for at least one provider");
			failures.forEach(error::addSuppressed);
			if (resolvers.isEmpty())
				throw error;
			else
				LOG.log(Level.WARNING, error, error::getMessage);
		}

		ChainingMetadataResolver cmdr = new ChainingMetadataResolver();
		cmdr.setId(IdGenerator.generateId());
		IuException.unchecked(() -> {
			cmdr.setResolvers(resolvers);
			cmdr.initialize();
		});

		if (!failures.isEmpty())
			return cmdr;

		metadataResolver = cmdr;
		this.lastMetadataUpdate = Instant.now();
		return metadataResolver;
	}
	
	/**
	 * Gets service provider metadata XML
	 * @return service provider metadata XML
	 */
	String getServiceProviderMetadata() {
		Thread current = Thread.currentThread();
		ClassLoader samlBuilder = SamlBuilder.class.getClassLoader();
		current.setContextClassLoader(samlBuilder);
		X509Certificate spX509Cert = (X509Certificate) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(X509Certificate.DEFAULT_ELEMENT_NAME).buildObject(X509Certificate.DEFAULT_ELEMENT_NAME);
		
		spX509Cert.setValue(IuText.base64((IuException.unchecked(certificate::getEncoded))));

		X509Data spX509data = (X509Data) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(X509Data.DEFAULT_ELEMENT_NAME).buildObject(X509Data.DEFAULT_ELEMENT_NAME);
		spX509data.getX509Certificates().add(spX509Cert);

		KeyInfo spKeyInfo = (KeyInfo) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(KeyInfo.DEFAULT_ELEMENT_NAME).buildObject(KeyInfo.DEFAULT_ELEMENT_NAME);
		spKeyInfo.getX509Datas().add(spX509data);

		KeyDescriptor spKeyDescriptor = (KeyDescriptor) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(KeyDescriptor.DEFAULT_ELEMENT_NAME).buildObject(KeyDescriptor.DEFAULT_ELEMENT_NAME);
		spKeyDescriptor.setKeyInfo(spKeyInfo);

		EntityDescriptor spEntityDescriptor = (EntityDescriptor) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(EntityDescriptor.ELEMENT_QNAME).buildObject(EntityDescriptor.ELEMENT_QNAME);
		spEntityDescriptor.setEntityID(serviceProviderEntityId);

		AttributeConsumingService spAttrConsumingService = (AttributeConsumingService) XMLObjectProviderRegistrySupport
				.getBuilderFactory().getBuilder(AttributeConsumingService.DEFAULT_ELEMENT_NAME)
				.buildObject(AttributeConsumingService.DEFAULT_ELEMENT_NAME);

		RequestedAttribute spAttrEPPN = (RequestedAttribute) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(RequestedAttribute.DEFAULT_ELEMENT_NAME)
				.buildObject(RequestedAttribute.DEFAULT_ELEMENT_NAME);
		spAttrEPPN.setFriendlyName("mail");
		spAttrEPPN.setName("urn:oid:0.9.2342.19200300.100.1.3");
		spAttrEPPN.setNameFormat(RequestedAttribute.URI_REFERENCE);
		spAttrEPPN.setIsRequired(true);
		spAttrConsumingService.getRequestedAttributes().add(spAttrEPPN);

		spAttrEPPN = (RequestedAttribute) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(RequestedAttribute.DEFAULT_ELEMENT_NAME)
				.buildObject(RequestedAttribute.DEFAULT_ELEMENT_NAME);
		spAttrEPPN.setFriendlyName("displayName");
		spAttrEPPN.setName("urn:oid:2.16.840.1.113730.3.1.241");
		spAttrEPPN.setNameFormat(RequestedAttribute.URI_REFERENCE);
		spAttrEPPN.setIsRequired(true);
		spAttrConsumingService.getRequestedAttributes().add(spAttrEPPN);

		spAttrEPPN = (RequestedAttribute) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(RequestedAttribute.DEFAULT_ELEMENT_NAME)
				.buildObject(RequestedAttribute.DEFAULT_ELEMENT_NAME);
		spAttrEPPN.setFriendlyName("eduPersonPrincipalName");
		spAttrEPPN.setName("urn:oid:1.3.6.1.4.1.5923.1.1.1.6");
		spAttrEPPN.setNameFormat(RequestedAttribute.URI_REFERENCE);
		spAttrEPPN.setIsRequired(true);
		spAttrConsumingService.getRequestedAttributes().add(spAttrEPPN);

		SPSSODescriptor spsso = (SPSSODescriptor) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(SPSSODescriptor.DEFAULT_ELEMENT_NAME).buildObject(SPSSODescriptor.DEFAULT_ELEMENT_NAME);
		spsso.getKeyDescriptors().add(spKeyDescriptor);
		spsso.getAttributeConsumingServices().add(spAttrConsumingService);
		spsso.addSupportedProtocol("urn:oasis:names:tc:SAML:2.0:protocol");

		spEntityDescriptor.getRoleDescriptors().add(spsso);

		int i = 0;
		for (String acsUrl : IuIterable.map(acsUris, URI::toString)) {
			AssertionConsumerService acs = (AssertionConsumerService) XMLObjectProviderRegistrySupport
					.getBuilderFactory().getBuilder(AssertionConsumerService.DEFAULT_ELEMENT_NAME)
					.buildObject(AssertionConsumerService.DEFAULT_ELEMENT_NAME);
			acs.setBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
			acs.setLocation(acsUrl);
			acs.setIndex(i++);
			spsso.getAssertionConsumerServices().add(acs);
		}

		return IuException.unchecked(() -> XmlDomUtil.getContent(XMLObjectProviderRegistrySupport.getMarshallerFactory()
				.getMarshaller(spEntityDescriptor).marshall(spEntityDescriptor)));

	}
}
