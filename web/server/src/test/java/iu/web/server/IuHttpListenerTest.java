package iu.web.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class IuHttpListenerTest {

	@Test
	public void testStartStop() {
		final var server = mock(HttpServer.class);
		final var address = mock(InetSocketAddress.class);
		final int backlog = ThreadLocalRandom.current().nextInt();
		final int stopDelay = ThreadLocalRandom.current().nextInt();
		try (final var mockHttpServer = mockStatic(HttpServer.class)) {
			mockHttpServer.when(() -> HttpServer.create(address, backlog)).thenReturn(server);

			IuTestLogger.expect(IuHttpListener.class.getName(), Level.FINE,
					"started IuHttpListener [stopDelay=" + stopDelay + ", server=" + server + ']');

			final var listener = assertDoesNotThrow(() -> IuHttpListener.create(address, backlog, stopDelay));
			verify(server).createContext(eq("/"), argThat((HttpHandler a) -> {
				final var exchange = mock(HttpExchange.class);
				when(exchange.getRequestMethod()).thenReturn("GET");

				final var requestUri = URI.create(IdGenerator.generateId());
				when(exchange.getRequestURI()).thenReturn(requestUri);

				final var headers = mock(Headers.class);
				when(exchange.getResponseHeaders()).thenReturn(headers);
				final var body = mock(OutputStream.class);
				when(exchange.getResponseBody()).thenReturn(body);

				assertDoesNotThrow(() -> a.handle(exchange));
				verify(headers).add("content-type", "text/html");
				assertDoesNotThrow(() -> verify(exchange).sendResponseHeaders(200, 0));
				assertDoesNotThrow(() -> verify(body).write(IuText.utf8("<html><body><p>TODO: implement "
						+ StringEscapeUtils.escapeHtml4(exchange.getRequestURI().toString()) + "</p></body></html>")));

				return true;
			}));
			verify(server).setExecutor(null);
			verify(server).start();

			IuTestLogger.expect(IuHttpListener.class.getName(), Level.FINE,
					"stopped IuHttpListener [stopDelay=" + stopDelay + ", server=null]; " + server);

			assertDoesNotThrow(listener::close);
			verify(server).stop(stopDelay);

			assertDoesNotThrow(listener::close);
			verifyNoMoreInteractions(server);
		}
	}

}
