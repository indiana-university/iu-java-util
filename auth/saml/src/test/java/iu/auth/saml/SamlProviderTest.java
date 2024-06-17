package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;

import org.apache.xml.security.encryption.XMLEncryptionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
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
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.security.credential.CredentialResolver;
import org.opensaml.xmlsec.encryption.EncryptedData;
import org.opensaml.xmlsec.encryption.support.DecryptionException;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.X509Data;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.w3c.dom.Document;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.crypt.PemEncoded;
import edu.iu.test.IuTestLogger;

public class SamlProviderTest {
	private static X509Certificate certificate;
	private static SamlProvider provider;
	private static String privateKey;
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

		final var spi = new SamlConnectSpi();
		File metaDataFile = new File("src/test/resource/metadata_sample.xml");
		final var uri = mock(URI.class);
		when(uri.toURL()).thenReturn(metaDataFile.toPath().toUri().toURL());

		final var client = getClient(Arrays.asList(uri), "urn:iu:ess:kiosk");
		provider = (SamlProvider) spi.getSamlProvider(client);

	}

	/**
	 * Test to validate empty SAML response
	 * 
	 * @throws MalformedURLException
	 */
	@Test
	public void testEmptySamlResponse() throws MalformedURLException {
		IuTestLogger.allow("iu.auth.saml.SamlProvider", Level.FINE);
		{
			assertThrows(IllegalArgumentException.class, () -> provider.handleAcsPostResponse(InetAddress.getByName("127.0.0.0"),
					URI.create("test://postUrl/"), "", IdGenerator.generateId()));

		}
	}

	/**
	 * Test to validate signature mismatch
	 */
	@Test
	public void testSamlResponseSignatureMismatch() {
		IuTestLogger.allow("iu.auth.saml.SamlProvider", Level.FINE);

		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn("https://sp.identityserver");

		final var mockSignature = mock(Signature.class);

		final var mockResponse = mock(Response.class);
		when(mockResponse.getIssuer()).thenReturn(issuer);
		when(mockResponse.getSignature()).thenReturn(mockSignature);

		// test to verify security exception on signature mismatch
		try (final var mockXmlObjectSupport = mockStatic(XMLObjectSupport.class)) {
			mockXmlObjectSupport.when(() -> XMLObjectSupport.unmarshallFromInputStream(any(), any()))
					.thenReturn(mockResponse);
			final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));
			assertThrows(SecurityException.class, () -> provider.handleAcsPostResponse(InetAddress.getByName("127.0.0.0"),
					URI.create("test://postUrl/"), samlResponse, IdGenerator.generateId()));
		}

	}

	/**
	 * Test to validate empty assertions
	 */
	@Test
	public void testEmptyAssertionSamlResponse() {
		IuTestLogger.allow("iu.auth.saml.SamlProvider", Level.FINE);

		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn("https://sp.identityserver");

		final var mockSignature= mock(Signature.class);
		final var mockResponse = mock(Response.class);
		when(mockResponse.getIssuer()).thenReturn(issuer);
		when(mockResponse.getSignature()).thenReturn(mockSignature);
		{

			try (MockedConstruction<ExplicitKeySignatureTrustEngine> signatureTrustEngine = mockConstruction(
					ExplicitKeySignatureTrustEngine.class, (mock, context) -> {
						CredentialResolver resolver = (CredentialResolver) context.arguments().get(0);
						when(mock.getCredentialResolver()).thenReturn(resolver);
						KeyInfoCredentialResolver keyResolver = (KeyInfoCredentialResolver) context.arguments().get(1);
						when(mock.getKeyInfoResolver()).thenReturn(keyResolver);
						when(mock.validate(any(), any())).thenReturn(true);
					});

					var mockXmlObjectSupport = mockStatic(XMLObjectSupport.class)) {

				mockXmlObjectSupport.when(() -> XMLObjectSupport.unmarshallFromInputStream(any(), any()))
						.thenReturn(mockResponse);
				final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));
				assertThrows(IllegalArgumentException.class,
						() -> provider.handleAcsPostResponse(InetAddress.getByName("127.0.0.0"), URI.create("test://postUrl/"),
								samlResponse, IdGenerator.generateId()));
			}
		}

	}

	/**
	 * Test to verify valid assertion values and empty encrypted assertion in the
	 * response
	 */
	@Test
	public void testValidAssertionSamlResponse() {
		IuTestLogger.allow("iu.auth.saml.SamlProvider", Level.FINE);
		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn("https://sp.identityserver");

		final var mockSignature = mock(Signature.class);

		final var mockResponse = mock(Response.class);
		when(mockResponse.getIssuer()).thenReturn(issuer);
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
		when(mockResponse.getAssertions()).thenReturn(Arrays.asList(mockAssertion));

		{
			when(mockAttributePrincipalName.getFriendlyName()).thenReturn("eduPersonPrincipalName");
			when(mockAttributeDisplayName.getFriendlyName()).thenReturn("displayName");
			when(mockAttributeEmail.getFriendlyName()).thenReturn("mail");

			try (MockedConstruction<ExplicitKeySignatureTrustEngine> signatureTrustEngine = mockConstruction(
					ExplicitKeySignatureTrustEngine.class, (mock, context) -> {
						CredentialResolver resolver = (CredentialResolver) context.arguments().get(0);
						when(mock.getCredentialResolver()).thenReturn(resolver);
						KeyInfoCredentialResolver keyResolver = (KeyInfoCredentialResolver) context.arguments().get(1);
						when(mock.getKeyInfoResolver()).thenReturn(keyResolver);
						when(mock.validate(any(), any())).thenReturn(true);
					});

					var mockXmlObjectSupport = mockStatic(XMLObjectSupport.class)) {
				mockXmlObjectSupport.when(() -> XMLObjectSupport.unmarshallFromInputStream(any(), any()))
						.thenReturn(mockResponse);
				final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));
				assertThrows(IllegalArgumentException.class,
						() -> provider.handleAcsPostResponse(InetAddress.getByName("127.0.0.0"), URI.create("test://postUrl/"),
								samlResponse, IdGenerator.generateId()));
			}
		}
	}

	@Test
	public void testValidSamlRespone() throws XMLEncryptionException, DecryptionException, UnknownHostException {
		IuTestLogger.allow("iu.auth.saml.SamlProvider", Level.FINE);
		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn("https://sp.identityserver");

		final var signature = mock(Signature.class);

		final var mockResponse = mock(Response.class);
		when(mockResponse.getIssuer()).thenReturn(issuer);
		when(mockResponse.getSignature()).thenReturn(signature);
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
		Instant now = Instant.now();
		Instant issueInstant = now.plus(Duration.ofMinutes(5)).minusSeconds(5);
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
		when(mockAssertion.getAuthnStatements()).thenReturn(Arrays.asList(mockAuthnStatement));

		final var mockSubject = mock(Subject.class);
		final var mockSubjectConfirmation = mock(SubjectConfirmation.class);
		when(mockSubjectConfirmation.getMethod()).thenReturn("urn:oasis:names:tc:SAML:2.0:cm:bearer");
		final var mockSubjectConfirmationData = mock(SubjectConfirmationData.class);
		when(mockSubjectConfirmation.getSubjectConfirmationData()).thenReturn(mockSubjectConfirmationData);
		when(mockSubject.getSubjectConfirmations()).thenReturn(Arrays.asList(mockSubjectConfirmation));

		when(mockAssertion.getSubject()).thenReturn(mockSubject);

		when(mockResponse.getAssertions()).thenReturn(Arrays.asList(mockAssertion));
		final var encryptedData = mock(EncryptedData.class);
		final var encryptedAssertion = mock(EncryptedAssertion.class);
		when(mockResponse.getEncryptedAssertions()).thenReturn(Arrays.asList(encryptedAssertion));
		when(encryptedAssertion.getEncryptedData()).thenReturn(encryptedData);

		{
			try (final var mockProvider = mockStatic(SamlProvider.class, CALLS_REAL_METHODS)) {
				final var mockDecrypter = mock(Decrypter.class);
				when(mockDecrypter.decrypt(encryptedAssertion)).thenReturn(mockAssertion);

				mockProvider.when(() -> SamlProvider.getDecrypter(any())).thenReturn(mockDecrypter);
				when(mockAttributePrincipalName.getFriendlyName()).thenReturn("eduPersonPrincipalName");
				when(mockAttributeDisplayName.getFriendlyName()).thenReturn("displayName");
				when(mockAttributeEmail.getFriendlyName()).thenReturn("mail");

				try (MockedConstruction<ExplicitKeySignatureTrustEngine> signatureTrustEngine = mockConstruction(
						ExplicitKeySignatureTrustEngine.class, (mock, context) -> {
							CredentialResolver resolver = (CredentialResolver) context.arguments().get(0);
							when(mock.getCredentialResolver()).thenReturn(resolver);
							KeyInfoCredentialResolver keyResolver = (KeyInfoCredentialResolver) context.arguments()
									.get(1);
							when(mock.getKeyInfoResolver()).thenReturn(keyResolver);
							when(mock.validate(any(), any())).thenReturn(true);
						});

						var mockXmlObjectSupport = mockStatic(XMLObjectSupport.class)) {
					mockXmlObjectSupport.when(() -> XMLObjectSupport.unmarshallFromInputStream(any(), any()))
							.thenReturn(mockResponse);
					final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));

					SamlPrincipal principal = provider.authorize(InetAddress.getByName("127.0.0.0"),
							URI.create("test://postUrl/"), samlResponse, IdGenerator.generateId());
					assertNotNull(principal);
					assertEquals("testUser", principal.getName());
					assertEquals("testUser", principal.getDisplayName());
					assertEquals("testUser", principal.getEmailAddress());

				}
			}
		}

	}

	@Test
	public void testValidSamlRespone_2() throws XMLEncryptionException, DecryptionException, UnknownHostException {
		IuTestLogger.allow("iu.auth.saml.SamlProvider", Level.FINE);
		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn("https://sp.identityserver");

		final var mockSignature = mock(Signature.class);

		final var mockResponse = mock(Response.class);
		when(mockResponse.getIssuer()).thenReturn(issuer);
		when(mockResponse.getSignature()).thenReturn(mockSignature);
		final var mockXSString = mock(XSString.class);
		when(mockXSString.getValue()).thenReturn("testUser");

		final var mockAtributePrincipalName = mock(Attribute.class);
		final var mockAttributeDisplayName = mock(Attribute.class);

		final var mockAttributeEmail = mock(Attribute.class);

		when(mockAtributePrincipalName.getAttributeValues()).thenReturn(Arrays.asList(mockXSString));
		when(mockAttributeDisplayName.getAttributeValues()).thenReturn(Arrays.asList(mockXSString));
		when(mockAttributeEmail.getAttributeValues()).thenReturn(Arrays.asList(mockXSString));

		final var attributeStatement = mock(AttributeStatement.class);
		when(attributeStatement.getAttributes())
				.thenReturn(Arrays.asList(mockAtributePrincipalName, mockAttributeDisplayName, mockAttributeEmail));

		final var mockAssertion = mock(Assertion.class);
		when(mockAssertion.getAttributeStatements()).thenReturn(Arrays.asList(attributeStatement));
		when(mockAssertion.getVersion()).thenReturn(SAMLVersion.VERSION_20);
		Instant now = Instant.now();
		Instant issueInstant = now.plus(Duration.ofMinutes(5)).minusSeconds(5);
		when(mockAssertion.getIssueInstant()).thenReturn(issueInstant);
		final var mockIssuer = mock(Issuer.class);
		when(mockIssuer.getValue()).thenReturn("test");
		when(mockAssertion.getIssuer()).thenReturn(mockIssuer);

		final var mockConditions = mock(Conditions.class);
		Instant expectedNotBefore = Instant.parse("1984-08-26T10:01:30.043Z");
		when(mockConditions.getNotBefore()).thenReturn(expectedNotBefore);
		Conditions conditions = null;
		when(mockAssertion.getConditions()).thenReturn(mockConditions, conditions);
		final var mockAuthnStatement = mock(AuthnStatement.class);
		when(mockAssertion.getAuthnStatements()).thenReturn(Arrays.asList(mockAuthnStatement));

		final var mockSubject = mock(Subject.class);
		final var mockSubjectConfirmation = mock(SubjectConfirmation.class);
		when(mockSubjectConfirmation.getMethod()).thenReturn("urn:oasis:names:tc:SAML:2.0:cm:bearer");
		final var mockSubjectConfirmationData = mock(SubjectConfirmationData.class);
		when(mockSubjectConfirmation.getSubjectConfirmationData()).thenReturn(mockSubjectConfirmationData);
		when(mockSubject.getSubjectConfirmations()).thenReturn(Arrays.asList(mockSubjectConfirmation));

		when(mockAssertion.getSubject()).thenReturn(mockSubject);

		when(mockResponse.getAssertions()).thenReturn(Arrays.asList(mockAssertion));
		final var mockEncryptedData = mock(EncryptedData.class);
		final var mockEncryptedAssertion = mock(EncryptedAssertion.class);
		when(mockResponse.getEncryptedAssertions()).thenReturn(Arrays.asList(mockEncryptedAssertion));
		when(mockEncryptedAssertion.getEncryptedData()).thenReturn(mockEncryptedData);

		{
			try (final var mockProvider = mockStatic(SamlProvider.class, CALLS_REAL_METHODS)) {
				final var mockDecrypter = mock(Decrypter.class);
				when(mockDecrypter.decrypt(mockEncryptedAssertion)).thenReturn(mockAssertion);

				mockProvider.when(() -> SamlProvider.getDecrypter(any())).thenReturn(mockDecrypter);
				when(mockAtributePrincipalName.getName()).thenReturn(EDU_PERSON_PRINCIPAL_NAME_OID);
				when(mockAttributeDisplayName.getName()).thenReturn(DISPLAY_NAME_OID);
				when(mockAttributeEmail.getName()).thenReturn(MAIL_OID);

				try (MockedConstruction<ExplicitKeySignatureTrustEngine> signatureTrustEngine = mockConstruction(
						ExplicitKeySignatureTrustEngine.class, (mock, context) -> {
							CredentialResolver resolver = (CredentialResolver) context.arguments().get(0);
							when(mock.getCredentialResolver()).thenReturn(resolver);
							KeyInfoCredentialResolver keyResolver = (KeyInfoCredentialResolver) context.arguments()
									.get(1);
							when(mock.getKeyInfoResolver()).thenReturn(keyResolver);
							when(mock.validate(any(), any())).thenReturn(true);
						});

						var mockXmlObjectSupport = mockStatic(XMLObjectSupport.class)) {
					mockXmlObjectSupport.when(() -> XMLObjectSupport.unmarshallFromInputStream(any(), any()))
							.thenReturn(mockResponse);
					final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));

					SamlPrincipal principal = provider.authorize(InetAddress.getByName("127.0.0.0"),
							URI.create("test://postUrl/"), samlResponse, IdGenerator.generateId());
					assertNotNull(principal);
					assertEquals("testUser", principal.getName());
					assertEquals("testUser", principal.getDisplayName());
					assertEquals("testUser", principal.getEmailAddress());

				}
			}
		}

	}

	@Test
	public void testValidSamlResponeEmailAddressNull()
			throws XMLEncryptionException, DecryptionException, UnknownHostException {
		IuTestLogger.allow("iu.auth.saml.SamlProvider", Level.FINE);
		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn("https://sp.identityserver");

		final var signature = mock(Signature.class);

		final var mockResponse = mock(Response.class);
		when(mockResponse.getIssuer()).thenReturn(issuer);
		when(mockResponse.getSignature()).thenReturn(signature);
		final var mockXSAny = mock(XSAny.class);
		when(mockXSAny.getTextContent()).thenReturn("testUser");

		final var mockAttributePrincipalName = mock(Attribute.class);
		final var mockAttributeDisplayName = mock(Attribute.class);

		final var mockAttributeEmail = mock(Attribute.class);

		when(mockAttributePrincipalName.getAttributeValues()).thenReturn(Arrays.asList(mockXSAny));
		when(mockAttributeDisplayName.getAttributeValues()).thenReturn(Arrays.asList(mockXSAny));
		when(mockAttributeEmail.getAttributeValues()).thenReturn(Arrays.asList(mockXSAny));

		final var attributeStatement = mock(AttributeStatement.class);
		when(attributeStatement.getAttributes())
				.thenReturn(Arrays.asList(mockAttributePrincipalName, mockAttributeDisplayName, mockAttributeEmail));

		final var mockAssertion = mock(Assertion.class);
		when(mockAssertion.getAttributeStatements()).thenReturn(Arrays.asList(attributeStatement));
		when(mockAssertion.getVersion()).thenReturn(SAMLVersion.VERSION_20);
		Instant now = Instant.now();
		Instant issueInstant = now.plus(Duration.ofMinutes(5)).minusSeconds(5);
		when(mockAssertion.getIssueInstant()).thenReturn(issueInstant);
		final var mockIssuer = mock(Issuer.class);
		when(mockIssuer.getValue()).thenReturn("test");
		when(mockAssertion.getIssuer()).thenReturn(mockIssuer);

		final var mockConditions = mock(Conditions.class);
		Instant expectedNotBefore = Instant.parse("1984-08-26T10:01:30.043Z");

		Instant expectedNotOnOrAfter = now.plusMillis(Duration.ofMinutes(5).toMillis());
		when(mockConditions.getNotBefore()).thenReturn(expectedNotBefore);
		Conditions conditions = null;
		when(mockAssertion.getConditions()).thenReturn(mockConditions, conditions);
		final var mockAuthnStatement = mock(AuthnStatement.class);
		when(mockAssertion.getAuthnStatements()).thenReturn(Arrays.asList(mockAuthnStatement));

		final var mockSubject = mock(Subject.class);
		final var mockSubjectConfirmation = mock(SubjectConfirmation.class);
		when(mockSubjectConfirmation.getMethod()).thenReturn("urn:oasis:names:tc:SAML:2.0:cm:bearer");
		final var mockSubjectConfirmationData = mock(SubjectConfirmationData.class);
		when(mockSubjectConfirmationData.getNotBefore()).thenReturn(expectedNotBefore);
		when(mockSubjectConfirmationData.getNotOnOrAfter()).thenReturn(expectedNotOnOrAfter);

		when(mockSubjectConfirmation.getSubjectConfirmationData()).thenReturn(mockSubjectConfirmationData);
		when(mockSubject.getSubjectConfirmations()).thenReturn(Arrays.asList(mockSubjectConfirmation));

		when(mockAssertion.getSubject()).thenReturn(mockSubject);

		when(mockResponse.getAssertions()).thenReturn(Arrays.asList(mockAssertion));
		final var mockEncryptedData = mock(EncryptedData.class);
		final var mockEncryptedAssertion = mock(EncryptedAssertion.class);
		when(mockResponse.getEncryptedAssertions()).thenReturn(Arrays.asList(mockEncryptedAssertion));
		when(mockEncryptedAssertion.getEncryptedData()).thenReturn(mockEncryptedData);

		{
			try (final var mockProvider = mockStatic(SamlProvider.class, CALLS_REAL_METHODS)) {
				final var mockDecrypter = mock(Decrypter.class);
				when(mockDecrypter.decrypt(mockEncryptedAssertion)).thenReturn(mockAssertion);

				mockProvider.when(() -> SamlProvider.getDecrypter(any())).thenReturn(mockDecrypter);
				when(mockAttributePrincipalName.getName()).thenReturn(EDU_PERSON_PRINCIPAL_NAME_OID);
				when(mockAttributeDisplayName.getName()).thenReturn(DISPLAY_NAME_OID);

				try (MockedConstruction<ExplicitKeySignatureTrustEngine> signatureTrustEngine = mockConstruction(
						ExplicitKeySignatureTrustEngine.class, (mock, context) -> {
							CredentialResolver resolver = (CredentialResolver) context.arguments().get(0);
							when(mock.getCredentialResolver()).thenReturn(resolver);
							KeyInfoCredentialResolver keyResolver = (KeyInfoCredentialResolver) context.arguments()
									.get(1);
							when(mock.getKeyInfoResolver()).thenReturn(keyResolver);
							when(mock.validate(any(), any())).thenReturn(true);
						});

						var mockXmlObjectSupport = mockStatic(XMLObjectSupport.class)) {
					mockXmlObjectSupport.when(() -> XMLObjectSupport.unmarshallFromInputStream(any(), any()))
							.thenReturn(mockResponse);
					final var samlResponse = Base64.getEncoder().encodeToString(IuText.utf8(IdGenerator.generateId()));

					SamlPrincipal principal = provider.authorize(InetAddress.getByName("127.0.0.0"),
							URI.create("test://postUrl/"), samlResponse, IdGenerator.generateId());
					assertNotNull(principal);
					assertEquals("testUser", principal.getName());
					assertEquals("testUser", principal.getDisplayName());
					assertNull(principal.getEmailAddress());

				}
			}
		}

	}

	@SuppressWarnings("static-access")
	@Test
	public void testGetKeyInfoCredentialResolverSuccess() throws CertificateEncodingException {
		final var mockResponse = mock(Response.class);
		final var mockAssertion = mock(Assertion.class);
		final var mockSignature = mock(Signature.class);
		final var mockKeyInfo = mock(KeyInfo.class);
		final var mockX509Data = mock(X509Data.class);
		final var mockX509Certificate = mock(org.opensaml.xmlsec.signature.X509Certificate.class);
		when(mockX509Certificate.getValue()).thenReturn(IuText.base64(certificate.getEncoded()));
		when(mockX509Data.getX509Certificates()).thenReturn(Arrays.asList(mockX509Certificate));
		when(mockKeyInfo.getX509Datas()).thenReturn(Arrays.asList(mockX509Data));

		when(mockSignature.getKeyInfo()).thenReturn(mockKeyInfo);
		when(mockAssertion.getSignature()).thenReturn(mockSignature);
		when(mockResponse.getAssertions()).thenReturn(Arrays.asList(mockAssertion));
		KeyInfoCredentialResolver resolver = provider.getKeyInfoCredentialResolver(mockResponse);
		assertNotNull(resolver);
	}

	@SuppressWarnings("static-access")
	@Test
	public void testGetKeyInfoCredentialResolverNullKeyInfo() throws CertificateEncodingException {
		final var mockResponse = mock(Response.class);
		final var mockAssertion = mock(Assertion.class);
		final var mockSignature = mock(Signature.class);
		final var mockX509Data = mock(X509Data.class);
		final var mockX509Certificate = mock(org.opensaml.xmlsec.signature.X509Certificate.class);
		when(mockX509Certificate.getValue()).thenReturn(IuText.base64(certificate.getEncoded()));
		when(mockX509Data.getX509Certificates()).thenReturn(Arrays.asList(mockX509Certificate));
		when(mockAssertion.getSignature()).thenReturn(mockSignature);
		when(mockResponse.getAssertions()).thenReturn(Arrays.asList(mockAssertion));
		KeyInfoCredentialResolver resolver = provider.getKeyInfoCredentialResolver(mockResponse);
		assertNotNull(resolver);
	}

	@Test
	public void testGetSingleSignOnLocationInvalidSamlProtocol() throws IOException {
		IuTestLogger.allow("iu.auth.saml.SamlBuilder", Level.WARNING);
		final var spi = new SamlConnectSpi();
		File file = new File("src/test/resource/metadata_invalid_protocol.xml");
		final var uri = mock(URI.class);
		when(uri.toURL()).thenReturn(file.toPath().toUri().toURL());

		final var client = getClient(Arrays.asList(uri), "urn:iu:ess:trex");
		SamlProvider provider = (SamlProvider) spi.getSamlProvider(client);
		assertThrows(IllegalStateException.class, () -> provider.getSingleSignOnLocation("https://sp.identityserver"));
	}

	@Test
	public void testGetSingleSignOnLocationInvalidSamlBinding() throws IOException {
		IuTestLogger.allow("iu.auth.saml.SamlBuilder", Level.WARNING);
		final var spi = new SamlConnectSpi();
		File file = new File("src/test/resource/metadata_invalid_binding.xml");
		final var uri = mock(URI.class);
		when(uri.toURL()).thenReturn(file.toPath().toUri().toURL());

		final var client = getClient(Arrays.asList(uri), "urn:iu:esshr:test");
		SamlProvider provider = (SamlProvider) spi.getSamlProvider(client);
		assertThrows(IllegalStateException.class, () -> provider.getSingleSignOnLocation("https://sp.identityserver"));
	}

	@Test
	public void testGetAuthRequestSuccess() throws IOException {
		String resourceUri = "test://entry/show";

		assertThrows(IllegalArgumentException.class,
				() -> provider.getAuthRequest(URI.create("test://iumobile"), IdGenerator.generateId(), resourceUri));
		assertNotNull(provider.getAuthRequest(URI.create("test://postUrl/"), IdGenerator.generateId(), resourceUri));
	}

	@Test
	public void testGetAuthRequestStripXmlDeclaration() {
		String resourceUri = "test://entry/show";
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><child>content</child></root>";
		Document doc = XmlDomUtil.parse("<foo bar='bam'><bar>baz</bar></foo>");
	
		try (final var mockXmlDomUtil = mockStatic(XmlDomUtil.class)) {
			mockXmlDomUtil.when(() -> XmlDomUtil.getContent(any())).thenReturn(xml);

			assertNotNull(
					provider.getAuthRequest(URI.create("test://postUrl/"), IdGenerator.generateId(), resourceUri));
		}

	}

	@Test
	public void testGetDecrypterSuccess() {
		assertNotNull(SamlProvider.getDecrypter(provider.getClient()));
	}

	static IuSamlServiceProviderMetadata getClient(List<URI> metadataUris, String serviceProviderEntityId) {
		final var client = new IuSamlServiceProviderMetadata() {

			@Override
			public String getServiceProviderEntityId() {
				return serviceProviderEntityId;
			}

			@Override
			public String getPrivateKey() {
				return privateKey;
			}

			@Override
			public X509Certificate getCertificate() {
				return certificate;
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
			public URI getApplicationUri() {
				return IuException.unchecked(() -> new URI("test://"));
			}

		};
		return client;
	}

}
