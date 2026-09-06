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
package iu.oidc.provider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import edu.iu.IdGenerator;
import edu.iu.IuDataStore;
import edu.iu.IuDigest;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuProcess;
import edu.iu.IuText;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.X500Utils;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcProviderMetadata;
import edu.iu.oidc.config.IuOidcClientAuthorization;
import edu.iu.oidc.config.IuOidcClientEndpoint;
import edu.iu.oidc.config.IuOidcProviderConfiguration;
import edu.iu.oidc.config.IuOidcProviderReference;
import edu.iu.pki.IuPkiVerifier;
import edu.iu.test.IuTestLogger;
import iu.oidc.provider.ClientAuthenticator.Credential;
import iu.oidc.provider.ClientAuthenticator.Method;

@SuppressWarnings("javadoc")
public class ClientAuthenticatorTest {

	static {
		edu.iu.crypt.Init.init();
		iu.jwt.spi.Init.init();
	}

	private static final URI ISSUER = URI.create("https://example.iu.edu/oidc");
	private static final URI TOKEN_ENDPOINT = URI.create("https://example.iu.edu/oidc/token");

	/** Client IDs are URIs, since an assertion names one as its issuer. */
	private static final String CLIENT_ID = "https://client.example.iu.edu";

	private static final Duration TTL = Duration.ofMinutes(15L);

	private final IuOidcProviderReference reference = mock(IuOidcProviderReference.class);
	private final IuDataStore dataStore = mock(IuDataStore.class);

	private ClientAuthenticator authenticator;

	@BeforeEach
	void setup() {
		IuTestLogger.allow(ClientAuthenticator.class.getName(), Level.FINE);
		IuTestLogger.allow(ClientAuthenticator.class.getName(), Level.FINER);

		final var metadata = mock(IuOidcProviderMetadata.class);
		when(metadata.getIssuer()).thenReturn(ISSUER);

		final var configuration = mock(IuOidcProviderConfiguration.class);
		when(configuration.getMetadata()).thenReturn(metadata);

		when(reference.getConfiguration()).thenReturn(configuration);
		when(reference.getDataStore()).thenReturn(dataStore);

		authenticator = new ClientAuthenticator(reference);
	}

	/** Answers a registered shared secret, which is a RAW key. */
	private static WebKey secretKey(String secret) {
		return WebKey.builder(Algorithm.HS256).keyId(CLIENT_ID).key(IuText.utf8(secret)).build();
	}

	/** Answers a registered asymmetric key. */
	private static WebKey signingKey() {
		return WebKey.builder(Algorithm.ES256).keyId(IdGenerator.generateId()).ephemeral().build();
	}

	/** Answers one registration record. */
	private static IuOidcClientAuthorization authorization(WebKey jwk) {
		final var authorization = mock(IuOidcClientAuthorization.class);
		when(authorization.getJwk()).thenReturn(jwk);
		when(authorization.getAssertionTtl()).thenReturn(TTL);
		return authorization;
	}

	private static IuOidcClientEndpoint endpoint(Iterable<IuOidcClientAuthorization> authorizations) {
		final var endpoint = mock(IuOidcClientEndpoint.class);
		when(endpoint.getAuthorization()).thenReturn(authorizations);
		return endpoint;
	}

	private static IuOidcClientEndpoint endpoint(IuOidcClientAuthorization authorization) {
		return endpoint(List.of(authorization));
	}

	/** Signs a client assertion naming itself as issuer and subject. */
	private static String assertion(WebKey key, Algorithm algorithm, String subject, URI audience, boolean withJti) {
		final var builder = WebToken.builder() //
				.iss(URI.create(CLIENT_ID)) //
				.sub(subject) //
				.aud(audience) //
				.iat() //
				.exp(Instant.now().plusSeconds(60L));

		if (withJti)
			builder.jti();

		return builder.build().sign("JWT", algorithm, key);
	}

	private static String assertion(WebKey key, Algorithm algorithm) {
		return assertion(key, algorithm, CLIENT_ID, TOKEN_ENDPOINT, true);
	}

	@Test
	void testAReferenceIsRequired() {
		assertEquals("Missing provider reference",
				assertThrows(NullPointerException.class, () -> new ClientAuthenticator(null)).getMessage());
	}

	@Test
	void testTheAudienceIsThisProvidersOwnTokenEndpoint() {
		assertEquals(TOKEN_ENDPOINT, authenticator.tokenEndpoint());
	}

	@Test
	void testASecretMustBeRegistered() {
		final var jwk = mock(WebKey.class);
		assertEquals("Missing client secret",
				assertThrows(IllegalStateException.class, () -> ClientAuthenticator.verifySecret(jwk, "x")).getMessage());

		when(jwk.getKey()).thenReturn(new byte[0]);
		assertEquals("Missing client secret",
				assertThrows(IllegalStateException.class, () -> ClientAuthenticator.verifySecret(jwk, "x")).getMessage());
	}

	@Test
	void testASecretIsComparedWholeOrNotAtAll() {
		final var jwk = secretKey("hunter2");
		assertTrue(ClientAuthenticator.verifySecret(jwk, "hunter2"));
		assertFalse(ClientAuthenticator.verifySecret(jwk, "hunter3"));
		assertFalse(ClientAuthenticator.verifySecret(jwk, "hunter2 "));
	}

	@Test
	void testARevocationListIsWhatMakesARegistrationAnAuthority() {
		// stubbed explicitly: a mock answers an unstubbed Iterable with an empty one
		// rather than null, so the unregistered case has to be asked for
		final var authorization = mock(IuOidcClientAuthorization.class);
		when(authorization.getCrl()).thenReturn(null);
		assertFalse(ClientAuthenticator.hasCrl(authorization));

		when(authorization.getCrl()).thenReturn(List.of());
		assertFalse(ClientAuthenticator.hasCrl(authorization));

		when(authorization.getCrl()).thenReturn(List.of(mock(X509CRL.class)));
		assertTrue(ClientAuthenticator.hasCrl(authorization));
	}

	@Test
	void testARegistrationWithNoExpiryNeverExpires() {
		final var authorization = mock(IuOidcClientAuthorization.class);
		assertTrue(ClientAuthenticator.isActive(authorization));

		when(authorization.getExpires()).thenReturn(Instant.now().plusSeconds(60L));
		assertTrue(ClientAuthenticator.isActive(authorization));

		when(authorization.getExpires()).thenReturn(Instant.now().minusSeconds(1L));
		assertFalse(ClientAuthenticator.isActive(authorization));
	}

	@Test
	void testAnEndpointRegisteringNothingAcceptsNothing() {
		final var endpoint = endpoint((Iterable<IuOidcClientAuthorization>) null);
		assertEquals("No client authorization configured for endpoint", assertThrows(SecurityException.class,
				() -> authenticator.authenticate(endpoint, CLIENT_ID, Credential.basic("x"))).getMessage());
	}

	@Test
	void testANullRecordIsSkipped() {
		final var endpoint = endpoint(Arrays.asList((IuOidcClientAuthorization) null));
		assertEquals("No client authorization configured for endpoint", assertThrows(SecurityException.class,
				() -> authenticator.authenticate(endpoint, CLIENT_ID, null)).getMessage());
	}

	@Test
	void testAnExpiredRegistrationIsRefused() {
		final var authorization = authorization(secretKey("hunter2"));
		when(authorization.getExpires()).thenReturn(Instant.now().minusSeconds(1L));

		final var endpoint = endpoint(authorization);
		assertEquals("Client registration has expired", assertThrows(SecurityException.class,
				() -> authenticator.authenticate(endpoint, CLIENT_ID, Credential.basic("hunter2"))).getMessage());
	}

	@Test
	void testARecordWithNoKeyIsAPublicRegistration() {
		final var endpoint = endpoint(authorization(null));
		assertSame(Method.NONE, authenticator.authenticate(endpoint, CLIENT_ID, null));
	}

	@Test
	void testAPublicRegistrationRefusesACredential() {
		final var endpoint = endpoint(authorization(null));
		assertEquals("Client presented a credential but is registered public", assertThrows(SecurityException.class,
				() -> authenticator.authenticate(endpoint, CLIENT_ID, Credential.post("x"))).getMessage());
	}

	@Test
	void testARegisteredCredentialRefusesAPublicRequest() {
		final var endpoint = endpoint(authorization(secretKey("hunter2")));
		assertEquals("Client is registered with a credential but presented none",
				assertThrows(SecurityException.class, () -> authenticator.authenticate(endpoint, CLIENT_ID, null))
						.getMessage());
	}

	@Test
	void testASecretIsAcceptedHoweverItArrived() {
		final var endpoint = endpoint(authorization(secretKey("hunter2")));
		assertSame(Method.CLIENT_SECRET_BASIC, authenticator.authenticate(endpoint, CLIENT_ID, Credential.basic("hunter2")));
		assertSame(Method.CLIENT_SECRET_POST, authenticator.authenticate(endpoint, CLIENT_ID, Credential.post("hunter2")));
	}

	@Test
	void testAWrongSecretIsRefused() {
		final var endpoint = endpoint(authorization(secretKey("hunter2")));
		assertEquals("Invalid client secret", assertThrows(SecurityException.class,
				() -> authenticator.authenticate(endpoint, CLIENT_ID, Credential.basic("hunter3"))).getMessage());
	}

	@Test
	void testAKeyRegistrationRefusesASecret() {
		final var endpoint = endpoint(authorization(signingKey()));
		assertEquals("Client is registered for private_key_jwt and presented a secret",
				assertThrows(SecurityException.class,
						() -> authenticator.authenticate(endpoint, CLIENT_ID, Credential.basic("hunter2"))).getMessage());
	}

	@Test
	void testTheFirstRecordThatAcceptsWins() {
		// an old and a new credential are both honored while one is rotated in
		final var endpoint = endpoint(List.of(authorization(secretKey("old")), authorization(secretKey("new"))));
		assertSame(Method.CLIENT_SECRET_BASIC, authenticator.authenticate(endpoint, CLIENT_ID, Credential.basic("new")));
	}

	@Test
	void testTheFirstFailureIsThrownCarryingTheRest() {
		// a rejection reads as why the most likely record refused, without losing
		// what the others said
		final var expired = authorization(secretKey("old"));
		when(expired.getExpires()).thenReturn(Instant.now().minusSeconds(1L));

		final var endpoint = endpoint(List.of(expired, authorization(secretKey("new"))));
		final var refusal = assertThrows(SecurityException.class,
				() -> authenticator.authenticate(endpoint, CLIENT_ID, Credential.basic("wrong")));

		assertEquals("Client registration has expired", refusal.getMessage());
		assertEquals(1, refusal.getSuppressed().length);
		assertEquals("Invalid client secret", refusal.getSuppressed()[0].getMessage());
	}

	@Test
	void testAMalformedAssertionIsRefused() {
		final var endpoint = endpoint(authorization(secretKey("hunter2")));
		assertEquals("Malformed client assertion", assertThrows(SecurityException.class,
				() -> authenticator.authenticate(endpoint, CLIENT_ID, Credential.assertion("not a jwt"))).getMessage());
	}

	@Test
	void testASecretSignedAssertionIsClientSecretJwt() {
		final var jwk = secretKey("hunter2");
		final var endpoint = endpoint(authorization(jwk));

		assertSame(Method.CLIENT_SECRET_JWT,
				authenticator.authenticate(endpoint, CLIENT_ID, Credential.assertion(assertion(jwk, Algorithm.HS256))));
		verify(dataStore).put(any(), any(), org.mockito.ArgumentMatchers.eq(TTL));
	}

	@Test
	void testAKeySignedAssertionIsPrivateKeyJwt() {
		final var jwk = signingKey();
		final var endpoint = endpoint(authorization(jwk));

		assertSame(Method.PRIVATE_KEY_JWT,
				authenticator.authenticate(endpoint, CLIENT_ID, Credential.assertion(assertion(jwk, Algorithm.ES256))));
	}

	@Test
	void testAnAssertionThatDoesntVerifyIsRefused() {
		final var elsewhere = WebKey.builder(Algorithm.ES256).keyId(IdGenerator.generateId()).ephemeral().build();
		final var endpoint = endpoint(authorization(signingKey()));

		assertEquals("Client assertion did not verify", assertThrows(SecurityException.class, () -> authenticator
				.authenticate(endpoint, CLIENT_ID, Credential.assertion(assertion(elsewhere, Algorithm.ES256))))
				.getMessage());
	}

	@Test
	void testAnAssertionLifetimeIsRequired() {
		final var jwk = secretKey("hunter2");
		final var authorization = authorization(jwk);
		when(authorization.getAssertionTtl()).thenReturn(null);

		final var endpoint = endpoint(authorization);
		final var credential = Credential.assertion(assertion(jwk, Algorithm.HS256));
		assertEquals("Missing assertion TTL", assertThrows(NullPointerException.class,
				() -> authenticator.authenticate(endpoint, CLIENT_ID, credential)).getMessage());
	}

	@Test
	void testAnAssertionAddressedElsewhereIsRefused() {
		final var jwk = secretKey("hunter2");
		final var endpoint = endpoint(authorization(jwk));
		final var credential = Credential
				.assertion(assertion(jwk, Algorithm.HS256, CLIENT_ID, URI.create("https://elsewhere.iu.edu"), true));

		assertEquals("Client assertion claims did not validate", assertThrows(SecurityException.class,
				() -> authenticator.authenticate(endpoint, CLIENT_ID, credential)).getMessage());
	}

	@Test
	void testAnAssertionMustNameItselfAsSubject() {
		final var jwk = secretKey("hunter2");
		final var endpoint = endpoint(authorization(jwk));
		final var credential = Credential
				.assertion(assertion(jwk, Algorithm.HS256, "somebody-else", TOKEN_ENDPOINT, true));

		assertEquals("Client assertion subject is not the client", assertThrows(SecurityException.class,
				() -> authenticator.authenticate(endpoint, CLIENT_ID, credential)).getMessage());
	}

	/**
	 * Answers a key carrying real certificates, since the crypt implementation
	 * refuses a mocked one &mdash; it casts every key it is handed to its own type.
	 *
	 * @param caSigned true for a two-certificate chain issued by an authority;
	 *                 false for a single self-signed certificate
	 * @return key whose {@link WebKey#getCertificateChain() chain} is real
	 */
	private static WebKey certified(boolean caSigned) {
		final var id = IdGenerator.generateId();
		final var subject = "/CN=" + id.replaceAll("([+=/])", "\\\\$1");
		final var key = WebKey.builder(Algorithm.ES256).keyId(id).ephemeral().build();
		final var privateKey = key.getPrivateKey();
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);

		final String pem;
		if (!caSigned)
			pem = IuProcess.exec("openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
					"-subj", subject, //
					"-addext", "basicConstraints=CA:false", //
					"-addext", "keyUsage=" + X500Utils.keyUsage(key));
		else {
			final var caId = IdGenerator.generateId();
			final var caKey = WebKey.builder(Algorithm.ES256).keyId(caId).ephemeral().build();
			final var caPrivateKeyFile = IuProcess.temp(PemEncoded::print, caKey.getPrivateKey());
			final var caCert = IuProcess.exec("openssl", "req", "-x509", "-key", caPrivateKeyFile.toString(), "-days",
					"1", //
					"-subj", "/CN=" + caId.replaceAll("([+=/])", "\\\\$1"), //
					"-addext", "basicConstraints=critical,CA:true,pathlen:0", //
					"-addext", "keyUsage=keyCertSign,cRLSign");
			final var caCertFile = IuProcess.temp((out, value) -> out.print(value), caCert);

			final var csr = IuProcess.exec("openssl", "req", "-new", "-key", privateKeyFile.toString(), //
					"-subj", subject, //
					"-addext", "basicConstraints=CA:false", //
					"-addext", "keyUsage=" + X500Utils.keyUsage(key));
			final var csrFile = IuProcess.temp((out, value) -> out.print(value), csr);
			final var leafCert = IuProcess.exec("openssl", "x509", "-req", "-in", csrFile.toString(), //
					"-CA", caCertFile.toString(), "-CAkey", caPrivateKeyFile.toString(), //
					"-set_serial", "1", "-days", "1", "-copy_extensions", "copyall");

			pem = leafCert + System.lineSeparator() + caCert;
		}
		IuProcess.deleteTempFiles();

		return WebKey.builder(key.getType()) //
				.keyId(id) //
				.key(privateKey) //
				.key(key.getPublicKey()) //
				.algorithm(key.getAlgorithm()) //
				.pem(pem) //
				.build();
	}

	/** Registers a revocation list, which is what makes a record an authority. */
	private static IuOidcClientAuthorization authority(WebKey jwk) {
		final var authorization = authorization(jwk);
		when(authorization.getCrl()).thenReturn(List.of(mock(X509CRL.class)));
		return authorization;
	}

	@Test
	void testACaRegistrationVerifiesTheAssertionsOwnChain() {
		// a chain of more than one certificate is carried by the assertion as x5c, and
		// the key the signature verifies against is the one resolved from it
		final var signerKey = certified(true);
		final var endpoint = endpoint(authority(signingKey()));

		final var verifier = mock(IuPkiVerifier.class);
		when(reference.getCertificateAuthorityVerifier(any())).thenReturn(verifier);

		assertSame(Method.PRIVATE_KEY_JWT, authenticator.authenticate(endpoint, CLIENT_ID,
				Credential.assertion(assertion(signerKey, Algorithm.ES256))));

		final var verified = ArgumentCaptor.forClass(WebKey.class);
		verify(verifier).verify(verified.capture());
		assertArrayEquals(signerKey.getCertificateChain(), verified.getValue().getCertificateChain());
	}

	@Test
	void testACaRegistrationRequiresTheAssertionToBringACertificate() {
		final var real = signingKey();
		final var endpoint = endpoint(authority(signingKey()));
		final var credential = Credential.assertion(assertion(real, Algorithm.ES256));

		try (final var header = mockStatic(WebCryptoHeader.class)) {
			header.when(() -> WebCryptoHeader.getProtectedHeader(any())).thenReturn(mock(WebCryptoHeader.class));

			// nothing resolvable in the header at all
			header.when(() -> WebCryptoHeader.verify(any())).thenReturn(null);
			assertEquals("Client assertion is missing the x5c header a CA registration verifies", assertThrows(
					SecurityException.class, () -> authenticator.authenticate(endpoint, CLIENT_ID, credential))
					.getMessage());

			// resolvable, but carrying no chain to verify
			header.when(() -> WebCryptoHeader.verify(any())).thenReturn(real);
			assertEquals("Client assertion is missing the x5c header a CA registration verifies", assertThrows(
					SecurityException.class, () -> authenticator.authenticate(endpoint, CLIENT_ID, credential))
					.getMessage());
		}
	}

	@Test
	void testAChainTheAuthorityDoesntTrustIsRefused() {
		final var signerKey = certified(true);
		final var endpoint = endpoint(authority(signingKey()));

		final var verifier = mock(IuPkiVerifier.class);
		doThrow(new IllegalArgumentException("not in the chain")).when(verifier).verify(any());
		when(reference.getCertificateAuthorityVerifier(any())).thenReturn(verifier);

		final var credential = Credential.assertion(assertion(signerKey, Algorithm.ES256));
		assertEquals("Client certificate is not trusted by the registered authority",
				assertThrows(SecurityException.class, () -> authenticator.authenticate(endpoint, CLIENT_ID, credential))
						.getMessage());
	}

	@Test
	void testASelfSignedRegistrationVerifiesTheRegisteredKey() {
		// no revocation list, one certificate: the registered key is trusted as itself
		final var jwk = certified(false);
		final var endpoint = endpoint(authorization(jwk));

		final var verifier = mock(IuPkiVerifier.class);
		when(reference.getSelfSignedVerifier(any())).thenReturn(verifier);

		assertSame(Method.PRIVATE_KEY_JWT,
				authenticator.authenticate(endpoint, CLIENT_ID, Credential.assertion(assertion(jwk, Algorithm.ES256))));

		verify(verifier).verify(jwk);
	}

	@Test
	void testMoreThanOneCertificateNeedsARevocationList() {
		final var jwk = certified(true);
		final var endpoint = endpoint(authorization(jwk));
		final var credential = Credential.assertion(assertion(jwk, Algorithm.ES256));

		assertEquals(
				"Client certificate must be a single self-signed certificate when no revocation list is registered",
				assertThrows(IllegalArgumentException.class,
						() -> authenticator.authenticate(endpoint, CLIENT_ID, credential)).getMessage());
	}

	@Test
	void testAThumbprintTheRegistrationCantCheckIsRefused() {
		// the assertion claims a specific certificate signed it; a registration with
		// none can't check that, and must not accept it silently
		final var signed = certified(false);
		final var endpoint = endpoint(authorization(signingKey()));
		final var credential = Credential.assertion(assertion(signed, Algorithm.ES256));

		assertEquals("Client assertion asserts a certificate thumbprint but the registration carries none",
				assertThrows(SecurityException.class, () -> authenticator.authenticate(endpoint, CLIENT_ID, credential))
						.getMessage());
	}

	@Test
	void testAThumbprintMismatchIsRefused() {
		// two certificates, so the x5t the assertion carries names one the registration
		// doesn't
		final var signed = certified(false);
		final var registered = certified(false);

		final var endpoint = endpoint(authorization(registered));
		when(reference.getSelfSignedVerifier(any())).thenReturn(mock(IuPkiVerifier.class));

		final var credential = Credential.assertion(assertion(signed, Algorithm.ES256));
		assertEquals("Client certificate thumbprint mismatch",
				assertThrows(SecurityException.class, () -> authenticator.authenticate(endpoint, CLIENT_ID, credential))
						.getMessage());
	}

	@Test
	void testASha256ThumbprintIsCheckedToo() {
		// a signature carries x5t rather than x5t#S256, so the stronger thumbprint is
		// only seen from an assertion built elsewhere
		final var registered = certified(false);
		final var cert = registered.getCertificateChain()[0];

		final var endpoint = endpoint(authorization(registered));
		when(reference.getSelfSignedVerifier(any())).thenReturn(mock(IuPkiVerifier.class));

		final var header = mock(WebCryptoHeader.class);
		when(header.getCertificateSha256Thumbprint()).thenReturn(IuDigest.sha256(IuText.utf8("somebody-else")));

		final var credential = Credential.assertion(assertion(registered, Algorithm.ES256));
		try (final var headers = mockStatic(WebCryptoHeader.class)) {
			headers.when(() -> WebCryptoHeader.getProtectedHeader(any())).thenReturn(header);

			assertEquals("Client certificate thumbprint mismatch", assertThrows(SecurityException.class,
					() -> authenticator.authenticate(endpoint, CLIENT_ID, credential)).getMessage());

			// and passes when it matches what the registration carries
			when(header.getCertificateSha256Thumbprint())
					.thenReturn(IuDigest.sha256(IuException.unchecked(cert::getEncoded)));
			assertSame(Method.PRIVATE_KEY_JWT, authenticator.authenticate(endpoint, CLIENT_ID, credential));
		}
	}

	@Test
	void testAnAssertionMustCarryAJti() {
		final var jwk = secretKey("hunter2");
		final var endpoint = endpoint(authorization(jwk));
		final var credential = Credential.assertion(assertion(jwk, Algorithm.HS256, CLIENT_ID, TOKEN_ENDPOINT, false));

		assertEquals("Client assertion is missing the jti claim replay protection needs",
				assertThrows(SecurityException.class, () -> authenticator.authenticate(endpoint, CLIENT_ID, credential))
						.getMessage());
	}

	@Test
	void testAnAssertionIsGoodOnce() {
		final var jwk = secretKey("hunter2");
		final var endpoint = endpoint(authorization(jwk));
		final var credential = Credential.assertion(assertion(jwk, Algorithm.HS256));

		final var used = Instant.now();
		when(dataStore.get(any())).thenReturn(IuText.utf8(used.toString()));

		assertEquals("jti was previously used at " + used, assertThrows(SecurityException.class,
				() -> authenticator.authenticate(endpoint, CLIENT_ID, credential)).getMessage());
	}

	@Test
	void testARecordOlderThanTheLifetimeDoesntRefuse() {
		final var jwk = secretKey("hunter2");
		final var endpoint = endpoint(authorization(jwk));
		when(dataStore.get(any())).thenReturn(IuText.utf8(Instant.now().minus(TTL).minusSeconds(1L).toString()));

		assertSame(Method.CLIENT_SECRET_JWT,
				authenticator.authenticate(endpoint, CLIENT_ID, Credential.assertion(assertion(jwk, Algorithm.HS256))));
	}

	@Test
	void testAnUnreadableReplayRecordAssumesReplay() {
		final var jwk = secretKey("hunter2");
		final var endpoint = endpoint(authorization(jwk));
		final var credential = Credential.assertion(assertion(jwk, Algorithm.HS256));
		when(dataStore.get(any())).thenReturn(IuText.utf8("not an instant"));

		assertEquals("Invalid replay cutoff in data store, assuming jti was previously used",
				assertThrows(SecurityException.class, () -> authenticator.authenticate(endpoint, CLIENT_ID, credential))
						.getMessage());
	}

}
