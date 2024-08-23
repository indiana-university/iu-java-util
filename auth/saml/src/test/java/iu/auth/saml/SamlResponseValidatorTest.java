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
import java.util.Set;
import java.util.logging.Level;

import javax.security.auth.Subject;

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
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.client.IuJson;
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
	private IuPrincipalIdentity identity;
	private IuSamlServiceProviderMetadata config;
	private SAMLSignatureProfileValidator signatureProfileValidator;

	private MockedStatic<AuthConfig> mockAuthConfig;
	private MockedStatic<SamlServiceProvider> mockSamlServiceProvider;
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
		mockSamlServiceProvider = mockStatic(SamlServiceProvider.class);
		mockSamlBuilder = mockStatic(SamlBuilder.class);

		identity = mock(IuPrincipalIdentity.class);
		config = mock(IuSamlServiceProviderMetadata.class);
		mockSamlServiceProvider.when(() -> SamlServiceProvider.serviceProviderIdentity(config)).thenReturn(identity);
		mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);

		signatureProfileValidator = mock(SAMLSignatureProfileValidator.class);
		mockSamlBuilder.when(() -> SamlBuilder.createSignatureProfileValidator()).thenReturn(signatureProfileValidator);
	}

	@AfterEach
	public void teardown() {
		mockAuthConfig.close();
		mockSamlServiceProvider.close();
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
		// For demonstration purposes only, not for production use
		// to recreate: pkp create saml_sp SamlResponseValidatorTest | pkp self
		final var pkp = AuthConfig.adaptJson(IuPrivateKeyPrincipal.class).fromJson(IuJson
				.parse("""
						{
						    "type": "pki",
						    "alg": "RS256",
						    "encrypt_alg": "RSA-OAEP",
						    "enc": "A256GCM",
						    "jwk": {
						        "kid": "SamlResponseValidatorTest",
						        "x5c": [
						            "MIID+DCCAuCgAwIBAgIUO1OT5DL8EAO9xpaQoTZPlf9AWZ8wDQYJKoZIhvcNAQELBQAwgYcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxIjAgBgNVBAMMGVNhbWxSZXNwb25zZVZhbGlkYXRvclRlc3QwHhcNMjQwODEzMTIyMDE2WhcNMjYxMTIxMTIyMDE2WjCBhzELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEiMCAGA1UEAwwZU2FtbFJlc3BvbnNlVmFsaWRhdG9yVGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANMKQKCq3p1zEU8agnT0fJixfjlmQrgNECRG+5wBfVZIfzUd7iW2gtM6oDAZNqrXLIq6jVB9zqWkSmiA0X73x7ivH9lQJiugNfVJjKGoNQwSeg7gcxmJ8N4YyZudsE3ayIx4W10iVECjkjiXnYeA4/UmVr9iB/8GUoASgS1rdDN2M9Mqsf7KK80fykEp16FSVV4f4byi/n6nqLo3GG9fFZlMYGjpBvSO67y5q+XbeklZWWHn7rx0uA+3aGgIkzhqEyM8p7KWr/+Q6ZV9KPoT4zybtSa7lDb7FjmfWc0oc33KwWWZ0pV/CQAsorItihF2/77JhitZD2Sd3ZI6U/V3I5MCAwEAAaNaMFgwHQYDVR0OBBYEFDypUMQd0PRGYlSFRLoJ823FMs67MB8GA1UdIwQYMBaAFDypUMQd0PRGYlSFRLoJ823FMs67MAkGA1UdEwQCMAAwCwYDVR0PBAQDAgWgMA0GCSqGSIb3DQEBCwUAA4IBAQAXXnTjuYMwCSmbWlAEM2tImpUZc7ZCeG1KiOzbUQYccHnFhfHVkAlvzp3EwpuemDXvKg54BdjJmAuLAs22k0Quk0B90MdP+a2Ww4cqdJ2alqZ+vN/UNij3pnQ0HXqrfXp2+uYlMtL8YxtqSxc95ADvXVwXWAO695gZx3aH20s0Ri8npv0MF5wC8LamNjkP9/uV6qZ0r63iBrC4QURtDK3rApzNjAdVt4cKSIOhtpuMs5Pt/CQ/99nJukPG0tVQj3tXNAlP31blYGWl9I7XkPjBbrIwE8xgcgs4P29uGlHXtrdsiKBPFBy7/fcFsTgGcwy/TapY3TKVXYflXV4BIpV6"
						        ],
						        "kty": "RSA",
						        "n": "0wpAoKrenXMRTxqCdPR8mLF-OWZCuA0QJEb7nAF9Vkh_NR3uJbaC0zqgMBk2qtcsirqNUH3OpaRKaIDRfvfHuK8f2VAmK6A19UmMoag1DBJ6DuBzGYnw3hjJm52wTdrIjHhbXSJUQKOSOJedh4Dj9SZWv2IH_wZSgBKBLWt0M3Yz0yqx_sorzR_KQSnXoVJVXh_hvKL-fqeoujcYb18VmUxgaOkG9I7rvLmr5dt6SVlZYefuvHS4D7doaAiTOGoTIzynspav_5DplX0o-hPjPJu1JruUNvsWOZ9ZzShzfcrBZZnSlX8JACyisi2KEXb_vsmGK1kPZJ3dkjpT9Xcjkw",
						        "e": "AQAB",
						        "d": "LO6-YtbujeRdd5Oj2gXh71q_DraMlwZE_QxV7tnMV04ZM3R7a3En-pQ9XfBIWOh2VdUxWEVo9ZB8vTJMKHXWAqbap5iuf9RdGKv_sr2PCdJ3RWqZZwMdExSA_E5_Jpxh3bKUdUhlWtvYuo7hXePd5Si0CIx1OmGcuCL4ePSraXcqvVXt-uIW6AxZ_qx6JYa8XqwVDgzkO973nU3rtsRwt7CmU_mpj8RtDRqiJnY7xpdkzOpgQNzS93x578XeH7nhvRWFBLVXKQR0GUVLuvltt5luzIoknzIushNhtPwGn0Nca8L7iYMQDEZaUdBly2te21gwD7ra1fqngEBZ0J7u7Q",
						        "p": "72gxxLCXCm2deQgvXU-JqaRSSyBFdoX9dtowIScEyi_XthcG_XvBm_KX_Jwwobuw_waaMoXQNydycUC4kDtAlLEYUEdsfvRhJlP0t3s1nDLZUEHsisupvTKTGimB9ScRZAfphOYQurjDzuIzYe03G4YMJS0TH3VtJdESLSEwGj0",
						        "q": "4aq-ApTzyttfKg-pi3uSDgKvn8NqzLrS4Ph23KFzTSSvjG5NVjLpIOd26M_bXlJueKQsXmS6W4eMAU3KAB2ucY7XLskoRIC_9MKj6tzmr5hdeJbkPhhsCPLm3LK-0bgQAzeqwd2UfkSA3BofBbxeKIy_jf_FHXI9mPYd_t7nog8",
						        "dp": "wUIzKrwCsYBbJmDdG04hqrfjVpHugQcY3OC1CY4d57lHQM7F7coBOIpU9q5-85A4CSajQzWSJ3PIhnPgiU3LjDyJjAScKL_NzMrpOVRUqorBsnAFKuXNV9WDuhLXvbaT61QXxhiSWKjeKBuhruN3INjM5RXF4hdAzM5BBf1Mf2E",
						        "dq": "ZXVXkl-XsFeq1IVQK-b1xpjMjx7T8JH6Z60t-4oXBdL9njylRqEDEYkffBKfxSt4gYMGc7YD10z81EU-EYlGucWH14AXO51LMGcmPVzt1nrBY4sruQNP50IWK5mtkyqXAGtRuXG-5no0GUEhO3nyN3b4VIZvAAsxyIi2-bUMHV8",
						        "qi": "5zXt64qF5lfYsQTarbDZVvQBGPJjfdbly65uUE-NsJzVnNNI--engb3KmTFpDAaIJZ7EQnDTcCkVKuZTbtqDqbXaWxHe1D_apQslRZka2JqWUKUxqHJIPcDOItNQJZdDc4zFViLg3aWTfxOpJd54fjb2zFBRq87GJ5npSLsfuuM"
						    }
						}
						"""));
		final var subject = new Subject();
		subject.getPrivateCredentials().add(pkp.getJwk());
		final var identity = mock(IuPrincipalIdentity.class);
		when(identity.getSubject()).thenReturn(subject);

		try (final var mockResolver = mockConstruction(StaticKeyInfoCredentialResolver.class, (a, ctx) -> {
			final List<?> certs = (List<?>) ctx.arguments().get(0);
			assertEquals(1, certs.size());
			final var basicX509 = assertInstanceOf(BasicX509Credential.class, certs.get(0));
			assertEquals(pkp.getJwk().getPrivateKey(), basicX509.getPrivateKey());
			assertEquals(pkp.getJwk().getCertificateChain()[0], basicX509.getEntityCertificate());
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
			when(assertion.getAuthnInstant()).thenReturn(authnInstant);
			when(assertion.getAttributes()).thenReturn(Map.of(principalNameAttribute, principalName));
			assertDoesNotThrow(
					() -> when(samlResponseValidator.validateAssertions(response)).thenReturn(List.of(assertion)));

			when(config.getAuthenticatedSessionTimeout()).thenReturn(Duration.ofSeconds(15L));
			final var expires = authnInstant.plusSeconds(15L);

			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE, "saml:pre-validate\n" + content);
			IuTestLogger.expect(SamlResponseValidator.class.getName(), Level.FINE,
					"saml:post-validate:" + principalName + ":" + authnInstant + "; issuer: " + iss + " @"
							+ issuedInstant + ", expires " + expires + "; assertions: [" + assertion + "]");

			assertDoesNotThrow(() -> samlResponseValidator.validate(response));

			assertDoesNotThrow(() -> verify(samlResponseValidator).validateSignature(response));
		}
	}

}
