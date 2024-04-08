package iu.auth.saml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.config.XMLConfigurator;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.common.assertion.AssertionValidationException;
import org.opensaml.saml.common.assertion.ValidationContext;
import org.opensaml.saml.config.SAMLConfiguration;
import org.opensaml.saml.metadata.resolver.ChainingMetadataResolver;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.saml2.assertion.SAML20AssertionValidator;
import org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters;
import org.opensaml.saml.saml2.assertion.impl.AudienceRestrictionConditionValidator;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.EncryptedAssertion;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.AttributeConsumingService;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.RequestedAttribute;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialResolver;
import org.opensaml.security.credential.impl.StaticCredentialResolver;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.SignatureValidationParameters;
import org.opensaml.xmlsec.config.DecryptionParserPool;
import org.opensaml.xmlsec.encryption.support.DecryptionException;
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.X509Certificate;
import org.opensaml.xmlsec.signature.X509Data;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignaturePrevalidator;
import org.opensaml.xmlsec.signature.support.SignatureTrustEngine;
import org.opensaml.xmlsec.signature.support.SignatureValidationParametersCriterion;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.w3c.dom.Element;

import edu.iu.IuBadRequestException;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuWebUtils;
import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;
import iu.auth.util.XmlDomUtil;
import net.shibboleth.shared.component.ComponentInitializationException;
import net.shibboleth.shared.resolver.CriteriaSet;
import net.shibboleth.shared.resolver.ResolverException;
import net.shibboleth.shared.xml.ParserPool;

/**
 * {@link IuSamlProvider implementation}
 */
public class SamlProvider implements IuSamlProvider {
	private final Logger LOG = Logger.getLogger(SamlProvider.class.getName());

	private IuSamlClient client;
	private MetadataResolver metadataResolver;
	private long lastMetadataUpdate;
	private String spMetaData;

	static {

		Thread current = Thread.currentThread();
		ClassLoader currentLoader = current.getContextClassLoader();
		try {
			ClassLoader scl = SamlProvider.class.getClassLoader();
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
		} catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		} finally {
			current.setContextClassLoader(currentLoader);
		}
	}

	private class Id implements Principal, Serializable {
		private static final long serialVersionUID = 1L;

		private final String name;

		private Id(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			// TODO Auto-generated method stub
			return null;
		}

		// TODO verification
	}

	/**
	 * Initialize SAML provider
	 * 
	 * @param client {@link IuSamlClient}
	 */
	public SamlProvider(IuSamlClient client) {
		this.client = client;

		this.metadataResolver = getMetadata(client.getMetaDataUris(), client.getMetadataTtl());
		this.spMetaData = setSpMetadata();

	}

	private String getSingleSignOnLocation(String entityId) {
		EntityDescriptor entity = getEntity(entityId);
		IDPSSODescriptor idp = entity.getIDPSSODescriptor("urn:oasis:names:tc:SAML:2.0:protocol");
		if (idp == null)
			throw new IllegalStateException("Missing SAML 2.0 IDP descriptor for " + entityId);
		for (SingleSignOnService sso : idp.getSingleSignOnServices())
			if ("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect".equals(sso.getBinding()))
				return sso.getLocation();
		throw new IllegalStateException("Missing SAML 2.0 Redirect Binding in IDP descriptor for " + entityId);
	}

	private EntityDescriptor getEntity(String entityId) {
		try {
			EntityDescriptor entity = metadataResolver.resolveSingle(new CriteriaSet(new EntityIdCriterion(entityId)));
			if (entity == null)
				throw new IllegalArgumentException("Entity " + entityId + " not found in SAML metadata");
			return entity;
		} catch (ResolverException e) {
			throw new IllegalArgumentException("Failed to resolve SAML metadata for " + entityId, e);
		}
	}

	/**
	 * Get metadata resolver
	 * 
	 * @param metadataUrls metadata url configured with identity provider
	 * @param metadataTtl  metadata maximum life time
	 * @return {@link MetadataResolver}
	 */
	synchronized MetadataResolver getMetadata(List<URI> metadataUrls, Duration metadataTtl) {
		if (metadataResolver != null && lastMetadataUpdate >= (System.currentTimeMillis() - metadataTtl.toMillis()))
			return metadataResolver;

		Queue<Throwable> failures = new ArrayDeque<>();
		List<MetadataResolver> resolvers = new ArrayList<>();

		ParserPool pp = XMLObjectProviderRegistrySupport.getParserPool();
		for (URI metadataUrl : metadataUrls)
			try {
				Element md;
				try (InputStream in = new URL(metadataUrl.toString()).openStream()) {
					md = pp.parse(in).getDocumentElement();
				}

				DOMMetadataResolver mdr = new DOMMetadataResolver(md);
				// TODO pass from client configuration
				// verify without setting it
				mdr.setId("iu-metadata");
				mdr.setRequireValidMetadata(true);
				mdr.initialize();

				resolvers.add(mdr);

			} catch (Throwable e) {
				failures.add(new ServiceConfigurationError(metadataUrl.toString(), e));
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
		// TODO pass from client configuration
		// verify without setting it.
		cmdr.setId("iu-endpoint-SAMLMetadata");
		try {
			cmdr.setResolvers(resolvers);
			cmdr.initialize();
		} catch (ComponentInitializationException | ResolverException e) {
			throw new IllegalStateException(e);
		}

		if (!failures.isEmpty())
			return cmdr;

		metadataResolver = cmdr;
		lastMetadataUpdate = System.currentTimeMillis();

		return metadataResolver;
	}

	@Override
	public URI authRequest(URI samlEntityId, URI postURI, String sessionId) {

		// validate entityId against metadataUrl configuration
		var matchAcs = false;
		// TODO create new session and maintain it
		// activate
		// var sessionId = IdGenerator.generateId();
		var destinationLocation = getSingleSignOnLocation(samlEntityId.toString());
		for (URI acsUrl : client.getAcsUris()) {
			if (acsUrl.getPath().compareTo(postURI.getPath()) == 0)
				matchAcs = true;
		}
		if (!matchAcs) {
			throw new IllegalArgumentException(
					"Post URI doesn't match with allowed list of Assestion Consumer Service URLs" + postURI);
		}

		AuthnRequest authnRequest = (AuthnRequest) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(AuthnRequest.DEFAULT_ELEMENT_NAME).buildObject(AuthnRequest.DEFAULT_ELEMENT_NAME);
		authnRequest.setAssertionConsumerServiceURL(postURI.toString());
		authnRequest.setDestination(destinationLocation);
		authnRequest.setID(sessionId);
		authnRequest.setIssueInstant(Instant.now());
		authnRequest.setProtocolBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
		authnRequest.setVersion(SAMLVersion.VERSION_20);

		Issuer issuer = (Issuer) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(Issuer.DEFAULT_ELEMENT_NAME).buildObject(Issuer.DEFAULT_ELEMENT_NAME);

		issuer.setValue(client.getServiceProviderEntityId());
		authnRequest.setIssuer(issuer);

		NameIDPolicy nameIdPolicy = (NameIDPolicy) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(NameIDPolicy.DEFAULT_ELEMENT_NAME).buildObject(NameIDPolicy.DEFAULT_ELEMENT_NAME);
		nameIdPolicy.setAllowCreate(true);
		authnRequest.setNameIDPolicy(nameIdPolicy);

		String s = IuException.unchecked(() -> XmlDomUtil.getContent(XMLObjectProviderRegistrySupport
				.getMarshallerFactory().getMarshaller(authnRequest).marshall(authnRequest)));
		if (s.startsWith("<?xml")) {
			StringBuilder sb = new StringBuilder(s);
			int i = sb.indexOf("?>\n", 4);
			if (i != -1)
				sb.delete(0, i + 3);
			s = sb.toString();
		}

		final var ms = s;
		Deflater deflater = new Deflater(Deflater.DEFLATED, true);
		ByteArrayOutputStream samlRequestBuffer = new ByteArrayOutputStream();
		IuException.unchecked(() -> {
			try (DeflaterOutputStream d = new DeflaterOutputStream(samlRequestBuffer, deflater)) {
				d.write(ms.getBytes("UTF-8"));
			}
		});
		Map<String, Iterable<String>> idpParams = new LinkedHashMap<>();
		idpParams.put("SAMLRequest",
				Collections.singleton(Base64.getEncoder().encodeToString(samlRequestBuffer.toByteArray())));

		// replace this with IuJson
		// JsonObjectBuilder j = Json.createObjectBuilder();
		// j.add("sessionId", sessionId);
		// j.add("returnUrl", postURI.toString());
		String jsonString = "{\"sessionId\":\"" + sessionId + "\"" + "," + "\"returnUrl\":\"" + postURI.toString()
				+ "\"" + "}";
		idpParams.put("RelayState", Collections.singleton(SamlUtil.encrypt(jsonString)));

		URI redirectUrl = IuException
				.unchecked(() -> new URI(destinationLocation + '?' + IuWebUtils.createQueryString(idpParams)));
		return redirectUrl;

	}

	private String setSpMetadata() {
		Thread current = Thread.currentThread();
		ClassLoader currentLoader = current.getContextClassLoader();
		ClassLoader samlProvider = SamlProvider.class.getClassLoader();
		try {
			current.setContextClassLoader(samlProvider);
			X509Certificate spX509Cert = (X509Certificate) XMLObjectProviderRegistrySupport.getBuilderFactory()
					.getBuilder(X509Certificate.DEFAULT_ELEMENT_NAME).buildObject(X509Certificate.DEFAULT_ELEMENT_NAME);
			spX509Cert.setValue(PemEncoded.getEncoded(client.getCertificate()));

			X509Data spX509data = (X509Data) XMLObjectProviderRegistrySupport.getBuilderFactory()
					.getBuilder(X509Data.DEFAULT_ELEMENT_NAME).buildObject(X509Data.DEFAULT_ELEMENT_NAME);
			spX509data.getX509Certificates().add(spX509Cert);

			KeyInfo spKeyInfo = (KeyInfo) XMLObjectProviderRegistrySupport.getBuilderFactory()
					.getBuilder(KeyInfo.DEFAULT_ELEMENT_NAME).buildObject(KeyInfo.DEFAULT_ELEMENT_NAME);
			spKeyInfo.getX509Datas().add(spX509data);

			KeyDescriptor spKeyDescriptor = (KeyDescriptor) XMLObjectProviderRegistrySupport.getBuilderFactory()
					.getBuilder(KeyDescriptor.DEFAULT_ELEMENT_NAME).buildObject(KeyDescriptor.DEFAULT_ELEMENT_NAME);
			spKeyDescriptor.setKeyInfo(spKeyInfo);

			EntityDescriptor spEntityDescriptor = (EntityDescriptor) XMLObjectProviderRegistrySupport
					.getBuilderFactory().getBuilder(EntityDescriptor.ELEMENT_QNAME)
					.buildObject(EntityDescriptor.ELEMENT_QNAME);
			spEntityDescriptor.setEntityID(client.getServiceProviderEntityId());

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
			for (String acsUrl : IuIterable.map(client.getAcsUris(), URI::toString)) {
				AssertionConsumerService acs = (AssertionConsumerService) XMLObjectProviderRegistrySupport
						.getBuilderFactory().getBuilder(AssertionConsumerService.DEFAULT_ELEMENT_NAME)
						.buildObject(AssertionConsumerService.DEFAULT_ELEMENT_NAME);
				acs.setBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
				acs.setLocation(acsUrl);
				acs.setIndex(i++);
				spsso.getAssertionConsumerServices().add(acs);
			}

			return XmlDomUtil.getContent(XMLObjectProviderRegistrySupport.getMarshallerFactory()
					.getMarshaller(spEntityDescriptor).marshall(spEntityDescriptor));

		} catch (MarshallingException e) {
			throw new IllegalStateException(e);
		} finally {
			current.setContextClassLoader(currentLoader);
		}
	}

	@Override
	public void validate(InetAddress address, String acsUrl, String samlResponse) {
		Thread current = Thread.currentThread();
		ClassLoader currentLoader = current.getContextClassLoader();
		ClassLoader endpointLoader = SamlProvider.class.getClassLoader();
		try {
			current.setContextClassLoader(endpointLoader);

			Response response;
			try (InputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(samlResponse))) {
				response = (Response) XMLObjectSupport
						.unmarshallFromInputStream(XMLObjectProviderRegistrySupport.getParserPool(), in);
			} catch (Throwable e) {
				throw new IuBadRequestException("Invalid SAMLResponse", e);
			}
			String entityId = response.getIssuer().getValue();
			LOG.fine(() -> "SAML2 authentication response\nEntity ID: " + entityId + "\nACS URL: " + acsUrl + "\n"
					+ XmlDomUtil.getContent(response.getDOM()));

			CredentialResolver credentialResolver = getCredentialResolver(entityId);
			SignatureTrustEngine newTrustEngine = new ExplicitKeySignatureTrustEngine(credentialResolver,
					getKeyInfoCredentialResolver(response));
			SignaturePrevalidator newSignaturePrevalidator = new SAMLSignatureProfileValidator();
			try {
				SignatureValidationParameters vparam = new SignatureValidationParameters();
				vparam.setSignatureTrustEngine(newTrustEngine);
				newSignaturePrevalidator.validate(response.getSignature());
				if (!newTrustEngine.validate(response.getSignature(),
						new CriteriaSet(new SignatureValidationParametersCriterion(vparam))))
					throw new SecurityException("SAML signature verification failed");
			} catch (SignatureException | org.opensaml.security.SecurityException e) {
				throw new SecurityException(e);
			}

			IuSubjectConfirmationValidator subjectValidator = new IuSubjectConfirmationValidator(null, false);

			SAML20AssertionValidator validator = new SAML20AssertionValidator(
					Arrays.asList(new AudienceRestrictionConditionValidator()), Arrays.asList(subjectValidator),
					Collections.emptySet(), null, newTrustEngine, newSignaturePrevalidator);

			Map<String, Object> staticParams = new HashMap<>();
			staticParams.put(SAML2AssertionValidationParameters.COND_VALID_AUDIENCES,
					Collections.singleton(client.getServiceProviderEntityId()));

			Set<InetAddress> addresses = new LinkedHashSet<>();
			addresses.add(address);
			// TODO handle development environment
			/*
			 * if (IU.SPI.isDevelopment()) { Enumeration<NetworkInterface> nifc; try { nifc
			 * = NetworkInterface.getNetworkInterfaces(); } catch (SocketException e) {
			 * throw new IllegalStateException(e); } if (nifc != null) while
			 * (nifc.hasMoreElements()) { NetworkInterface ni = nifc.nextElement();
			 * Enumeration<InetAddress> iae = ni.getInetAddresses(); if (iae == null)
			 * continue; while (iae.hasMoreElements()) addresses.add(iae.nextElement()); } }
			 */
			staticParams.put(SAML2AssertionValidationParameters.SC_VALID_ADDRESSES, addresses);

			staticParams.put(SAML2AssertionValidationParameters.SC_VALID_RECIPIENTS, Collections.singleton(acsUrl));
			staticParams.put(SAML2AssertionValidationParameters.SIGNATURE_REQUIRED, false);
			ValidationContext ctx = new ValidationContext(staticParams);

			if (LOG.isLoggable(Level.FINE))
				LOG.fine("SAML2 authentication response\nEntity ID: " + client.getServiceProviderEntityId()
						+ "\nACS URL: " + acsUrl + "\nAllow IP Range: " + client.getAllowedRange() + "\n"
						+ XmlDomUtil.getContent(response.getDOM()) + "\nStatic Params: " + staticParams);

			// SamlAttributes samlAttributes = new SamlAttributes();
			// samlAttributes.setEntityId(entityId);
			/*
			 * try { for (Assertion assertion : response.getAssertions()) {
			 * validator.validate(assertion, ctx); for (AttributeStatement
			 * attributeStatement : assertion.getAttributeStatements()) for (Attribute
			 * attribute : attributeStatement.getAttributes()) /*if
			 * ("eduPersonPrincipalName".equals(attribute.getFriendlyName()) ||
			 * EDU_PERSON_PRINCIPAL_NAME_OID.equals(attribute.getName()))
			 * //samlAttributes.setEduPersonPrincipalName(readStringAttribute(attribute));
			 * else if ("displayName".equals(attribute.getFriendlyName()) ||
			 * DISPLAY_NAME_OID.equals(attribute.getName()))
			 * //samlAttributes.setDisplayName(readStringAttribute(attribute)); else if
			 * ("mail".equals(attribute.getFriendlyName()) ||
			 * MAIL_OID.equals(attribute.getName()))
			 * //samlAttributes.setMail(readStringAttribute(attribute));
			 * 
			 * }
			 */
			for (EncryptedAssertion encryptedAssertion : response.getEncryptedAssertions()) {
				Assertion assertion;
				try {
					// see:
					// https://stackoverflow.com/questions/22672416/org-opensaml-xml-validation-validationexception-apache-xmlsec-idresolver-could
					// https://stackoverflow.com/questions/24364686/saml-2-0-decrypting-encryptedassertion-removes-a-namespace-declaration
					Decrypter dc = getDecrypter();
					dc.setRootInNewDocument(true);
					assertion = dc.decrypt(encryptedAssertion);
				} catch (DecryptionException e) {
					throw new IuBadRequestException("Invalid encrypted assertion in response", e);
				}
				try {
					validator.validate(assertion, ctx);
				} catch (AssertionValidationException e) {
					throw new IuBadRequestException(ctx.toString(), e);
				}
				LOG.fine("SAML2 assertion " + XmlDomUtil.getContent(assertion.getDOM()));
				/*
				 * for (AttributeStatement attributeStatement :
				 * assertion.getAttributeStatements()) for (Attribute attribute :
				 * attributeStatement.getAttributes()) if
				 * ("eduPersonPrincipalName".equals(attribute.getFriendlyName()) ||
				 * EDU_PERSON_PRINCIPAL_NAME_OID.equals(attribute.getName()))
				 * samlAttributes.setEduPersonPrincipalName(readStringAttribute(attribute));
				 * else if ("displayName".equals(attribute.getFriendlyName()) ||
				 * DISPLAY_NAME_OID.equals(attribute.getName()))
				 * samlAttributes.setDisplayName(readStringAttribute(attribute)); else if
				 * ("mail".equals(attribute.getFriendlyName()) ||
				 * MAIL_OID.equals(attribute.getName()))
				 * samlAttributes.setMail(readStringAttribute(attribute));
				 */
			}

			/*
			 * if (samlAttributes.getEduPersonPrincipalName() == null) throw new
			 * IuBadRequestException(
			 * "SAML2 must have at least one assertion with eduPersonPrincipalName attribute"
			 * );
			 */

			SubjectConfirmation confirmation = (SubjectConfirmation) ctx.getDynamicParameters()
					.get(SAML2AssertionValidationParameters.CONFIRMED_SUBJECT_CONFIRMATION);
			if (confirmation == null)
				throw new IuBadRequestException("Missing subject confirmation: " + ctx.getValidationFailureMessages()
						+ "\n" + ctx.getDynamicParameters());

			LOG.fine("SAML2 subject confirmation " + XmlDomUtil.getContent(confirmation.getDOM()));

			// samlAttributes.setInResponseTo(confirmation.getSubjectConfirmationData().getInResponseTo());

			// return samlAttributes;
		} finally {
			current.setContextClassLoader(currentLoader);
		}
	}

	private Decrypter getDecrypter() {
		PrivateKey privateKey = getPrivateKey();
		List<Credential> certs = new ArrayList<>();
		certs.add(new BasicX509Credential(client.getCertificate(), privateKey));
		KeyInfoCredentialResolver keyInfoResolver = new StaticKeyInfoCredentialResolver(certs);

		return new Decrypter(null, keyInfoResolver, new InlineEncryptedKeyResolver());
	}

	public PrivateKey getPrivateKey() {
		PrivateKey parsedPrivateKey = null;
		StringBuilder pk = new StringBuilder(client.getPrivateKey());
		int i = pk.indexOf("-----BEGIN PRIVATE KEY-----");
		if (i != -1)
			pk.delete(0, i + 28);
		i = pk.indexOf("-----END PRIVATE KEY-----");
		if (i != -1)
			pk.setLength(i);
		for (i = 0; i < pk.length(); i++)
			if (Character.isWhitespace(pk.charAt(i)))
				pk.deleteCharAt(i--);

		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pk.toString()));
		KeyFactory kf;
		try {
			kf = KeyFactory.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		try {
			parsedPrivateKey = kf.generatePrivate(spec);
		} catch (InvalidKeySpecException e) {
			throw new SecurityException(e);
		}

		return parsedPrivateKey;
	}

	private CredentialResolver getCredentialResolver(String entityId) {
		EntityDescriptor entity = getEntity(entityId);
		IDPSSODescriptor idp = entity.getIDPSSODescriptor("urn:oasis:names:tc:SAML:2.0:protocol");
		CertificateFactory certFactory;
		try {
			certFactory = CertificateFactory.getInstance("X.509");
		} catch (CertificateException e) {
			throw new IllegalArgumentException(e);
		}

		List<Credential> certs = new ArrayList<>();
		for (KeyDescriptor kds : idp.getKeyDescriptors())
			if (kds.getKeyInfo() != null)
				for (X509Data x509data : kds.getKeyInfo().getX509Datas())
					for (org.opensaml.xmlsec.signature.X509Certificate x509cert : x509data.getX509Certificates())
						try {
							StringBuilder keyData = new StringBuilder(x509cert.getValue());
							for (int i = 0; i < keyData.length();)
								if (Character.isWhitespace(keyData.charAt(i)))
									keyData.deleteCharAt(i);
								else
									i++;

							certs.add(new BasicX509Credential(
									(java.security.cert.X509Certificate) certFactory.generateCertificate(
											new ByteArrayInputStream(Base64.getDecoder().decode(keyData.toString())))));

						} catch (CertificateException e) {
							LOG.log(Level.WARNING, e, () -> "Invalid cert in key data for " + entityId);
						}

		return new StaticCredentialResolver(certs);
	}

	private KeyInfoCredentialResolver getKeyInfoCredentialResolver(Response response) {
		CertificateFactory certFactory;
		try {
			certFactory = CertificateFactory.getInstance("X.509");
		} catch (CertificateException e) {
			throw new IllegalArgumentException(e);
		}

		List<Credential> certs = new ArrayList<>();
		for (Assertion assertion : response.getAssertions())
			if (assertion.getSignature() != null && assertion.getSignature().getKeyInfo() != null)
				for (X509Data x509data : assertion.getSignature().getKeyInfo().getX509Datas())
					for (X509Certificate x509cert : x509data.getX509Certificates())
						try {
							StringBuilder keyData = new StringBuilder(x509cert.getValue());
							for (int i = 0; i < keyData.length();)
								if (Character.isWhitespace(keyData.charAt(i)))
									keyData.deleteCharAt(i);
								else
									i++;

							certs.add(new BasicX509Credential(
									(java.security.cert.X509Certificate) certFactory.generateCertificate(
											new ByteArrayInputStream(Base64.getDecoder().decode(keyData.toString())))));

						} catch (CertificateException e) {
							LOG.log(Level.WARNING, e,
									() -> "Invalid cert in response data for " + response.getDestination());
						}

		return new StaticKeyInfoCredentialResolver(certs);
	}

	@Override
	public String getServiceProviderMetaData() {
		return this.spMetaData;
	}

}
