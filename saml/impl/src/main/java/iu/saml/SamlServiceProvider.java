/*
 * Copyright © 2026 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.saml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.config.XMLConfigurator;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.config.SAMLConfiguration;
import org.opensaml.saml.metadata.resolver.ChainingMetadataResolver;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.AttributeConsumingService;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.RequestedAttribute;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.impl.StaticCredentialResolver;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.config.DecryptionParserPool;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.X509Certificate;
import org.opensaml.xmlsec.signature.X509Data;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.w3c.dom.Document;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuRequestAttributes;
import edu.iu.IuStatefulRedirect;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.client.HttpResponseHandler;
import edu.iu.client.IuHttp;
import edu.iu.crypt.PemEncoded;
import edu.iu.saml.IuSamlPrincipal;
import edu.iu.saml.IuSamlServiceProvider;
import edu.iu.session.IuSessionHandler;
import iu.saml.config.IuSamlServiceProviderMetadata;
import net.shibboleth.shared.resolver.CriteriaSet;

/**
 * SAML session implementation to support session management
 */
public final class SamlServiceProvider implements IuSamlServiceProvider {

	static {
		IuObject.assertNotOpen(SamlServiceProvider.class);

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

	private static final Logger LOG = Logger.getLogger(SamlServiceProvider.class.getName());

	private Supplier<IuSamlServiceProviderMetadata> config;
	private IuSessionHandler sessionHandler;
	private MetadataResolver metadataResolver;
	private Instant lastMetadataUpdate;

	/**
	 * Constructor.
	 * 
	 * @param config         SP metadata configuration supplier
	 * @param sessionHandler Session handler
	 */
	SamlServiceProvider(Supplier<IuSamlServiceProviderMetadata> config, IuSessionHandler sessionHandler) {
		this.config = config;
		this.sessionHandler = sessionHandler;
	}

	/**
	 * Gets the most recently cached metadata resolver.
	 * 
	 * @param config supplied configuration
	 * @return metadata resolver
	 */
	MetadataResolver getMetadataResolver(IuSamlServiceProviderMetadata config) {
		if (lastMetadataUpdate == null //
				|| lastMetadataUpdate.isBefore(Instant.now().minus(config.getMetadataTtl()))) {
			metadataResolver = readMetadata(config);
			lastMetadataUpdate = Instant.now();
		}
		return metadataResolver;
	}

	/**
	 * Validates 200 OK then returns the response as UTF-8 text.
	 */
	static final HttpResponseHandler<Document> READ_XML = IuHttp.validate(
			a -> IuException.unchecked(() -> XMLObjectProviderRegistrySupport.getParserPool().parse(a)), IuHttp.OK);

	/**
	 * Get metadata resolver {@link MetadataResolver} for given metadata URIs
	 * 
	 * @param config SP configuration metadata
	 * @return {@link MetadataResolver} metadata resolver
	 */
	static MetadataResolver readMetadata(IuSamlServiceProviderMetadata config) {
		return IuException.unchecked(() -> {
			final List<MetadataResolver> resolvers = new ArrayList<>();
			for (URI metadataUri : config.getMetadataUris()) {
				final var metadataElement = IuHttp.get(metadataUri, READ_XML).getDocumentElement();
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
	}

	/**
	 * Looks up an {@link IDPSSODescriptor} in IDP metadata.
	 * 
	 * @param entityId IDP entity ID
	 * @param metadata IDP metadata resolver
	 * @return {@link IDPSSODescriptor}
	 */
	static IDPSSODescriptor getIdpSSO(String entityId, MetadataResolver metadata) {
		return Objects.requireNonNull(
				Objects.requireNonNull(
						IuException.unchecked(
								() -> metadata.resolveSingle(new CriteriaSet(new EntityIdCriterion(entityId)))),
						"Entity " + entityId + " not found in SAML metadata")
						.getIDPSSODescriptor("urn:oasis:names:tc:SAML:2.0:protocol"),
				"Missing SAML 2.0 IDP descriptor for " + entityId);
	}

	/**
	 * Creates a {@link ExplicitKeySignatureTrustEngine}
	 * 
	 * @param entityId IDP entity ID
	 * @param metadata IDP metadata resolver
	 * @return {@link ExplicitKeySignatureTrustEngine}
	 */
	static ExplicitKeySignatureTrustEngine createTrustEngine(String entityId, MetadataResolver metadata) {
		final List<Credential> certs = new ArrayList<>();
		for (final var keyDescriptor : getIdpSSO(entityId, metadata).getKeyDescriptors())
			for (final var x509data : keyDescriptor.getKeyInfo().getX509Datas())
				for (final var x509cert : x509data.getX509Certificates())
					certs.add(new BasicX509Credential(PemEncoded.parse(x509cert.getValue()).next().asCertificate()));

		final var credentialResolver = new StaticCredentialResolver(certs);
		final var keyInfoResolver = new StaticKeyInfoCredentialResolver(certs);

		return new ExplicitKeySignatureTrustEngine(credentialResolver, keyInfoResolver);
	}

	/**
	 * Gets service provider metadata XML
	 * 
	 * @param config SP metadata configuration
	 * @return service provider metadata XML
	 */
	static String getServiceProviderMetadata(IuSamlServiceProviderMetadata config) {
		Thread current = Thread.currentThread();
		final var restore = current.getContextClassLoader();
		final var loader = SamlServiceProvider.class.getClassLoader();
		try {
			current.setContextClassLoader(loader);
			X509Certificate spX509Cert = (X509Certificate) XMLObjectProviderRegistrySupport.getBuilderFactory()
					.getBuilder(X509Certificate.DEFAULT_ELEMENT_NAME).buildObject(X509Certificate.DEFAULT_ELEMENT_NAME);

			final var certificate = config.getIdentity().getCertificateChain()[0];
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

			EntityDescriptor spEntityDescriptor = (EntityDescriptor) XMLObjectProviderRegistrySupport
					.getBuilderFactory().getBuilder(EntityDescriptor.ELEMENT_QNAME)
					.buildObject(EntityDescriptor.ELEMENT_QNAME);
			spEntityDescriptor.setEntityID(config.getServiceProviderEntityId());

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
			for (String acsUrl : IuIterable.map(config.getAcsUris(), URI::toString)) {
				AssertionConsumerService acs = (AssertionConsumerService) XMLObjectProviderRegistrySupport
						.getBuilderFactory().getBuilder(AssertionConsumerService.DEFAULT_ELEMENT_NAME)
						.buildObject(AssertionConsumerService.DEFAULT_ELEMENT_NAME);
				acs.setBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
				acs.setLocation(acsUrl);
				acs.setIndex(i++);
				spsso.getAssertionConsumerServices().add(acs);
			}

			return IuException.unchecked(() -> XmlDomUtil.getContent(XMLObjectProviderRegistrySupport
					.getMarshallerFactory().getMarshaller(spEntityDescriptor).marshall(spEntityDescriptor)));
		} finally {
			current.setContextClassLoader(restore);
		}
	}

	@Override
	public String metadata() {
		return getServiceProviderMetadata(config.get());
	}

	@Override
	public IuStatefulRedirect initRequest(URI postUri, URI returnUri) {
		Objects.requireNonNull(returnUri, "Missing returnUri");
		Objects.requireNonNull(postUri, "Missing postUri");

		final var config = this.config.get();
		final var sessionId = IdGenerator.generateId();

		final var metadata = getMetadataResolver(config);
		final var entityId = config.getIdentityProviderEntityIds().iterator().next();
		final var singleSignOnLocation = IuIterable
				.select(getIdpSSO(entityId, metadata).getSingleSignOnServices(),
						sso -> "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect".equals(sso.getBinding()))
				.getLocation();

		final var authnRequest = (AuthnRequest) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(AuthnRequest.DEFAULT_ELEMENT_NAME).buildObject(AuthnRequest.DEFAULT_ELEMENT_NAME);
		authnRequest.setAssertionConsumerServiceURL(postUri.toString());
		authnRequest.setDestination(singleSignOnLocation.toString());
		authnRequest.setID(sessionId);
		authnRequest.setIssueInstant(Instant.now());
		authnRequest.setProtocolBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
		authnRequest.setVersion(SAMLVersion.VERSION_20);

		final var issuer = (Issuer) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(Issuer.DEFAULT_ELEMENT_NAME).buildObject(Issuer.DEFAULT_ELEMENT_NAME);
		issuer.setValue(config.getServiceProviderEntityId());
		authnRequest.setIssuer(issuer);

		final var nameIdPolicy = (NameIDPolicy) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(NameIDPolicy.DEFAULT_ELEMENT_NAME).buildObject(NameIDPolicy.DEFAULT_ELEMENT_NAME);
		nameIdPolicy.setAllowCreate(true);
		authnRequest.setNameIDPolicy(nameIdPolicy);

		final var samlRequest = IuException.unchecked(() -> {
			var s = XmlDomUtil.getContent(XMLObjectProviderRegistrySupport.getMarshallerFactory()
					.getMarshaller(authnRequest).marshall(authnRequest));

			final var deflater = new Deflater(Deflater.DEFLATED, true);
			final var samlRequestBuffer = new ByteArrayOutputStream();
			try (DeflaterOutputStream d = new DeflaterOutputStream(samlRequestBuffer, deflater)) {
				d.write(IuText.utf8(s));
			}

			return IuText.base64(samlRequestBuffer.toByteArray());
		});

		final var relayState = IdGenerator.generateId();
		final Map<String, Iterable<String>> idpParams = new LinkedHashMap<>();
		idpParams.put("SAMLRequest", Collections.singleton(samlRequest));
		idpParams.put("RelayState", Collections.singleton(relayState));
		final var location = URI.create(singleSignOnLocation + '?' + IuWebUtils.createQueryString(idpParams));

		final var session = sessionHandler.create();
		final var detail = session.getDetail(SamlPreAuthentication.class);
		detail.setRelayState(relayState);
		detail.setSessionId(sessionId);
		detail.setPostUri(postUri);
		detail.setReturnUri(returnUri);
		session.setStrict(false);
		final var setCookie = sessionHandler.store(session);

		return new IuStatefulRedirect() {
			@Override
			public String getSetCookie() {
				return setCookie;
			}

			@Override
			public URI getLocation() {
				return location;
			}
		};
	}

	@Override
	public IuStatefulRedirect verifyResponse(IuRequestAttributes requestAttributes, String samlResponse,
			String relayState) {
		final var config = this.config.get();
		final var metadata = getMetadataResolver(config);

		final var session = sessionHandler.activate(requestAttributes.getCookies());
		final var preAuth = session.getDetail(SamlPreAuthentication.class);
		session.clearDetail(SamlPreAuthentication.class);

		final var postAuth = session.getDetail(SamlPostAuthentication.class);

		final var returnUri = Objects.requireNonNull(preAuth.getReturnUri(), "Missing returnUri");
		final var postUri = Objects.requireNonNull(preAuth.getPostUri(), "Missing postUri");
		final var sessionId = Objects.requireNonNull(preAuth.getSessionId(), "Missing sessionId");

		try {
			if (relayState != null)
				IuObject.once(relayState,
						Objects.requireNonNull(preAuth.getRelayState(), "Missing relayState in session"),
						"RelayState mismatch");

			Objects.requireNonNull(samlResponse, "Missing SAMLResponse parameter");

			Thread current = Thread.currentThread();
			ClassLoader currentLoader = current.getContextClassLoader();
			try {
				current.setContextClassLoader(XMLObjectSupport.class.getClassLoader());

				final var response = IuException.unchecked(() -> {
					try (InputStream in = new ByteArrayInputStream(IuText.base64(samlResponse))) {
						return (Response) XMLObjectSupport
								.unmarshallFromInputStream(XMLObjectProviderRegistrySupport.getParserPool(), in);
					}
				}, "Invalid SAMLResponse");

				final var entityId = response.getIssuer().getValue();
				LOG.fine(() -> "SAML2 authentication response\nEntity ID: " + entityId + "\nPOST URL: "
						+ postUri.toString() + "\n" + XmlDomUtil.getContent(response.getDOM()));

				final var samlResponseValidator = new SamlResponseValidator(postUri, entityId, sessionId,
						IuWebUtils.getInetAddress(requestAttributes.getRemoteAddr()), config,
						createTrustEngine(entityId, metadata));

				final var samlPrincipal = IuException.unchecked(() -> samlResponseValidator.validate(response));
				postAuth.setName(samlPrincipal.getName());
				postAuth.setAssertions(samlPrincipal.getAssertions());

			} finally {
				current.setContextClassLoader(currentLoader);
			}
		} catch (Throwable e) {
			LOG.log(Level.INFO, "Invalid SAML Response", e);
			postAuth.setInvalid(true);
		}

		final var setCookie = sessionHandler.store(session);
		return new IuStatefulRedirect() {
			@Override
			public String getSetCookie() {
				return setCookie;
			}

			@Override
			public URI getLocation() {
				return returnUri;
			}
		};
	}

	@Override
	public IuSamlPrincipal getPrincipalIdentity(IuRequestAttributes requestAttributes) {
		return SamlPrincipal.from(Objects
				.requireNonNull(sessionHandler.activate(requestAttributes.getCookies()), "missing or expired session")
				.getDetail(SamlPostAuthentication.class));
	}

}
