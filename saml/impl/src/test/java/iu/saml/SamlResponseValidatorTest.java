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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensaml.saml.common.assertion.ValidationContext;
import org.opensaml.saml.saml2.assertion.SAML20AssertionValidator;
import org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters;
import org.opensaml.saml.saml2.assertion.impl.AudienceRestrictionConditionValidator;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.EncryptedAssertion;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.SignatureValidationParameters;
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureValidationParametersCriterion;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuProcess;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.X500Utils;
import edu.iu.test.IuTestLogger;
import iu.saml.config.IuSamlServiceProviderMetadata;
import net.shibboleth.shared.resolver.CriteriaSet;
import net.shibboleth.shared.resolver.Criterion;

@SuppressWarnings("javadoc")
@ExtendWith(IuHttpAware.class)
public class SamlResponseValidatorTest {

	private String spEntityId;
	private WebKey identity;
	private URI postUri;
	private String entityId;
	private String sessionId;
	private InetAddress remoteAddr;
	private IuSamlServiceProviderMetadata config;
	private String nameAttribute;

	@BeforeEach
	void setup() {
		IuTestLogger.allow("edu.iu.crypt", Level.CONFIG);
		IuTestLogger.allow("org.apache.xml", Level.FINE);
		IuTestLogger.allow("org.opensaml", Level.FINE);
		assertDoesNotThrow(() -> Class.forName(SamlServiceProvider.class.getName()));

		spEntityId = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.EDDSA).keyId(IdGenerator.generateId()).use(Use.SIGN).ephemeral()
				.build();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);
		IuProcess.deleteTempFiles();
		identity = WebKey.builder(Algorithm.EDDSA).keyId(IdGenerator.generateId()).use(Use.SIGN).key(privateKey)
				.key(jwk.getPublicKey()).pem(pemCert).build();

		postUri = URI.create(IdGenerator.generateId());
		entityId = IdGenerator.generateId();
		sessionId = IdGenerator.generateId();
		remoteAddr = mock(InetAddress.class);

		nameAttribute = IdGenerator.generateId();

		config = mock(IuSamlServiceProviderMetadata.class);
		when(config.getServiceProviderEntityId()).thenReturn(spEntityId);
		when(config.getIdentityProviderEntityIds()).thenReturn(IuIterable.iter(entityId));
		when(config.getIdentity()).thenReturn(identity);
		when(config.getPrincipalNameAttribute()).thenReturn(nameAttribute);
		when(config.getAuthenticatedSessionTimeout()).thenReturn(Duration.ofHours(12L));
	}

	@Test
	void testValidateInvalidSignature() {
		final var trustEngine = mock(ExplicitKeySignatureTrustEngine.class);

		IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE, "SamlResponseValidator spEntityId: "
				+ spEntityId + "; postUri: " + postUri + "; allowedRange: \\[\\]; staticParams: .*");

		try (final var mockValidator = mockConstruction(SAMLSignatureProfileValidator.class);
				final var mockValidationParam = mockConstruction(SignatureValidationParameters.class);
				final var mockValidationParamCriterion = mockConstruction(SignatureValidationParametersCriterion.class,
						(a, ctx) -> {
							assertEquals(ctx.arguments().get(0), mockValidationParam.constructed().get(0));
						});
				final var mockCriteriaSet = mockConstruction(CriteriaSet.class, (a, ctx) -> {
					assertArrayEquals((Criterion[]) ctx.arguments().get(0),
							new Criterion[] { mockValidationParamCriterion.constructed().get(0) });
				});) {
			final var validator = new SamlResponseValidator(postUri, entityId, sessionId, remoteAddr, config,
					trustEngine);
			final var response = mock(Response.class);
			final var responseXml = "<Response>" + IdGenerator.generateId() + "</Response>";
			final var responseDom = XmlDomUtil.parse(responseXml);
			when(response.getDOM()).thenReturn(responseDom.getDocumentElement());

			final var signature = mock(Signature.class);
			when(response.getSignature()).thenReturn(signature);

			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE, "saml:pre-validate\n" + responseXml);

			assertEquals("SAML signature verification failed",
					assertThrows(IllegalArgumentException.class, () -> validator.validate(response)).getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testValidate() {
		final var trustEngine = mock(ExplicitKeySignatureTrustEngine.class);
		final var name = IdGenerator.generateId();
		final var authnInstant = Instant.now().minusSeconds(5L);
		final var expires = authnInstant.plus(Duration.ofHours(12L));
		final var authnAuthority = IdGenerator.generateId();

		final var authnAssertion = mock(Assertion.class);
		final var authnAssertionXml = "<Assertion>" + IdGenerator.generateId() + "</Assertion>";
		final var authnAssertionDom = XmlDomUtil.parse(authnAssertionXml);
		when(authnAssertion.getDOM()).thenReturn(authnAssertionDom.getDocumentElement());

		final var assertion = mock(Assertion.class);
		final var assertionXml = "<Assertion>" + IdGenerator.generateId() + "</Assertion>";
		final var assertionDom = XmlDomUtil.parse(assertionXml);
		when(assertion.getDOM()).thenReturn(assertionDom.getDocumentElement());

		final var encryptedAssertion = mock(EncryptedAssertion.class);

		final var subjectConfirmation = mock(SubjectConfirmation.class);
		final var subjectConfirmationXml = "<SubjectConfirmation>" + IdGenerator.generateId()
				+ "</SubjectConfirmation>";
		final var subjectConfirmationDom = XmlDomUtil.parse(subjectConfirmationXml);
		when(subjectConfirmation.getDOM()).thenReturn(subjectConfirmationDom.getDocumentElement());

		IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE, "SamlResponseValidator spEntityId: "
				+ spEntityId + "; postUri: " + postUri + "; allowedRange: \\[\\]; staticParams: .*");

		try (//
				final var mockValidationContext = mockConstruction(ValidationContext.class, (a, ctx) -> {
					final Map<String, Object> staticParams = (Map<String, Object>) ctx.arguments().get(0);
					assertEquals(staticParams.get(SAML2AssertionValidationParameters.COND_VALID_AUDIENCES),
							Set.of(spEntityId));
					assertEquals(staticParams.get(SAML2AssertionValidationParameters.SC_VALID_IN_RESPONSE_TO),
							sessionId);
					assertEquals(staticParams.get(SAML2AssertionValidationParameters.SC_VALID_RECIPIENTS),
							Set.of(postUri.toString()));
					assertEquals(staticParams.get(SAML2AssertionValidationParameters.SC_VALID_ADDRESSES),
							Set.of(remoteAddr));
					assertEquals(staticParams.get(SAML2AssertionValidationParameters.VALID_ISSUERS), Set.of(entityId));
					assertEquals(staticParams.get(SAML2AssertionValidationParameters.SIGNATURE_REQUIRED), false);

					when(a.getDynamicParameters()).thenReturn(Map.of(
							SAML2AssertionValidationParameters.CONFIRMED_SUBJECT_CONFIRMATION, subjectConfirmation));
				});
				final var mockValidator = mockConstruction(SAMLSignatureProfileValidator.class);
				final var mockValidationParam = mockConstruction(SignatureValidationParameters.class);
				final var mockValidationParamCriterion = mockConstruction(SignatureValidationParametersCriterion.class,
						(a, ctx) -> {
							assertEquals(ctx.arguments().get(0), mockValidationParam.constructed().get(0));
						});
				final var mockCriteriaSet = mockConstruction(CriteriaSet.class, (a, ctx) -> {
					assertArrayEquals((Criterion[]) ctx.arguments().get(0),
							new Criterion[] { mockValidationParamCriterion.constructed().get(0) });
				});
				final var mockSubjectConfirmation = mockConstruction(IuSubjectConfirmationValidator.class, (a, ctx) -> {
					assertEquals(config.getAllowedRange(), ctx.arguments().get(0));
					assertEquals(config.isFailOnAddressMismatch(), ctx.arguments().get(1));
				});
				final var mockAudienceRestrictionConditionValidator = mockConstruction(
						AudienceRestrictionConditionValidator.class);
				final var mockAssertionValidator = mockConstruction(SAML20AssertionValidator.class, (a, ctx) -> {
					assertEquals(Set.of(mockAudienceRestrictionConditionValidator.constructed().get(0)),
							ctx.arguments().get(0));
					assertEquals(Set.of(mockSubjectConfirmation.constructed().get(0)), ctx.arguments().get(1));
					assertEquals(Collections.emptySet(), ctx.arguments().get(2));
					assertNull(ctx.arguments().get(3));
					assertEquals(trustEngine, ctx.arguments().get(4));
					assertEquals(mockValidator.constructed().get(0), ctx.arguments().get(5));
				});
				final var mockSamlAssertion = mockConstruction(SamlAssertion.class, (a, ctx) -> {
					if (authnAssertion.equals(ctx.arguments().get(0))) {
						when(a.getAuthnInstant()).thenReturn(authnInstant);
						when(a.getAuthnAuthority()).thenReturn(authnAuthority);
						when(a.getAttributes()).thenReturn(Map.of(nameAttribute, name));
					} else
						assertEquals(assertion, ctx.arguments().get(0));
				});
				final var mockKeyInfoResolver = mockConstruction(StaticKeyInfoCredentialResolver.class, (a, ctx) -> {
					final List<Credential> certs = (List<Credential>) ctx.arguments().get(0);
					final var x509 = (BasicX509Credential) certs.get(0);
					assertEquals(identity.getPrivateKey(), x509.getPrivateKey());
					assertEquals(identity.getCertificateChain()[0], x509.getEntityCertificate());
				});
				final var mockDecrypter = mockConstruction(Decrypter.class, (a, ctx) -> {
					assertNull(ctx.arguments().get(0));
					assertEquals(mockKeyInfoResolver.constructed().get(0), ctx.arguments().get(1));
					assertInstanceOf(InlineEncryptedKeyResolver.class, ctx.arguments().get(2));
					when(a.decrypt(encryptedAssertion)).thenReturn(authnAssertion);
				})) {
			final var validator = new SamlResponseValidator(postUri, entityId, sessionId, remoteAddr, config,
					trustEngine);
			final var response = mock(Response.class);
			final var responseXml = "<Response>" + IdGenerator.generateId() + "</Response>";
			final var responseDom = XmlDomUtil.parse(responseXml);
			when(response.getDOM()).thenReturn(responseDom.getDocumentElement());

			final var signature = mock(Signature.class);
			when(response.getSignature()).thenReturn(signature);
			when(response.getAssertions()).thenReturn(List.of(assertion));
			when(response.getEncryptedAssertions()).thenReturn(List.of(encryptedAssertion));

			final var issuer = mock(Issuer.class);
			when(issuer.getValue()).thenReturn(entityId);
			final var issueInstant = Instant.now();
			when(response.getIssuer()).thenReturn(issuer);
			when(response.getIssueInstant()).thenReturn(issueInstant);

			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE, "saml:pre-validate\n" + responseXml);
			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE,
					"saml:valid-assertion\n" + authnAssertionXml);
			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE,
					"saml:valid-assertion\n" + assertionXml);
			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE,
					"saml:subject-confirmation\n" + subjectConfirmationXml);
			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE,
					"saml:post-validate:" + name + ":" + authnAuthority + " @" + authnInstant + "; issuer: " + entityId
							+ " @" + issueInstant + ", expires " + expires + "; assertions: \\[.*\\]");

			assertDoesNotThrow(() -> when(trustEngine.validate(eq(signature), any())).thenReturn(true));
			final var principal = assertDoesNotThrow(() -> validator.validate(response));
			assertDoesNotThrow(() -> verify(mockValidator.constructed().get(0)).validate(signature));
			assertDoesNotThrow(() -> verify(trustEngine).validate(signature, mockCriteriaSet.constructed().get(0)));
			assertDoesNotThrow(() -> verify(mockAssertionValidator.constructed().get(0)).validate(authnAssertion,
					mockValidationContext.constructed().get(0)));

			assertEquals(name, principal.getName());
			assertEquals(authnAuthority, principal.getAuthnAuthority());
			assertEquals(authnInstant, principal.getAuthnInstant());
			assertEquals(expires, principal.getExpires());
			final var assertions = principal.getAssertions().iterator();
			assertEquals(mockSamlAssertion.constructed().get(0), assertions.next());
			assertEquals(mockSamlAssertion.constructed().get(1), assertions.next());
		}
	}
}
