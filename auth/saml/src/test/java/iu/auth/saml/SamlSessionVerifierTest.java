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
import java.time.Instant;
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
		assertEquals("Missing entryPointUri", error.getMessage());
	}

	@Test
	public void testInitRequestRejectRelayState() {
		final var details = mock(SamlSessionDetails.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var relayState = IdGenerator.generateId();
		when(details.getRelayState()).thenReturn(relayState);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> verifier.initRequest(session, URI.create(IdGenerator.generateId())));
		assertEquals("relayState is already initialized", error.getMessage());
	}

	@Test
	public void testInitRequestRejectSessionId() {
		final var details = mock(SamlSessionDetails.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var sessionId = IdGenerator.generateId();
		when(details.getSessionId()).thenReturn(sessionId);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> verifier.initRequest(session, URI.create(IdGenerator.generateId())));
		assertEquals("sessionId is already initialized", error.getMessage());
	}

	@Test
	public void testInitRequestRejectEntryPointUri() {
		final var details = mock(SamlSessionDetails.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var entryPointUri = URI.create(IdGenerator.generateId());
		when(details.getEntryPointUri()).thenReturn(entryPointUri);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> verifier.initRequest(session, URI.create(IdGenerator.generateId())));
		assertEquals("entryPointUri is already initialized", error.getMessage());
	}

	@Test
	public void testInitRequestRejectInvalidSession() {
		final var details = mock(SamlSessionDetails.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		when(details.isInvalid()).thenReturn(true);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> verifier.initRequest(session, URI.create(IdGenerator.generateId())));
		assertEquals("invalid session", error.getMessage());
	}

	@Test
	public void testInitRequestRejectBoundPrincipal() {
		final var details = mock(SamlSessionDetails.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		when(details.getName()).thenReturn(IdGenerator.generateId());
		when(details.getRealm()).thenReturn(IdGenerator.generateId());
		when(details.getIssueTime()).thenReturn(Instant.now());
		when(details.getAuthTime()).thenReturn(Instant.now());
		when(details.getExpires()).thenReturn(Instant.now());

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> verifier.initRequest(session, URI.create(IdGenerator.generateId())));
		assertEquals("principal attributes have already been bound", error.getMessage());
	}

	@Test
	public void testInitRequest() {
		final var entryPointUri = URI.create(IdGenerator.generateId());

		final var details = mock(SamlSessionDetails.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		assertDoesNotThrow(() -> verifier.initRequest(session, entryPointUri));

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
	public void testVerifyResponseRequiresSessionId() {
		final var details = mock(SamlSessionDetails.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(NullPointerException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, null));
		assertEquals("Missing sessionId", error.getMessage());
	}

	@Test
	public void testVerifyResponseRequiresEntryPointUri() {
		final var sessionId = IdGenerator.generateId();

		final var details = mock(SamlSessionDetails.class);
		when(details.getSessionId()).thenReturn(sessionId);

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(NullPointerException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, null));
		assertEquals("Missing entryPointUri", error.getMessage());
	}

	@Test
	public void testVerifyResponseRejectInvalidSession() {
		final var sessionId = IdGenerator.generateId();
		final var entryPointUri = URI.create(IdGenerator.generateId());

		final var details = mock(SamlSessionDetails.class);
		when(details.getSessionId()).thenReturn(sessionId);
		when(details.getEntryPointUri()).thenReturn(entryPointUri);
		when(details.isInvalid()).thenReturn(true);

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, null));
		assertEquals("invalid session", error.getMessage());
	}

	@Test
	public void testVerifyResponseRejectBoundPrincipal() {
		final var sessionId = IdGenerator.generateId();
		final var entryPointUri = URI.create(IdGenerator.generateId());

		final var details = mock(SamlSessionDetails.class);
		when(details.getSessionId()).thenReturn(sessionId);
		when(details.getEntryPointUri()).thenReturn(entryPointUri);
		when(details.getName()).thenReturn(IdGenerator.generateId());
		when(details.getRealm()).thenReturn(IdGenerator.generateId());
		when(details.getIssueTime()).thenReturn(Instant.now());
		when(details.getAuthTime()).thenReturn(Instant.now());
		when(details.getExpires()).thenReturn(Instant.now());

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, null));
		assertEquals("principal attributes have already been bound", error.getMessage());
	}

	@Test
	public void testVerifyResponseRequiresRelayStateParam() {
		final var sessionId = IdGenerator.generateId();
		final var entryPointUri = URI.create(IdGenerator.generateId());

		final var details = mock(SamlSessionDetails.class);
		when(details.getSessionId()).thenReturn(sessionId);
		when(details.getEntryPointUri()).thenReturn(entryPointUri);

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(NullPointerException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, null));
		assertEquals("Missing RelayState parameter", error.getMessage());
	}

	@Test
	public void testVerifyResponseRequiresRelayStateInSession() {
		final var sessionId = IdGenerator.generateId();
		final var entryPointUri = URI.create(IdGenerator.generateId());
		final var relayState = IdGenerator.generateId();

		final var details = mock(SamlSessionDetails.class);
		when(details.getSessionId()).thenReturn(sessionId);
		when(details.getEntryPointUri()).thenReturn(entryPointUri);

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(NullPointerException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, relayState));
		assertEquals("Missing relayState in session", error.getMessage());
	}

	@Test
	public void testVerifyResponseRelayStateMismatch() {
		final var sessionId = IdGenerator.generateId();
		final var entryPointUri = URI.create(IdGenerator.generateId());
		final var relayState = IdGenerator.generateId();

		final var details = mock(SamlSessionDetails.class);
		when(details.getSessionId()).thenReturn(sessionId);
		when(details.getEntryPointUri()).thenReturn(entryPointUri);
		when(details.getRelayState()).thenReturn(IdGenerator.generateId());

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(IllegalArgumentException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, relayState));
		assertEquals("RelayState mismatch", error.getMessage());
	}

	@Test
	public void testVerifyResponseRequiresSamlResponse() {
		final var sessionId = IdGenerator.generateId();
		final var entryPointUri = URI.create(IdGenerator.generateId());
		final var relayState = IdGenerator.generateId();

		final var details = mock(SamlSessionDetails.class);
		when(details.getSessionId()).thenReturn(sessionId);
		when(details.getEntryPointUri()).thenReturn(entryPointUri);
		when(details.getRelayState()).thenReturn(relayState);

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		IuTestLogger.expect(SamlSessionVerifier.class.getName(), Level.INFO, "Invalid SAML Response",
				NullPointerException.class, e -> "Missing SAMLResponse parameter".equals(e.getMessage()));
		final var error = assertThrows(IuAuthenticationException.class,
				() -> verifier.verifyResponse(session, "127.0.0.1", null, relayState));
		assertEquals(entryPointUri, error.getLocation());
	}

	@Test
	public void testVerifyResponse() {
		final var sessionId = IdGenerator.generateId();
		final var entryPointUri = URI.create(IdGenerator.generateId());
		final var relayState = IdGenerator.generateId();
		final var samlResponse = IdGenerator.generateId();

		final var details = mock(SamlSessionDetails.class);
		when(details.getSessionId()).thenReturn(sessionId);
		when(details.getEntryPointUri()).thenReturn(entryPointUri);
		when(details.getRelayState()).thenReturn(relayState);

		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		final var principal = mock(SamlPrincipal.class);
		when(provider.verifyResponse(IuWebUtils.getInetAddress("127.0.0.1"), samlResponse, sessionId))
				.thenReturn(principal);
		assertEquals(entryPointUri,
				assertDoesNotThrow(() -> verifier.verifyResponse(session, "127.0.0.1", samlResponse, relayState)));
		verify(principal).bind(details);
	}

	@Test
	public void testGetPrincipalIdentityRejectsInvalidSession() {
		final var details = mock(SamlSessionDetails.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		when(details.isInvalid()).thenReturn(true);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(IllegalArgumentException.class, () -> verifier.getPrincipalIdentity(session));
		assertEquals("invalid session", error.getMessage());
	}

	@Test
	public void testGetPrincipalIdentityRejectsUnboundSession() {
		final var details = mock(SamlSessionDetails.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		final var error = assertThrows(IllegalArgumentException.class, () -> verifier.getPrincipalIdentity(session));
		assertEquals("Session missing principal", error.getMessage());
	}

	@Test
	public void testGetPrincipalIdentityRequiresEntryPoint() {
		final var details = mock(SamlSessionDetails.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		final var verifier = new SamlSessionVerifier(postUri);
		try (final var mockSamlPrincipal = mockStatic(SamlPrincipal.class)) {
			mockSamlPrincipal.when(() -> SamlPrincipal.isBound(details)).thenReturn(true);
			final var error = assertThrows(NullPointerException.class, () -> verifier.getPrincipalIdentity(session));
			assertEquals("Missing entryPointUri", error.getMessage());
		}
	}

	@Test
	public void testGetPrincipalIdentity() {
		final var entryPointUri = URI.create(IdGenerator.generateId());
		final var realm = IdGenerator.generateId();

		final var details = mock(SamlSessionDetails.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		when(provider.getRealm()).thenReturn(realm);
		when(details.getEntryPointUri()).thenReturn(entryPointUri);

		final var verifier = new SamlSessionVerifier(postUri);
		final var id = mock(SamlPrincipal.class);
		try (final var mockSamlPrincipal = mockStatic(SamlPrincipal.class);
				final var mockIuPrincipalIdentity = mockStatic(IuPrincipalIdentity.class)) {
			mockSamlPrincipal.when(() -> SamlPrincipal.isBound(details)).thenReturn(true);
			mockSamlPrincipal.when(() -> SamlPrincipal.from(details)).thenReturn(id);
			assertSame(id, assertDoesNotThrow(() -> verifier.getPrincipalIdentity(session)));
			mockIuPrincipalIdentity.verify(() -> IuPrincipalIdentity.verify(id, realm));
		}
	}

	@Test
	public void testGetPrincipalIdentityVerificationFailure() {
		final var entryPointUri = URI.create(IdGenerator.generateId());
		final var realm = IdGenerator.generateId();

		final var details = mock(SamlSessionDetails.class);
		final var session = mock(IuSession.class);
		when(session.getDetail(SamlSessionDetails.class)).thenReturn(details);

		when(provider.getRealm()).thenReturn(realm);
		when(details.getEntryPointUri()).thenReturn(entryPointUri);

		final var verifier = new SamlSessionVerifier(postUri);
		final var id = mock(SamlPrincipal.class);
		try (final var mockSamlPrincipal = mockStatic(SamlPrincipal.class);
				final var mockIuPrincipalIdentity = mockStatic(IuPrincipalIdentity.class)) {
			mockSamlPrincipal.when(() -> SamlPrincipal.isBound(details)).thenReturn(true);
			mockSamlPrincipal.when(() -> SamlPrincipal.from(details)).thenReturn(id);

			final var error = new IuAuthenticationException(null);
			mockIuPrincipalIdentity.when(() -> IuPrincipalIdentity.verify(id, realm)).thenThrow(error);
			assertSame(error, assertThrows(IuAuthenticationException.class, () -> verifier.getPrincipalIdentity(session)));
			assertEquals(entryPointUri, error.getLocation());
		}
	}

}
