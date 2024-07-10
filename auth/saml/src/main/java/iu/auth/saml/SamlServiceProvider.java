/*
 * Copyright © 2024 Indiana University
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
package iu.auth.saml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.security.KeyPair;
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
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.security.auth.Subject;

import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.common.assertion.AssertionValidationException;
import org.opensaml.saml.common.assertion.ValidationContext;
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
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.SignatureValidationParameters;
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.support.SignatureValidationParametersCriterion;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuAuthenticationRealm;
import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import iu.auth.config.AuthConfig;
import iu.auth.config.IuAuthConfig;
import iu.auth.config.IuSamlServiceProvider;
import iu.auth.config.IuTrustedIssuer;
import iu.auth.principal.PrincipalVerifier;
import net.shibboleth.shared.resolver.CriteriaSet;

/**
 * SAML Service Provider implementation class.
 */
public final class SamlServiceProvider implements IuSamlServiceProvider, PrincipalVerifier<SamlPrincipal> {
	static {
		IuObject.assertNotOpen(SamlServiceProvider.class);
	}

	private static final Logger LOG = Logger.getLogger(SamlServiceProvider.class.getName());

	/**
	 * Locates a SAML Service Provider configured with a given HTTP POST Binding URI
	 * as its {@link IuAuthConfig#getAuthenticationEndpoint() registered
	 * authentication endpoint}.
	 * 
	 * @param postUri HTTP POST Binding URI
	 * @return {@link SamlServiceProvider}
	 */
	static SamlServiceProvider withBinding(URI postUri) {
		Iterable<IuSamlServiceProvider> providers = AuthConfig.get(IuSamlServiceProvider.class);
		for (final var sp : providers) {
			final var samlServiceProvider = (SamlServiceProvider) sp;
			if (postUri.equals(samlServiceProvider.postUri))
				return samlServiceProvider;
		}
		throw new IllegalArgumentException();
	}

	/**
	 * Gets a {@link Decrypter} for deciphering encrypted SAML Response and
	 * Assertion content.
	 * 
	 * @param identity SAML SP {@link IuPrincipalIdentity}
	 * @return {@link Decrypter}
	 */
	static Decrypter getDecrypter(IuPrincipalIdentity identity) {
		final var encryptKey = identity.getSubject().getPrivateCredentials(WebKey.class).stream().findFirst().get();

		final var pem = PemEncoded.serialize(new KeyPair(encryptKey.getPublicKey(), encryptKey.getPrivateKey()),
				encryptKey.getCertificateChain());
		final var sb = new StringBuilder();
		pem.forEachRemaining(sb::append);

		final var pemImported = PemEncoded.parse(sb.toString());
		final var privateKey = pemImported.next().asPrivate("RSA");
		final var cert = pemImported.next().asCertificate();

		List<Credential> certs = new ArrayList<>();
		certs.add(new BasicX509Credential(cert, privateKey));
		KeyInfoCredentialResolver keyInfoResolver = new StaticKeyInfoCredentialResolver(certs);

		return new Decrypter(null, keyInfoResolver, new InlineEncryptedKeyResolver());
	}

	private final String realm;
	private final URI postUri;
	private final SamlBuilder samlBuilder;

	/**
	 * Initialize SAML provider
	 * 
	 * @param postUri HTTP POST Binding URI
	 * @param realm   authentication realm
	 * @param client  {@link IuSamlServiceProviderMetadata}
	 */
	public SamlServiceProvider(URI postUri, String realm) {
		final IuSamlServiceProviderMetadata config = IuAuthenticationRealm.of(realm);

		var matchAcs = false;
		for (final var acsUri : config.getAcsUris())
			if (acsUri.equals(postUri)) {
				matchAcs = true;
				break;
			}

		if (!matchAcs)
			throw new IllegalArgumentException(
					"Post URI doesn't match with allowed list of Assertion Consumer Service URLs");

		this.postUri = postUri;
		this.realm = realm;
		this.samlBuilder = new SamlBuilder(config);
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public String getAuthScheme() {
		return null;
	}

	@Override
	public URI getAuthenticationEndpoint() {
		return postUri;
	}

	@Override
	public Class<SamlPrincipal> getType() {
		return SamlPrincipal.class;
	}

	@Override
	public boolean isAuthoritative() {
		return true;
	}

	@Override
	public void verify(SamlPrincipal id) throws IuAuthenticationException {
		try {
			id.verify(realm);
		} catch (Throwable e) {
			throw new IuAuthenticationException(null, e);
		}
	}

	/**
	 * Gets the service provider metadata for external hosting.
	 * 
	 * @return SP metadata
	 */
	public String getServiceProviderMetaData() {
		return samlBuilder.getServiceProviderMetadata();
	}

	/**
	 * Checks an entry point URI against the configured allow list.
	 * 
	 * @param entryPointUri entry point URI
	 * @return true if allowed
	 */
	boolean isValidEntryPoint(URI entryPointUri) {
		for (final var entryPoint : samlBuilder.entryPointUris)
			if (entryPoint.equals(entryPointUri))
				return true;
		return false;
	}

	/**
	 * Generate SAML authentication request use by client to redirect user to
	 * identity provider system for authentication.
	 * 
	 * @param relayState RelayState parameter value
	 * @param sessionId  SAML AuthnRequest ID
	 * @return SAML AuthnRequest Redirect URI
	 */
	URI getAuthnRequest(String relayState, String sessionId) {
		final var destinationLocation = samlBuilder.singleSignOnLocation.toString();

		final var authnRequest = (AuthnRequest) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(AuthnRequest.DEFAULT_ELEMENT_NAME).buildObject(AuthnRequest.DEFAULT_ELEMENT_NAME);
		authnRequest.setAssertionConsumerServiceURL(postUri.toString());
		authnRequest.setDestination(destinationLocation);
		authnRequest.setID(sessionId);
		authnRequest.setIssueInstant(Instant.now());
		authnRequest.setProtocolBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
		authnRequest.setVersion(SAMLVersion.VERSION_20);

		final var issuer = (Issuer) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(Issuer.DEFAULT_ELEMENT_NAME).buildObject(Issuer.DEFAULT_ELEMENT_NAME);
		issuer.setValue(samlBuilder.serviceProviderEntityId);
		authnRequest.setIssuer(issuer);

		final var nameIdPolicy = (NameIDPolicy) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(NameIDPolicy.DEFAULT_ELEMENT_NAME).buildObject(NameIDPolicy.DEFAULT_ELEMENT_NAME);
		nameIdPolicy.setAllowCreate(true);
		authnRequest.setNameIDPolicy(nameIdPolicy);

		final var samlRequest = IuException.unchecked(() -> {
			var s = XmlDomUtil.getContent(XMLObjectProviderRegistrySupport.getMarshallerFactory()
					.getMarshaller(authnRequest).marshall(authnRequest));
			if (s.startsWith("<?xml")) {
				StringBuilder sb = new StringBuilder(s);
				int i = sb.indexOf("?>\n", 4);
				if (i != -1)
					sb.delete(0, i + 3);
				s = sb.toString();
			}

			final var deflater = new Deflater(Deflater.DEFLATED, true);
			final var samlRequestBuffer = new ByteArrayOutputStream();
			try (DeflaterOutputStream d = new DeflaterOutputStream(samlRequestBuffer, deflater)) {
				d.write(IuText.utf8(s));
			}

			return Base64.getEncoder().encodeToString(samlRequestBuffer.toByteArray());
		});

		final Map<String, Iterable<String>> idpParams = new LinkedHashMap<>();
		idpParams.put("SAMLRequest", Collections.singleton(samlRequest));
		idpParams.put("RelayState", Collections.singleton(relayState));
		return URI.create(destinationLocation + '?' + IuWebUtils.createQueryString(idpParams));
	}

	/**
	 * Authorize SAML response return back from IDP
	 * 
	 * @param address      IP address use by user to authenticate
	 * @param samlResponse xml SAML response back from identity provider
	 * @param sessionId    SAML session ID from original AuthnRequest
	 * @return {@link SamlPrincipal}
	 */
	SamlPrincipal verifyResponse(InetAddress address, String samlResponse, String sessionId) {
		final IuSamlServiceProviderMetadata config = IuAuthenticationRealm.of(realm);
		final IuPrincipalIdentity identity = serviceProviderIdentity(config);

		Thread current = Thread.currentThread();
		ClassLoader currentLoader = current.getContextClassLoader();
		try {
			current.setContextClassLoader(XMLObjectSupport.class.getClassLoader());

			final var response = IuException.unchecked(() -> {
				try (InputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(samlResponse))) {
					return (Response) XMLObjectSupport
							.unmarshallFromInputStream(XMLObjectProviderRegistrySupport.getParserPool(), in);
				}
			}, "Invalid SAMLResponse");

			String entityId = response.getIssuer().getValue();
			LOG.fine(() -> "SAML2 authentication response\nEntity ID: " + entityId + "\nPOST URL: " + postUri.toString()
					+ "\n" + XmlDomUtil.getContent(response.getDOM()));

			final var newTrustEngine = new ExplicitKeySignatureTrustEngine(samlBuilder.credentialResolver,
					samlBuilder.getKeyInfoCredentialResolver(entityId));

			final var newSignaturePrevalidator = new SAMLSignatureProfileValidator();

			// Verify SAMLResponse signature
			IuException.unchecked(() -> {
				SignatureValidationParameters vparam = new SignatureValidationParameters();
				vparam.setSignatureTrustEngine(newTrustEngine);
				newSignaturePrevalidator.validate(response.getSignature());
				if (!newTrustEngine.validate(response.getSignature(),
						new CriteriaSet(new SignatureValidationParametersCriterion(vparam))))
					throw new IllegalArgumentException("SAML signature verification failed");
			});

			final var validator = new SAML20AssertionValidator(
					Arrays.asList(new AudienceRestrictionConditionValidator()),
					Arrays.asList(samlBuilder.subjectConfirmationValidator), Collections.emptySet(), null,
					newTrustEngine, newSignaturePrevalidator);

			final Map<String, Object> staticParams = new LinkedHashMap<>();
			staticParams.put(SAML2AssertionValidationParameters.COND_VALID_AUDIENCES,
					Set.of(samlBuilder.serviceProviderEntityId));
			staticParams.put(SAML2AssertionValidationParameters.SC_VALID_IN_RESPONSE_TO, sessionId);
			staticParams.put(SAML2AssertionValidationParameters.SC_VALID_RECIPIENTS, Set.of(postUri.toString()));
			staticParams.put(SAML2AssertionValidationParameters.SC_VALID_ADDRESSES, Set.of(address));
			staticParams.put(SAML2AssertionValidationParameters.SIGNATURE_REQUIRED, false);
			ValidationContext ctx = new ValidationContext(staticParams);

			LOG.fine("SAML2 authentication response\nEntity ID: " + samlBuilder.serviceProviderEntityId + "\nACS URL: "
					+ postUri.toString() + "\nAllow IP Range: " + samlBuilder.allowedRange + "\n"
					+ XmlDomUtil.getContent(response.getDOM()) + "\nStatic Params: " + staticParams);

			final var decrypter = getDecrypter(identity);
			// https://stackoverflow.com/questions/22672416/org-opensaml-xml-validation-validationexception-apache-xmlsec-idresolver-could
			// https://stackoverflow.com/questions/24364686/saml-2-0-decrypting-encryptedassertion-removes-a-namespace-declaration
			decrypter.setRootInNewDocument(true);

			// validate assertions
			return IuException.unchecked(() -> validateResponse(config, response, decrypter, validator, ctx));

		} finally {
			current.setContextClassLoader(currentLoader);
		}
	}

	/**
	 * Gets the token signature verification algorithm.
	 * 
	 * @return {@link Algorithm}
	 */
	Algorithm getVerifyAlg() {
		return samlBuilder.verifyAlg;
	}

	/**
	 * Gets the token signature verification key.
	 * 
	 * @return {@link WebKey}
	 */
	WebKey getVerifyKey() {
		final IuSamlServiceProviderMetadata config = IuAuthenticationRealm.of(realm);
		final IuPrincipalIdentity identity = serviceProviderIdentity(config);
		return identity.getSubject().getPrivateCredentials(WebKey.class).stream()
				.filter(a -> "verify".equals(a.getKeyId())).findFirst().get();
	}

	private SamlPrincipal validateResponse(IuSamlServiceProviderMetadata config, Response response, Decrypter decrypter,
			SAML20AssertionValidator validator, ValidationContext ctx) throws AssertionValidationException {
		// Gets the date/time the response was issued.
		Instant issueInstant = response.getIssueInstant();

		final Queue<SamlAssertion> samlAssertions = new ArrayDeque<>();
		for (Assertion assertion : response.getAssertions())
			samlAssertions.offer(new SamlAssertion(validator, ctx, assertion));
		for (EncryptedAssertion encryptedAssertion : response.getEncryptedAssertions())
			samlAssertions.offer(new SamlAssertion(validator, ctx,
					IuException.unchecked(() -> decrypter.decrypt(encryptedAssertion))));

		final var principalNameAttribute = config.getPrincipalNameAttribute();
		String principalName = null;
		Instant authnInstant = null;
		for (final var samlAssertion : samlAssertions) {
			final var assertionAuthnInstant = samlAssertion.getAuthnInstant();
			final var attributes = samlAssertion.getAttributes();
			final var assertionPrincipalName = Objects.requireNonNull( //
					attributes.get(principalNameAttribute), //
					principalNameAttribute);

			principalName = IuObject.once(principalName, assertionPrincipalName);
			authnInstant = IuObject.once(authnInstant, assertionAuthnInstant);

		}

		Objects.requireNonNull(principalName,
				"SAML Response must have at least one assertion with an AuthnStatement and principal name attribute "
						+ principalNameAttribute);

		final var confirmation = (SubjectConfirmation) ctx.getDynamicParameters()
				.get(SAML2AssertionValidationParameters.CONFIRMED_SUBJECT_CONFIRMATION);
		if (confirmation == null)
			throw new IllegalArgumentException("Missing subject confirmation: " + ctx.getValidationFailureMessages()
					+ "\n" + ctx.getDynamicParameters());

		LOG.fine(() -> "SAML2 subject confirmation " + XmlDomUtil.getContent(confirmation.getDOM()));

		return new SamlPrincipal(realm, response.getIssuer().getValue(), principalName, issueInstant, authnInstant,
				authnInstant.plus(config.getAuthenticatedSessionTimeout()), samlAssertions);
	}

	/**
	 * Looks for an authoritative trusted issuer identity matching the SAML Service
	 * Provider configuration and returns its principal identity.
	 * 
	 * <p>
	 * This identity will have {@link Subject#getPrivateCredentials(Class) private}
	 * {@link WebKey} {@link Subject#getPrivateCredentials(Class) credentials} with
	 * kid values {@code verify} and {@code encrypt} suitable for use with
	 * SP-related crypto operations.
	 * </p>
	 * 
	 * @param config {@link IuSamlServiceProviderMetadata}
	 * @return {@link IuPrincipalIdentity}
	 */
	IuPrincipalIdentity serviceProviderIdentity(IuSamlServiceProviderMetadata config) {
		final var identity = IuIterable.stream(AuthConfig.get(IuTrustedIssuer.class)) //
				.map(a -> a.getPrincipal(config.getIdentity())) //
				.filter(Objects::nonNull).findFirst() //
				.orElseThrow(() -> new IllegalStateException("service provider is not trusted"));

		if (!IuException.unchecked(() -> IuPrincipalIdentity.verify(identity, identity.getName())))
			throw new IllegalStateException("service provider is not authoritative");
		else
			return identity;
	}

}
