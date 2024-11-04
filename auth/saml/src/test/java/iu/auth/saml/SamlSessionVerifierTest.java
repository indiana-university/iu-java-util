package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Objects;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.session.IuSession;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class SamlSessionVerifierTest {

	private URI postUri;
	private SamlServiceProvider provider;
	private MockedStatic<SamlServiceProvider> mockSamlServiceProvider;

	@BeforeEach
	public void setup() {
		postUri = URI.create(IdGenerator.generateId());
		provider = mock(SamlServiceProvider.class);
		mockSamlServiceProvider = mockStatic(SamlServiceProvider.class);
		mockSamlServiceProvider.when(() -> SamlServiceProvider.withBinding(postUri)).thenReturn(provider);
	}

	@AfterEach
	public void teardown() {
		mockSamlServiceProvider.close();
	}

	@Test
	public void testCreate() {
		new SamlSessionVerifier(postUri);
		mockSamlServiceProvider.verify(() -> SamlServiceProvider.withBinding(postUri));
	}

	@Test
	public void testInitRequestMissingEntryPoint() {
		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(NullPointerException.class, () -> verifier.initRequest(null, null));
		assertEquals("Missing returnUri", error.getMessage());
	}

	@Test
	public void testInitRequest() {
		final var entryPointUri = URI.create(IdGenerator.generateId());

		final var details = mock(SamlPreAuthentication.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		assertDoesNotThrow(() -> verifier.initRequest(session, entryPointUri));
		verify(session).clearDetail(SamlPreAuthentication.class);

		class StringTracker implements ArgumentMatcher<String> {
			private String value;

			@Override
			public boolean matches(String argument) {
				if (value == null) {
					value = Objects.requireNonNull(argument);
					return true;
				} else
					return value.equals(argument);
			}
		}

		final var relayStateTracker = new StringTracker();
		verify(details).setRelayState(argThat(relayStateTracker));
		assertNotNull(relayStateTracker.value);

		final var sessionIdTracker = new StringTracker();
		verify(details).setSessionId(argThat(sessionIdTracker));
		assertNotNull(sessionIdTracker.value);

		verify(provider).getAuthnRequest(argThat(relayStateTracker), argThat(sessionIdTracker));
	}

	@Test
	public void testVerifyResponseRequiresReturnUri() {
		final var details = mock(SamlPreAuthentication.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(NullPointerException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, null));
		assertEquals("Missing returnUri", error.getMessage());
	}

	@Test
	public void testVerifyResponseRequiresSessionId() {
		final var returnUri = URI.create(IdGenerator.generateId());

		final var details = mock(SamlPreAuthentication.class);
		when(details.getReturnUri()).thenReturn(returnUri);

		final var postAuth = mock(SamlPostAuthentication.class);

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(details);
		when(session.getDetail(SamlPostAuthentication.class)).thenReturn(postAuth);

		final var verifier = new SamlSessionVerifier(postUri);
		IuTestLogger.expect(SamlSessionVerifier.class.getName(), Level.INFO, "Invalid SAML Response",
				NullPointerException.class, e -> "Missing sessionId".equals(e.getMessage()));
		final var error = assertThrows(IuAuthenticationException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, null));
		verify(session).clearDetail(SamlPreAuthentication.class);
		verify(session).clearDetail(SamlPostAuthentication.class);
		verify(postAuth).setInvalid(true);
		assertEquals(returnUri, error.getLocation());
	}

	@Test
	public void testVerifyResponseRequiresRelayStateParam() {
		final var sessionId = IdGenerator.generateId();
		final var returnUri = URI.create(IdGenerator.generateId());

		final var details = mock(SamlPreAuthentication.class);
		when(details.getSessionId()).thenReturn(sessionId);
		when(details.getReturnUri()).thenReturn(returnUri);

		final var postAuth = mock(SamlPostAuthentication.class);

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(details);
		when(session.getDetail(SamlPostAuthentication.class)).thenReturn(postAuth);

		final var verifier = new SamlSessionVerifier(postUri);
		IuTestLogger.expect(SamlSessionVerifier.class.getName(), Level.INFO, "Invalid SAML Response",
				NullPointerException.class, e -> "Missing RelayState parameter".equals(e.getMessage()));
		final var error = assertThrows(IuAuthenticationException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, null));
		verify(session).clearDetail(SamlPreAuthentication.class);
		verify(session).clearDetail(SamlPostAuthentication.class);
		verify(postAuth).setInvalid(true);
		assertEquals(returnUri, error.getLocation());
	}

	@Test
	public void testVerifyResponseRequiresRelayStateInSession() {
		final var sessionId = IdGenerator.generateId();
		final var returnUri = URI.create(IdGenerator.generateId());
		final var relayState = IdGenerator.generateId();

		final var details = mock(SamlPreAuthentication.class);
		when(details.getSessionId()).thenReturn(sessionId);
		when(details.getReturnUri()).thenReturn(returnUri);

		final var postAuth = mock(SamlPostAuthentication.class);

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(details);
		when(session.getDetail(SamlPostAuthentication.class)).thenReturn(postAuth);

		final var verifier = new SamlSessionVerifier(postUri);
		IuTestLogger.expect(SamlSessionVerifier.class.getName(), Level.INFO, "Invalid SAML Response",
				NullPointerException.class, e -> "Missing relayState in session".equals(e.getMessage()));
		final var error = assertThrows(IuAuthenticationException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, relayState));
		verify(session).clearDetail(SamlPreAuthentication.class);
		verify(session).clearDetail(SamlPostAuthentication.class);
		verify(postAuth).setInvalid(true);
		assertEquals(returnUri, error.getLocation());
	}

	@Test
	public void testVerifyResponseRelayStateMismatch() {
		final var sessionId = IdGenerator.generateId();
		final var returnUri = URI.create(IdGenerator.generateId());
		final var relayState = IdGenerator.generateId();

		final var details = mock(SamlPreAuthentication.class);
		when(details.getSessionId()).thenReturn(sessionId);
		when(details.getReturnUri()).thenReturn(returnUri);
		when(details.getRelayState()).thenReturn(IdGenerator.generateId());

		final var postAuth = mock(SamlPostAuthentication.class);

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(details);
		when(session.getDetail(SamlPostAuthentication.class)).thenReturn(postAuth);

		final var verifier = new SamlSessionVerifier(postUri);
		IuTestLogger.expect(SamlSessionVerifier.class.getName(), Level.INFO, "Invalid SAML Response",
				IllegalArgumentException.class, e -> "RelayState mismatch".equals(e.getMessage()));
		final var error = assertThrows(IuAuthenticationException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, relayState));
		verify(session).clearDetail(SamlPreAuthentication.class);
		verify(session).clearDetail(SamlPostAuthentication.class);
		verify(postAuth).setInvalid(true);
		assertEquals(returnUri, error.getLocation());
	}

	@Test
	public void testVerifyResponseRequiresSamlResponse() {
		final var sessionId = IdGenerator.generateId();
		final var returnUri = URI.create(IdGenerator.generateId());
		final var relayState = IdGenerator.generateId();

		final var details = mock(SamlPreAuthentication.class);
		when(details.getSessionId()).thenReturn(sessionId);
		when(details.getReturnUri()).thenReturn(returnUri);
		when(details.getRelayState()).thenReturn(relayState);

		final var postAuth = mock(SamlPostAuthentication.class);

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(details);
		when(session.getDetail(SamlPostAuthentication.class)).thenReturn(postAuth);

		final var verifier = new SamlSessionVerifier(postUri);
		IuTestLogger.expect(SamlSessionVerifier.class.getName(), Level.INFO, "Invalid SAML Response",
				NullPointerException.class, e -> "Missing SAMLResponse parameter".equals(e.getMessage()));
		final var error = assertThrows(IuAuthenticationException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, relayState));
		verify(session).clearDetail(SamlPreAuthentication.class);
		verify(session).clearDetail(SamlPostAuthentication.class);
		verify(postAuth).setInvalid(true);
		assertEquals(returnUri, error.getLocation());
	}

	@Test
	public void testVerifyResponse() {
		final var sessionId = IdGenerator.generateId();
		final var returnUri = URI.create(IdGenerator.generateId());
		final var relayState = IdGenerator.generateId();
		final var samlResponse = IdGenerator.generateId();

		final var details = mock(SamlPreAuthentication.class);
		when(details.getSessionId()).thenReturn(sessionId);
		when(details.getReturnUri()).thenReturn(returnUri);
		when(details.getRelayState()).thenReturn(relayState);

		final var postAuth = mock(SamlPostAuthentication.class);

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(details);
		when(session.getDetail(SamlPostAuthentication.class)).thenReturn(postAuth);

		final var verifier = new SamlSessionVerifier(postUri);
		final var principal = mock(SamlPrincipal.class);
		when(provider.verifyResponse(IuWebUtils.getInetAddress("127.0.0.1"), samlResponse, sessionId))
				.thenReturn(principal);
		assertEquals(returnUri,
				assertDoesNotThrow(() -> verifier.verifyResponse(session, "127.0.0.1", samlResponse, relayState)));
		verify(session).clearDetail(SamlPreAuthentication.class);
		verify(session).clearDetail(SamlPostAuthentication.class);
		verify(principal).bind(postAuth);
	}

	@Test
	public void testGetPrincipalIdentityRejectsInvalidSession() {
		final var details = mock(SamlPostAuthentication.class);
		final var preAuthSession = mock(IuSession.class);
		final var postAuthSession = mock(IuSession.class);
		when(preAuthSession.getDetail(SamlPostAuthentication.class)).thenReturn(details);

		when(details.isInvalid()).thenReturn(true);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> verifier.getPrincipalIdentity(preAuthSession, postAuthSession));
		assertEquals("invalid session", error.getMessage());
	}

	@Test
	public void testGetPrincipalIdentityRejectsUnboundSession() {
		final var details = mock(SamlPostAuthentication.class);
		final var postAuthSession = mock(IuSession.class);
		when(postAuthSession.getDetail(SamlPostAuthentication.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> verifier.getPrincipalIdentity(null, postAuthSession));
		assertEquals("Session missing principal", error.getMessage());
	}

	@Test
	public void testGetPrincipalIdentity() {
		final var realm = IdGenerator.generateId();

		final var details = mock(SamlPostAuthentication.class);
		final var postAuthSession = mock(IuSession.class);
		when(postAuthSession.getDetail(SamlPostAuthentication.class)).thenReturn(details);

		when(provider.getRealm()).thenReturn(realm);

		final var verifier = new SamlSessionVerifier(postUri);
		final var id = mock(SamlPrincipal.class);
		try (final var mockSamlPrincipal = mockStatic(SamlPrincipal.class);
				final var mockIuPrincipalIdentity = mockStatic(IuPrincipalIdentity.class)) {
			mockSamlPrincipal.when(() -> SamlPrincipal.isBound(details)).thenReturn(true);
			mockSamlPrincipal.when(() -> SamlPrincipal.from(details)).thenReturn(id);
			assertSame(id, assertDoesNotThrow(() -> verifier.getPrincipalIdentity(null, postAuthSession)));
			mockIuPrincipalIdentity.verify(() -> IuPrincipalIdentity.verify(id, realm));
		}
	}

	@Test
	public void testGetPrincipalIdentityVerificationFailure() {
		final var realm = IdGenerator.generateId();

		final var details = mock(SamlPostAuthentication.class);
		final var postAuthSession = mock(IuSession.class);
		when(postAuthSession.getDetail(SamlPostAuthentication.class)).thenReturn(details);

		when(provider.getRealm()).thenReturn(realm);

		final var verifier = new SamlSessionVerifier(postUri);
		final var id = mock(SamlPrincipal.class);
		try (final var mockSamlPrincipal = mockStatic(SamlPrincipal.class);
				final var mockIuPrincipalIdentity = mockStatic(IuPrincipalIdentity.class)) {
			mockSamlPrincipal.when(() -> SamlPrincipal.isBound(details)).thenReturn(true);
			mockSamlPrincipal.when(() -> SamlPrincipal.from(details)).thenReturn(id);

			final var error = new IuAuthenticationException(null);
			mockIuPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(id, realm)).thenThrow(error);
			assertSame(error, assertThrows(IuAuthenticationException.class,
					() -> verifier.getPrincipalIdentity(null, postAuthSession)));
		}
	}

	@Test
	public void testGetPrincipalBindsPostAuth() {
		final var realm = IdGenerator.generateId();

        final var preAuth = mock(SamlPostAuthentication.class);
        final var preAuthSession = mock(IuSession.class);
        when(preAuthSession.getDetail(SamlPostAuthentication.class)).thenReturn(preAuth);
        
        final var postAuth = mock(SamlPostAuthentication.class);
        final var postAuthSession = mock(IuSession.class);
        when(postAuthSession.getDetail(SamlPostAuthentication.class)).thenReturn(postAuth);

        when(provider.getRealm()).thenReturn(realm);

        final var verifier = new SamlSessionVerifier(postUri);
        final var id = mock(SamlPrincipal.class);
        try (final var mockSamlPrincipal = mockStatic(SamlPrincipal.class);
                final var mockIuPrincipalIdentity = mockStatic(IuPrincipalIdentity.class)) {
            mockSamlPrincipal.when(() -> SamlPrincipal.isBound(preAuth)).thenReturn(true);
            mockSamlPrincipal.when(() -> SamlPrincipal.from(preAuth)).thenReturn(id);
            
            assertSame(id, assertDoesNotThrow(() -> verifier.getPrincipalIdentity(preAuthSession, postAuthSession)));
            verify(id).bind(postAuth);
        }
	}
	
}
