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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.security.auth.Subject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
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
import org.w3c.dom.Document;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuAuthenticationRealm;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.test.IuTestLogger;
import iu.auth.config.AuthConfig;
import iu.auth.config.IuTrustedIssuer;
import iu.auth.pki.PkiFactory;
import iu.auth.principal.PrincipalVerifier;



public class SamlServiceProviderTest {
	private static X509Certificate certificate;
	private static SamlServiceProvider provider;
	private static String privateKey;
	private static IuSamlServiceProviderMetadata config;
	private static final String MAIL_OID = "urn:oid:0.9.2342.19200300.100.1.3";
	private static final String DISPLAY_NAME_OID = "urn:oid:2.16.840.1.113730.3.1.241";
	private static final String EDU_PERSON_PRINCIPAL_NAME_OID = "urn:oid:1.3.6.1.4.1.5923.1.1.1.6";

	@BeforeAll
	static void setup() throws MalformedURLException {

		// This is a sample key for testing and demonstration purpose only.
		// ---- NOT FOR PRODUCTION USE -----
		// $ openssl genrsa | tee /tmp/k
		// $ openssl req -days 410 -x509 -key /tmp/k
		final var certText = "-----BEGIN CERTIFICATE-----\r\n" //
				+ "MIID5TCCAs2gAwIBAgIUDSy2fR7Mli1vvbswCfNcW8crSZYwDQYJKoZIhvcNAQEL\r\n"
				+ "BQAwgYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtC\r\n"
				+ "bG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQL\r\n"
				+ "DAZTVEFSQ0gxGzAZBgNVBAMMEml1LWphdmEtY3J5cHQtdGVzdDAgFw0yNDAzMTAx\r\n"
				+ "OTIxNDlaGA8yMTI0MDMxMTE5MjE0OVowgYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQI\r\n"
				+ "DAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFu\r\n"
				+ "YSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxGzAZBgNVBAMMEml1LWphdmEt\r\n"
				+ "Y3J5cHQtdGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALL0kKuy\r\n"
				+ "9h1E6AqPrFu3dUvOb3f2fPjyqlyStGGk4P8rUljJd+QyubAapIF2Sq420a9Q7atp\r\n"
				+ "EBZiLeC0fV8VbrBTYSFp2Up3rxUcEVkDZKpCjbwZ16RZIenGZWYBkLQh5P/VjrUG\r\n"
				+ "HCD9QSnTy08yBLAFrnOzBRL0mLoLmRVbam47QUV98pNAsmZF0wxsrSp6pmMSnHGY\r\n"
				+ "zlWFX9/vnrSWGMSKy229hYKMfSbY76sJNt605JWK19A3NjgeMT0rWZcCHnpv1s63\r\n"
				+ "DWx2ZQuKVNgTZm5oftLPQ6Dj4PwqEo9aMqahnIYw8t37zbq3ZsZgL+4Hcu866YAe\r\n"
				+ "W0GhvZVeOd89zS8CAwEAAaNTMFEwHQYDVR0OBBYEFNVoqadb2L5DK9+5yJ3WPxQs\r\n"
				+ "Dv/dMB8GA1UdIwQYMBaAFNVoqadb2L5DK9+5yJ3WPxQsDv/dMA8GA1UdEwEB/wQF\r\n"
				+ "MAMBAf8wDQYJKoZIhvcNAQELBQADggEBADzre/3bkFb36eYUeTrun+334+9v3VM2\r\n"
				+ "S6Sa2ycrUqquA0igkVI7Bf6odne+rW8z3YVxlRLBqOfuqCz/XShv+NiSvGTe4oGd\r\n"
				+ "rZv1Uz6s8SaUgbrOD7CphrUpkXl10jLiOwK77bBQBXXIjiTgReVQlZj3ni9ysvUP\r\n"
				+ "j05uY1zNDU631DQSHUZkPDAv4t5rCS9atoznIGDLgkSRDYLSbGoX7/1qSUg/yZvl\r\n"
				+ "vJ2qfMhgmuzhrTOF4rGNOZmJ/eMarqBu3oRBdpsZzdGQehAoEqoVTgrnhZ7KdWKE\r\n"
				+ "U++EQOj4ZKOR2YyYTXuYGLNZZiJZs9U6GmI32qLnxQIlhl6wxDKvjMs=\r\n" //
				+ "-----END CERTIFICATE-----\r\n";

		final var pem = PemEncoded.parse(certText);
		certificate = pem.next().asCertificate();

		// This is a sample key for testing and demonstration purpose only.
		// ---- NOT FOR PRODUCTION USE -----
		// $ openssl genrsa
		privateKey = "-----BEGIN PRIVATE KEY-----\r\n"
				+ "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDYXFUKjgq4Iblp\r\n"
				+ "mU3Ymww0LgMjaWIO/DmFQF+EViO+rCRteffNzKFWR3+raINMH4uKXL7d9NGJa1TF\r\n"
				+ "pbrj3XdsdKk/uhrmWfvnClvs79e8J/+UBQ59h5Da7C3f19rVfdIxf+jkPYff+lSw\r\n"
				+ "JLCLlZVsdn71BPAKpOvsu5qr9Nc04EMcPMklbc+n882hPsyeopAgZ01l928RX7/U\r\n"
				+ "NU3Uw+MQuYYia54XI3P6PPKDNfqd9dMY0KHLUeo6b/5FZZkLZvnikuvNVO4H+fQO\r\n"
				+ "OVDvezhXFxO2zM9Q2eCJbvayR2p0TthK2N7O48cKofgMdk1U4Un2vMDXF7pTdGtI\r\n"
				+ "udC1OzJXAgMBAAECggEACObvptIGVeIpV1Nz9QQYIfN8tJHK85PkJ/vokjDbIqbB\r\n"
				+ "jvGURRb00nB5q8tOj6zCmIxNXCONFYrhf4pcoLCFj+RS7GjTX4P3Td/KvXp21WqN\r\n"
				+ "5QC6Qmb4ClHqZ0nh2qPlKJ07L1zqwMfzgRXZX7zlW4OaoKk12TJE9MYZTJbz3dyC\r\n"
				+ "7Dl6Z6o2PM7HEUXfw7ge6CFDTUV6/cQxfNieKrpVEsCOSj3XUf1hCscWBa7JApWe\r\n"
				+ "ejhz3YEqFHwprIPe21ZkPbVGz1hkhNCMfBFLw2ZJmiu/yyV9/LhefIul+4nJyIGE\r\n"
				+ "InYzbjnYPn+gI46i9I8S6v/WQYCJu+q1ZD4mHPnMDQKBgQD8d92fLDIZwxnbC99J\r\n"
				+ "sJemmhxvcX3F8PvfqG+JcyNf6dgiaIUECUnvfgDipdDmKzHjzD6OTKyerzRVmidj\r\n"
				+ "qpDkivHVhTbuqNZpQaWt+8tSpjxN5oZySfizeOeBLrphcay61h6q6ne3HHiWIa82\r\n"
				+ "6qDVQUe0qYb18SP/RLmofMizUwKBgQDbYyioRlvzgbwZ+lhXD0gimx1+QRxb90/d\r\n"
				+ "+9GiE3IbSCTWwz1efyOdDy+xCzh8/L7NX8E0ZQ6e4pmuBBnDt8aZ3boyRt0e0ui/\r\n"
				+ "Sepg20iCfBVOJ37i3n9GhBprkzzIPBb4YoPQQwyOvBgjYhak8hrpADJUWJeIYzi0\r\n"
				+ "pdGCQdrIbQKBgQCH9ZEe7/EHGJ8q7EjR6Uyxxpp7lXWzDCTH/HAcaCnrtAXV+c1w\r\n"
				+ "MARl+chGRh+qZCaY01v4y+fGCPo5AywlKyyeNwknAHdlrPzScCzl9gw3tRgSp4tN\r\n"
				+ "rvJEzF53ng92/H2VnEulpWDU9nsl9nviKhZ04ZPZAdaRScwl4v/McW6vywKBgHq0\r\n"
				+ "MDZF/AHrKvjgo242FuN8HHfUFPd/EIWY5bwf4i9OH4Sa+IUU2SdsKgF8xCBsAI+/\r\n"
				+ "ocEbUJ0fIlNI6dwkuoiukgiyx9QIpLLwtY1suFZ67jOjNX3QciFPm7NVS6a2rSZJ\r\n"
				+ "e24NQkXHAD0yDHY/DzwIpx2z2zUmQb4QDGktSh/VAoGBAI+93qCHtVU5rUeY3771\r\n"
				+ "V541cJqy1gKCob3w9wfhbCTM8ynVREZyUpljcnDBQ9H+gkaoHtPy000FlbUHNyBf\r\n"
				+ "K1ixXXvUZZEvN/8UyQp3VJipKbL+NDXaq8qE8eixPwkG1L2ebqlbjZsxKXKbotnp\r\n"
				+ "Jh+eDKPGD66PxfmLT9GtZxS+\r\n" //
				+ "-----END PRIVATE KEY-----\r\n";

		final var spi = new SamlSpi();
		File metaDataFile = new File("src/test/resource/metadata_sample.xml");
		final var cert = mock(X509Certificate.class);
		final var mockWebKey = mock(WebKey.class);
		when(mockWebKey.getCertificateChain()).thenReturn(new X509Certificate[] { cert });
		final var mockPkp = mock(IuPrivateKeyPrincipal.class);
		when(mockPkp.getEncryptJwk()).thenReturn(mockWebKey);
		when(mockPkp.getAlg()).thenReturn(WebKey.Algorithm.RS256);
		when(mockPkp.getJwk()).thenReturn(mockWebKey);
		final var uri = mock(URI.class);
		when(uri.toURL()).thenReturn(metaDataFile.toPath().toUri().toURL());

		config = getConfig(Arrays.asList(uri), "urn:iu:ess:kiosk", mockPkp);
		// final var postUri = URI.create("test://postUrl/");
		// provider = new SamlServiceProvider(postUri, "iu-saml-test");

	}

	@AfterEach
	public void tearDown() throws Exception {

	}

	@Test
	public void testGetAuthRequestSuccess() throws IOException {

		final var postUri = URI.create("test://postUrl/");
		final var realm = "iu-saml-test";
		final var relayState = IdGenerator.generateId();
		final var sessionId = IdGenerator.generateId();

		try (final var mockIuAuthenticationRealm = mockStatic(IuAuthenticationRealm.class)) {
			mockIuAuthenticationRealm.when(() -> IuAuthenticationRealm.of(realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm);
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
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><child>content</child></root>";
		Document doc = XmlDomUtil.parse("<foo bar='bam'><bar>baz</bar></foo>");

		try (final var mockIuAuthenticationRealm = mockStatic(IuAuthenticationRealm.class)) {
			final var mockXmlDomUtil = mockStatic(XmlDomUtil.class);
			mockXmlDomUtil.when(() -> XmlDomUtil.getContent(any())).thenReturn(xml);
			mockIuAuthenticationRealm.when(() -> IuAuthenticationRealm.of(realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm);
			URI authnRequest = samlprovider.getAuthnRequest(relayState, sessionId);
			assertNotNull(authnRequest);
			assertTrue(authnRequest.getQuery().startsWith("SAMLRequest"));
			assertTrue(authnRequest.getQuery().contains("RelayState"));

		}

	}

	@Test
	public void testGetDecrypterSuccess() {
		// assertNotNull(SamlServiceProvider.getDecrypter(provider.getClient()));
	}

	@Test 
	public void testSamlResponse() throws UnknownHostException, DecryptionException {

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
		
		final var mockResponse = mock(Response.class);
		when(mockResponse.getIssuer()).thenReturn(issuer);
		when(mockResponse.getIssueInstant()).thenReturn(issueInstant);
		when(mockResponse.getSignature()).thenReturn(mockSignature);
		
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
		final var mockConditionsForEncAssertion = mock(Conditions.class);
		Instant expectedNotBefore = Instant.parse("1984-08-26T10:01:30.043Z");
		when(mockConditions.getNotBefore()).thenReturn(expectedNotBefore);
		when(mockAssertion.getConditions()).thenReturn(mockConditions, mockConditionsForEncAssertion);
		final var mockAuthnStatement = mock(AuthnStatement.class);
		when(mockAuthnStatement.getAuthnInstant()).thenReturn(issueInstant);
		when(mockAssertion.getAuthnStatements()).thenReturn(Arrays.asList(mockAuthnStatement));

		final var mockSubject = mock( org.opensaml.saml.saml2.core.Subject.class);
		final var mockSubjectConfirmation = mock(SubjectConfirmation.class);
		when(mockSubjectConfirmation.getMethod()).thenReturn("urn:oasis:names:tc:SAML:2.0:cm:bearer");
		final var mockSubjectConfirmationData = mock(SubjectConfirmationData.class);
		when(mockSubjectConfirmation.getSubjectConfirmationData()).thenReturn(mockSubjectConfirmationData);
		when(mockSubject.getSubjectConfirmations()).thenReturn(Arrays.asList(mockSubjectConfirmation));

		when(mockAssertion.getSubject()).thenReturn(mockSubject);
		final var mockDecrypter = mock(Decrypter.class);
		
		when(mockAssertion.getSubject()).thenReturn(mockSubject);

		when(mockResponse.getAssertions()).thenReturn(Arrays.asList(mockAssertion));
		final var encryptedData = mock(EncryptedData.class);
		final var encryptedAssertion = mock(EncryptedAssertion.class);
		when(mockResponse.getEncryptedAssertions()).thenReturn(Arrays.asList(encryptedAssertion));
		when(encryptedAssertion.getEncryptedData()).thenReturn(encryptedData);
		when(mockDecrypter.decrypt(encryptedAssertion)).thenReturn(mockAssertion);

		
		final var mockIuTrustedIssuer = mock(IuTrustedIssuer.class);
		when(mockIuTrustedIssuer.getPrincipal(any())).thenReturn(id);
		
		try (final var mockIuAuthenticationRealm = mockStatic(IuAuthenticationRealm.class);
			final var mockAuthConfig = mockStatic(AuthConfig.class, CALLS_REAL_METHODS)) {
			final var mockXMLObjectSupport = mockStatic(XMLObjectSupport.class);
			final var mockProvider = mockStatic(SamlServiceProvider.class, CALLS_REAL_METHODS);
			
			mockProvider.when(() -> SamlServiceProvider.getDecrypter(any())).thenReturn(mockDecrypter);
			when(mockAttributePrincipalName.getName()).thenReturn(EDU_PERSON_PRINCIPAL_NAME_OID);
			when(mockAttributeDisplayName.getName()).thenReturn(DISPLAY_NAME_OID);
			when(mockAttributeEmail.getName()).thenReturn(MAIL_OID);


			mockXMLObjectSupport.when(()-> XMLObjectSupport.unmarshallFromInputStream(any(), any())).thenReturn(mockResponse);
			mockAuthConfig.when(() -> AuthConfig.get(IuTrustedIssuer.class)).thenReturn(Arrays.asList(mockIuTrustedIssuer));

			mockIuAuthenticationRealm.when(() -> IuAuthenticationRealm.of(realm)).thenReturn(config);
			SamlServiceProvider samlprovider = new SamlServiceProvider(postUri, realm);
			AuthConfig.register(samlprovider);
			AuthConfig.seal();
			
			MockedConstruction<ExplicitKeySignatureTrustEngine> signatureTrustEngine = mockConstruction(
					ExplicitKeySignatureTrustEngine.class, (mock, context) -> {
						CredentialResolver resolver = (CredentialResolver) context.arguments().get(0);
						when(mock.getCredentialResolver()).thenReturn(resolver);
						KeyInfoCredentialResolver keyResolver = (KeyInfoCredentialResolver) context.arguments().get(1);
						when(mock.getKeyInfoResolver()).thenReturn(keyResolver);
						when(mock.validate(any(), any())).thenReturn(true);
			});
	
			final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));
			IuTestLogger.allow(SamlServiceProvider.class.getName(), Level.FINE, "SAML2 .*");
			
			SamlPrincipal principal = samlprovider.verifyResponse(InetAddress.getByName("127.0.0.0"), samlResponse, sessionId);
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

		//private final String realm;
		private final String name = IdGenerator.generateId();
		private final Instant issuedAt = Instant.now();
		private final Instant authTime = issuedAt.truncatedTo(ChronoUnit.SECONDS);
		private final Instant expires = authTime.plusSeconds(5L);

		/*private TestId(String realm) {
			this.realm = realm;
		}*/

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
			return new Subject(true, Set.of(this), Set.of(), Set.of());
		}
	}

	static IuSamlServiceProviderMetadata getConfig(List<URI> metadataUris, String serviceProviderEntityId,
			IuPrivateKeyPrincipal pkp) {
		final var config = new IuSamlServiceProviderMetadata() {

			@Override
			public String getServiceProviderEntityId() {
				return serviceProviderEntityId;
			}

			@Override
			public List<URI> getAcsUris() {
				final var acsUri = URI.create("test://postUrl/");
				return IuException.unchecked(() -> Arrays.asList(acsUri));
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
			public Duration getAuthenticatedSessionTimeout() {
				return Duration.ofMinutes(2L);
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
