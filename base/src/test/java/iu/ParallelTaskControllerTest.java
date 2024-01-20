/*
 * Copyright © 2024 Indiana University
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
package iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import edu.iu.IuTaskController;
import edu.iu.UnsafeConsumer;

@SuppressWarnings("javadoc")
public class ParallelTaskControllerTest {

	// values need to be just high enough for represent cold warm-up
	// for stand-alone threads: ~20ms synchronization latency
	private static final Duration TIME_OUT = Duration.ofMillis(500L);
	private static final Duration PER_PAUSE = Duration.ofMillis(50L);
	private static final Duration SYNC_LATENCY = Duration.ofMillis(25L);
	private static final Duration PAUSE_SLO = PER_PAUSE.plus(SYNC_LATENCY);

	// BEGIN Same-Thread Sanity Checks

	@Test
	public void testSimpleTask() throws Throwable {
		final var init = Instant.now();
		final var timeout = Duration.ofMillis(20L);
		final var expires = init.plus(timeout);
		final var task = new ParallelTaskController(expires);
		assertNull(task.getStart());
		assertNull(task.getElapsed());
		assertSame(expires, task.getExpires());

		task.accept(() -> {
			assertFalse(task.getStart().isAfter(Instant.now()));

			final var elapsed = task.getElapsed();
			assertTrue(elapsed.compareTo(Duration.ZERO) >= 0);

			final var remaining = task.getRemaining();
			assertTrue(remaining.compareTo(timeout) <= 0);

			Thread.sleep(1L);

			assertTrue(elapsed.compareTo(task.getElapsed()) < 0);
			assertTrue(remaining.compareTo(task.getRemaining()) > 0);

			assertFalse(task.isExpired());
			assertFalse(task.isSuccess());
		});

		assertFalse(task.isExpired());
		assertTrue(task.isSuccess());

		final var elapsed = task.getElapsed();
		final var remaining = task.getRemaining();
		assertEquals(Duration.ZERO, remaining);

		Thread.sleep(1L);
		assertEquals(elapsed, task.getElapsed());
		assertEquals(remaining, task.getRemaining());

		Thread.sleep(timeout.toMillis());
		assertFalse(task.isExpired());
		assertTrue(task.isSuccess());
	}

	@Test
	public void testAcceptCantAccept() {
		final var task = new ParallelTaskController(Instant.now().plus(Duration.ofMillis(50L)));
		assertThrows(AssertionError.class, () -> task.accept(() -> task.accept(() -> {
		})));
	}

	@Test
	public void testTaskThatThrowsAnException() throws Throwable {
		final var exception = new Exception();
		final var task = new ParallelTaskController(Instant.now().plus(Duration.ofMillis(50L)));
		assertSame(exception, assertThrows(Exception.class, () -> task.accept(() -> {
			throw exception;
		})));
		assertSame(exception, assertThrows(ExecutionException.class, task::join).getCause());
		assertFalse(task.isSuccess());
	}

	@Test
	public void testTaskThatExpires() throws Throwable {
		final var timeout = Duration.ofMillis(5L);
		final var task = new ParallelTaskController(Instant.now().plus(timeout));
		task.accept(() -> {
			Thread.sleep(10L);
			assertTrue(task.getElapsed().compareTo(timeout) <= 0);
			assertTrue(task.getRemaining().isNegative());
		});
		assertTrue(task.isExpired());
		assertTrue(task.isSuccess());
		assertTrue(task.getRemaining().isNegative());
	}

	private void assertWithin(Instant start, Duration from, Duration to) {
		final var since = Duration.between(start, Instant.now());
		assertTrue(from.compareTo(since) <= 0, () -> from + " [<=] " + since + " <= " + to);
		assertTrue(since.compareTo(to) <= 0, () -> from + " <= " + since + " [<=] " + to);
	}

	// BEGIN "Task Controller" UML Sequence Validation

	private void runSequence(UnsafeConsumer<IuTaskController> controller, UnsafeConsumer<IuTaskController> task)
			throws Throwable {
		final var taskController = new ParallelTaskController(Instant.now().plus(TIME_OUT));

		class Box {
			Throwable unexpected;
			boolean done;
		}
		final var box = new Box();

		new Thread(() -> {
			try {
				taskController.accept(() -> task.accept(taskController));
			} catch (Throwable e) {
				box.unexpected = e;
			} finally {
				synchronized (box) {
					box.done = true;
					box.notify();
				}
			}
		}).start();

		Throwable throwing = null;
		try {
			controller.accept(taskController);
			while (!box.done)
				synchronized (box) {
					box.wait(5L);
				}
		} catch (Throwable e) {
			throwing = e;
			throw e;
		} finally {
			if (box.unexpected != null) {
				if (throwing == null)
					throw box.unexpected;
				else
					throwing.addSuppressed(box.unexpected);
			}
		}
	}

	@Test
	public void testTaskThatPausesAndJoins() throws Throwable {
		runSequence(task -> {
			final var beforePause = Instant.now();
			task.pause();
			assertWithin(beforePause, PER_PAUSE, PAUSE_SLO);

			Thread.sleep(PER_PAUSE.toMillis());
			task.unpause();
			assertWithin(beforePause, PER_PAUSE.multipliedBy(2L), PAUSE_SLO.multipliedBy(2L));

			assertFalse(task.isSuccess());
			assertNull(task.getError());

			final var beforeJoined = Instant.now();
			task.join();
			assertWithin(beforeJoined, PER_PAUSE.minus(SYNC_LATENCY), PAUSE_SLO);

			assertFalse(task.isExpired());
			assertTrue(task.isSuccess());

			final var beforeStalePauseAndJoin = Instant.now();
			task.pause();
			task.join();
			final var sinceBeforeStalePauseAndJoin = Duration.between(beforeStalePauseAndJoin, Instant.now());
			assertTrue(sinceBeforeStalePauseAndJoin.toMillis() <= 5L, sinceBeforeStalePauseAndJoin::toString);
		}, task -> {
			Thread.sleep(PER_PAUSE.toMillis());
			task.unpause();

			final var beforePause = Instant.now();
			task.pause();
			assertWithin(beforePause, PER_PAUSE, PAUSE_SLO);

			Thread.sleep(PER_PAUSE.toMillis());
		});
	}

	@Test
	public void testTaskThatGetsCanceled() throws Throwable {
		runSequence(task -> {
			Thread.sleep(PER_PAUSE.toMillis());
			task.interrupt();
		}, task -> assertThrows(InterruptedException.class, task::pause));
	}

	@Test
	public void testTaskThatTimesOutOnPause() throws Throwable {
		runSequence(task -> {
			final var pauseTimeout = assertThrows(TimeoutException.class, task::pause);
			assertTrue(task.isExpired());
			assertFalse(task.isComplete());
			assertTrue(pauseTimeout.getMessage().startsWith("Timed out in PT"), pauseTimeout::getMessage);

			Thread.sleep(SYNC_LATENCY.toMillis());
			final var preCompleteTimeout = assertThrows(TimeoutException.class, task::pause);
			assertFalse(task.isComplete());
			assertTrue(preCompleteTimeout.getMessage().startsWith("Timed out in PT"), preCompleteTimeout::getMessage);

			Thread.sleep(TIME_OUT.toMillis());
			assertTrue(task.isComplete());
			final var postCompleteTimeout = assertThrows(TimeoutException.class, task::pause);
			assertTrue(postCompleteTimeout.getMessage().startsWith("Timed out in PT"), postCompleteTimeout::getMessage);
		}, task -> {
			Thread.sleep(TIME_OUT.multipliedBy(2L).toMillis());
		});
	}

	@Test
	public void testTaskThatTimesOutOnJoin() throws Throwable {
		runSequence(task -> {
			final var joinTimeout = assertThrows(TimeoutException.class, task::join);
			assertTrue(task.isExpired());
			assertFalse(task.isComplete());
			assertTrue(joinTimeout.getMessage().startsWith("Timed out in PT"), joinTimeout::getMessage);

			Thread.sleep(SYNC_LATENCY.toMillis() * 2L);
			final var postCompleteTimeout = assertThrows(TimeoutException.class, task::join);
			assertTrue(task.isComplete());
			assertTrue(postCompleteTimeout.getMessage().startsWith("Timed out in PT"), postCompleteTimeout::getMessage);
		}, task -> {
			Thread.sleep(TIME_OUT.plus(SYNC_LATENCY).toMillis());
		});
	}

}
