/*
 * Copyright © 2025 Indiana University
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
