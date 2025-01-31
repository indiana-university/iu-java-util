package edu.iu.web.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.test.IuTestLogger;
import iu.web.server.IuHttpListener;

@SuppressWarnings("javadoc")
public class IuHttpServerTest {

	@Test
	public void testStartStop() {
		final var server = assertDoesNotThrow(IuHttpServer::new);
		final var error = assertThrows(IllegalStateException.class, () -> server.setOnline(true));
		assertEquals("not started", error.getMessage());

		final var listener = mock(IuHttpListener.class);

		IuTestLogger.expect(IuHttpServer.class.getName(), Level.CONFIG,
				"starting IuHttpServer [host=, port=8780, backlog=0, stopDelay=PT15S, online=false, closed=false]");
		IuTestLogger.expect(IuHttpServer.class.getName(), Level.FINE,
				"started IuHttpServer [host=, port=8780, backlog=0, stopDelay=PT15S, online=true, closed=false]; "
						+ listener);
		IuTestLogger.expect(IuHttpServer.class.getName(), Level.FINE,
				"stopping IuHttpServer [host=, port=8780, backlog=0, stopDelay=PT15S, online=false, closed=true]; "
						+ listener);
		IuTestLogger.expect(IuHttpServer.class.getName(), Level.CONFIG,
				"stopped IuHttpServer [host=, port=8780, backlog=0, stopDelay=PT15S, online=false, closed=true]");

		final var thread = new Thread() {
			volatile boolean done;
			volatile Throwable error;

			@Override
			public void run() {
				try {
					try (final var mockIuHttpListener = mockStatic(IuHttpListener.class)) {
						mockIuHttpListener.when(() -> IuHttpListener.create(null, new InetSocketAddress(8780), null,
								Collections.emptySet(), 100, 0, 15)).thenReturn(listener);

						server.run();
					}
				} catch (Throwable e) {
					error = e;
				} finally {
					synchronized (this) {
						done = true;
						notifyAll();
					}
				}
			}

		};
		thread.start();
		assertDoesNotThrow(() -> IuObject.waitFor(server, () -> server.isOnline(), Duration.ofSeconds(2L)));

		assertDoesNotThrow(server::close);
		assertDoesNotThrow(() -> IuObject.waitFor(thread, () -> thread.done, Duration.ofSeconds(2L)));

		final var errorAfterClose = assertThrows(IllegalStateException.class, () -> server.setOnline(true));
		assertEquals("closed", errorAfterClose.getMessage());

		if (thread.error != null)
			assertDoesNotThrow(() -> {
				throw thread.error;
			});
	}

	@Test
	public void testNamedHost() {
		final var server = assertDoesNotThrow(IuHttpServer::new);
		final var host = IdGenerator.generateId();
		IuException.unchecked(() -> {
			final var f = IuHttpServer.class.getDeclaredField("host");
			f.setAccessible(true);
			f.set(server, host);
		});

		final var listener = mock(IuHttpListener.class);

		IuTestLogger.expect(IuHttpServer.class.getName(), Level.CONFIG, "starting IuHttpServer [host=" + host
				+ ", port=8780, backlog=0, stopDelay=PT15S, online=false, closed=false]");
		IuTestLogger.expect(IuHttpServer.class.getName(), Level.FINE, "started IuHttpServer [host=" + host
				+ ", port=8780, backlog=0, stopDelay=PT15S, online=true, closed=false]; " + listener);
		IuTestLogger.expect(IuHttpServer.class.getName(), Level.FINE, "stopping IuHttpServer [host=" + host
				+ ", port=8780, backlog=0, stopDelay=PT15S, online=false, closed=true]; " + listener);
		IuTestLogger.expect(IuHttpServer.class.getName(), Level.CONFIG, "stopped IuHttpServer [host=" + host
				+ ", port=8780, backlog=0, stopDelay=PT15S, online=false, closed=true]");

		try (final var mockIuHttpListener = mockStatic(IuHttpListener.class)) {
			mockIuHttpListener.when(() -> IuHttpListener.create(null, new InetSocketAddress(host, 8780), null,
					Collections.emptySet(), 100, 0, 15)).thenReturn(listener);
			final var timer = new Timer();
			try {
				timer.schedule(new TimerTask() {
					@Override
					public void run() {
						assertDoesNotThrow(server::close);
					}
				}, 250L);
				assertDoesNotThrow(server::run);
			} finally {
				timer.cancel();
			}
		}
	}

}
