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

	@SuppressWarnings("unchecked")
	@BeforeEach
	public void setup() {
		resourceUri = URI.create("http://localhost/");
		configuration = mock(IuSessionConfiguration.class);
		issuerKey = WebKey.ephemeral(Algorithm.HS256) ;
		algorithm = Algorithm.HS256;
		sessionHandler = new SessionHandler(resourceUri, configuration, () -> issuerKey, algorithm);
	}

	@Test
	public void sessionHandlerConstructorWithValidParameters() {
		assertDoesNotThrow(() -> new SessionHandler(resourceUri, configuration, ()-> issuerKey, algorithm));
	}

	@Test
	public void sessionHandlerConstructorWithInvalidResourceUri() {
		resourceUri = URI.create("http://localhost/resource");
		assertThrows(IllegalArgumentException.class, () -> new SessionHandler(resourceUri, configuration, ()-> issuerKey, algorithm));
	}

	@Test
	public void sessionCreationWithValidParameters() {
		when(configuration.getMaxSessionTtl()).thenReturn(Duration.ofHours(12L));
		assertNotNull(sessionHandler.create());
	}

	@Test
	public void activateSessionWithNoSecretKey() {
		Iterable<HttpCookie> cookies = Arrays.asList(new HttpCookie("iu-sk", "validSecretKey"));
		assertNull(sessionHandler.activate(cookies));
	}

	@Test	
	public void storeSession() {
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);
		Session session = new Session(resourceUri, Duration.ofHours(12L));
		when(configuration.getInActiveTtl()).thenCallRealMethod();
		
		String cookie = sessionHandler.store(session, false);
		assertNotNull(cookie);
		assertNotNull(sessionHandler.activate(Arrays.asList(new HttpCookie("iu-sk", cookie))));
	}

	@Test	
	public void purgeStoredSession() {
		Session session = new Session(resourceUri, Duration.ofHours(12L));
		String cookie = sessionHandler.store(session, false);
		assertNotNull(cookie);
		assertNull(sessionHandler.activate(Arrays.asList(new HttpCookie("iu-sk", cookie))));
	}

	

}
