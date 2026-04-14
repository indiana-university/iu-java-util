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
package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.saml.common.assertion.ValidationContext;
import org.opensaml.saml.saml2.assertion.SAML20AssertionValidator;
import org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.EncryptedAssertion;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.SignatureValidationParameters;
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureValidationParametersCriterion;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.w3c.dom.Element;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuProcess;
import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.X500Utils;
import edu.iu.test.IuTestLogger;
import iu.auth.config.AuthConfig;
import net.shibboleth.shared.resolver.CriteriaSet;
import net.shibboleth.shared.resolver.Criterion;

@SuppressWarnings("javadoc")
public class SamlResponseValidatorTest extends SamlTestCase {

	private String realm;
	private URI postUri;
	private String entityId;
	private String spEntityId;
	private Set<String> idpEntityIds;
	private String sessionId;
	private InetAddress remoteAddr;
	private SamlBuilder samlBuilder;
	private String allowedIp;
	private ExplicitKeySignatureTrustEngine trustEngine;
	private IuSubjectConfirmationValidator subjectConfigurationValidator;
	private WebKey identity;
	private IuSamlServiceProviderMetadata config;
	private SAMLSignatureProfileValidator signatureProfileValidator;

	private MockedStatic<AuthConfig> mockAuthConfig;
	private MockedStatic<SamlBuilder> mockSamlBuilder;

	@SuppressWarnings("unchecked")
	@BeforeEach
	public void setup() {
		realm = IdGenerator.generateId();
		postUri = URI.create(IdGenerator.generateId());
		entityId = IdGenerator.generateId();
		spEntityId = IdGenerator.generateId();
		idpEntityIds = Set.of(IdGenerator.generateId());
		sessionId = IdGenerator.generateId();
		remoteAddr = mock(InetAddress.class);
		samlBuilder = mock(SamlBuilder.class);
		allowedIp = IdGenerator.generateId();
		when(samlBuilder.getServiceProviderEntityId()).thenReturn(spEntityId);
		when(samlBuilder.getIdentityProviderEntityIds()).thenReturn(idpEntityIds);
		when(samlBuilder.getAllowedRange()).thenReturn(IuIterable.iter(allowedIp));

		trustEngine = mock(ExplicitKeySignatureTrustEngine.class);
		when(samlBuilder.createTrustEngine(entityId)).thenReturn(trustEngine);

		subjectConfigurationValidator = mock(IuSubjectConfirmationValidator.class);
		when(samlBuilder.getSubjectConfirmationValidator()).thenReturn(subjectConfigurationValidator);

		mockAuthConfig = mockStatic(AuthConfig.class);
		mockAuthConfig.when(() -> AuthConfig.adaptJson(any(Class.class))).thenCallRealMethod();
		mockAuthConfig.when(() -> AuthConfig.adaptJson(any(Type.class))).thenCallRealMethod();
		mockSamlBuilder = mockStatic(SamlBuilder.class);

		identity = mock(WebKey.class);
		config = mock(IuSamlServiceProviderMetadata.class);
		when(config.getIdentity()).thenReturn(identity);
		mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);

		signatureProfileValidator = mock(SAMLSignatureProfileValidator.class);
		mockSamlBuilder.when(() -> SamlBuilder.createSignatureProfileValidator()).thenReturn(signatureProfileValidator);
	}

	@AfterEach
	public void teardown() {
		mockAuthConfig.close();
		mockSamlBuilder.close();
	}

	@Test
	public void testConstructor() {
		try (final var mockSamlResponseValidator = mockStatic(SamlResponseValidator.class)) {

			final var validationContext = mock(ValidationContext.class);
			final var paramName = IdGenerator.generateId();
			final var paramValue = IdGenerator.generateId();
			when(validationContext.getStaticParameters()).thenReturn(Map.of(paramName, paramValue));

			mockSamlResponseValidator.when(() -> SamlResponseValidator.createValidationContext(spEntityId, sessionId,
					postUri, remoteAddr, idpEntityIds)).thenReturn(validationContext);

			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE,
					"SamlResponseValidator spEntityId: " + spEntityId + "; postUri: " + postUri + "; allowedRange: ["
							+ allowedIp + "]; staticParams: {" + paramName + "=" + paramValue + "}");

			final var validator = assertDoesNotThrow(
					() -> new SamlResponseValidator(realm, postUri, entityId, sessionId, remoteAddr, samlBuilder));
			assertSame(config, validator.config());
			assertSame(realm, validator.realm());

			mockSamlResponseValidator.verify(() -> SamlResponseValidator
					.createAssertionValidator(signatureProfileValidator, trustEngine, subjectConfigurationValidator));
			mockSamlResponseValidator.verify(() -> SamlResponseValidator.createValidationContext(spEntityId, sessionId,
					postUri, remoteAddr, idpEntityIds));
			mockSamlResponseValidator.verify(() -> SamlResponseValidator.getDecrypter(identity));
		}
	}

	@Test
	public void testGetDecrypter() {
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(WebKey.Type.RSA).keyId(kid).ephemeral(2048).build();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + kid.replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);
		
		final var identity = WebKey.builder(jwk.getType()) //
				.keyId(jwk.getKeyId()) //
				.key(jwk.getPrivateKey()) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();

		IuProcess.deleteTempFiles();

		try (final var mockResolver = mockConstruction(StaticKeyInfoCredentialResolver.class, (a, ctx) -> {
			final List<?> certs = (List<?>) ctx.arguments().get(0);
			assertEquals(1, certs.size());
			final var basicX509 = assertInstanceOf(BasicX509Credential.class, certs.get(0));
			assertEquals(identity.getPrivateKey(), basicX509.getPrivateKey());
			assertEquals(identity.getCertificateChain()[0], basicX509.getEntityCertificate());
		}); final var mockDecrypter = mockConstruction(Decrypter.class, (a, ctx) -> {
			assertNull(ctx.arguments().get(0));
			assertSame(mockResolver.constructed().get(0), ctx.arguments().get(1));
			assertInstanceOf(InlineEncryptedKeyResolver.class, ctx.arguments().get(2));
		})) {
			final var decrypter = assertDoesNotThrow(() -> SamlResponseValidator.getDecrypter(identity));
			assertNotNull(decrypter);
			assertSame(decrypter, mockDecrypter.constructed().get(0));
			verify(decrypter).setRootInNewDocument(true);
		}
	}

	@Test
	public void testValidateSignature() {
		try (final var mockSamlResponseValidator = mockStatic(SamlResponseValidator.class)) {
			IuTestLogger.allow(SamlResponseValidator.class.getName(), Level.FINE);
			final var validationContext = mock(ValidationContext.class);
			mockSamlResponseValidator.when(() -> SamlResponseValidator.createValidationContext(spEntityId, sessionId,
					postUri, remoteAddr, idpEntityIds)).thenReturn(validationContext);

			final var samlResponseValidator = new SamlResponseValidator(realm, postUri, entityId, sessionId, remoteAddr,
					samlBuilder);
			final var response = mock(Response.class);
			final var signature = mock(Signature.class);
			when(response.getSignature()).thenReturn(signature);

			try (final var mockSignatureValidationParameters = mockConstruction(SignatureValidationParameters.class); //
					final var mockSignatureValidationParametersCriterion = mockConstruction(
							SignatureValidationParametersCriterion.class, (a, ctx) -> {
								final var vparam = mockSignatureValidationParameters.constructed().get(0);
								assertSame(vparam, ctx.arguments().get(0));
							}); //
					final var mockCriteriaSet = mockConstruction(CriteriaSet.class, (a, ctx) -> {
						assertSame(mockSignatureValidationParametersCriterion.constructed().get(0),
								((Criterion[]) ctx.arguments().get(0))[0]);
					})) {
				assertDoesNotThrow(() -> when(
						trustEngine.validate(eq(signature), argThat(mockCriteriaSet.constructed()::contains)))
						.thenReturn(true));

				assertDoesNotThrow(() -> samlResponseValidator.validateSignature(response));
			}
		}
	}

	@Test
	public void testInvalidSignature() {
		try (final var mockSamlResponseValidator = mockStatic(SamlResponseValidator.class)) {
			IuTestLogger.allow(SamlResponseValidator.class.getName(), Level.FINE);
			final var validationContext = mock(ValidationContext.class);
			mockSamlResponseValidator.when(() -> SamlResponseValidator.createValidationContext(spEntityId, sessionId,
					postUri, remoteAddr, idpEntityIds)).thenReturn(validationContext);

			final var samlResponseValidator = new SamlResponseValidator(realm, postUri, entityId, sessionId, remoteAddr,
					samlBuilder);
			final var response = mock(Response.class);
			final var signature = mock(Signature.class);
			when(response.getSignature()).thenReturn(signature);

			final var error = assertThrows(IllegalArgumentException.class,
					() -> samlResponseValidator.validateSignature(response));
			assertEquals("SAML signature verification failed", error.getMessage());
		}
	}

	@Test
	public void testValidAssertions() {
		try (final var mockSamlResponseValidator = mockStatic(SamlResponseValidator.class);
				final var mockXmlDomUtil = mockStatic(XmlDomUtil.class)) {
			IuTestLogger.allow(SamlResponseValidator.class.getName(), Level.FINE);
			final var validationContext = mock(ValidationContext.class);
			mockSamlResponseValidator.when(() -> SamlResponseValidator.createValidationContext(spEntityId, sessionId,
					postUri, remoteAddr, idpEntityIds)).thenReturn(validationContext);

			final var assertionValidator = mock(SAML20AssertionValidator.class);
			mockSamlResponseValidator.when(() -> SamlResponseValidator.createAssertionValidator(any(), any(), any()))
					.thenReturn(assertionValidator);

			final var response = mock(Response.class);
			final var assertion1 = mock(Assertion.class);
			final var element1 = mock(Element.class);
			final var content1 = IdGenerator.generateId();
			when(assertion1.getDOM()).thenReturn(element1);
			mockXmlDomUtil.when(() -> XmlDomUtil.getContent(element1)).thenReturn(content1);
			when(response.getAssertions()).thenReturn(List.of(assertion1));

			final var assertion2 = mock(Assertion.class);

			final var attribute = mock(Attribute.class);
			final var name = IdGenerator.generateId();
			when(config.getPrincipalNameAttribute()).thenReturn(name);

			final var friendlyName = IdGenerator.generateId();
			when(attribute.getName()).thenReturn(name);
			when(attribute.getFriendlyName()).thenReturn(friendlyName);

			final var value = IdGenerator.generateId();
			final var xsstring = mock(XSString.class);
			when(xsstring.getValue()).thenReturn(value);
			when(attribute.getAttributeValues()).thenReturn(List.of(xsstring));
			final var attributeStatement = mock(AttributeStatement.class);
			when(attributeStatement.getAttributes()).thenReturn(List.of(attribute));
			when(assertion2.getAttributeStatements()).thenReturn(List.of(attributeStatement));

			final var authnInstant = Instant.now();
			final var authnStatement = mock(AuthnStatement.class);
			when(authnStatement.getAuthnInstant()).thenReturn(authnInstant);
			when(assertion2.getAuthnStatements()).thenReturn(List.of(authnStatement));

			final var element2 = mock(Element.class);
			final var content2 = IdGenerator.generateId();
			when(assertion2.getDOM()).thenReturn(element2);
			mockXmlDomUtil.when(() -> XmlDomUtil.getContent(element2)).thenReturn(content2);

			final var encryptedAssertion = mock(EncryptedAssertion.class);
			final var decrypter = mock(Decrypter.class);
			mockSamlResponseValidator.when(() -> SamlResponseValidator.getDecrypter(identity)).thenReturn(decrypter);
			assertDoesNotThrow(() -> when(decrypter.decrypt(encryptedAssertion)).thenReturn(assertion2));
			when(response.getEncryptedAssertions()).thenReturn(List.of(encryptedAssertion));

			final var samlResponseValidator = new SamlResponseValidator(realm, postUri, entityId, sessionId, remoteAddr,
					samlBuilder);
			IuTestLogger.expect(SamlAssertion.class.getName(), Level.FINE, "SAML2 assertion " + content1);
			IuTestLogger.expect(SamlAssertion.class.getName(), Level.FINE, "SAML2 assertion " + content2);
			assertDoesNotThrow(() -> samlResponseValidator.validateAssertions(response));
		}
	}

	@Test
	public void testInvalidAssertions() {
		try (final var mockSamlResponseValidator = mockStatic(SamlResponseValidator.class)) {
			IuTestLogger.allow(SamlResponseValidator.class.getName(), Level.FINE);
			final var validationContext = mock(ValidationContext.class);
			mockSamlResponseValidator.when(() -> SamlResponseValidator.createValidationContext(spEntityId, sessionId,
					postUri, remoteAddr, idpEntityIds)).thenReturn(validationContext);

			final var samlResponseValidator = new SamlResponseValidator(realm, postUri, entityId, sessionId, remoteAddr,
					samlBuilder);
			final var response = mock(Response.class);

			final var error = assertThrows(NullPointerException.class,
					() -> samlResponseValidator.validateAssertions(response));
			assertEquals(
					"SAML Response must have at least one assertion with an AuthnStatement and principal name attribute null",
					error.getMessage());
		}
	}

	@Test
	public void testMissingSubjectConfirmation() {
		try (final var mockSamlResponseValidator = mockStatic(SamlResponseValidator.class)) {
			IuTestLogger.allow(SamlResponseValidator.class.getName(), Level.FINE);
			final var validationContext = mock(ValidationContext.class);
			mockSamlResponseValidator.when(() -> SamlResponseValidator.createValidationContext(spEntityId, sessionId,
					postUri, remoteAddr, idpEntityIds)).thenReturn(validationContext);

			final var samlResponseValidator = new SamlResponseValidator(realm, postUri, entityId, sessionId, remoteAddr,
					samlBuilder);
			final var error = assertThrows(NullPointerException.class,
					samlResponseValidator::verifySubjectConfirmation);
			assertEquals("Missing subject confirmation: []\n{}", error.getMessage());
		}
	}

	@Test
	public void testVerifiedSubjectConfirmation() {
		try (final var mockXmlDomUtil = mockStatic(XmlDomUtil.class);
				final var mockSamlResponseValidator = mockStatic(SamlResponseValidator.class)) {
			final var validationContext = mock(ValidationContext.class);
			mockSamlResponseValidator.when(() -> SamlResponseValidator.createValidationContext(spEntityId, sessionId,
					postUri, remoteAddr, idpEntityIds)).thenReturn(validationContext);

			final var subjectConfirmation = mock(SubjectConfirmation.class);
			final var element = mock(Element.class);
			final var content = IdGenerator.generateId();
			mockXmlDomUtil.when(() -> XmlDomUtil.getContent(element)).thenReturn(content);
			when(subjectConfirmation.getDOM()).thenReturn(element);
			when(validationContext.getDynamicParameters()).thenReturn(
					Map.of(SAML2AssertionValidationParameters.CONFIRMED_SUBJECT_CONFIRMATION, subjectConfirmation));

			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE, "SamlResponseValidator spEntityId: "
					+ spEntityId + "; postUri: " + postUri + "; allowedRange: [" + allowedIp + "]; staticParams: {}");
			final var samlResponseValidator = new SamlResponseValidator(realm, postUri, entityId, sessionId, remoteAddr,
					samlBuilder);

			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE,
					"SAML2 subject confirmation " + content);
			assertDoesNotThrow(samlResponseValidator::verifySubjectConfirmation);
		}
	}

	@Test
	public void testResponseMissingAuthnAssertion() {
		IuTestLogger.allow(SamlResponseValidator.class.getName(), Level.FINE);
		try (final var mockXmlDomUtil = mockStatic(XmlDomUtil.class);
				final var mockSamlResponseValidator = mockStatic(SamlResponseValidator.class);
				final var mockSamlResponseValidatorCon = mockConstruction(SamlResponseValidator.class)) {
			final var validationContext = mock(ValidationContext.class);
			mockSamlResponseValidator.when(() -> SamlResponseValidator.createValidationContext(spEntityId, sessionId,
					postUri, remoteAddr, idpEntityIds)).thenReturn(validationContext);

			final var samlResponseValidator = new SamlResponseValidator(realm, postUri, entityId, sessionId, remoteAddr,
					samlBuilder);
			assertDoesNotThrow(() -> when(samlResponseValidator.validate(any())).thenCallRealMethod());

			final var response = mock(Response.class);
			final var assertion = mock(SamlAssertion.class);
			assertDoesNotThrow(
					() -> when(samlResponseValidator.validateAssertions(response)).thenReturn(List.of(assertion)));

			assertThrows(NoSuchElementException.class, () -> samlResponseValidator.validate(response));
		}
	}

	@Test
	public void testValidateResponse() {
		try (final var mockXmlDomUtil = mockStatic(XmlDomUtil.class);
				final var mockSamlResponseValidator = mockStatic(SamlResponseValidator.class);
				final var mockSamlResponseValidatorCon = mockConstruction(SamlResponseValidator.class)) {
			final var validationContext = mock(ValidationContext.class);
			mockSamlResponseValidator.when(() -> SamlResponseValidator.createValidationContext(spEntityId, sessionId,
					postUri, remoteAddr, idpEntityIds)).thenReturn(validationContext);

			final var samlResponseValidator = new SamlResponseValidator(realm, postUri, entityId, sessionId, remoteAddr,
					samlBuilder);
			assertDoesNotThrow(() -> when(samlResponseValidator.validate(any())).thenCallRealMethod());
			when(samlResponseValidator.config()).thenReturn(config);
			when(samlResponseValidator.realm()).thenReturn(realm);

			final var issuedInstant = Instant.now();
			final var authnAuthority = IdGenerator.generateId();
			final var authnInstant = issuedInstant.minusSeconds(5L);

			final var response = mock(Response.class);
			when(response.getIssueInstant()).thenReturn(issuedInstant);

			final var iss = IdGenerator.generateId();
			final var issuer = mock(Issuer.class);
			when(issuer.getValue()).thenReturn(iss);
			when(response.getIssuer()).thenReturn(issuer);

			final var element = mock(Element.class);
			final var content = IdGenerator.generateId();
			mockXmlDomUtil.when(() -> XmlDomUtil.getContent(element)).thenReturn(content);
			when(response.getDOM()).thenReturn(element);

			final var principalNameAttribute = IdGenerator.generateId();
			when(config.getPrincipalNameAttribute()).thenReturn(principalNameAttribute);
			final var principalName = IdGenerator.generateId();
			final var assertion = mock(SamlAssertion.class);
			when(assertion.getAuthnAuthority()).thenReturn(authnAuthority);
			when(assertion.getAuthnInstant()).thenReturn(authnInstant);
			when(assertion.getAttributes()).thenReturn(Map.of(principalNameAttribute, principalName));
			assertDoesNotThrow(
					() -> when(samlResponseValidator.validateAssertions(response)).thenReturn(List.of(assertion)));

			when(config.getAuthenticatedSessionTimeout()).thenReturn(Duration.ofSeconds(15L));
			final var expires = authnInstant.plusSeconds(15L);

			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE, "saml:pre-validate\n" + content);
			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE,
					"saml:post-validate:" + principalName + ":" + authnAuthority + " @" + authnInstant + "; issuer: "
							+ iss + " @" + issuedInstant + ", expires " + expires + "; assertions: [" + assertion
							+ "]");

			assertDoesNotThrow(() -> samlResponseValidator.validate(response));

			assertDoesNotThrow(() -> verify(samlResponseValidator).validateSignature(response));
		}
	}

}
