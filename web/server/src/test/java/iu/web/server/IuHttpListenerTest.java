package iu.web.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.logging.IuLogContext;
import edu.iu.test.IuTestLogger;
import edu.iu.web.IuWebContext;

@SuppressWarnings("javadoc")
public class IuHttpListenerTest {

	@Test
	public void testStartStop() {
		final var server = mock(HttpServer.class);
		final var address = mock(InetSocketAddress.class);
		final int threads = ThreadLocalRandom.current().nextInt();
		final int backlog = ThreadLocalRandom.current().nextInt();
		final int stopDelay = ThreadLocalRandom.current().nextInt();
		try (final var mockHttpServer = mockStatic(HttpServer.class);
				final var mockIuLogContext = mockStatic(IuLogContext.class)) {
			mockHttpServer.when(() -> HttpServer.create(address, backlog)).thenReturn(server);

			IuTestLogger.expect(IuHttpListener.class.getName(), Level.FINE,
					"started IuHttpListener [stopDelay=" + stopDelay + ", server=" + server + ']');

			final var externalUri = URI.create(IdGenerator.generateId());
			final var application = IdGenerator.generateId();
			final var environment = IdGenerator.generateId();
			final var module = IdGenerator.generateId();
			final var runtime = IdGenerator.generateId();
			final var component = IdGenerator.generateId();

			final var authenticator = mock(Authenticator.class);
			final var context = mock(IuWebContext.class);
			when(context.getApplication()).thenReturn(application);
			when(context.getEnvironment()).thenReturn(environment);
			when(context.getModule()).thenReturn(module);
			when(context.getRuntime()).thenReturn(runtime);
			when(context.getComponent()).thenReturn(component);
			
			final var handler = mock(HttpHandler.class);
			final var path = "/" + IdGenerator.generateId();
			when(context.getPath()).thenReturn(path);
			when(context.getHandler()).thenReturn(handler);
			final var contexts = IuIterable.iter(context);

			final var rootContext = mock(HttpContext.class);
			when(server.createContext("/")).thenReturn(rootContext);
			final var appContext = mock(HttpContext.class);
			when(server.createContext(path)).thenReturn(appContext);

			final var listener = assertDoesNotThrow(() -> IuHttpListener.create(externalUri, address, authenticator,
					contexts, threads, backlog, stopDelay));
			mockIuLogContext.verify(() -> IuLogContext.initializeContext(null, false, externalUri + path, application,
					environment, module, runtime, component));
			verify(server).createContext(path);
			verify(appContext).setHandler(handler);
			
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
