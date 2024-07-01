package iu.auth.saml;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.criterion.EntityIdCriterion;
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
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialResolver;
import org.opensaml.security.credential.impl.StaticCredentialResolver;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.config.DecryptionParserPool;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.X509Certificate;
import org.opensaml.xmlsec.signature.X509Data;
import org.w3c.dom.Element;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey.Algorithm;
import net.shibboleth.shared.resolver.CriteriaSet;
import net.shibboleth.shared.xml.ParserPool;

/**
 * A SAML builder
 * 
 */
final class SamlBuilder {

	static {
		IuObject.assertNotOpen(SamlBuilder.class);

		Thread current = Thread.currentThread();
		ClassLoader currentLoader = current.getContextClassLoader();
		try {
			IuException.unchecked(() -> {
				ClassLoader scl = XMLObjectProviderRegistry.class.getClassLoader();
				current.setContextClassLoader(scl);
				ConfigurationService.register(XMLObjectProviderRegistry.class, new XMLObjectProviderRegistry());
				ConfigurationService.register(SAMLConfiguration.class, new SAMLConfiguration());
				ConfigurationService.register(DecryptionParserPool.class,
						new DecryptionParserPool(new SamlParserPool()));

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
			});
		} finally {
			current.setContextClassLoader(currentLoader);
		}
	}

	/** metadata URIs */
	private final Iterable<URI> metadataUris;

	/** maximum duration of time metadata resolver is valid */
	private final Duration metadataTtl;

	/** allowed list of assertion consumer {@link URI} */
	private final Iterable<URI> acsUris;

	/** allowed list of application entry point {@link URI} */
	final Iterable<URI> entryPointUris;

	/** IDP redirect URI for single sign-on */
	final URI singleSignOnLocation;

	/** unique service provider id that register with identity provider */
	final String serviceProviderEntityId;

	/** X.509 certificate */
	final java.security.cert.X509Certificate certificate;

	/** IP address ranges to allow in addition to IDP observed remote address */
	final Iterable<String> allowedRange;

	/** True if an IP mismatch should result in failure, false to log a warning */
	final boolean failOnAddressMismatch;

	/** allowed list of IP addresses to validate against SAML response */
	final IuSubjectConfirmationValidator subjectConfirmationValidator;

	/** token signature verification algorithm */
	final Algorithm verifyAlg;

	/** metadata resolver object */
	private MetadataResolver metadataResolver;

	/** metadata resolver last updated time */
	private Instant lastMetadataUpdate;

	/** credential resolver for validating the SAMLResponse signature */
	CredentialResolver credentialResolver;

	/**
	 * Constructor
	 * 
	 * @param config {@link IuSamlServiceProviderMetadata} loaded from Vault
	 */
	public SamlBuilder(IuSamlServiceProviderMetadata config) {
		this.metadataUris = Objects.requireNonNull(config.getMetadataUris(), "metadataUris");
		this.metadataTtl = Objects.requireNonNull(config.getMetadataTtl(), "metadataTtl");
		this.acsUris = Objects.requireNonNull(config.getAcsUris(), "acsUris");
		this.entryPointUris = Objects.requireNonNull(config.getEntryPointUris(), "entryPointUris");
		this.certificate = Objects.requireNonNull(config.getIdentity().getEncryptJwk().getCertificateChain()[0],
				"certificate");
		this.verifyAlg = Objects.requireNonNull(config.getIdentity().getAlg());
		this.serviceProviderEntityId = Objects.requireNonNull(config.getServiceProviderEntityId(),
				"serviceProviderEntityId");

		this.allowedRange = config.getAllowedRange();
		this.failOnAddressMismatch = config.isFailOnAddressMismatch();
		this.subjectConfirmationValidator = new IuSubjectConfirmationValidator(allowedRange, failOnAddressMismatch);

		final var resolver = getMetadata();
		final var entityId = Objects.requireNonNull(config.getIdentityProviderEntityId(), "identityProviderEntityId");
		final var entity = Objects.requireNonNull(
				IuException.unchecked(() -> resolver.resolveSingle(new CriteriaSet(new EntityIdCriterion(entityId)))),
				"Entity " + entityId + " not found in SAML metadata");

		final var idp = Objects.requireNonNull(entity.getIDPSSODescriptor("urn:oasis:names:tc:SAML:2.0:protocol"),
				"Missing SAML 2.0 IDP descriptor for " + entityId);

		URI singleSignOnLocation = null;
		for (final var sso : idp.getSingleSignOnServices())
			if ("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect".equals(sso.getBinding())) {
				singleSignOnLocation = URI.create(sso.getLocation());
				break;
			}

		if (singleSignOnLocation == null)
			throw new IllegalStateException("Missing SAML 2.0 Redirect Binding in IDP descriptor for " + entityId);

		this.singleSignOnLocation = singleSignOnLocation;

		List<Credential> certs = new ArrayList<>();
		for (final var keyDescriptor : idp.getKeyDescriptors())
			if (keyDescriptor.getKeyInfo() != null)
				for (final var x509data : keyDescriptor.getKeyInfo().getX509Datas())
					for (final var x509cert : x509data.getX509Certificates())
						certs.add(
								new BasicX509Credential(PemEncoded.parse(x509cert.getValue()).next().asCertificate()));

		credentialResolver = new StaticCredentialResolver(certs);
	}

	/**
	 * Get credential resolver for SAML response
	 * 
	 * @param entityId IDP entity ID
	 * @return credential resolver
	 */
	KeyInfoCredentialResolver getKeyInfoCredentialResolver(String entityId) {
		final var entity = Objects.requireNonNull(
				IuException
						.unchecked(() -> getMetadata().resolveSingle(new CriteriaSet(new EntityIdCriterion(entityId)))),
				"Entity " + entityId + " not found in SAML metadata");

		final var idp = Objects.requireNonNull(entity.getIDPSSODescriptor("urn:oasis:names:tc:SAML:2.0:protocol"),
				"Missing SAML 2.0 IDP descriptor for " + entityId);

		final List<Credential> certs = new ArrayList<>();
		for (final var keyDescriptor : idp.getKeyDescriptors())
			for (X509Data x509data : keyDescriptor.getKeyInfo().getX509Datas())
				for (X509Certificate x509cert : x509data.getX509Certificates())
					certs.add(new BasicX509Credential(PemEncoded.parse(x509cert.getValue()).next().asCertificate()));

		return new StaticKeyInfoCredentialResolver(certs);
	}

	/**
	 * Get metadata resolver {@link MetadataResolver} for given metadata URIs
	 * 
	 * @return {@link MetadataResolver} metadata resolver
	 */
	MetadataResolver getMetadata() {
		if (metadataResolver != null && lastMetadataUpdate.isAfter(Instant.now().minus(metadataTtl)))
			return metadataResolver;

		metadataResolver = IuException.unchecked(() -> {
			final List<MetadataResolver> resolvers = new ArrayList<>();
			final ParserPool parserPool = XMLObjectProviderRegistrySupport.getParserPool();
			for (URI metadataUri : metadataUris) {
				final Element metadataElement;
				try (final var in = metadataUri.toURL().openStream()) {
					metadataElement = parserPool.parse(in).getDocumentElement();
				}

				final var metadataResolver = new DOMMetadataResolver(metadataElement);
				metadataResolver.setId(IdGenerator.generateId());
				metadataResolver.setRequireValidMetadata(true);
				metadataResolver.initialize();
				resolvers.add(metadataResolver);
			}

			final var chainingMetadataResolver = new ChainingMetadataResolver();
			chainingMetadataResolver.setId(IdGenerator.generateId());
			chainingMetadataResolver.setResolvers(resolvers);
			chainingMetadataResolver.initialize();
			return chainingMetadataResolver;
		});

		this.lastMetadataUpdate = Instant.now();
		return metadataResolver;
	}

	/**
	 * Gets service provider metadata XML
	 * 
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
