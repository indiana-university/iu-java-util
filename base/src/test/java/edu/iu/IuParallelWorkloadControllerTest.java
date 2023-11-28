/*
 * Copyright © 2023 Indiana University
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@SuppressWarnings("javadoc")
public class IuParallelWorkloadControllerTest {

	private IuParallelWorkloadController workload;
	private Logger log;

	@BeforeEach
	private void setup(TestInfo testInfo) {
		log = Logger.getAnonymousLogger();
		log.setLevel(Level.ALL);
		log.setUseParentHandlers(false);
		log.addHandler(new Handler() {
			{
				setLevel(Level.ALL);
			}

			@Override
			public void publish(LogRecord record) {
				logListener.accept(record);
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() throws SecurityException {
			}
		});

		final var name = testInfo.getTestMethod().get().getName();
		if (!name.startsWith("testRequiresPositive") && !name.equals("testRunsALotOfTasks")) {
			workload = new IuParallelWorkloadController(name, 5, Duration.ofMillis(50L));
			workload.setLog(log);
		}
	}

	@AfterEach
	private void teardown() throws Exception {
		if (workload != null)
			workload.close();
		workload = null;
		log = null;
		logListener = defaultLogListener;
	}

	private final Consumer<LogRecord> defaultLogListener = b -> {
		// useful for troubleshooting, uncomment before commit
//		var a = Thread.currentThread();
//		System.out.println(a.getName() + ": " + workload.elapsed() + " " + b.getLevel() + " " + b.getMessage());
	};

	private Consumer<LogRecord> logListener = defaultLogListener;

	@Test
	public void testCantSetLogTwice() {
		assertEquals("Logger already initialized",
				assertThrows(IllegalStateException.class, () -> workload.setLog(null)).getMessage());
	}

	@Test
	public void testRequiresPositiveSize() {
		assertEquals("size must be positive", assertThrows(IllegalArgumentException.class,
				() -> new IuParallelWorkloadController(null, 0, Duration.ofSeconds(1L))).getMessage());
	}

	@Test
	public void testRequiresPositveTimeout() {
		assertEquals("timeout must be positive", assertThrows(IllegalArgumentException.class,
				() -> new IuParallelWorkloadController(null, 1, Duration.ZERO)).getMessage());
		assertEquals("timeout must be positive", assertThrows(IllegalArgumentException.class,
				() -> new IuParallelWorkloadController(null, 1, Duration.ofNanos(-1L))).getMessage());
	}

	@Test
	public void testGracefulShutdownMustBePositive() {
		assertEquals("Must be positive",
				assertThrows(IllegalArgumentException.class, () -> workload.setGracefulShutdown(Duration.ZERO))
						.getMessage());
		assertEquals("Must be positive",
				assertThrows(IllegalArgumentException.class, () -> workload.setGracefulShutdown(Duration.ofNanos(-1L)))
						.getMessage());
	}

	@Test
	public void testGracefulTerminationMustBePositive() {
		assertEquals("Must be positive",
				assertThrows(IllegalArgumentException.class, () -> workload.setGracefulTermination(Duration.ZERO))
						.getMessage());
		assertEquals("Must be positive", assertThrows(IllegalArgumentException.class,
				() -> workload.setGracefulTermination(Duration.ofNanos(-1L))).getMessage());
	}

	@Test
	public void testGracefulDestroyMustBePositive() {
		assertEquals("Must be positive",
				assertThrows(IllegalArgumentException.class, () -> workload.setGracefulDestroy(Duration.ZERO))
						.getMessage());
		assertEquals("Must be positive",
				assertThrows(IllegalArgumentException.class, () -> workload.setGracefulDestroy(Duration.ofNanos(-1L)))
						.getMessage());
	}

	@Test
	public void testSevereClosesWorkloadWithSuppressedTimeout() throws InterruptedException, TimeoutException {
		workload.setGracefulShutdown(Duration.ofMillis(1L));
		workload.setGracefulTermination(Duration.ofMillis(1L));
		workload.setGracefulDestroy(Duration.ofMillis(1L));

		final var e = new Throwable();
		workload.apply(t -> {
			workload.defaultHandleFailedExecution(e);
			Instant until = Instant.now().plus(Duration.ofMillis(200L));
			for (var now = Instant.now(); now.isBefore(until);)
				try {
					Thread.sleep(50L);
				} catch (InterruptedException ignore) {
				}
		});

		Thread.sleep(100L);
		assertSame(e, assertThrows(RejectedExecutionException.class, () -> workload.apply(t -> {
		})).getCause());
		assertTrue(workload.isClosed());
		assertSame(e, assertThrows(ExecutionException.class, () -> workload.await()).getCause());
		assertInstanceOf(TimeoutException.class, e.getSuppressed()[0]);
	}

	@Test
	public void testSevereClosesWorkloadAndFailsByTimeout() throws InterruptedException, TimeoutException {
		workload.setGracefulShutdown(Duration.ofMillis(5L));
		workload.setGracefulTermination(Duration.ofMillis(5L));
		workload.setGracefulDestroy(Duration.ofMillis(5L));
		workload.apply(t -> {
			Instant until = Instant.now().plus(Duration.ofMillis(200L));
			for (var now = Instant.now(); now.isBefore(until);)
				try {
					Thread.sleep(50L);
				} catch (InterruptedException e) {
				}
		});
		Thread.sleep(100L);
		assertTrue(workload.isClosed());
		assertInstanceOf(TimeoutException.class,
				assertThrows(ExecutionException.class, () -> workload.await()).getCause());
	}

	@Test
	public void testHandlesOneTask() throws ExecutionException, InterruptedException, TimeoutException {
		class Box {
			String value;
		}
		var box = new Box();
		workload.apply(task -> box.value = "foo").join();
		assertEquals("foo", box.value);
	}

	@Test
	public void testClosesOnTimeout() throws ExecutionException, InterruptedException {
		Thread.sleep(100L);
		assertTrue(workload.isClosed());
		assertTrue(workload.isExpired());
		assertTrue(workload.getExpires().isBefore(Instant.now()));
	}

	@Test
	public void testExecutionFailuresLogAsWarningOnTimeout() throws Throwable {
		var e = new Throwable("not an error, will be logged as INFO then WARNING");
		class Box {
			boolean seen;
		}
		var box = new Box();
		this.logListener = record -> {
			if (record.getThrown() == e)
				box.seen = true;
		};
		workload.apply(task -> {
			throw e;
		});

		Thread.sleep(100L);
		assertTrue(workload.isExpired());
		assertTrue(box.seen);
	}

	@Test
	public void testLogListenerLogsShutdown() throws Exception {
		Set<String> expected = new LinkedHashSet<>();
		expected.add("Close requested");
		expected.add("Close reserved, pending = 0");
		expected.add("Graceful shutdown complete");
		expected.add("Executor shutdown requested");
		expected.add("Terminated gracefully");
		expected.add("Executor shutdown complete");
		expected.add("Closed [IuParallelWorkloadController (0/0 of 5 -> 0) expires PT0.05S closed]");
		Set<String> unexpected = new LinkedHashSet<>();

		logListener = record -> {
			final var a = record.getMessage();
			if (!expected.remove(a))
				unexpected.add(a);
		};

		workload.close();

		assertTrue(expected.isEmpty(), expected::toString);
		assertTrue(unexpected.isEmpty(), unexpected::toString);
	}

	@Test
	public void testLogListenerLogsShutdownOnTimeout() throws ExecutionException, InterruptedException {
		Set<String> expected = new LinkedHashSet<>();
		expected.add("Terminating all execution due to parallel workload timeout");
		expected.add("Close requested");
		expected.add("Close reserved, pending = 0");
		expected.add("Graceful shutdown complete");
		expected.add("Executor shutdown requested");
		expected.add("Terminated gracefully");
		expected.add("Executor shutdown complete");
		Set<String> unexpected = new LinkedHashSet<>();

		logListener = record -> {
			final var a = record.getMessage();
			if (!expected.remove(a) && !a.startsWith("Closed "))
				unexpected.add(a);
		};

		Thread.sleep(100L);
		assertTrue(expected.isEmpty(), expected::toString);
		assertTrue(unexpected.isEmpty(), unexpected::toString);
	}

	@Test
	public void testSpawnedPendingCompleted() throws Throwable {
		assertEquals(0, workload.getSpawnedThreadCount());
		assertEquals(0, workload.getPendingTaskCount());
		assertEquals(0, workload.getCompletedTaskCount());
		var pause = workload.apply(a -> Thread.sleep(10L));
		assertEquals(1, workload.getSpawnedThreadCount());
		assertEquals(1, workload.getPendingTaskCount());
		assertEquals(0, workload.getCompletedTaskCount());
		pause.join();
		assertEquals(1, workload.getSpawnedThreadCount());
		assertEquals(0, workload.getPendingTaskCount());
		assertEquals(1, workload.getCompletedTaskCount());
		assertTrue(workload.getElapsed().toMillis() < 50L);
	}

	@Test
	public void testAwaitPending() throws Throwable {
		workload.apply(a -> Thread.sleep(30L));
		assertEquals(1, workload.getPendingTaskCount());
		workload.await();
		assertEquals(0, workload.getPendingTaskCount());
	}

	@Test
	public void testAwaitWithin() throws Throwable {
		workload.apply(a -> Thread.sleep(10L));
		final var finish = workload.apply(a -> workload.await());
		assertEquals(2, workload.getPendingTaskCount());
		finish.join();
		assertEquals(0, workload.getPendingTaskCount());
	}

	@Test
	public void testAwaitExpired() throws Throwable {
		workload.apply(a -> Thread.sleep(100L));
		workload.apply(a -> Thread.sleep(150L));
		assertEquals("Timed out in PT0.05S after completing 0 tasks, 2 tasks remain",
				assertThrows(TimeoutException.class, workload::await).getMessage());
		Thread.sleep(75L);
		assertEquals("Timed out in PT0.05S after completing 1 task, 1 task remaining",
				assertThrows(TimeoutException.class, workload::await).getMessage());
		Thread.sleep(50L);
		assertDoesNotThrow(workload::await);
	}

	@Test
	public void testForceShutdown() throws Throwable {
		class Box {
			boolean finished;
		}
		var box = new Box();
		workload.apply(a -> {
			var now = Instant.now();
			var until = now.plus(Duration.ofSeconds(1L));
			while ((now = Instant.now()).isBefore(until))
				synchronized (this) {
					try {
						this.wait(Math.max(1L, Duration.between(now, until).toMillis()));
					} catch (InterruptedException e) {
					}
				}
			box.finished = true;
		});

		var now = Instant.now();
		var until = now.plus(Duration.ofSeconds(1L));
		workload.close();
		assertTrue(box.finished);
		assertTrue(Instant.now().isAfter(until));
	}

	@Test
	public void testShutdownThreadIsInterrupted() throws Throwable {
		final var current = Thread.currentThread();
		workload.apply(a -> {
			var now = Instant.now();
			var until = now.plus(Duration.ofMillis(75L));
			while ((now = Instant.now()).isBefore(until))
				synchronized (this) {
					try {
						this.wait(Math.max(1L, Duration.between(now, until).toMillis()));
					} catch (InterruptedException e) {
					}
				}
			current.interrupt();
		});

		var now = Instant.now();
		var until = now.plus(Duration.ofMillis(75L));
		assertThrows(InterruptedException.class, workload::close);
		assertFalse(Instant.now().isBefore(until));
	}

	@Test
	public void testShutdownNotVeryGracefully() throws Throwable {
		final var finalTimeout = Duration.ofMillis(100L);
		workload.setGracefulShutdown(Duration.ofMillis(1L));
		workload.setGracefulTermination(Duration.ofMillis(9L));
		workload.setGracefulDestroy(Duration.ofMillis(100L));
		workload.apply(a -> {
			var now = Instant.now();
			var until = now.plus(finalTimeout);
			while ((now = Instant.now()).isBefore(until))
				synchronized (this) {
					final var d = Duration.between(now, until);
					try {
						this.wait(d.toMillis(), d.toNanosPart() % 1_000_000);
					} catch (InterruptedException e) {
					}
				}
		});

		var now = Instant.now();
		var until = now.plus(finalTimeout);

		class Box {
			boolean found, found2;
		}
		final var box = new Box();
		final Set<String> unhandled = new HashSet<>();
		logListener = record -> {
			final var message = record.getMessage();
			if (message.matches("Thread pool failed to terminate gracefully after PT0.0[0-9]{1,8}S, interrupting"))
				box.found = true;
			else if ("Terminated gracefully after interrupt".equals(message))
				box.found2 = true;
			else
				unhandled.add(message);
		};

		workload.close();
		assertTrue(box.found, unhandled::toString);
		assertTrue(box.found2, unhandled::toString);
		assertTrue(Instant.now().isAfter(until));
	}

	@Test
	public void testShutdownMiserably() throws Throwable {
		final var finalTimeout = Duration.ofMillis(150L);
		workload.setGracefulShutdown(Duration.ofMillis(1L));
		workload.setGracefulTermination(Duration.ofMillis(9L));
		workload.setGracefulDestroy(Duration.ofMillis(50L));
		workload.apply(a -> {
			var now = Instant.now();
			var until = now.plus(finalTimeout);
			while ((now = Instant.now()).isBefore(until))
				synchronized (this) {
					final var d = Duration.between(now, until);
					try {
						this.wait(d.toMillis(), d.toNanosPart() % 1_000_000);
					} catch (InterruptedException e) {
					}
				}
		});

		var now = Instant.now();
		var until = now.plus(Duration.ofMillis(60L));
		var finalUntil = now.plus(finalTimeout);

		class Box {
			boolean found;
		}
		final var box = new Box();
		final Set<String> unhandled = new HashSet<>();
		logListener = record -> {
			final var message = record.getMessage();
			if (message.matches("Thread pool failed to terminate gracefully after PT0.0[0-9]{1,8}S, interrupting"))
				box.found = true;
			else
				unhandled.add(message);
		};

		final var timeoutException = assertThrows(TimeoutException.class, workload::close);
		assertTrue(
				timeoutException.getMessage().matches(
						"Graceful thread termination timed out after PT0.0[0-9]{1,8}S, 1 still active after interrupt"),
				timeoutException::getMessage);
		assertTrue(box.found, unhandled::toString);
		assertTrue(Instant.now().isAfter(until));
		assertTrue(Instant.now().isBefore(finalUntil));
	}

	@Test
	public void testAwaitThrowsException() throws Throwable {
		final var e = new Exception();
		workload.apply(task -> {
			throw e;
		});
		assertSame(e, assertThrows(ExecutionException.class, workload::await).getCause());
		assertSame(Throwable.class, e.getSuppressed()[0].getClass());
		assertEquals("caller stack trace", e.getSuppressed()[0].getMessage());
	}

	@Test
	public void testCantAsyncAfterClose() throws Throwable {
		workload.close();
		assertEquals("Closed [IuParallelWorkloadController (0/0 of 5 -> 0) expires PT0.05S closed]",
				assertThrows(RejectedExecutionException.class, () -> workload.apply(t -> {
				})).getMessage());
	}

	@Test
	public void testRunsALotOfTasks() throws Throwable {
		class Box {
			boolean found;
		}
		final var box = new Box();

		workload = new IuParallelWorkloadController("testRunsALotOfTasks", 5, Duration.ofSeconds(5L));
		logListener = record -> {
			if ("used 10000 times".equals(record.getMessage()))
				box.found = true;
		};
		workload.setLog(log);

		for (int i = 0; i < 100000; i++)
			workload.apply(t -> {
			});
		workload.await();

		assertTrue(box.found);
	}

	@Test
	public void testFailedExecutionHandler() throws InterruptedException, TimeoutException, ExecutionException {
		class Box {
			Throwable thrown;
		}
		final var box = new Box();
		final var e = new Throwable();
		workload.setFailedExecutionHandler(t -> {
			box.thrown = t;
		});
		workload.apply(t -> {
			throw e;
		});

		workload.await();
		assertSame(e, box.thrown);
	}

	@Test
	public void testDefaultFailedExecutionHandler() {
		final var e = new Throwable();
		final var e2 = new Throwable();
		workload.defaultHandleFailedExecution(e);
		workload.defaultHandleFailedExecution(e2);
		assertSame(e, assertThrows(ExecutionException.class, workload::await).getCause());
		assertSame(e2, e.getSuppressed()[0]);
	}

}
