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
package iu.logging.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.test.IuTestLogger;
import iu.logging.Bootstrap;
import iu.logging.LogContext;
import iu.logging.LogEnvironment;

@SuppressWarnings("javadoc")
public class ProcessLoggerTest {

	@BeforeEach
	public void resetRequestId() {
		// asserted rather than cleared: a frame left bound by an earlier test is the
		// regression this file exists to catch, so it must fail at the boundary of the
		// test that leaked it instead of contaminating this one
		assertNull(ProcessLogger.fork(), "process left bound by an earlier test");
		assertNull(activeJoinId(), "join ID left bound by an earlier test");

		// each test asserts on generated request IDs, so the sequence must not depend
		// on the order tests run in
		assertDoesNotThrow(() -> {
			final var requestId = ProcessLogger.class.getDeclaredField("REQUEST_ID");
			requestId.setAccessible(true);
			((AtomicLong) requestId.get(null)).set(0L);
		});
	}

	private static Object activeJoinId() {
		return assertDoesNotThrow(() -> {
			final var joinId = ProcessLogger.class.getDeclaredField("ACTIVE_JOIN_ID");
			joinId.setAccessible(true);
			return ((ThreadLocal<?>) joinId.get(null)).get();
		});
	}

	@Test
	public void testSizeToString() {
		assertEquals("0B", ProcessLogger.sizeToString(0L));
		assertEquals("-1B", ProcessLogger.sizeToString(-1L));
		assertEquals("1KiB", ProcessLogger.sizeToString(1024L));
		assertEquals("1024TiB", ProcessLogger.sizeToString(1125899906842624L));
		assertEquals("4.767MiB", ProcessLogger.sizeToString(5000000L));
	}

	@Test
	public void testIntervalToString() {
		assertEquals("00:00.000", ProcessLogger.intervalToString(Duration.ZERO));
		assertEquals("02:00:00.000", ProcessLogger.intervalToString(Duration.ofHours(2L)));
		assertEquals("3 days, 02:00:00.000",
				ProcessLogger.intervalToString(Duration.ofDays(3L).plus(Duration.ofHours(2L))));
	}

	@Test
	public void testMemoryToString() {
		assertEquals("1B/2B/3B - 50% free", ProcessLogger.memoryToString(1L, 2L, 3L));
	}

	@Test
	public void testNotFollowing() {
		ProcessLogger.trace(IdGenerator::generateId); // no-op
		assertNull(ProcessLogger.getActiveContext());
		assertNull(ProcessLogger.export());
		assertNull(ProcessLogger.fork());
	}

	@Test
	public void testJoinNothing() {
		final var joined = new boolean[1];
		ProcessLogger.join(null, () -> {
			joined[0] = true;
			ProcessLogger.trace(IdGenerator::generateId); // no-op
			assertNull(ProcessLogger.getActiveContext());
			assertNull(ProcessLogger.export());
		});
		assertTrue(joined[0], "task not invoked");
		assertNull(ProcessLogger.fork(), "process left bound to the joining thread");
	}

	private static final String NUM_REGEX = "-?[\\d\\.]+";
	private static final String PCT_REGEX = NUM_REGEX + "%";
	private static final String SIZE_REGEX = "(?:0B|" + NUM_REGEX + "[KMG]iB)";
	private static final String MEM_REGEX = SIZE_REGEX + "/" + SIZE_REGEX + "/" + SIZE_REGEX + " - " + PCT_REGEX
			+ " free";
	private static final String INT_REGEX = "\\d{2}:\\d{2}.\\d{3}";
	private static final String TIME_REGEX = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3,9}Z";
	private static final String DURATION_REGEX = "P(?:\\d+D)?T(?:\\d+H)?(?:\\d+M)?(?:\\d+(?:\\.\\d+)?S)?";

	private static final Duration WORKER_TIMEOUT = Duration.ofSeconds(10L);

	private static final String msgRegex(String message) {
		StringBuilder sb = new StringBuilder(message);
		if (sb.length() >= 80)
			sb.setLength(80);
		final var l = sb.length();

		for (int i = 0; i < sb.length(); i++)
			if (sb.charAt(i) == '(' || sb.charAt(i) == ')')
				sb.insert(i++, '\\');
			else if (sb.charAt(i) < ' ')
				sb.setCharAt(i, ' ');
		if (l < 80)
			sb.append("\\.{").append(80 - l).append('}');

		return sb + " " + INT_REGEX + " " + INT_REGEX + " " + SIZE_REGEX + " " + SIZE_REGEX;
	}

	/**
	 * Runs a task on a dedicated thread, waits for it to complete, and reports
	 * assertion failures back to the calling thread.
	 */
	private static void runInWorker(Runnable task) throws Throwable {
		final var error = new Throwable[1];
		final var worker = new Thread(task);
		worker.setUncaughtExceptionHandler((t, e) -> error[0] = e);
		worker.start();

		// bounded: this helper drives the cross-thread handoff, which is exactly the
		// code a future change could block forever. An unbounded join would turn that
		// into a hung build with no test name attached to it
		worker.join(WORKER_TIMEOUT.toMillis());
		assertFalse(worker.isAlive(), () -> "worker " + worker.getName() + " did not complete within "
				+ WORKER_TIMEOUT + "; the forked process handoff is blocked");

		if (error[0] != null)
			throw error[0];
	}

	@Test
	public void testFollowAndExport() {
		final var header = IdGenerator.generateId();
		final var header2 = IdGenerator.generateId();
		final var header3 = IdGenerator.generateId();
		final var context = mock(LogContext.class);
		final var application = IdGenerator.generateId();

		final var message = IdGenerator.generateId();
		final var message2 = IdGenerator.generateId();
		final var message3 = IdGenerator.generateId();
		final var message4 = IdGenerator.generateId() + "\n" + IdGenerator.generateId() + "\n"
				+ IdGenerator.generateId();
		final var message5 = IdGenerator.generateId();
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1: " + header);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1.1: " + header2);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "end 1.1: " + header2);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1.2: " + header3);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "end 1.2: " + header3);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "complete 1: " + header + System.lineSeparator() //
				+ "init: " + TIME_REGEX + " " + MEM_REGEX + System.lineSeparator() //
				+ msgRegex("begin 1: " + header) + System.lineSeparator() //
				+ msgRegex(message) + System.lineSeparator() //
				+ msgRegex(">1.1: " + header2) + System.lineSeparator() //
				+ msgRegex(" " + message2) + System.lineSeparator() //
				+ msgRegex("<1.1: " + header2) + System.lineSeparator() //
				+ msgRegex(message3) + System.lineSeparator() //
				+ msgRegex(">1.2 " + application + ": " + header3) + System.lineSeparator() //
				+ msgRegex(" " + message4) + System.lineSeparator() //
				+ msgRegex(" " + message5) + System.lineSeparator() //
				+ msgRegex("<1.2 " + application + ": " + header3) + System.lineSeparator() //
				+ msgRegex("end 1: " + header) + System.lineSeparator() //
				+ "final: " + DURATION_REGEX + " " + SIZE_REGEX + " " + MEM_REGEX + "(?:\\r?\\n)?" //
		);

		final var env = mock(LogEnvironment.class);
		try (final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			assertDoesNotThrow(() -> ProcessLogger.follow(context, header, () -> {
				ProcessLogger.trace(() -> null);
				ProcessLogger.trace(() -> message);
				assertSame(context, ProcessLogger.getActiveContext());
				ProcessLogger.follow(context, header2, () -> {
					ProcessLogger.trace(() -> message2);
					assertSame(context, ProcessLogger.getActiveContext());
					return null;
				});
				ProcessLogger.trace(() -> message3);

				when(env.getApplication()).thenReturn(application);
				ProcessLogger.follow(context, header3, () -> {
					assertSame(context, ProcessLogger.getActiveContext());
					ProcessLogger.trace(() -> message4);

					final var exp = ProcessLogger.export();
					assertTrue(exp.matches("init: " + TIME_REGEX + " " + MEM_REGEX + System.lineSeparator() //
							+ msgRegex(">1.2 " + application + ": " + header3) + System.lineSeparator() //
							+ msgRegex(" " + message4) + System.lineSeparator() //
					), exp::toString);

					ProcessLogger.trace(() -> message5);
					return null;
				});
				reset(env);

				return null;
			}));
		}
	}

	@Test
	public void testForkAndJoin() {
		final var header = IdGenerator.generateId();
		final var context = mock(LogContext.class);

		final var firstJoined = IdGenerator.generateId();
		final var nestedJoined = IdGenerator.generateId();
		final var afterNestedJoined = IdGenerator.generateId();
		final var secondJoined = IdGenerator.generateId();
		final var message = IdGenerator.generateId();
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1: " + header);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "complete 1: " + header + System.lineSeparator() //
				+ "init: " + TIME_REGEX + " " + MEM_REGEX + System.lineSeparator() //
				+ msgRegex("begin 1: " + header) + System.lineSeparator() //
				+ msgRegex("1> " + firstJoined) + System.lineSeparator() //
				+ msgRegex("2> " + nestedJoined) + System.lineSeparator() //
				+ msgRegex("1> " + afterNestedJoined) + System.lineSeparator() //
				+ msgRegex("3> " + secondJoined) + System.lineSeparator() //
				+ msgRegex(message) + System.lineSeparator() //
				+ msgRegex("end 1: " + header) + System.lineSeparator() //
				+ "final: " + DURATION_REGEX + " " + SIZE_REGEX + " " + MEM_REGEX + "(?:\\r?\\n)?" //
		);

		final var env = mock(LogEnvironment.class);
		try (final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			assertDoesNotThrow(() -> ProcessLogger.follow(context, header, () -> {
				final var forked = ProcessLogger.fork();
				assertNotNull(forked);

				runInWorker(() -> {
					assertNull(ProcessLogger.fork(), "worker thread must start clean");
					ProcessLogger.join(forked, () -> {
						assertSame(context, ProcessLogger.getActiveContext());
						ProcessLogger.trace(() -> firstJoined);

						// a joined task may itself join, i.e. when splitting work further
						ProcessLogger.join(forked, () -> ProcessLogger.trace(() -> nestedJoined));

						ProcessLogger.trace(() -> afterNestedJoined);
					});
					assertNull(ProcessLogger.fork(), "process left bound to the worker thread");
				});

				runInWorker(() -> ProcessLogger.join(forked, () -> ProcessLogger.trace(() -> secondJoined)));

				assertSame(forked, ProcessLogger.fork(), "forking thread lost its process");
				ProcessLogger.trace(() -> message);

				return null;
			}));
		}
	}

	@Test
	public void testJoinOnForkingThread() {
		final var header = IdGenerator.generateId();
		final var context = mock(LogContext.class);

		final var joined = IdGenerator.generateId();
		final var message = IdGenerator.generateId();
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1: " + header);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "complete 1: " + header + System.lineSeparator() //
				+ "init: " + TIME_REGEX + " " + MEM_REGEX + System.lineSeparator() //
				+ msgRegex("begin 1: " + header) + System.lineSeparator() //
				+ msgRegex("1> " + joined) + System.lineSeparator() //
				+ msgRegex(message) + System.lineSeparator() //
				+ msgRegex("end 1: " + header) + System.lineSeparator() //
				+ "final: " + DURATION_REGEX + " " + SIZE_REGEX + " " + MEM_REGEX + "(?:\\r?\\n)?" //
		);

		final var env = mock(LogEnvironment.class);
		try (final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			assertDoesNotThrow(() -> ProcessLogger.follow(context, header, () -> {
				final var forked = ProcessLogger.fork();

				// i.e. a work queue that falls back to running on the submitting thread
				ProcessLogger.join(forked, () -> {
					assertSame(context, ProcessLogger.getActiveContext());
					ProcessLogger.trace(() -> joined);
				});

				assertSame(forked, ProcessLogger.fork(), "forking thread lost its process");
				ProcessLogger.trace(() -> message);

				return null;
			}));
		}
	}

	@Test
	public void testFollowReportsAndUnbindsWhenTheTaskThrows() {
		final var header = IdGenerator.generateId();
		final var context = mock(LogContext.class);
		final var message = IdGenerator.generateId();
		final var thrown = new Throwable(IdGenerator.generateId());

		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1: " + header);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "failed 1: " + header + System.lineSeparator() //
				+ "init: " + TIME_REGEX + " " + MEM_REGEX + System.lineSeparator() //
				+ msgRegex("begin 1: " + header) + System.lineSeparator() //
				+ msgRegex(message) + System.lineSeparator() //
				+ msgRegex("end 1: " + header) + System.lineSeparator() //
				+ "final: " + DURATION_REGEX + " " + SIZE_REGEX + " " + MEM_REGEX + "(?:\\r?\\n)?" //
		);

		final var env = mock(LogEnvironment.class);
		try (final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			assertSame(thrown, assertThrows(Throwable.class, () -> ProcessLogger.follow(context, header, () -> {
				ProcessLogger.trace(() -> message);
				throw thrown;
			})));
		}

		assertNull(ProcessLogger.fork(), "process left bound after an abrupt completion");
	}

	@Test
	public void testNestedFollowReportsAndUnbindsWhenTheTaskThrows() {
		final var header = IdGenerator.generateId();
		final var subHeader = IdGenerator.generateId();
		final var context = mock(LogContext.class);
		final var thrown = new Throwable(IdGenerator.generateId());

		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1: " + header);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1.1: " + subHeader);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "end 1.1: " + subHeader);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "complete 1: " + header
				+ System.lineSeparator() + "(?s).*" + msgRegex("<1.1: " + subHeader) + "(?s).*");

		final var env = mock(LogEnvironment.class);
		try (final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			assertDoesNotThrow(() -> ProcessLogger.follow(context, header, () -> {
				// the enclosing process must be restored, and the sub-process closed out,
				// even though the sub-process completed abruptly
				assertSame(thrown, assertThrows(Throwable.class,
						() -> ProcessLogger.follow(context, subHeader, () -> {
							throw thrown;
						})));
				assertNotNull(ProcessLogger.fork(), "enclosing process not restored");
				return null;
			}));
		}
	}

	@Test
	public void testJoinUnbindsWhenTheTaskThrows() throws Throwable {
		final var header = IdGenerator.generateId();
		final var context = mock(LogContext.class);
		final var thrown = new RuntimeException(IdGenerator.generateId());

		IuTestLogger.allow(ProcessLogger.class.getName(), Level.INFO);

		final var env = mock(LogEnvironment.class);
		try (final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			assertDoesNotThrow(() -> ProcessLogger.follow(context, header, () -> {
				final var forked = ProcessLogger.fork();

				runInWorker(() -> {
					assertSame(thrown, assertThrows(RuntimeException.class, () -> ProcessLogger.join(forked, () -> {
						throw thrown;
					})));
					assertNull(ProcessLogger.fork(), "process left bound to the worker thread");
					assertNull(activeJoinId(), "join ID left bound to the worker thread");
				});

				assertSame(forked, ProcessLogger.fork(), "forking thread lost its process");
				return null;
			}));
		}
	}

	@Test
	public void testJoinRejectsAForeignHandle() {
		final var handle = new Object();
		final var invoked = new boolean[1];

		final var e = assertThrows(IllegalArgumentException.class,
				() -> ProcessLogger.join(handle, () -> invoked[0] = true));
		assertEquals(Object.class.getName() + " did not come from fork(); see IuLogContext.fork()", e.getMessage());
		assertFalse(invoked[0], "task invoked with a handle that did not come from fork()");
		assertNull(ProcessLogger.fork(), "process bound by a rejected handle");
	}

	@Test
	public void testNestedFollowInsideJoinDropsTheJoinPrefix() {
		final var header = IdGenerator.generateId();
		final var nestedHeader = IdGenerator.generateId();
		final var context = mock(LogContext.class);

		final var beforeNested = IdGenerator.generateId();
		final var withinNested = IdGenerator.generateId();
		final var afterNested = IdGenerator.generateId();

		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1: " + header);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1.1: " + nestedHeader);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "end 1.1: " + nestedHeader);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "complete 1: " + header + System.lineSeparator() //
				+ "init: " + TIME_REGEX + " " + MEM_REGEX + System.lineSeparator() //
				+ msgRegex("begin 1: " + header) + System.lineSeparator() //
				+ msgRegex("1> " + beforeNested) + System.lineSeparator() //
				+ msgRegex(">1.1: " + nestedHeader) + System.lineSeparator() //
				// a nested process' own messages belong to neither the forking thread nor the
				// join, so they must not be labelled as part of the join
				+ msgRegex(" " + withinNested) + System.lineSeparator() //
				+ msgRegex("<1.1: " + nestedHeader) + System.lineSeparator() //
				+ msgRegex("1> " + afterNested) + System.lineSeparator() //
				+ msgRegex("end 1: " + header) + System.lineSeparator() //
				+ "final: " + DURATION_REGEX + " " + SIZE_REGEX + " " + MEM_REGEX + "(?:\\r?\\n)?" //
		);

		final var env = mock(LogEnvironment.class);
		try (final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			assertDoesNotThrow(() -> ProcessLogger.follow(context, header, () -> {
				final var forked = ProcessLogger.fork();

				// joined on the forking thread: a mocked static applies only to the thread
				// that installed it, so a worker would build its sub-process against the real
				// LogEnvironment and stamp its markers with whatever application that names
				ProcessLogger.join(forked, () -> {
					ProcessLogger.trace(() -> beforeNested);
					assertDoesNotThrow(() -> ProcessLogger.follow(context, nestedHeader, () -> {
						ProcessLogger.trace(() -> withinNested);
						return null;
					}));
					ProcessLogger.trace(() -> afterNested);
				});

				return null;
			}));
		}
	}

	@Test
	public void testTraceIsCapped() {
		final var header = IdGenerator.generateId();
		final var context = mock(LogContext.class);

		IuTestLogger.allow(ProcessLogger.class.getName(), Level.INFO);

		final var env = mock(LogEnvironment.class);
		try (final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			assertDoesNotThrow(() -> ProcessLogger.follow(context, header, () -> {
				for (int i = 0; i < ProcessLogger.MAX_TRACED_MESSAGES + 100; i++)
					ProcessLogger.trace(IdGenerator::generateId);

				final var exported = ProcessLogger.export();
				final var lines = exported.split("\\R");

				// init, the process' own "begin" marker -- appended directly, so it does not
				// draw on the budget -- the budgeted messages, then the overflow marker in
				// place of everything after them
				assertEquals(ProcessLogger.MAX_TRACED_MESSAGES + 3, lines.length, exported::toString);
				assertTrue(
						lines[lines.length - 1]
								.startsWith("... process trace truncated at " + ProcessLogger.MAX_TRACED_MESSAGES),
						() -> lines[lines.length - 1]);

				return null;
			}));
		}
	}

	@Test
	public void testExportEndingWithSubProcess() {
		final var header = IdGenerator.generateId();
		final var subHeader = IdGenerator.generateId();
		final var context = mock(LogContext.class);

		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1: " + header);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "begin 1.1: " + subHeader);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "end 1.1: " + subHeader);
		IuTestLogger.expect(ProcessLogger.class.getName(), Level.INFO, "complete 1: " + header + System.lineSeparator() //
				+ "init: " + TIME_REGEX + " " + MEM_REGEX + System.lineSeparator() //
				+ msgRegex("begin 1: " + header) + System.lineSeparator() //
				+ msgRegex(">1.1: " + subHeader) + System.lineSeparator() //
				+ msgRegex("<1.1: " + subHeader) + System.lineSeparator() //
				+ msgRegex("end 1: " + header) + System.lineSeparator() //
				+ "final: " + DURATION_REGEX + " " + SIZE_REGEX + " " + MEM_REGEX + "(?:\\r?\\n)?" //
		);

		final var env = mock(LogEnvironment.class);
		try (final var mockBootstrap = mockStatic(Bootstrap.class)) {
			mockBootstrap.when(() -> Bootstrap.getEnvironment()).thenReturn(env);
			assertDoesNotThrow(() -> ProcessLogger.follow(context, header, () -> {
				ProcessLogger.follow(context, subHeader, () -> null);

				// the sub-process is the last entry in the trace: the traversal must resume
				// the exhausted parent iterator without attempting to read past its end
				final var exp = ProcessLogger.export();
				assertTrue(exp.matches("init: " + TIME_REGEX + " " + MEM_REGEX + System.lineSeparator() //
						+ msgRegex("begin 1: " + header) + System.lineSeparator() //
						+ msgRegex(">1.1: " + subHeader) + System.lineSeparator() //
						+ msgRegex("<1.1: " + subHeader) + System.lineSeparator() //
				), exp::toString);

				return null;
			}));
		}
	}

}
