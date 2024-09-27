package iu.auth.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpCookie;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.auth.config.IuSessionConfiguration;
import edu.iu.auth.session.IuSession;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class SessionHandlerTest {
	private URI resourceUri;
	private IuSessionConfiguration configuration;
	private WebKey issuerKey;
	private Algorithm algorithm;
	private SessionHandler sessionHandler;
	private SessionHandler.PurgeTask purgeTask;

	@SuppressWarnings("unchecked")
	@BeforeEach
	public void setup() {
		purgeTask = new SessionHandler.PurgeTask();
		resourceUri = URI.create("http://localhost/");
		configuration = mock(IuSessionConfiguration.class);
		issuerKey = WebKey.ephemeral(Algorithm.HS256);
		algorithm = Algorithm.HS256;
		sessionHandler = new SessionHandler(resourceUri, configuration, () -> issuerKey, algorithm);
	}

	@Test
	public void testSessionHandlerConstructorWithValidParameters() {
		assertDoesNotThrow(() -> new SessionHandler(resourceUri, configuration, () -> issuerKey, algorithm));
	}

	@Test
	public void testSessionHandlerConstructorWithInvalidResourceUri() {
		resourceUri = URI.create("http://localhost/resource");
		assertThrows(IllegalArgumentException.class,
				() -> new SessionHandler(resourceUri, configuration, () -> issuerKey, algorithm));
	}

	@Test
	public void testSessionCreationWithValidParameters() {
		when(configuration.getMaxSessionTtl()).thenReturn(Duration.ofHours(12L));
		assertNotNull(sessionHandler.create());
	}

	@Test
	public void testActivateSessionWithNoSecretKey() {
		Iterable<HttpCookie> cookies = Arrays.asList(new HttpCookie("iu-sk", "validSecretKey"));
		assertNull(sessionHandler.activate(cookies));
	}

	@Test
	public void testStoreSessionAndActivateSessionSuccess() {
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);
		Session session = new Session(resourceUri, Duration.ofHours(12L));
		when(configuration.getInActiveTtl()).thenCallRealMethod();

		String cookie = sessionHandler.store(session, false);
		assertNotNull(cookie);
		assertNotNull(sessionHandler.activate(Arrays.asList(new HttpCookie("iu-sk", cookie))));
	}

	@Test
	public void testPurgeStoredSessionWhenExpire() {
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);
		when(configuration.getInActiveTtl()).thenReturn(Duration.ofMillis(250L));
		Session session = new Session(resourceUri, Duration.ofHours(12L));
		String cookie = sessionHandler.store(session, false);
		assertNotNull(cookie);
		assertDoesNotThrow(() -> Thread.sleep(250L));

		assertNull(sessionHandler.activate(Arrays.asList(new HttpCookie("iu-sk", cookie))));
	}

	@Test
	public void testPurgeTask() {
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);

		final var purgeTask = new SessionHandler.PurgeTask();
		assertDoesNotThrow(purgeTask::run);
		Session session = new Session(resourceUri, Duration.ofHours(12L));
		String cookie = sessionHandler.store(session, true);
		assertDoesNotThrow(purgeTask::run);

	}

	@Test
	public void testStoreWithNoPurgeTask() {
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);

		final var purgeTask = new SessionHandler.PurgeTask();
		when(configuration.getInActiveTtl()).thenCallRealMethod();
		Session session = new Session(resourceUri, Duration.ofHours(12L));
		sessionHandler.store(session, true);
		assertDoesNotThrow(purgeTask::run);

	}

	@Test
	public void testStoreWithPurgeTaskAndActivate() {
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);
		final var purgeTask = new SessionHandler.PurgeTask();
		when(configuration.getInActiveTtl()).thenReturn(Duration.ofMillis(250L));
		Session session = new Session(resourceUri, Duration.ofHours(12L));
		String cookie = sessionHandler.store(session, true);
		assertDoesNotThrow(() -> Thread.sleep(250L));
		assertDoesNotThrow(purgeTask::run);
		assertNull(sessionHandler.activate(Arrays.asList(new HttpCookie("iu-sk", cookie))));

	}

}
