/*
 * Copyright © 2026 Indiana University
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
package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@SuppressWarnings({ "javadoc", "exports" })
public class IuAsynchronousPipeTest {

	// Committed values balance thorough verification with fast build times
	// This default configuration is set for high-latency; e.g. PSJOA CI
	// May be tuned to simulate timings for large parallel processing tasks
	private static final int N = 50;
	private static final int LOG_PER_N = 10;
	private static final int PARTIAL_SEND_SIZE = 25;
	private static final Duration SIMULATED_SOURCE_DELAY = Duration.ofMillis(25L);
	private static final Duration SIMULATED_SEND_DELAY = Duration.ofMillis(25L);
	private static final Duration SIMULATED_SEND_PER_ITEM = Duration.ofNanos(100_000L);
	private static final Duration TIME_OUT = Duration.ofMillis(5000L);
	private static final int POOL_SIZE = 16;

	// Largest verified scenario:
	// low-latency 100M sent via split collector in 30 minutes on VM with 4xCPU
//	private static final int N = 100_000_000;
//	private static final int LOG_PER_N = 100_000;
//	private static final int PARTIAL_SEND_SIZE = 50_000;
//	private static final Duration SIMULATED_SOURCE_DELAY = Duration.ofMillis(1L);
//	private static final Duration SIMULATED_SEND_DELAY = Duration.ofMillis(2L);
//	private static final Duration SIMULATED_SEND_PER_ITEM = Duration.ofNanos(10_000L);
//	private static final Duration TIME_OUT = Duration.ofMinutes(30L);
//	private static final int POOL_SIZE = 250;

	private IuParallelWorkloadController workload;
	private IuAsynchronousPipe<String> pipe;
	private Stream<String> stream;
	private List<String> controlList;
	private volatile int count;
	private Logger log;
	private Handler logHandler;

	private class LogEntry {
		private final Duration elapsed;
		private final Thread thread;
		private final LogRecord record;

		private LogEntry(Duration elapsed, Thread thread, LogRecord record) {
			this.elapsed = elapsed;
			this.thread = thread;
			this.record = record;
		}

		void commit(PrintWriter logWriter) {
			logWriter.print(elapsed);
			logWriter.print(" ");
			logWriter.print(thread.getName());
			logWriter.print(" ");
			logWriter.print(record.getLevel());

			String className = record.getSourceClassName();
			if (className != null) {
				logWriter.print(" ");
				logWriter.print(className);
			}

			String methodName = record.getSourceMethodName();
			if (methodName != null) {
				if (className == null)
					logWriter.print(" ");
				logWriter.print("#");
				logWriter.print(record.getSourceMethodName());
			}

			logWriter.print(": ");
			logWriter.println(record.getMessage());

			Throwable thrown = record.getThrown();
			if (thrown != null) {
				try (PrintWriter w = new PrintWriter(new Writer() {
					@Override
					public void write(char[] cbuf, int off, int len) throws IOException {
						char last = '\n';
						for (int i = off; i < len; i++) {
							char c = cbuf[i];
							if (c != '\n' && last == '\n')
								logWriter.write("  ");
							logWriter.write(c);
							last = c;
						}
					}

					@Override
					public void flush() throws IOException {
					}

					@Override
					public void close() throws IOException {
					}
				})) {
					thrown.printStackTrace(w);
				}
			}
		}
	}

	private class PipeLog extends Handler {
		private final Queue<LogEntry> logEntries = new ConcurrentLinkedQueue<>();
		private final Thread flushAndCommit;
		private volatile boolean closed;

		private PipeLog() throws IOException {
			setLevel(Level.FINE);
			flushAndCommit = new Thread(() -> {
				final var logFile = Paths.get("target", "pipe.log");
				try (final var logWriter = new PrintWriter(
						Files.newBufferedWriter(logFile, StandardOpenOption.APPEND, StandardOpenOption.CREATE))) {

					while (!closed || !logEntries.isEmpty()) {
						while (!logEntries.isEmpty())
							logEntries.poll().commit(logWriter);

						logWriter.flush();
						synchronized (this) {
							this.notifyAll();
							this.wait(500L);
						}
					}

				} catch (Throwable e) {
					e.printStackTrace();
				}
			});
			flushAndCommit.start();
		}

		@Override
		public synchronized void publish(LogRecord record) {
			logEntries.offer(new LogEntry(workload.getElapsed(), Thread.currentThread(), record));
		}

		@Override
		public synchronized void flush() {
			synchronized (this) {
				this.notifyAll();
				while (flushAndCommit.isAlive() && logEntries.isEmpty())
					try {
						this.wait(500L);
					} catch (InterruptedException e) {
						break;
					}
			}
		}

		@Override
		public synchronized void close() throws SecurityException {
			closed = true;
			flush();
		}
	}

	@BeforeEach
	public void setup(TestInfo testInfo) throws Throwable {
		pipe = new IuAsynchronousPipe<>();
		stream = pipe.stream();
		controlList = new ArrayList<>(N);
		count = 0;

		logHandler = new PipeLog();
		log = Logger.getAnonymousLogger();
		log.setLevel(Level.FINE);
		log.setUseParentHandlers(false);
		log.addHandler(logHandler);

		workload = new IuParallelWorkloadController(testInfo.getTestMethod().get().getName(), POOL_SIZE, TIME_OUT);
		workload.setLog(log);

		log.config("setup complete");
	}

	@AfterEach
	public void tearDown() throws Exception {
		log.config("begin teardown");
		try {
			pipe.close();
			pipe.pauseController(workload.getExpires());
			log.config("cleared pipe");
			workload.close();
			log.config("closed controller");
		} finally {
			pipe = null;
			stream = null;
			controlList = null;
			count = 0;
			log.config("teardown complete\n");
			log.removeHandler(logHandler);
			logHandler.flush();
			logHandler.close();
			workload = null;
			logHandler = null;
			log = null;
		}
	}

	@Test
	public void testCantGetStreamAgain() {
		assertEquals("Stream has already been retreived",
				assertThrows(IllegalStateException.class, pipe::stream).getMessage());
	}

	@Test
	public void testItCanCount() {
		assertEquals(0, pipe.getAcceptedCount());
		assertEquals(0, pipe.getPendingCount());
		assertEquals(0, pipe.getReceivedCount());
		pipe.accept("foo");
		assertEquals(1, pipe.getAcceptedCount());
		assertEquals(1, pipe.getPendingCount());
		assertEquals(0, pipe.getReceivedCount());
		final var i = stream.iterator();
		assertTrue(i.hasNext());
		assertEquals("foo", i.next());
		assertEquals(1, pipe.getAcceptedCount());
		assertEquals(0, pipe.getPendingCount());
		assertEquals(1, pipe.getReceivedCount());
	}

	@Test
	public void testRejectAfterClose() {
		pipe.close();
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> pipe.accept("")).getMessage());
	}

	@Test
	public void testPassesErrorFromReceiverWithExpiringPause() throws InterruptedException, TimeoutException {
		final var e = new RuntimeException();
		workload.apply(task -> {
			Thread.sleep(50L);
			pipe.error(e);
		});
		assertSame(e, assertThrows(RuntimeException.class, () -> pipe.pauseController(workload.getExpires())));
	}

	@Test
	public void testPassesErrorFromReceiverWithCountPause() throws InterruptedException, TimeoutException {
		final var e = new RuntimeException();
		workload.apply(task -> {
			Thread.sleep(50L);
			pipe.error(e);
		});
		assertSame(e, assertThrows(RuntimeException.class, () -> pipe.pauseController(1, Duration.ofMillis(1000L))));
	}

	@Test
	public void testPassesErrorFromControllerWithPausedReceiver() throws Throwable {
		final var e = new RuntimeException();

		class Box {
			Throwable error;
		}
		final var box = new Box();

		workload.apply(task -> {
			try {
				box.error = assertThrows(RuntimeException.class, () -> pipe.pauseReceiver(workload.getExpires()));
			} catch (Throwable e2) {
				box.error = e2;
			}
		});
		Thread.sleep(250L);
		pipe.error(e);
		Thread.sleep(250L);
		assertNotNull(box.error);
		if (box.error instanceof RuntimeException)
			assertSame(e, box.error, box.error::toString);
		else
			throw box.error;
	}

	@Test
	public void testPassesErrorFromControllerWithReceiverPausedOnCount() throws Throwable {
		final var e = new RuntimeException();

		class Box {
			Throwable error;
		}
		final var box = new Box();

		workload.apply(task -> {
			try {
				box.error = assertThrows(RuntimeException.class, () -> pipe.pauseReceiver(1, Duration.ofMillis(100L)));
			} catch (Throwable e2) {
				box.error = e2;
			}
		});
		Thread.sleep(50L);
		pipe.error(e);
		Thread.sleep(50L);
		assertNotNull(box.error);
		if (box.error instanceof RuntimeException)
			assertSame(e, box.error, box.error::toString);
		else
			throw box.error;
	}

	@Test
	public void testPassesErrorFromControllerWithBlockingReceiver() throws Throwable {
		final var e = new RuntimeException();

		class Box {
			Throwable error;
		}
		final var box = new Box();

		workload.apply(task -> {
			try {
				box.error = assertThrows(RuntimeException.class, () -> stream.findAny().get());
			} catch (Throwable e2) {
				box.error = e2;
			}
		});
		Thread.sleep(50L);
		pipe.error(e);
		Thread.sleep(50L);
		assertNotNull(box.error);
		if (box.error instanceof RuntimeException)
			assertSame(e, box.error, box.error::toString);
		else
			throw box.error;
	}

	@Test
	public void testPauseControllerTimeout() {
		assertEquals("Timed out after receiving 0 of 1 values in PT0.005S",
				assertThrows(TimeoutException.class, () -> pipe.pauseController(1, Duration.ofMillis(5L)))
						.getMessage());
	}

	@Test
	public void testPauseController() throws InterruptedException, TimeoutException {
		workload.apply(c -> {
			final var i = stream.iterator();
			for (int a = 0; a < 100; a++) {
				simulateRemoteWait(SIMULATED_SEND_DELAY);
				final var value = i.next();
				IdGenerator.verifyId(value, TIME_OUT.toMillis());
			}
			Thread.sleep(25L);
			IdGenerator.verifyId(i.next(), TIME_OUT.toMillis());
			synchronized (pipe) {
				pipe.notifyAll();
			}
			Thread.sleep(25L);
			stream.close();
		});

		var before = Instant.now();
		// should be no-op boudary checks
		pipe.pauseController(Instant.now().minus(SIMULATED_SEND_DELAY));
		pipe.pauseController(0, SIMULATED_SEND_DELAY);
		var sinceBefore = Duration.between(before, Instant.now());
		assertTrue(sinceBefore.toMillis() < 5L, sinceBefore::toString);

		for (int a = 0; a < 100; a++) {
			pipe.accept(IdGenerator.generateId());
			if (pipe.getPendingCount() > 15)
				pipe.pauseController(10, TIME_OUT);
		}
		pipe.accept(IdGenerator.generateId());
		assertThrows(TimeoutException.class, () -> pipe.pauseController(16, SIMULATED_SEND_DELAY));

		pipe.pauseController(workload.getExpires());
		assertTrue(pipe.isCompleted());
		assertEquals(101, pipe.getReceivedCount());
		assertEquals("IuAsynchronousPipe [acceptedCount=101, receivedCount=101, queued=0, completed=true, closed=true]",
				pipe.toString());
	}

	@Test
	public void testPauseControllerEarlyComplete() throws InterruptedException, TimeoutException {
		workload.apply(c -> {
			Thread.sleep(50L);
			stream.close();
		});
		pipe.accept("one");
		assertEquals(0, pipe.pauseController(1, Duration.ofMillis(1000L)));
	}

	@Test
	public void testPauseReceiverNoop() throws TimeoutException, InterruptedException {
		final var before = Instant.now();
		pipe.pauseReceiver(0, SIMULATED_SEND_DELAY);
		final var sinceBefore = Duration.between(before, Instant.now());
		assertTrue(sinceBefore.toMillis() <= 5L, sinceBefore::toString);
	}

	@Test
	public void testPauseReceiverTimeout() {
		assertEquals("Timed out waiting for 0 of 1 values in PT0.005S",
				assertThrows(TimeoutException.class, () -> pipe.pauseReceiver(1, Duration.ofMillis(5L))).getMessage());
	}

	@Test
	public void testPauseReceiverBySegment() throws Throwable {
		workload.apply(c -> {
			final var i = stream.iterator();

			var accepted = pipe.pauseReceiver(10, TIME_OUT);
			while (accepted > 0) {
				log.info("accepted " + accepted);
				for (int b = 0; b < accepted; b++) {
					final var value = i.next();
					IdGenerator.verifyId(value, TIME_OUT.toMillis());
				}
				accepted = pipe.pauseReceiver(10, TIME_OUT);
			}
		});

		for (int a = 0; a < 100; a++) {
			simulateRemoteWait(SIMULATED_SEND_DELAY);
			pipe.accept(IdGenerator.generateId());
		}
		log.info("closing");
		pipe.close();
		log.info("closed");
		pipe.pauseController(workload.getExpires());
		log.info("unpaused after close " + pipe);
		assertTrue(pipe.isCompleted());
		assertEquals(100, pipe.getReceivedCount());
	}

	@Test
	public void testPauseReceiverTotal() throws Throwable {
		class Box {
			Throwable thrown;
		}
		final var box = new Box();
		workload.apply(c -> {
			try {
				var before = Instant.now();
				var accepted = pipe.pauseReceiver(before);
				var sinceBefore = Duration.between(before, Instant.now());
				assertTrue(sinceBefore.toMillis() <= 5L);
				assertEquals(0, accepted);

				accepted = pipe.pauseReceiver(workload.getExpires());
				assertTrue(accepted == 99 || accepted == 100, Long.toString(accepted));

				assertTrue(pipe.isClosed());

				before = Instant.now();
				accepted = pipe.pauseReceiver(workload.getExpires());
				sinceBefore = Duration.between(before, Instant.now());
				assertTrue(sinceBefore.toMillis() <= 5L);
				assertEquals(0, accepted);

				assertFalse(pipe.isCompleted());
				stream.forEach(value -> IdGenerator.verifyId(value, TIME_OUT.toMillis()));
				assertTrue(pipe.isCompleted());
			} catch (Throwable e) {
				box.thrown = e;
				stream.close();
			}
		});

		for (int a = 0; a < 100; a++) {
			simulateRemoteWait(SIMULATED_SEND_DELAY);
			pipe.accept(IdGenerator.generateId());
		}
		log.info("closing");
		pipe.close();
		log.info("closed");
		pipe.pauseController(workload.getExpires());
		if (box.thrown != null)
			throw box.thrown;
		log.info("unpaused after close " + pipe);
		assertTrue(pipe.isCompleted());
		assertEquals(100, pipe.getReceivedCount());
	}

	@Test
	public void testSequential() throws Exception {
		simulateSequentialRun();
		simulateFullSend(collectAllSequential(stream));
	}

	@Test
	public void testSequentialAsyncReceiver() throws Throwable {
		IuTaskController receiver = workload.apply(c -> simulateFullSend(collectAllSequential(stream)));
		simulateSequentialRun();
		receiver.join();
	}

	@Test
	public void testSequentialAsyncController() throws Throwable {
		IuTaskController controller = workload.apply(c -> this.simulateSequentialRun());
		simulateFullSend(collectAllSequential(stream));
		controller.join();
	}

	@Test
	public void testSequentialAsyncBoth() throws Throwable {
		IuTaskController receiver = workload.apply(c -> simulateFullSend(collectAllSequential(stream)));
		IuTaskController controller = workload.apply(c -> this.simulateSequentialRun());
		controller.join();
		receiver.join();
	}

	@Test
	public void testParallel() throws Throwable {
		simulateParallelRun();
		simulatePartialSend(collectAllParallel(stream.parallel()));
		assertAllPartsSent();
	}

	@Test
	public void testParallelAsyncReceiver() throws Throwable {
		IuTaskController receiver = workload.apply(c -> simulatePartialSend(collectAllParallel(stream.parallel())));
		simulateParallelRun();
		receiver.join();
		assertAllPartsSent();
	}

	@Test
	public void testParallelAsyncSupplier() throws Throwable {
		IuTaskController controller = workload.apply(c -> this.simulateParallelRun());
		simulatePartialSend(collectAllParallel(stream.parallel()));
		controller.join();
		assertAllPartsSent();
	}

	@Test
	public void testParallelAsyncBoth() throws Throwable {
		IuTaskController receiver = workload.apply(c -> simulatePartialSend(collectAllParallel(stream.parallel())));
		IuTaskController controller = workload.apply(c -> this.simulateParallelRun());
		controller.join();
		receiver.join();
		assertAllPartsSent();
	}

	@Test
	public void testParallelSplitCollector() throws Throwable {
		try {
			final Spliterator<String> pipeSplitter = stream.parallel().spliterator();
			workload.apply(c -> this.simulateParallelRun());

			IuRateLimitter rateLimit = new IuRateLimitter(POOL_SIZE, workload.getExpires());
			while (!pipe.isClosed()) {
				final var remaining = workload.getRemaining();
				final var ready = pipe.pauseReceiver(PARTIAL_SEND_SIZE, remaining);

				rateLimit.failFast();

				Spliterator<String> split = pipeSplitter.trySplit();
				if (split != null) {
					assertTrue(ready >= 0);
					assertTrue(ready <= split.estimateSize());
					rateLimit.accept(workload
							.apply(c -> simulatePartialSend(collectAllParallel(StreamSupport.stream(split, true)))));
				}
			}
			rateLimit.join();

			simulatePartialSend(collectAllParallel(StreamSupport.stream(pipeSplitter, true)));
			assertAllPartsSent();
		} finally {
			pipe.close();
			stream.close();
		}
	}

	// BEGIN private load simulator methods

	private void simulateRemoteWait(Duration max) throws InterruptedException {
		Duration timeToSleep = workload.getRemaining();
		if (timeToSleep.compareTo(Duration.ZERO) <= 0)
			return;

		if (timeToSleep.compareTo(max) > 0)
			timeToSleep = max;

		Thread.sleep(timeToSleep.toMillis(), timeToSleep.toNanosPart() % 1_000_000);
	}

	private void supplyFromExternalSource() throws InterruptedException {
		simulateRemoteWait(SIMULATED_SOURCE_DELAY);

		String next = IdGenerator.generateId();

		synchronized (this) {
			controlList.add(next);

			int c = ++count;
			if (c < N && c % LOG_PER_N == 0)
				log.fine(() -> "supplied " + c + " so far");
		}

		pipe.accept(next);
	}

	private void simulateSequentialRun() throws InterruptedException {
		try {
			for (int i = 0; !workload.isExpired() && i < N; i++)
				supplyFromExternalSource();

			assertFalse(workload.isExpired(), () -> "Timed out after completing " + count);

			log.info(() -> "supplied " + count + " sequentially");
		} finally {
			pipe.close();
		}
	}

	private void simulateParallelRun() throws Throwable {
		try {
			IuRateLimitter rateLimit = new IuRateLimitter(POOL_SIZE, workload.getExpires());
			for (int i = 0; i < N; i++)
				rateLimit.accept(workload.apply(c -> this.supplyFromExternalSource()));
			rateLimit.join();

			log.info("supplied " + count + " in parallel");
		} finally {
			pipe.close();
		}
	}

	private List<String> collectAllSequential(Stream<String> stream) {
		List<String> all = new ArrayList<>();
		stream.forEach(a -> {
			synchronized (all) {
				all.add(a);
			}
		});
		log.fine(() -> "collected " + all.size() + " sequentially");
		return all;
	}

	private void simulateFullSend(List<String> collectedSequentialItems) throws InterruptedException {
		int size = collectedSequentialItems.size();

		assertEquals(N, size);

		synchronized (this) {
			assertEquals(count, size);
			assertTrue(controlList.equals(collectedSequentialItems));
			controlList.clear();
		}

		simulateRemoteWait(SIMULATED_SEND_DELAY.plus(SIMULATED_SEND_PER_ITEM.multipliedBy(size)));

		log.info(() -> "sent " + size + " in full");
	}

	private Set<String> collectAllParallel(Stream<String> stream) {
		Set<String> all = new HashSet<>(N);
		stream.parallel().forEach(a -> {
			synchronized (all) {
				all.add(a);
			}
		});
		log.fine(() -> "collected " + all.size() + " in parallel");
		return all;
	}

	private void simulatePartialSend(Set<String> collectedParallelItems) throws InterruptedException {
		final int size = collectedParallelItems.size();
		if (size > 0) {
			synchronized (this) {
				final int beforeSize = controlList.size();
				assertTrue(controlList.removeAll(collectedParallelItems));
				assertEquals(beforeSize, size + controlList.size());
			}

			simulateRemoteWait(SIMULATED_SEND_DELAY.plus(SIMULATED_SEND_PER_ITEM.multipliedBy(size)));

			log.info(() -> "sent " + size + " in part");
		} else
			log.fine(() -> "skipped empty partial send");
	}

	private void assertAllPartsSent() {
		synchronized (controlList) {
			assertTrue(controlList.isEmpty());
		}

		assertEquals(N, count);
		assertTrue(pipe.isClosed());

		log.info(() -> "sent " + count + " in total of all parts");
	}

}
