package iu.web.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Filter.Chain;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class ContextFilterTest {

	@Test
	public void testRequestUriNotInAllowList() {
		final var filter = new ContextFilter(Collections.emptyList(), Collections.emptyList());
		final var requestHeaders = mock(Headers.class);
		when(requestHeaders.get("X-Forwarded-Host")).thenReturn(null);
		final var requestUri = URI.create(IdGenerator.generateId());
		final var exchange = mock(HttpExchange.class);
		when(exchange.getRequestURI()).thenReturn(requestUri);
		when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
		final var chain = mock(Chain.class);
		IuTestLogger.expect(ContextFilter.class.getName(), Level.INFO,
				"rejecting " + requestUri + ", not in allow list []");
		assertDoesNotThrow(() -> filter.doFilter(exchange, chain));
	}

	@Test
	public void testRequest() {
		final var filter = new ContextFilter(Collections.emptyList(), Collections.emptyList());
		final var requestHeaders = mock(Headers.class);
		when(requestHeaders.get("X-Forwarded-Host")).thenReturn(null);
		final var requestUri = URI.create(IdGenerator.generateId());
		final var exchange = mock(HttpExchange.class);
		when(exchange.getRequestURI()).thenReturn(requestUri);
		when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
		final var chain = mock(Chain.class);
		assertDoesNotThrow(() -> filter.doFilter(exchange, chain));
	}

}
