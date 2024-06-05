package iu.auth.saml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.common.assertion.AssertionValidationException;
import org.opensaml.saml.common.assertion.ValidationContext;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.saml2.assertion.SAML20AssertionValidator;
import org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters;
import org.opensaml.saml.saml2.assertion.impl.AudienceRestrictionConditionValidator;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.EncryptedAssertion;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialResolver;
import org.opensaml.security.credential.impl.StaticCredentialResolver;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.SignatureValidationParameters;
import org.opensaml.xmlsec.encryption.support.DecryptionException;
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.X509Certificate;
import org.opensaml.xmlsec.signature.X509Data;
import org.opensaml.xmlsec.signature.support.SignaturePrevalidator;
import org.opensaml.xmlsec.signature.support.SignatureTrustEngine;
import org.opensaml.xmlsec.signature.support.SignatureValidationParametersCriterion;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;

import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;
import edu.iu.crypt.PemEncoded;
import net.shibboleth.shared.resolver.CriteriaSet;

/**
 * {@link IuSamlProvider implementation}
 */
public class SamlProvider implements IuSamlProvider {
	private final Logger LOG = Logger.getLogger(SamlProvider.class.getName());

	private IuSamlClient client;
	private SamlBuilder samlBuilder;

	private static final String MAIL_OID = "urn:oid:0.9.2342.19200300.100.1.3";
	private static final String DISPLAY_NAME_OID = "urn:oid:2.16.840.1.113730.3.1.241";
	private static final String EDU_PERSON_PRINCIPAL_NAME_OID = "urn:oid:1.3.6.1.4.1.5923.1.1.1.6";

	/**
	 * Initialize SAML provider
	 * 
	 * @param client {@link IuSamlClient}
	 */
	public SamlProvider(IuSamlClient client) {
		this.client = client;
		this.samlBuilder = new SamlBuilder(client);
	}

	/**
	 * Gets identity provider location
	 * 
	 * @param entityId service provider entity id
	 * @return identity provider location
	 */
	String getSingleSignOnLocation(String entityId) {
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
		MetadataResolver resolver = samlBuilder.getMetadata();
		EntityDescriptor entity = IuException
				.unchecked(() -> resolver.resolveSingle(new CriteriaSet(new EntityIdCriterion(entityId))));
		if (entity == null)
			throw new IllegalArgumentException("Entity " + entityId + " not found in SAML metadata");
		return entity;

	}

	/**
	 * Generate SAML authentication request use by client to redirect user to
	 * identity provider system for authentication.
	 * 
	 * @param postURI             send back URI
	 * @param sessionId           session id associated with authentication request
	 * @param destinationLocation destination location
	 * @return encrypted SAML XML authentication request
	 */
	ByteArrayOutputStream getAuthRequest(URI postURI, String sessionId, String destinationLocation) {

		var matchAcs = false;
		for (URI acsUrl : client.getAcsUris()) {
			if (acsUrl.compareTo(postURI) == 0)
				matchAcs = true;
		}
		if (!matchAcs) {
			throw new IllegalArgumentException(
					"Post URI doesn't match with allowed list of Assertion Consumer Service URLs " + postURI);
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

		return samlRequestBuffer;

	}

	/**
	 * Authorize SAML response return back from IDP
	 * 
	 * @param address      IP address use by user to authenticate
	 * @param postUri      Post back URI
	 * @param samlResponse xml SAML response back from identity provider
	 * @param relayState   return back relayState from identity provider
	 * @return SAML attributes
	 */
	SamlPrincipal authorize(InetAddress address, URI postUri, String samlResponse, String relayState) {
		Thread current = Thread.currentThread();
		ClassLoader currentLoader = current.getContextClassLoader();
		current.setContextClassLoader(currentLoader);

		Response response;
		try (InputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(samlResponse))) {
			response = (Response) XMLObjectSupport
					.unmarshallFromInputStream(XMLObjectProviderRegistrySupport.getParserPool(), in);
		} catch (Throwable e) {
			throw new IllegalArgumentException("Invalid SAMLResponse", e);
		}
		String entityId = response.getIssuer().getValue();
		LOG.fine("SAML2 authentication response\nEntity ID: " + entityId + "\nPOST URL: " + postUri.toString() + "\n"
				+ XmlDomUtil.getContent(response.getDOM()));
		CredentialResolver credentialResolver = IuException.unchecked(() -> getCredentialResolver(getEntity(entityId)));
		SignatureTrustEngine newTrustEngine = new ExplicitKeySignatureTrustEngine(credentialResolver,
				getKeyInfoCredentialResolver(response));
		SignaturePrevalidator newSignaturePrevalidator = new SAMLSignatureProfileValidator();

		// validate certificate
		IuException.unchecked(() -> {
			SignatureValidationParameters vparam = new SignatureValidationParameters();
			vparam.setSignatureTrustEngine(newTrustEngine);
			newSignaturePrevalidator.validate(response.getSignature());
			if (!newTrustEngine.validate(response.getSignature(),
					new CriteriaSet(new SignatureValidationParametersCriterion(vparam))))
				throw new SecurityException("SAML signature verification failed");
		});

		IuSubjectConfirmationValidator subjectValidator = new IuSubjectConfirmationValidator(client.getAllowedRange(),
				client.failOnAddressMismatch());

		SAML20AssertionValidator validator = new SAML20AssertionValidator(
				Arrays.asList(new AudienceRestrictionConditionValidator()), Arrays.asList(subjectValidator),
				Collections.emptySet(), null, newTrustEngine, newSignaturePrevalidator);

		Map<String, Object> staticParams = new HashMap<>();
		staticParams.put(SAML2AssertionValidationParameters.COND_VALID_AUDIENCES,
				Collections.singleton(client.getServiceProviderEntityId()));

		Set<InetAddress> addresses = new LinkedHashSet<>();
		addresses.add(address);

		staticParams.put(SAML2AssertionValidationParameters.SC_VALID_ADDRESSES, addresses);

		staticParams.put(SAML2AssertionValidationParameters.SC_VALID_RECIPIENTS,
				Collections.singleton(postUri.toString()));
		staticParams.put(SAML2AssertionValidationParameters.SIGNATURE_REQUIRED, false);
		staticParams.put(SAML2AssertionValidationParameters.SC_VALID_IN_RESPONSE_TO, relayState);
		ValidationContext ctx = new ValidationContext(staticParams);

		LOG.fine("SAML2 authentication response\nEntity ID: " + client.getServiceProviderEntityId() + "\nACS URL: "
				+ postUri.toString() + "\nAllow IP Range: " + client.getAllowedRange() + "\n"
				+ XmlDomUtil.getContent(response.getDOM()) + "\nStatic Params: " + staticParams);

		// validate assertions
		SamlPrincipal principal;
		principal = IuException.unchecked(() -> validateAssertion(response, validator, ctx));

		current.setContextClassLoader(currentLoader);

		return principal;

	}

	private SamlPrincipal validateAssertion(Response response, SAML20AssertionValidator validator,
			ValidationContext ctx) throws AssertionValidationException {
		String principalName = null;
		String emailAddress = null;
		String displayName = null;
		final Map<String, Object> claims = new LinkedHashMap<>();
		// Gets the date/time the response was issued.
		claims.put("issueInstant", response.getIssueInstant());
		for (Assertion assertion : response.getAssertions()) {
			validator.validate(assertion, ctx);

			for (AttributeStatement attributeStatement : assertion.getAttributeStatements())
				for (Attribute attribute : attributeStatement.getAttributes())
					if ("eduPersonPrincipalName".equals(attribute.getFriendlyName())
							|| EDU_PERSON_PRINCIPAL_NAME_OID.equals(attribute.getName()))
						principalName = readStringAttribute(attribute);
					else if ("displayName".equals(attribute.getFriendlyName())
							|| DISPLAY_NAME_OID.equals(attribute.getName()))
						displayName = readStringAttribute(attribute);
					else if ("mail".equals(attribute.getFriendlyName()) || MAIL_OID.equals(attribute.getName()))
						emailAddress = readStringAttribute(attribute);
			final Conditions conditions = assertion.getConditions();
			if (conditions != null) {
				// Get the date/time before which the assertion is invalid.
				claims.put("notBefore", conditions.getNotBefore());
				// Gets the date/time on, or after, which the assertion is invalid
				claims.put("notOnOrAfter", conditions.getNotOnOrAfter());
			}
			for (AuthnStatement statement : assertion.getAuthnStatements()) {
				// Gets the time when the authentication took place.
				claims.put("authnInstant", statement.getAuthnInstant());
			}
		}

		for (EncryptedAssertion encryptedAssertion : response.getEncryptedAssertions()) {
			Assertion assertion;
			try {
				// see:
				// https://stackoverflow.com/questions/22672416/org-opensaml-xml-validation-validationexception-apache-xmlsec-idresolver-could
				// https://stackoverflow.com/questions/24364686/saml-2-0-decrypting-encryptedassertion-removes-a-namespace-declaration
				Decrypter dc = getDecrypter(client);
				dc.setRootInNewDocument(true);
				assertion = dc.decrypt(encryptedAssertion);
			} catch (DecryptionException e) {
				throw new IllegalArgumentException("Invalid encrypted assertion in response", e);
			}
			try {
				validator.validate(assertion, ctx);
			} catch (AssertionValidationException e) {
				throw new IllegalArgumentException(ctx.toString(), e);
			}
			LOG.fine("SAML2 assertion " + XmlDomUtil.getContent(assertion.getDOM()));

			for (AttributeStatement attributeStatement : assertion.getAttributeStatements())
				for (Attribute attribute : attributeStatement.getAttributes())
					if ("eduPersonPrincipalName".equals(attribute.getFriendlyName())
							|| EDU_PERSON_PRINCIPAL_NAME_OID.equals(attribute.getName()))
						principalName = readStringAttribute(attribute);
					else if ("displayName".equals(attribute.getFriendlyName())
							|| DISPLAY_NAME_OID.equals(attribute.getName()))
						displayName = readStringAttribute(attribute);
					else if ("mail".equals(attribute.getFriendlyName()) || MAIL_OID.equals(attribute.getName()))
						emailAddress = readStringAttribute(attribute);
			final Conditions conditions = assertion.getConditions();
			if (conditions != null) {
				if (conditions.getNotBefore() != null) {
					claims.put("notBefore", conditions.getNotBefore());
				}
				if (conditions.getNotOnOrAfter() != null) {
					claims.put("notOnOrAfter", conditions.getNotOnOrAfter());
				}
			}
			for (AuthnStatement statement : assertion.getAuthnStatements()) {

				claims.put("authnInstant", statement.getAuthnInstant());
			}

		}

		if (principalName == null)
			throw new IllegalArgumentException(
					"SAML2 must have at least one assertion with eduPersonPrincipalName attribute");

		SubjectConfirmation confirmation = (SubjectConfirmation) ctx.getDynamicParameters()
				.get(SAML2AssertionValidationParameters.CONFIRMED_SUBJECT_CONFIRMATION);
		if (confirmation == null)
			throw new IllegalArgumentException("Missing subject confirmation: " + ctx.getValidationFailureMessages()
					+ "\n" + ctx.getDynamicParameters());

		LOG.fine("SAML2 subject confirmation " + XmlDomUtil.getContent(confirmation.getDOM()));
		Instant notBefore = confirmation.getSubjectConfirmationData().getNotBefore();
		Instant notOnOrAfter = confirmation.getSubjectConfirmationData().getNotOnOrAfter();
		if (notBefore != null) {
			claims.put("notBefore", notBefore);
		}
		if (notOnOrAfter != null) {
			claims.put("notOnOrAfter", notOnOrAfter);
		}
		String entityId = response.getIssuer().getValue();
		SamlPrincipal principal = new SamlPrincipal(principalName, displayName, emailAddress, entityId,
				client.getServiceProviderEntityId(), claims);
		return principal;
	}

	/**
	 * Return decrypted XML object
	 * @param client {@link IuSamlClient}
	 * @return Decrypter 
	 */
	static Decrypter getDecrypter(IuSamlClient client) {
		final var pem = PemEncoded.parse(new ByteArrayInputStream(IuText.utf8(client.getPrivateKey())));
		final var key = pem.next();

		List<Credential> certs = new ArrayList<>();
		certs.add(new BasicX509Credential(client.getCertificate(), key.asPrivate("RSA")));
		KeyInfoCredentialResolver keyInfoResolver = new StaticKeyInfoCredentialResolver(certs);

		return new Decrypter(null, keyInfoResolver, new InlineEncryptedKeyResolver());
	}

	private String readStringAttribute(Attribute attribute) {
		Object attrval = attribute.getAttributeValues().get(0);
		if (attrval instanceof XSString)
			return ((XSString) attrval).getValue();
		else
			return ((XSAny) attrval).getTextContent();
	}

	/**
	 * Get credential resolver for SAML response
	 * 
	 * @param response SAML response
	 * @return credential resolver
	 */
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

	/**
	 * CredentialResolver
	 * 
	 * @param entity {@link EntityDescriptor}
	 * @return {@link CredentialResolver}
	 * @throws CertificateException
	 */
	private CredentialResolver getCredentialResolver(EntityDescriptor entity) throws CertificateException {
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
							byte[] decodedKeyData = Base64.getDecoder().decode(keyData.toString());
							certs.add(new BasicX509Credential((java.security.cert.X509Certificate) certFactory
									.generateCertificate(new ByteArrayInputStream(decodedKeyData))));

						} catch (CertificateException e) {
							LOG.log(Level.WARNING, e, () -> "Invalid cert in key data");

						}

		return new StaticCredentialResolver(certs);
	}

	@Override
	public String getServiceProviderMetaData() {
		return samlBuilder.getServiceProviderMetadata();
	}

	/**
	 * {@link IuSamlClient}
	 * 
	 * @return client configuration
	 */
	IuSamlClient getClient() {
		return client;
	}

}
