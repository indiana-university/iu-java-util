package iu.auth.saml;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.ServiceConfigurationError;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;

import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.config.XMLConfigurator;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.config.SAMLConfiguration;
import org.opensaml.saml.metadata.resolver.ChainingMetadataResolver;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.AttributeConsumingService;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.RequestedAttribute;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.xmlsec.config.DecryptionParserPool;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.X509Certificate;
import org.opensaml.xmlsec.signature.X509Data;
import org.w3c.dom.Element;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuWebUtils;
import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;
import iu.auth.util.XmlDomUtil;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
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

	private static Key CRYPT_KEY;

	static {
		try {
			CRYPT_KEY = KeyGenerator.getInstance("AES").generateKey();
		} catch (NoSuchAlgorithmException e) {
			throw new SecurityException(e);
		}
	}
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

		// We could add a logic here to check for service provider entity id that
		// is register to avoid initialization for same
		// client

		Objects.requireNonNull(client.getAcsUrls(), "Missing activation consumer urls");
		Objects.requireNonNull(client.getMetaDataUrls(), "Missing metadat urls");
		Objects.requireNonNull(client.getServiceProviderEntityId(), "Missing service provider entity Id");
		// Objects.requireNonNull(client.getCertificate(), "Missing certificate");

		// LOG.info("SAML Provider configuration:\n" +
		// client.getServiceProviderEntityId());

		this.client = client;
		this.metadataResolver = getMetadata(client.getMetaDataUrls(), client.getMetadataTtl());
		// this.spMetaData = setSpMetadata();

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
	 * @param metadataTtl metadata maximum live time
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
	public URI authRequest(URI samlEntityId, URI postURI) {

		// validate entityId against metadataUrl configuration
		var matchAcs = false;
		// TODO create new session and maintain it
		// activate
		var sessionId = IdGenerator.generateId();
		var destinationLocation = getSingleSignOnLocation(samlEntityId.toString());
		for (URI acsUrl : client.getAcsUrls()) {
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
		DeflaterOutputStream d = new DeflaterOutputStream(samlRequestBuffer, deflater);
		IuException.unchecked(() -> d.write(ms.getBytes("UTF-8")));

		Map<String, Iterable<String>> idpParams = new LinkedHashMap<>();
		idpParams.put("SAMLRequest",
				Collections.singleton(Base64.getEncoder().encodeToString(samlRequestBuffer.toByteArray())));

		// replace this with IuJson
		JsonObjectBuilder j = Json.createObjectBuilder();
		j.add("sessionId", sessionId);
		j.add("returnUrl", postURI.toString());
		idpParams.put("RelayState", Collections.singleton(encrypt(j.toString())));

		URI redirectUrl = IuException
				.unchecked(() -> new URI(destinationLocation + '?' + IuWebUtils.createQueryString(idpParams)));
		return redirectUrl;

	}

	/**
	 * encrypt data
	 * 
	 * @param s string to encrypt
	 * @return encrypted string
	 */
	static String encrypt(String s) {
		try {
			byte[] dataToEncrypt = s.trim().getBytes("UTF-8");
			int mod = dataToEncrypt.length % 16;
			if (mod != 0)
				dataToEncrypt = Arrays.copyOf(dataToEncrypt, dataToEncrypt.length + 16 - mod);
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, CRYPT_KEY);
			return Base64.getEncoder().encodeToString(cipher.doFinal(dataToEncrypt));
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to encrypt", e);
		}
	}

	/**
	 * decrypt data
	 * 
	 * @param d string to decrypt
	 * @return decrypted string
	 */
	static String decrypt(String d) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, CRYPT_KEY);
			return new String(cipher.doFinal(Base64.getDecoder().decode(d)), "UTF-8").trim();
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to decrpty", e);
		}
	}

	/*
	 * private java.security.cert.X509Certificate getCertificate() { if
	 * (parsedCertificate == null) parsedCertificate =
	 * parseCertificate(client.getCertificate()); return parsedCertificate; }
	 * 
	 * private java.security.cert.X509Certificate parseCertificate(String x509) {
	 * StringBuilder xc = new StringBuilder(x509); int i =
	 * xc.indexOf("-----BEGIN CERTIFICATE-----"); if (i != -1) xc.delete(0, i + 28);
	 * i = xc.indexOf("-----END CERTIFICATE-----"); if (i != -1) xc.setLength(i);
	 * for (i = 0; i < xc.length(); i++) if (Character.isWhitespace(xc.charAt(i)))
	 * xc.deleteCharAt(i--);
	 * 
	 * CertificateFactory certFactory; try { certFactory =
	 * CertificateFactory.getInstance("X.509"); } catch (CertificateException e) {
	 * throw new IllegalStateException(e); } try { return
	 * (java.security.cert.X509Certificate) certFactory .generateCertificate(new
	 * ByteArrayInputStream(Base64.getDecoder().decode(xc.toString()))); } catch
	 * (CertificateException e) { throw new SecurityException(e); } }
	 */

	private String setSpMetadata() {
		Thread current = Thread.currentThread();
		ClassLoader currentLoader = current.getContextClassLoader();
		ClassLoader samlProvider = SamlProvider.class.getClassLoader();
		try {
			current.setContextClassLoader(samlProvider);

			// init();

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
			for (String acsUrl : IuIterable.map(client.getAcsUrls(), URI::toString)) {
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

}
