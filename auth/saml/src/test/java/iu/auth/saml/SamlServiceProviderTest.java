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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import javax.security.auth.Subject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.EncryptedAssertion;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.security.credential.CredentialResolver;
import org.opensaml.xmlsec.encryption.EncryptedData;
import org.opensaml.xmlsec.encryption.support.DecryptionException;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.crypt.WebKey;
import edu.iu.test.IuTestLogger;
import iu.auth.config.AuthConfig;
import iu.auth.config.IuSamlServiceProvider;
import iu.auth.config.IuTrustedIssuer;
import iu.auth.principal.PrincipalVerifier;

@SuppressWarnings("javadoc")
public class SamlServiceProviderTest {
	private static IuSamlServiceProviderMetadata config;
	private static final String MAIL_OID = "urn:oid:0.9.2342.19200300.100.1.3";
	private static final String DISPLAY_NAME_OID = "urn:oid:2.16.840.1.113730.3.1.241";
	private static final String EDU_PERSON_PRINCIPAL_NAME_OID = "urn:oid:1.3.6.1.4.1.5923.1.1.1.6";
	private MockedStatic<IuPrincipalIdentity> mockPrincipalIdentity;

	@SuppressWarnings("deprecation")
	@BeforeAll
	static void setup() throws Exception {
		final var cert = mock(X509Certificate.class);
		final var mockWebKey = mock(WebKey.class);
		when(mockWebKey.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		final var mockPkp = mock(IuPrivateKeyPrincipal.class);
		when(mockPkp.getJwk()).thenReturn(mockWebKey);
		when(mockPkp.getAlg()).thenReturn(WebKey.Algorithm.RS256);
		when(mockPkp.getJwk()).thenReturn(mockWebKey);
		final var acsUri = URI.create("test://postUrl/");
		config = getConfig(
				Arrays.asList(
						SamlServiceProviderTest.class.getClassLoader().getResource("metadata_sample.xml").toURI()),
				"urn:example:sp", mockPkp, Arrays.asList(acsUri));
	}

	@BeforeEach
	public void setupAuthConfig() throws Exception {
		mockPrincipalIdentity = mockStatic(IuPrincipalIdentity.class);

	}

	@AfterEach
	public void tearDown() throws Exception {
		mockPrincipalIdentity.close();
	}

	@Test
	public void testGetAuthRequestSuccess() throws IOException {

		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var relayState = IdGenerator.generateId();
		final var sessionId = IdGenerator.generateId();

		try (final var mockAuthConfig = mockStatic(AuthConfig.class)) {
			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);
			URI authnRequest = samlprovider.getAuthnRequest(relayState, sessionId);
			assertNotNull(authnRequest);
			assertTrue(authnRequest.getQuery().startsWith("SAMLRequest"));
			assertTrue(authnRequest.getQuery().contains("RelayState"));
		}
	}

	@Test
	public void testGetAuthRequestStripXmlDeclaration() {
		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var relayState = IdGenerator.generateId();
		final var sessionId = IdGenerator.generateId();
		String xml1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><child>content</child></root>";
		String xml2 = "<root><child>content</child></root>";

		try (final var mockAuthConfig = mockStatic(AuthConfig.class);
				final var mockXmlDomUtil = mockStatic(XmlDomUtil.class)) {
			mockXmlDomUtil.when(() -> XmlDomUtil.getContent(any())).thenReturn(xml1, xml2);
			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			for (int i = 0; i < 2; i++) {
				SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);
				final var authnRequest = samlprovider.getAuthnRequest(relayState, sessionId);
				assertNotNull(authnRequest);
				assertTrue(authnRequest.getQuery().startsWith("SAMLRequest"));
				assertTrue(authnRequest.getQuery().contains("RelayState"));
			}
		}
	}

	@Test
	public void testIdpMetadataCache() throws Exception {
		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";

		try (final var mockAuthConfig = mockStatic(AuthConfig.class);
				final var mockXmlDomUtil = mockStatic(XmlDomUtil.class)) {
			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);
			final var f = SamlServiceProvider.class.getDeclaredField("samlBuilder");
			f.setAccessible(true);
			SamlBuilder samlBuilder = (SamlBuilder) f.get(samlprovider);
			for (int i = 0; i < 3; i++) {
				samlBuilder.getKeyInfoCredentialResolver("https://sp.identityserver");
				if (i == 0)
					Thread.sleep(2000L);
			}
		}
	}

	@Test
	public void testVerifyKey() {
		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";

		final var id = new TestId();
		AuthConfig.register(new Verifier(id.getName(), true));
		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);
		mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any())).thenReturn(true);

		try (final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS)) {
			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));
			final SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);
			samlprovider.getVerifyKey();
		}
	}

	@Test
	public void testServiceProviderNotTrusted() {
		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var sessionId = IdGenerator.generateId();
		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(null);

		try (final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS)) {
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));
			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);
			mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any())).thenReturn(true);
			final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));

			assertThrows(IllegalStateException.class,
					() -> samlprovider.verifyResponse(InetAddress.getByName("127.0.0.0"), samlResponse, sessionId));
		}
	}

	@Test
	public void testServiceProviderUnauthoritative() {
		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var sessionId = IdGenerator.generateId();
		final var id = new TestId();
		AuthConfig.register(new Verifier(id.getName(), true));
		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);

		try (final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS)) {
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));
			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);
			mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any())).thenReturn(false);
			final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));
			assertThrows(IllegalStateException.class,
					() -> samlprovider.verifyResponse(InetAddress.getByName("127.0.0.0"), samlResponse, sessionId));
		}
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testGetSingleSignOnLocationInvalidSamlBinding() throws Exception {

		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var id = new TestId();
		AuthConfig.register(new Verifier(id.getName(), true));
		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);

		final var cert = mock(X509Certificate.class);
		final var mockWebKey = mock(WebKey.class);
		when(mockWebKey.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		final var mockPkp = mock(IuPrivateKeyPrincipal.class);
		when(mockPkp.getJwk()).thenReturn(mockWebKey);
		when(mockPkp.getAlg()).thenReturn(WebKey.Algorithm.RS256);
		when(mockPkp.getJwk()).thenReturn(mockWebKey);
		final var acsUri = URI.create("test://postUrl/");

		IuSamlServiceProviderMetadata metadata = getConfig(Arrays.asList(
				SamlServiceProviderTest.class.getClassLoader().getResource("metadata_invalid_binding.xml").toURI()),
				"urn:example:sp", mockPkp, Arrays.asList(acsUri));
		try (final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS)) {
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));
			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			assertThrows(IllegalStateException.class, () -> new SamlServiceProvider(postUri, realm, metadata));
		}
	}

	@Test
	public void testIsValidEntryPoint() {
		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var id = new TestId();
		AuthConfig.register(new Verifier(id.getName(), true));
		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);

		try (final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS)) {
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));
			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);
			mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any())).thenReturn(false);
			assertFalse(samlprovider.isValidEntryPoint(postUri));
			assertTrue(samlprovider.isValidEntryPoint(URI.create("test://init")));
			assertEquals(realm, samlprovider.getRealm());
			assertNull(samlprovider.getAuthScheme());
			assertEquals(postUri, samlprovider.getAuthenticationEndpoint());
			assertTrue(samlprovider.isAuthoritative());
			assertEquals(SamlPrincipal.class, samlprovider.getType());
			assertNotNull(samlprovider.getServiceProviderMetaData());

		}
	}

	@Test
	public void testGetVerifyAlg()
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var id = new TestId();
		AuthConfig.register(new Verifier(id.getName(), true));
		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);

		SamlBuilder builder = new SamlBuilder(config);

		try (final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS)) {
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));
			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);
			Field f;
			f = SamlServiceProvider.class.getDeclaredField("samlBuilder");
			f.setAccessible(true);
			f.set(samlprovider, builder);
			mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any())).thenReturn(true);
			assertNotNull(samlprovider.getVerifyAlg());
		}
	}

	@Test
	public void testWithBinding()
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var id = new TestId();
		AuthConfig.register(new Verifier(id.getName(), true));
		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);

		SamlBuilder builder = new SamlBuilder(config);
		final var provider = mock(SamlServiceProvider.class);

		try (final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS)) {
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));
			mockAuthConfig.when(() -> AuthConfig.get(IuSamlServiceProvider.class)).thenReturn(Arrays.asList(provider));
			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);
			Field f;
			f = SamlServiceProvider.class.getDeclaredField("samlBuilder");
			f.setAccessible(true);
			f.set(samlprovider, builder);
			mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any())).thenReturn(true);
			assertThrows(IllegalArgumentException.class, () -> SamlServiceProvider.withBinding(postUri));

			Field postUriField;
			postUriField = SamlServiceProvider.class.getDeclaredField("postUri");
			postUriField.setAccessible(true);
			postUriField.set(provider, postUri);

			assertNotNull(SamlServiceProvider.withBinding(postUri));
		}
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testInvalidAcsUrl() throws Exception {

		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";

		final var cert = mock(X509Certificate.class);
		final var mockWebKey = mock(WebKey.class);
		when(mockWebKey.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		final var mockPkp = mock(IuPrivateKeyPrincipal.class);
		when(mockPkp.getJwk()).thenReturn(mockWebKey);
		when(mockPkp.getAlg()).thenReturn(WebKey.Algorithm.RS256);
		when(mockPkp.getJwk()).thenReturn(mockWebKey);
		final var acsUri = URI.create("test://acsUrl/");
		IuSamlServiceProviderMetadata metadata = getConfig(
				Arrays.asList(
						SamlServiceProviderTest.class.getClassLoader().getResource("metadata_sample.xml").toURI()),
				"urn:example:sp", mockPkp, Arrays.asList(acsUri));

		final var id = new TestId();
		AuthConfig.register(new Verifier(id.getName(), true));
		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);

		try (final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS)) {
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));
			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			assertThrows(IllegalArgumentException.class, () -> new SamlServiceProvider(postUri, realm, metadata));
		}
	}

	@Test
	public void testInvalidSignatureVerificationSamlResponse() throws UnknownHostException {

		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var sessionId = IdGenerator.generateId();
		final var id = new TestId();
		AuthConfig.register(new Verifier(id.getName(), true));
		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);

		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn("https://sp.identityserver");
		final var mockSignature = mock(Signature.class);

		try (final var mockXMLObjectSupport = mockStatic(XMLObjectSupport.class);
				final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS);
				MockedConstruction<ExplicitKeySignatureTrustEngine> mockSignatureTrustEngine = mockConstruction(
						ExplicitKeySignatureTrustEngine.class, (mock, context) -> {
							CredentialResolver resolver = (CredentialResolver) context.arguments().get(0);
							when(mock.getCredentialResolver()).thenReturn(resolver);
							KeyInfoCredentialResolver keyResolver = (KeyInfoCredentialResolver) context.arguments()
									.get(1);
							when(mock.getKeyInfoResolver()).thenReturn(keyResolver);
							when(mock.validate(any(), any())).thenReturn(false);
						});

		) {
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));
			final var mockResponse = mock(Response.class);
			when(mockResponse.getIssuer()).thenReturn(issuer);
			when(mockResponse.getSignature()).thenReturn(mockSignature);

			mockXMLObjectSupport.when(() -> XMLObjectSupport.unmarshallFromInputStream(any(), any()))
					.thenReturn(mockResponse);
			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);
			mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any())).thenReturn(true);
			IuTestLogger.allow(SamlServiceProvider.class.getName(), Level.FINE, "SAML2 .*");
			final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));
			assertThrows(IllegalArgumentException.class,
					() -> samlprovider.verifyResponse(InetAddress.getByName("127.0.0.0"), samlResponse, sessionId));
		}
	}

//	@SuppressWarnings("static-access")
	@Test
	public void testGetDecrypter() throws UnknownHostException {

//		final var postUri = URI.create("test://postUrl/");
//		final var realm = "iu-saml-test";
//		final var sessionId = IdGenerator.generateId();
		final var id = new TestId();
		AuthConfig.register(new Verifier(id.getName(), true));
		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);

		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn("https://sp.identityserver");
		final var mockSignature = mock(Signature.class);

		try (// final var mockIuAuthenticationRealm =
				// mockStatic(IuAuthenticationRealm.class);
				// final var mockXMLObjectSupport = mockStatic(XMLObjectSupport.class);
				final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS);
//				MockedConstruction<ExplicitKeySignatureTrustEngine> mockSignatureTrustEngine = mockConstruction(
//						ExplicitKeySignatureTrustEngine.class, (mock, context) -> {
//							CredentialResolver resolver = (CredentialResolver) context.arguments().get(0);
//							when(mock.getCredentialResolver()).thenReturn(resolver);
//							KeyInfoCredentialResolver keyResolver = (KeyInfoCredentialResolver) context.arguments()
//									.get(1);
//							when(mock.getKeyInfoResolver()).thenReturn(keyResolver);
//							when(mock.validate(any(), any())).thenReturn(false);
//						});
		) {
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));
			final var mockResponse = mock(Response.class);
			when(mockResponse.getIssuer()).thenReturn(issuer);
			when(mockResponse.getSignature()).thenReturn(mockSignature);

//			mockXMLObjectSupport.when(() -> XMLObjectSupport.unmarshallFromInputStream(any(), any()))
//					.thenReturn(mockResponse);
//			mockIuAuthenticationRealm.when(() -> IuAuthenticationRealm.of(realm)).thenReturn(config);
//			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);
			mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any())).thenReturn(true);
			IuTestLogger.allow(SamlServiceProvider.class.getName(), Level.FINE, "SAML2 .*");
			SamlServiceProvider.getDecrypter(id);
		}
	}

	@Test
	public void testSamlResponseWithConditionAsNull()
			throws UnknownHostException, DecryptionException, IuAuthenticationException {

		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var sessionId = IdGenerator.generateId();
		final var id = new TestId();
		AuthConfig.register(new Verifier(id.getName(), true));
		Instant now = Instant.now();
		Instant issueInstant = now.plus(Duration.ofMinutes(5)).minusSeconds(5);

		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn("https://sp.identityserver");

		final var mockSignature = mock(Signature.class);

		final var mockXSAny = mock(XSAny.class);
		when(mockXSAny.getTextContent()).thenReturn("testUser");

		final var mockAttributePrincipalName = mock(Attribute.class);

		when(mockAttributePrincipalName.getAttributeValues()).thenReturn(Arrays.asList(mockXSAny));

		final var attributeStatement = mock(AttributeStatement.class);
		when(attributeStatement.getAttributes()).thenReturn(Arrays.asList(mockAttributePrincipalName));
		when(mockAttributePrincipalName.getName()).thenReturn(EDU_PERSON_PRINCIPAL_NAME_OID);

		final var mockAssertion = mock(Assertion.class);
		when(mockAssertion.getAttributeStatements()).thenReturn(Arrays.asList(attributeStatement));
		when(mockAssertion.getVersion()).thenReturn(SAMLVersion.VERSION_20);
		when(mockAssertion.getIssueInstant()).thenReturn(issueInstant);
		final var mockIssuer = mock(Issuer.class);
		when(mockIssuer.getValue()).thenReturn("test");
		when(mockAssertion.getIssuer()).thenReturn(mockIssuer);

		final var mockAuthnStatement = mock(AuthnStatement.class);
		when(mockAuthnStatement.getAuthnInstant()).thenReturn(issueInstant);
		when(mockAssertion.getAuthnStatements()).thenReturn(Arrays.asList(mockAuthnStatement));

		final var mockSubject = mock(org.opensaml.saml.saml2.core.Subject.class);
		final var mockSubjectConfirmation = mock(SubjectConfirmation.class);
		when(mockSubjectConfirmation.getMethod()).thenReturn("urn:oasis:names:tc:SAML:2.0:cm:bearer");
		final var mockSubjectConfirmationData = mock(SubjectConfirmationData.class);
		when(mockSubjectConfirmation.getSubjectConfirmationData()).thenReturn(mockSubjectConfirmationData);
		when(mockSubject.getSubjectConfirmations()).thenReturn(Arrays.asList(mockSubjectConfirmation));

		when(mockAssertion.getSubject()).thenReturn(mockSubject);
		final var mockDecrypter = mock(Decrypter.class);

		when(mockAssertion.getSubject()).thenReturn(mockSubject);

		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);
		try (final var mockAuthConfig = mockStatic(AuthConfig.class);
				final var mockXMLObjectSupport = mockStatic(XMLObjectSupport.class);
				final var mockProvider = mockStatic(SamlServiceProvider.class, CALLS_REAL_METHODS);
				MockedConstruction<ExplicitKeySignatureTrustEngine> mockSignatureTrustEngine = mockConstruction(
						ExplicitKeySignatureTrustEngine.class, (mock, context) -> {
							CredentialResolver resolver = (CredentialResolver) context.arguments().get(0);
							when(mock.getCredentialResolver()).thenReturn(resolver);
							KeyInfoCredentialResolver keyResolver = (KeyInfoCredentialResolver) context.arguments()
									.get(1);
							when(mock.getKeyInfoResolver()).thenReturn(keyResolver);
							when(mock.validate(any(), any())).thenReturn(true);
						});

		) {
			mockProvider.when(() -> SamlServiceProvider.getDecrypter(any())).thenReturn(mockDecrypter);

			final var mockResponse = mock(Response.class);
			final var encryptedAssertion = mock(EncryptedAssertion.class);

			when(mockResponse.getIssuer()).thenReturn(issuer);
			when(mockResponse.getIssueInstant()).thenReturn(issueInstant);
			when(mockResponse.getSignature()).thenReturn(mockSignature);
			when(mockResponse.getAssertions()).thenReturn(Arrays.asList(mockAssertion));
			when(mockResponse.getEncryptedAssertions()).thenReturn(Arrays.asList(encryptedAssertion));

			final var encryptedData = mock(EncryptedData.class);
			when(encryptedAssertion.getEncryptedData()).thenReturn(encryptedData);

			when(mockDecrypter.decrypt(encryptedAssertion)).thenReturn(mockAssertion);

			mockXMLObjectSupport.when(() -> XMLObjectSupport.unmarshallFromInputStream(any(), any()))
					.thenReturn(mockResponse);
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));

			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);

			final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));
			IuTestLogger.allow(SamlServiceProvider.class.getName(), Level.FINE, "SAML2 .*");

			mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any())).thenReturn(true);

			SamlPrincipal principal = samlprovider.verifyResponse(InetAddress.getByName("127.0.0.0"), samlResponse,
					sessionId);
			assertNotNull(principal);
			assertEquals("testUser", principal.getName());

			IuTestLogger.expect(SamlServiceProvider.class.getName(), Level.INFO,
					"saml:verify:testUser; serviceProvider: iu-saml-test");
			samlprovider.verify(principal);
		}
	}

	@Test
	public void testSamlResponseNullSubjectConfirmation() throws UnknownHostException, DecryptionException {

		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var sessionId = IdGenerator.generateId();
		final var id = new TestId();
		AuthConfig.register(new Verifier(id.getName(), true));
		Instant now = Instant.now();
		Instant issueInstant = now.plus(Duration.ofMinutes(5)).minusSeconds(5);

		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn("https://sp.identityserver");

		final var mockSignature = mock(Signature.class);

		final var mockXSAny = mock(XSAny.class);
		when(mockXSAny.getTextContent()).thenReturn("testUser");

		final var mockAttributePrincipalName = mock(Attribute.class);

		when(mockAttributePrincipalName.getAttributeValues()).thenReturn(Arrays.asList(mockXSAny));

		final var attributeStatement = mock(AttributeStatement.class);
		when(attributeStatement.getAttributes()).thenReturn(Arrays.asList(mockAttributePrincipalName));

		final var mockAssertion = mock(Assertion.class);
		when(mockAssertion.getAttributeStatements()).thenReturn(Arrays.asList(attributeStatement));
		when(mockAssertion.getVersion()).thenReturn(SAMLVersion.VERSION_20);
		when(mockAssertion.getIssueInstant()).thenReturn(issueInstant);
		final var mockIssuer = mock(Issuer.class);
		when(mockIssuer.getValue()).thenReturn("test");
		when(mockAssertion.getIssuer()).thenReturn(mockIssuer);

		final var mockAuthnStatement = mock(AuthnStatement.class);
		when(mockAuthnStatement.getAuthnInstant()).thenReturn(issueInstant);
		when(mockAssertion.getAuthnStatements()).thenReturn(Arrays.asList(mockAuthnStatement));

		final var mockSubject = mock(org.opensaml.saml.saml2.core.Subject.class);

		when(mockAssertion.getSubject()).thenReturn(mockSubject);
		final var mockDecrypter = mock(Decrypter.class);

		when(mockAssertion.getSubject()).thenReturn(mockSubject);

		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);

		try (final var mockAuthConfig = mockStatic(AuthConfig.class);
				final var mockXMLObjectSupport = mockStatic(XMLObjectSupport.class);
				final var mockProvider = mockStatic(SamlServiceProvider.class, CALLS_REAL_METHODS);

				MockedConstruction<ExplicitKeySignatureTrustEngine> mockSignatureTrustEngine = mockConstruction(
						ExplicitKeySignatureTrustEngine.class, (mock, context) -> {
							CredentialResolver resolver = (CredentialResolver) context.arguments().get(0);
							when(mock.getCredentialResolver()).thenReturn(resolver);
							KeyInfoCredentialResolver keyResolver = (KeyInfoCredentialResolver) context.arguments()
									.get(1);
							when(mock.getKeyInfoResolver()).thenReturn(keyResolver);
							when(mock.validate(any(), any())).thenReturn(true);
						});

		) {
			mockProvider.when(() -> SamlServiceProvider.getDecrypter(any())).thenReturn(mockDecrypter);
			when(mockAttributePrincipalName.getName()).thenReturn(EDU_PERSON_PRINCIPAL_NAME_OID);

			final var mockResponse = mock(Response.class);
			final var encryptedAssertion = mock(EncryptedAssertion.class);

			when(mockResponse.getIssuer()).thenReturn(issuer);
			when(mockResponse.getIssueInstant()).thenReturn(issueInstant);
			when(mockResponse.getSignature()).thenReturn(mockSignature);
			when(mockResponse.getAssertions()).thenReturn(Arrays.asList(mockAssertion));
			when(mockResponse.getEncryptedAssertions()).thenReturn(Arrays.asList(encryptedAssertion));

			final var encryptedData = mock(EncryptedData.class);
			when(encryptedAssertion.getEncryptedData()).thenReturn(encryptedData);

			when(mockDecrypter.decrypt(encryptedAssertion)).thenReturn(mockAssertion);

			mockXMLObjectSupport.when(() -> XMLObjectSupport.unmarshallFromInputStream(any(), any()))
					.thenReturn(mockResponse);
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));

			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);

			final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));
			IuTestLogger.allow(SamlServiceProvider.class.getName(), Level.FINE, "SAML2 .*");

			mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any())).thenReturn(true);

			assertThrows(IllegalArgumentException.class,
					() -> samlprovider.verifyResponse(InetAddress.getByName("127.0.0.0"), samlResponse, sessionId));
		}
	}

	@Test
	public void testSuccessSamlResponse() throws UnknownHostException, DecryptionException {

		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var sessionId = IdGenerator.generateId();
		final var id = new TestId();
		AuthConfig.register(new Verifier(id.getName(), true));
		Instant now = Instant.now();
		Instant issueInstant = now.plus(Duration.ofMinutes(5)).minusSeconds(5);

		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn("https://sp.identityserver");

		final var mockSignature = mock(Signature.class);

		final var xsString = mock(XSString.class);
		when(xsString.getValue()).thenReturn("testUser");

		final var mockAttributePrincipalName = mock(Attribute.class);
		final var mockAttributeDisplayName = mock(Attribute.class);

		final var mockAttributeEmail = mock(Attribute.class);

		when(mockAttributePrincipalName.getAttributeValues()).thenReturn(Arrays.asList(xsString));
		when(mockAttributeDisplayName.getAttributeValues()).thenReturn(Arrays.asList(xsString));
		when(mockAttributeEmail.getAttributeValues()).thenReturn(Arrays.asList(xsString));

		final var attributeStatement = mock(AttributeStatement.class);
		when(attributeStatement.getAttributes())
				.thenReturn(Arrays.asList(mockAttributePrincipalName, mockAttributeDisplayName, mockAttributeEmail));

		final var mockAssertion = mock(Assertion.class);
		when(mockAssertion.getAttributeStatements()).thenReturn(Arrays.asList(attributeStatement));
		when(mockAssertion.getVersion()).thenReturn(SAMLVersion.VERSION_20);
		when(mockAssertion.getIssueInstant()).thenReturn(issueInstant);
		final var mockIssuer = mock(Issuer.class);
		when(mockIssuer.getValue()).thenReturn("test");
		when(mockAssertion.getIssuer()).thenReturn(mockIssuer);

		final var mockConditions = mock(Conditions.class);
		Instant expectedNotBefore = Instant.parse("1984-08-26T10:01:30.043Z");
		when(mockConditions.getNotBefore()).thenReturn(expectedNotBefore);
		when(mockAssertion.getConditions()).thenReturn(mockConditions);
		final var mockAuthnStatement = mock(AuthnStatement.class);
		when(mockAuthnStatement.getAuthnInstant()).thenReturn(issueInstant);
		when(mockAssertion.getAuthnStatements()).thenReturn(Arrays.asList(mockAuthnStatement));

		final var mockSubject = mock(org.opensaml.saml.saml2.core.Subject.class);
		final var mockSubjectConfirmation = mock(SubjectConfirmation.class);
		when(mockSubjectConfirmation.getMethod()).thenReturn("urn:oasis:names:tc:SAML:2.0:cm:bearer");
		final var mockSubjectConfirmationData = mock(SubjectConfirmationData.class);
		when(mockSubjectConfirmation.getSubjectConfirmationData()).thenReturn(mockSubjectConfirmationData);
		when(mockSubject.getSubjectConfirmations()).thenReturn(Arrays.asList(mockSubjectConfirmation));

		when(mockAssertion.getSubject()).thenReturn(mockSubject);
		final var mockDecrypter = mock(Decrypter.class);

		when(mockAssertion.getSubject()).thenReturn(mockSubject);

		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);

		try (final var mockAuthConfig = mockStatic(AuthConfig.class);
				final var mockXMLObjectSupport = mockStatic(XMLObjectSupport.class);
				final var mockProvider = mockStatic(SamlServiceProvider.class, CALLS_REAL_METHODS);
				MockedConstruction<ExplicitKeySignatureTrustEngine> mockSignatureTrustEngine = mockConstruction(
						ExplicitKeySignatureTrustEngine.class, (mock, context) -> {
							CredentialResolver resolver = (CredentialResolver) context.arguments().get(0);
							when(mock.getCredentialResolver()).thenReturn(resolver);
							KeyInfoCredentialResolver keyResolver = (KeyInfoCredentialResolver) context.arguments()
									.get(1);
							when(mock.getKeyInfoResolver()).thenReturn(keyResolver);
							when(mock.validate(any(), any())).thenReturn(true);
						});

		) {

			mockProvider.when(() -> SamlServiceProvider.getDecrypter(any())).thenReturn(mockDecrypter);
			when(mockAttributePrincipalName.getName()).thenReturn(EDU_PERSON_PRINCIPAL_NAME_OID);
			when(mockAttributeDisplayName.getName()).thenReturn(DISPLAY_NAME_OID);
			when(mockAttributeEmail.getName()).thenReturn(MAIL_OID);

			final var mockResponse = mock(Response.class);
			final var encryptedAssertion = mock(EncryptedAssertion.class);

			when(mockResponse.getIssuer()).thenReturn(issuer);
			when(mockResponse.getIssueInstant()).thenReturn(issueInstant);
			when(mockResponse.getSignature()).thenReturn(mockSignature);
			when(mockResponse.getAssertions()).thenReturn(Arrays.asList(mockAssertion));
			when(mockResponse.getEncryptedAssertions()).thenReturn(Arrays.asList(encryptedAssertion));

			final var encryptedData = mock(EncryptedData.class);
			when(encryptedAssertion.getEncryptedData()).thenReturn(encryptedData);

			when(mockDecrypter.decrypt(encryptedAssertion)).thenReturn(mockAssertion);

			mockXMLObjectSupport.when(() -> XMLObjectSupport.unmarshallFromInputStream(any(), any()))
					.thenReturn(mockResponse);
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class))
					.thenReturn(Arrays.asList(mockIuTrustedIssuer));

			mockAuthConfig.when(() -> AuthConfig.load(IuSamlServiceProviderMetadata.class, realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm, config);

			final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));
			IuTestLogger.allow(SamlServiceProvider.class.getName(), Level.FINE, "SAML2 .*");

			mockPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(any(), any())).thenReturn(true);

			SamlPrincipal principal = samlprovider.verifyResponse(InetAddress.getByName("127.0.0.0"), samlResponse,
					sessionId);
			assertNotNull(principal);
			assertEquals("testUser", principal.getName());
		}
	}

	private static final class Verifier implements PrincipalVerifier<TestId> {
		private final String realm;
		private final boolean authoritative;

		private Verifier(String realm, boolean authoritative) {
			this.realm = realm;
			this.authoritative = authoritative;
		}

		@Override
		public Class<TestId> getType() {
			return TestId.class;
		}

		@Override
		public String getRealm() {
			return realm;
		}

		@Override
		public boolean isAuthoritative() {
			return authoritative;
		}

		@Override
		public void verify(TestId id) {
			assertEquals(realm, id.getName());
		}

		@Override
		public String getAuthScheme() {
			return null;
		}

		@Override
		public URI getAuthenticationEndpoint() {
			return null;
		}
	}

	private static final class TestId implements IuPrincipalIdentity {

		private final String name = IdGenerator.generateId();
		private final Instant issuedAt = Instant.now();
		private final Instant authTime = issuedAt.truncatedTo(ChronoUnit.SECONDS);
		private final Instant expires = authTime.plusSeconds(5L);

		// For testing purposes only. NOT FOR PRODUCTION USE
		private final WebKey encrypt = WebKey.parse("{\n" //
				+ "        \"kid\": \"verify\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIIEDjCCAvagAwIBAgIUYc91WRFm5DXRwgn1h61zNRIV4b0wDQYJKoZIhvcNAQELBQAwgZExCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxLDAqBgNVBAMMI3VybjpleGFtcGxlOlNhbWxTZXJ2aWNlUHJvdmlkZXJUZXN0MCAXDTI0MDcxMTE5NTgyMFoYDzIxMjQwNzEyMTk1ODIwWjCBkTELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEsMCoGA1UEAwwjdXJuOmV4YW1wbGU6U2FtbFNlcnZpY2VQcm92aWRlclRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDh5XgRboPi2gOvJBujUzPhGSpTAf+YV3is/70LEUw/4sGsnzUdn1GoiUBMpR+PKhz37eoOOQ4QWrHeI8DQtXMP4SmclBwwZOP03pLhNr3Jop0WoetZECTOA6Xj1jDqRVMQRPcdOMX47qlM5DhJy9qBOWt/tKSKbo2smOWygKYMIhUxllohjhfOxjI+FHKJSxCinXFIDlWxEA/WkUcMCqDeSY8x9aNBO5aubVPjQ2uNKKUZtfPoX8kvuNDxh8NsfTK4H3FHDC5b0u0gDE0jWCVYqLX3Abd4pqJgtOdq82aQmAXZk9UArtTczM/C7WjlkyA6CqbHEyMu6eB4fsudF/ABAgMBAAGjWjBYMB0GA1UdDgQWBBQFNUZPip9TKU5O2XUqRLo0zYaW7jAfBgNVHSMEGDAWgBQFNUZPip9TKU5O2XUqRLo0zYaW7jAJBgNVHRMEAjAAMAsGA1UdDwQEAwIFoDANBgkqhkiG9w0BAQsFAAOCAQEAQSAtji1V9wCEVAsExZfjuqUfGtYQvYzFj+kt2ooPVp887XzjPWiqOBBC2d+jW1oNgNJF71fpeGuaSkGMhDg9ASnq5pV3VgD/S+mIj9ItSefAjQifpTpyWBqL99UT5cAKN9577M1Ggh6gFLf9vZvobF62Ay4DRsh6AUF8twaXeh3ZxUghNm6K8HZnWQQSmT93QEGYWQDx1U0wKyckteFnIqDIczmH6Y2NmTFmHby4wTsA7OyGMev+vDskKYwGCnrCc0TBq6kzSKOYbPYtOVhS96atfnSZ2ehe64FMKoPZpE0QCaPrL7EJGPQm6JUAAqsWJWPNjNNpmlggNvA1OfKUBg==\"\n"
				+ "        ],\n" //
				+ "        \"kty\": \"RSA\",\n" //
				+ "        \"n\": \"4eV4EW6D4toDryQbo1Mz4RkqUwH_mFd4rP-9CxFMP-LBrJ81HZ9RqIlATKUfjyoc9-3qDjkOEFqx3iPA0LVzD-EpnJQcMGTj9N6S4Ta9yaKdFqHrWRAkzgOl49Yw6kVTEET3HTjF-O6pTOQ4ScvagTlrf7Skim6NrJjlsoCmDCIVMZZaIY4XzsYyPhRyiUsQop1xSA5VsRAP1pFHDAqg3kmPMfWjQTuWrm1T40NrjSilGbXz6F_JL7jQ8YfDbH0yuB9xRwwuW9LtIAxNI1glWKi19wG3eKaiYLTnavNmkJgF2ZPVAK7U3MzPwu1o5ZMgOgqmxxMjLungeH7LnRfwAQ\",\n"
				+ "        \"e\": \"AQAB\",\n" //
				+ "        \"d\": \"WZuaJmgNfxZ2capEIGSn5roB1Q2s4zSHlTCZP-OruIftxdkdy9NgJBfV3tF9lF_jP-Irf1rYnlorxm-uU9w2eW0bAZarG_NZjdAguZ_qZyrPX6P5ZMoHn4VI7_kOTFAVpBWHZRsZRSb_F5ZMUdHAqpQpdW4l-xfhsT6xlz57H8JCOplwWv6Kv60j-8vC2VNMZLiCNdvQAxxUKquYBt1HXMN46TlTQVeYwTa7c3HRl1qfaqkRHEXxFbM0UfTNbGybBEFxduuxjBRn8xVs8cJYgcqjzJt8aaDoNBeqCuWFwPAYTU77RKLcL3Gdj5-YsCTsnGx0WVNg7l8dZGABgH4GRQ\",\n"
				+ "        \"p\": \"_7K1mfRsjIbjRdrKjVnp6KczoMEx_t4MrrVtJKJDpu7qs0sJw0fKlWT98qeWLQLmN1gIlgWNgpV1qirSCO7woihj94ErBCZeSQRr6GBKZTftVtBpEFCrbf3w5PUxRGnzPZYxMR37vGz9P1cSwnTuf1_vFQNwmGO0U4sS7RKBLgs\",\n"
				+ "        \"q\": \"4inAWoIz6WszLBw7reRF1S0QeISYeRBl2X4uQqsca0RfvRVAJK6kkwdTTvl4gv8qFqeMLwJzS48uZy0IKAoPTYXAo27qpKl_HkdGoBwXDbabMdxTQkeHYejqpifj5zhhUO4GtcWhDpYjbve7ugJWaekYOTq4jF8vF88MZCT9PaM\",\n"
				+ "        \"dp\": \"OgE9Zx5mnX5gAlG-z1ANWwTLFnWdNNcEg4GOr9fLhwv93Axyu4UGtNtDLI_N5ooY1Yc382hxEKV9Gsw592LU3cRR4SzBKGDX1LKXFBD773g_dAk1PElAimQoCJiCw6VRU7BFmoHVwIns7TiAffJuxCBsKRUtrrQ3jRgog_VFrr8\",\n"
				+ "        \"dq\": \"jarRjuBYXDKGT28wAvEmvS4JTzTfvZYD9oUPvTsqBhdCUVLqZw_ujxrbmHC0iHoFh0NUkG3cgswhqQeQQGRsyYaq8LUdzh9OPU0wdEKkPjbQaB83GCFuMGqR8ZqzK7cpXmR7V4mAJX7umygbM50bPCSCw_aAe410FlnfzStOZjk\",\n"
				+ "        \"qi\": \"qO3wjC2Me3FfhzO-ZZaGM05n9oIV4LYtZqTUc0FQ6roXRsDY89rtGg8HLuS5zmESqsPqPiCjgt-S2PBZg9OY3BFUCvED1CV0FZO_zEqVIh_keQooQSWFK6Fep_esmgpuXjKfEAOVerbvX-qcv7iMSjYkes53ZS-QNQ9kNcab7Dw\"\n"
				+ "    }");

		@Override
		public String getName() {
			return name;
		}

		@Override
		public Instant getIssuedAt() {
			return issuedAt;
		}

		@Override
		public Instant getAuthTime() {
			return authTime;
		}

		@Override
		public Instant getExpires() {
			return expires;
		}

		@Override
		public Subject getSubject() {
			return new Subject(true, Set.of(this), Set.of(encrypt.wellKnown()), Set.of(encrypt));
		}
	}

	static IuSamlServiceProviderMetadata getConfig(List<URI> metadataUris, String serviceProviderEntityId,
			IuPrivateKeyPrincipal pkp, List<URI> acsUris) {
		final var config = new IuSamlServiceProviderMetadata() {
			@Override
			public Type getType() {
				return Type.SAML;
			}

			@Override
			public String getServiceProviderEntityId() {
				return serviceProviderEntityId;
			}

			@Override
			public List<URI> getAcsUris() {
				return acsUris;
			}

			@Override
			public List<URI> getMetadataUris() {
				return IuException.unchecked(() -> metadataUris);
			}

			@Override
			public List<String> getAllowedRange() {
				return Arrays.asList("");
			}

			@Override
			public Duration getMetadataTtl() {
				return Duration.ofSeconds(2L);
			}

			@Override
			public Duration getAuthenticatedSessionTimeout() {
				return Duration.ofSeconds(2L);
			}

			@Override
			public String getIdentityProviderEntityId() {
				return "https://sp.identityserver";
			}

			@Override
			public IuPrivateKeyPrincipal getIdentity() {
				return pkp;
			}

			@Override
			public Iterable<URI> getEntryPointUris() {
				return IuIterable.iter(URI.create("test://init"));
			}

		};
		return config;
	}

}
