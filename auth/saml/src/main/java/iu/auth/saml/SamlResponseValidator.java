/*
 * Copyright © 2025 Indiana University
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

import java.net.InetAddress;
import java.net.URI;
import java.security.KeyPair;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;

import org.opensaml.saml.common.assertion.ValidationContext;
import org.opensaml.saml.saml2.assertion.SAML20AssertionValidator;
import org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters;
import org.opensaml.saml.saml2.assertion.impl.AudienceRestrictionConditionValidator;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.EncryptedAssertion;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.SignatureValidationParameters;
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidationParametersCriterion;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import iu.auth.config.AuthConfig;
import net.shibboleth.shared.resolver.CriteriaSet;

/**
 * Encapsulates interactions with OpenSAML for handling IDP-issued responses.
 */
class SamlResponseValidator {

	private static final Logger LOG = Logger.getLogger(SamlResponseValidator.class.getName());

	private final String realm;
	private final IuSamlServiceProviderMetadata config;
	private final SAMLSignatureProfileValidator signatureProfileValidator;
	private final ExplicitKeySignatureTrustEngine trustEngine;
	private final SAML20AssertionValidator assertionValidator;
	private final ValidationContext validationContext;
	private final Decrypter decrypter;

	/**
	 * Constructor
	 * 
	 * @param realm       authentication realm
	 * @param postUri     HTTP POST binding URI
	 * @param entityId    IDP entity ID
	 * @param sessionId   expected session ID
	 * @param remoteAddr  remote address
	 * @param samlBuilder {@link SamlBuilder}
	 */
	SamlResponseValidator(String realm, URI postUri, String entityId, String sessionId, InetAddress remoteAddr,
			SamlBuilder samlBuilder) {
		this.realm = realm;
		final var spEntityId = samlBuilder.getServiceProviderEntityId();

		signatureProfileValidator = SamlBuilder.createSignatureProfileValidator();
		trustEngine = samlBuilder.createTrustEngine(entityId);

		assertionValidator = createAssertionValidator(signatureProfileValidator, trustEngine,
				samlBuilder.getSubjectConfirmationValidator());
		validationContext = createValidationContext(spEntityId, sessionId, postUri, remoteAddr,
				samlBuilder.getIdentityProviderEntityIds());

		config = AuthConfig.load(IuSamlServiceProviderMetadata.class, realm);
		final var identity = SamlServiceProvider.serviceProviderIdentity(config);
		decrypter = getDecrypter(identity);

		LOG.fine("SamlResponseValidator spEntityId: " + spEntityId + "; postUri: " + postUri.toString()
				+ "; allowedRange: " + samlBuilder.getAllowedRange() + "; staticParams: "
				+ validationContext.getStaticParameters());
	}

	/**
	 * Creates an {@link SAML20AssertionValidator}
	 * 
	 * @param signatureProfileValidator    {@link SAMLSignatureProfileValidator}
	 * @param trustEngine                  {@link ExplicitKeySignatureTrustEngine}
	 * @param subjectConfirmationValidator {@link IuSubjectConfirmationValidator}
	 * @return {@link SAML20AssertionValidator}
	 */
	static SAML20AssertionValidator createAssertionValidator(SAMLSignatureProfileValidator signatureProfileValidator,
			ExplicitKeySignatureTrustEngine trustEngine, IuSubjectConfirmationValidator subjectConfirmationValidator) {
		return new SAML20AssertionValidator( //
				Set.of(new AudienceRestrictionConditionValidator()), //
				Set.of(subjectConfirmationValidator), //
				Collections.emptySet(), null, trustEngine, signatureProfileValidator);
	}

	/**
	 * Creates {@link ValidationContext} from static parameters.
	 * 
	 * @param spEntityId   service provider entity ID
	 * @param sessionId    expected session ID
	 * @param postUri      HTTP POST binding URI
	 * @param remoteAddr   remote address
	 * @param validIssuers valid IDP entity IDs
	 * @return {@link ValidationContext}
	 */
	static ValidationContext createValidationContext(String spEntityId, String sessionId, URI postUri,
			InetAddress remoteAddr, Set<String> validIssuers) {
		final Map<String, Object> staticParams = new LinkedHashMap<>();
		staticParams.put(SAML2AssertionValidationParameters.COND_VALID_AUDIENCES, Set.of(spEntityId));
		staticParams.put(SAML2AssertionValidationParameters.SC_VALID_IN_RESPONSE_TO, sessionId);
		staticParams.put(SAML2AssertionValidationParameters.SC_VALID_RECIPIENTS, Set.of(postUri.toString()));
		staticParams.put(SAML2AssertionValidationParameters.SC_VALID_ADDRESSES, Set.of(remoteAddr));
		staticParams.put(SAML2AssertionValidationParameters.VALID_ISSUERS, validIssuers);
		staticParams.put(SAML2AssertionValidationParameters.SIGNATURE_REQUIRED, false);
		return new ValidationContext(staticParams);
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

		final var decrypter = new Decrypter(null, keyInfoResolver, new InlineEncryptedKeyResolver());
		// https://stackoverflow.com/questions/22672416/org-opensaml-xml-validation-validationexception-apache-xmlsec-idresolver-could
		// https://stackoverflow.com/questions/24364686/saml-2-0-decrypting-encryptedassertion-removes-a-namespace-declaration
		decrypter.setRootInNewDocument(true);
		return decrypter;
	}

	/**
	 * Validates a parsed SAML response (entry point).
	 * 
	 * @param response parsed SAML response
	 * @return Verified {@link SamlPrincipal} if the response and signature are
	 *         valid, subject confirmation verification checks pass, and the
	 *         response contains at least one potentially encrypted assertion that
	 *         includes an authentication statement, conditions, and principal name
	 *         attribute
	 * @throws Exception if any verification steps fail
	 */
	SamlPrincipal validate(Response response) throws Exception {
		final var issueInstant = response.getIssueInstant();

		LOG.fine(() -> "saml:pre-validate\n" + XmlDomUtil.getContent(response.getDOM()));

		validateSignature(response);
		final var samlAssertions = validateAssertions(response);
		final var authnAssertion = IuIterable.select(samlAssertions, a -> a.getAuthnInstant() != null);

		verifySubjectConfirmation();

		final var config = config();
		final var authnAuthority = authnAssertion.getAuthnAuthority();
		final var authnInstant = authnAssertion.getAuthnInstant();
		final var principalNameAttribute = config.getPrincipalNameAttribute();
		final var principalName = Objects.requireNonNull( //
				authnAssertion.getAttributes().get(principalNameAttribute), //
				principalNameAttribute);

		final var issuer = response.getIssuer().getValue();
		final var expires = authnInstant.plus(config.getAuthenticatedSessionTimeout());

		LOG.fine(() -> "saml:post-validate:" + principalName + ":" + authnAuthority + " @" + authnInstant + "; issuer: "
				+ issuer + " @" + issueInstant + ", expires " + expires + "; assertions: " + samlAssertions);

		return new SamlPrincipal(realm(), principalName, issuer, issueInstant, authnAuthority, authnInstant, expires,
				samlAssertions);
	}

	/**
	 * Gets {@link #config}
	 * 
	 * @return {@link #config}
	 */
	IuSamlServiceProviderMetadata config() {
		return config;
	}

	/**
	 * Gets {@link #realm}
	 * 
	 * @return {@link #realm}
	 */
	String realm() {
		return realm;
	}

	/**
	 * Validates the signature of a response
	 * 
	 * @param response {@link Response}
	 * @throws SecurityException  from trustEngine
	 * @throws SignatureException from signatureProfileValidator
	 */
	void validateSignature(Response response) throws SecurityException, SignatureException {
		SignatureValidationParameters vparam = new SignatureValidationParameters();
		vparam.setSignatureTrustEngine(trustEngine);
		signatureProfileValidator.validate(response.getSignature());
		if (!trustEngine.validate(response.getSignature(),
				new CriteriaSet(new SignatureValidationParametersCriterion(vparam))))
			throw new IllegalArgumentException("SAML signature verification failed");
	}

	/**
	 * Validates potentially encrypted SAML assertions from a response
	 * 
	 * @param response {@link Response}
	 * @return At least one {@link SamlAssertion}
	 * @throws Exception if an assertion fails to validate
	 */
	Iterable<StoredSamlAssertion> validateAssertions(Response response) throws Exception {
		final Queue<StoredSamlAssertion> samlAssertions = new ArrayDeque<>();

		for (Assertion assertion : response.getAssertions()) {
			assertionValidator.validate(assertion, validationContext);
			samlAssertions.offer(new SamlAssertion(assertion));
		}

		for (EncryptedAssertion encryptedAssertion : response.getEncryptedAssertions()) {
			final var assertion = decrypter.decrypt(encryptedAssertion);
			assertionValidator.validate(assertion, validationContext);
			samlAssertions.offer(new SamlAssertion(assertion));
		}

		final var principalNameAttribute = config.getPrincipalNameAttribute();
		String principalName = null;
		for (final var samlAssertion : samlAssertions) {
			final var assertionAuthnInstant = samlAssertion.getAuthnInstant();
			if (assertionAuthnInstant != null) {
				final var attributes = samlAssertion.getAttributes();
				final var assertionPrincipalName = Objects.requireNonNull( //
						attributes.get(principalNameAttribute), //
						principalNameAttribute);

				principalName = IuObject.once(principalName, assertionPrincipalName);
			}
		}

		Objects.requireNonNull(principalName,
				"SAML Response must have at least one assertion with an AuthnStatement and principal name attribute "
						+ principalNameAttribute);

		return samlAssertions;
	}

	/**
	 * Verifies that previous validation activities provided a confirmed subject
	 * confirmation.
	 */
	void verifySubjectConfirmation() {
		final var confirmation = (SubjectConfirmation) Objects.requireNonNull(
				validationContext.getDynamicParameters()
						.get(SAML2AssertionValidationParameters.CONFIRMED_SUBJECT_CONFIRMATION),
				"Missing subject confirmation: " + validationContext.getValidationFailureMessages() + "\n"
						+ validationContext.getDynamicParameters());

		LOG.fine(() -> "SAML2 subject confirmation " + XmlDomUtil.getContent(confirmation.getDOM()));
	}

}
