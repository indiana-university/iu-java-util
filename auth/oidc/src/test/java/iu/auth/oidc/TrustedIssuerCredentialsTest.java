package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.EnumSet;

import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.IuAuthenticationRealm;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.GrantType;
import edu.iu.auth.config.X500Utils;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class TrustedIssuerCredentialsTest {

	@Test
	public void testProperties() {
		final var commonName = IdGenerator.generateId();
		final var alg = IuTest.rand(Algorithm.class);
		final var jose = mock(WebCryptoHeader.class);
		when(jose.getAlgorithm()).thenReturn(alg);
		
		final var x5c = mock(X509Certificate.class);
		final var subject = mock(X500Principal.class);
		when(x5c.getSubjectX500Principal()).thenReturn(subject);

		final var notAfter = new Date();
		when(x5c.getNotAfter()).thenReturn(notAfter);

		final var jwk = mock(WebKey.class);
		final var builder = mock(WebKey.Builder.class);
		when(builder.cert(any(X509Certificate.class))).thenReturn(builder);
		when(builder.keyId(any())).thenReturn(builder);
		when(builder.build()).thenReturn(jwk);

		try (final var mockWebCertificateReference = mockStatic(WebCertificateReference.class);
				final var mockX500Utils = mockStatic(X500Utils.class);
				final var mockWebKey = mockStatic(WebKey.class)) {
			mockX500Utils.when(() -> X500Utils.getCommonName(subject)).thenReturn(commonName);
			mockWebCertificateReference.when(() -> WebCertificateReference.verify(jose))
					.thenReturn(new X509Certificate[] { x5c });
			mockWebKey.when(() -> WebKey.builder(alg)).thenReturn(builder);

			final var credentials = new TrustedIssuerCredentials(jose);
			verify(builder).keyId(commonName);
			verify(builder).cert(x5c);

			assertEquals(alg, credentials.getAlg());
			assertNull(credentials.getEncryptAlg());
			assertNull(credentials.getEnc());
			assertSame(jwk, credentials.getJwk());
			assertEquals(IuAuthenticationRealm.Type.CREDENTIALS, credentials.getType());
			assertEquals(AuthMethod.PRIVATE_KEY_JWT, credentials.getTokenEndpointAuthMethod());
			assertEquals(EnumSet.allOf(GrantType.class), credentials.getGrantTypes());
			assertEquals(notAfter.toInstant(), credentials.getExpires());
		}
	}

}
