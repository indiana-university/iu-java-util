package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.IuOpenIdProviderMetadata;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class OidcTokenIssuerIdentityTest {

	@Test
	void testNulls() {
		final var op = mock(IuOpenIdProviderMetadata.class, a -> null);
		final var issuer = new OidcTokenIssuer(op);
		assertNull(issuer.getAlg());
		assertNull(issuer.getEnc());
		assertNull(issuer.getEncryptAlg());
	}

	@Test
	void testAlgAndJwk() {
		final var op = mock(IuOpenIdProviderMetadata.class);
		final var alg = IuTest.rand(Algorithm.class);
		when(op.getIdTokenSigningAlgValuesSupported()).thenReturn(Set.of(alg));
		final var encryptAlg = IuTest.rand(Algorithm.class);
		when(op.getIdTokenEncryptionAlgValuesSupported()).thenReturn(Set.of(encryptAlg));
		final var enc = IuTest.rand(Encryption.class);
		when(op.getIdTokenEncryptionEncValuesSupported()).thenReturn(Set.of(enc));

		final var issuer = new OidcTokenIssuer(op);
		assertEquals(alg, issuer.getAlg());
		assertEquals(encryptAlg, issuer.getEncryptAlg());
		assertEquals(enc, issuer.getEnc());
		
		final var jwk = mock(WebKey.class);
		when(jwk.getUse()).thenReturn(Use.SIGN);

		final var jwks = URI.create(IdGenerator.generateId());
		when(op.getJwksUri()).thenReturn(jwks);

		try (final var mockWebKey = mockStatic(WebKey.class)) {
			mockWebKey.when(() -> WebKey.readJwks(jwks)).thenReturn(List.of(jwk));
			assertEquals(jwk, issuer.getJwk());
		}
	}

}
