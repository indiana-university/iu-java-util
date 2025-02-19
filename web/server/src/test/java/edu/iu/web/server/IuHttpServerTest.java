package edu.iu.web.server;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuHttpServerTest {

	@Test
	public void testStartStop() {
//		final var server = assertDoesNotThrow(IuHttpServer::new);
//		final var error = assertThrows(IllegalStateException.class, () -> server.setOnline(true));
//		assertEquals("not started", error.getMessage());
//
//		final var listener = mock(IuHttpListener.class);
//
//		IuTestLogger.expect(IuHttpServer.class.getName(), Level.CONFIG,
//				"starting IuHttpServer [host=, port=8780, backlog=0, stopDelay=PT15S, online=false, closed=false]");
//		IuTestLogger.expect(IuHttpServer.class.getName(), Level.FINE,
//				"started IuHttpServer [host=, port=8780, backlog=0, stopDelay=PT15S, online=true, closed=false]; "
//						+ listener);
//		IuTestLogger.expect(IuHttpServer.class.getName(), Level.FINE,
//				"stopping IuHttpServer [host=, port=8780, backlog=0, stopDelay=PT15S, online=false, closed=true]; "
//						+ listener);
//		IuTestLogger.expect(IuHttpServer.class.getName(), Level.CONFIG,
//				"stopped IuHttpServer [host=, port=8780, backlog=0, stopDelay=PT15S, online=false, closed=true]");
//
//		final var thread = new Thread() {
//			volatile boolean done;
//			volatile Throwable error;
//
//			@Override
//			public void run() {
//				try {
//					try (final var mockIuHttpListener = mockStatic(IuHttpListener.class)) {
//						mockIuHttpListener.when(() -> IuHttpListener.create(null, new InetSocketAddress(8780), null,
//								Collections.emptySet(), 100, 0, 15)).thenReturn(listener);
//
//						server.run();
//					}
//				} catch (Throwable e) {
//					error = e;
//				} finally {
//					synchronized (this) {
//						done = true;
//						notifyAll();
//					}
//				}
//
//		};
//		thread.start();
//		assertDoesNotThrow(() -> IuObject.waitFor(server, () -> server.isOnline(), Duration.ofSeconds(2L)));
//
//		assertDoesNotThrow(server::close);
//		assertDoesNotThrow(() -> IuObject.waitFor(thread, () -> thread.done, Duration.ofSeconds(2L)));
//
//		final var errorAfterClose = assertThrows(IllegalStateException.class, () -> server.setOnline(true));
//		assertEquals("closed", errorAfterClose.getMessage());
//
//		if (thread.error != null)
//			assertDoesNotThrow(() -> {
//				throw thread.error;
//			});
	}
//
//	@Test
//	public void testNamedHost() {
//		final var server = assertDoesNotThrow(IuHttpServer::new);
//		final var host = IdGenerator.generateId();
//		IuException.unchecked(() -> {
//			final var f = IuHttpServer.class.getDeclaredField("host");
//			f.setAccessible(true);
//			f.set(server, host);
//		});
//
//		try (final var mockIuHttpListener = mockStatic(IuHttpListener.class);
//				final var paths = mockStatic(Paths.class, CALLS_REAL_METHODS)) {
//			final var archivePathProperty = IuTest.getProperty("teststatic.archive");
//			final var archivePath = Path.of(archivePathProperty);
//			mockIuHttpListener.when(() -> IuHttpListener.create(null, new InetSocketAddress(host, 8780), null,
//					Collections.emptySet(), 100, 0, 15)).thenReturn(listener);
//			paths.when(() -> Paths.get("/opt/starch/resources")).thenReturn(archivePath);
//
//			IuTestLogger.expect(IuHttpServer.class.getName(), Level.CONFIG, "starting IuHttpServer [host=" + host
//					+ ", port=8780, backlog=0, stopDelay=PT15S, online=false, closed=false]");
//			IuTestLogger.expect(IuHttpServer.class.getName(), Level.FINE, "started IuHttpServer [host=" + host
//					+ ", port=8780, backlog=0, stopDelay=PT15S, online=true, closed=false]; " + listener);
//			IuTestLogger.expect(IuHttpServer.class.getName(), Level.FINE, "stopping IuHttpServer [host=" + host
//					+ ", port=8780, backlog=0, stopDelay=PT15S, online=false, closed=true]; " + listener);
//			IuTestLogger.expect(IuHttpServer.class.getName(), Level.CONFIG, "stopped IuHttpServer [host=" + host
//					+ ", port=8780, backlog=0, stopDelay=PT15S, online=false, closed=true]");
//
//			final var timer = new Timer();
//			try {
//				timer.schedule(new TimerTask() {
//					@Override
//					public void run() {
//						assertDoesNotThrow(server::close);
//					}
//				}, 250L);
//				assertDoesNotThrow(server::run);
//			} finally {
//				timer.cancel();
//			}
//		}
//
//	}
}
