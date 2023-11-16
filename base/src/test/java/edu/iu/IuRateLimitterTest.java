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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import iu.ParallelTaskController;

@SuppressWarnings("javadoc")
public class IuRateLimitterTest {

	private IuTaskController createTaskController(UnsafeConsumer<IuTaskController> task, Instant expires) {
		final var controller = new ParallelTaskController(expires);
		new Thread(() -> {
			try {
				controller.accept(() -> task.accept(controller));
			} catch (Throwable e) {
			}
		}).start();
		return controller;
	}

	@Test
	public void testLimitMustBePositive() {
		assertEquals("Limit must be positive",
				assertThrows(IllegalArgumentException.class, () -> new IuRateLimitter(-1, Duration.ofMillis(50L)))
						.getMessage());
	}

	@Test
	public void testExpires() throws InterruptedException {
		final var rateLimit = new IuRateLimitter(2, Duration.ofMillis(50L));
		Thread.sleep(50L);
		assertTrue(rateLimit.isExpired());
		assertThrows(TimeoutException.class, rateLimit::join);
	}
	
	@Test
	public void testAlreadySucceeded() throws ExecutionException, InterruptedException, TimeoutException {
		final var rateLimit = new IuRateLimitter(1, Duration.ofMillis(100L));
		final var task = createTaskController(c -> {
		}, rateLimit.getExpires());
		Thread.sleep(50L);
		rateLimit.accept(task);
		assertTrue(rateLimit.isComplete());
		assertTrue(rateLimit.isSuccess());
	}

	@Test
	public void testAcceptAndJoin() throws ExecutionException, InterruptedException, TimeoutException {
		class Box {
			volatile int count;
		}
		final var box = new Box();
		final var rateLimit = new IuRateLimitter(2, Duration.ofMillis(100L));
		for (int i = 0; i < 5; i++) {
			rateLimit.accept(createTaskController(c -> {
				Thread.sleep(5L);
				synchronized (box) {
					box.count++;
				}
			}, rateLimit.getExpires()));
		}
		assertFalse(rateLimit.isComplete());
		assertFalse(rateLimit.isSuccess());
		assertEquals(3, box.count);
		rateLimit.join();
		assertTrue(rateLimit.isComplete());
		assertTrue(rateLimit.isSuccess());
		assertEquals(5, box.count);
	}

	@Test
	public void testFailsFastOnError() throws Throwable {
		final var rateLimit = new IuRateLimitter(1, Duration.ofMillis(50L));
		final var e = new Throwable();
		final var task = createTaskController(c -> {
			throw e;
		}, rateLimit.getExpires());
		rateLimit.accept(task);
		Thread.sleep(10L);
		assertSame(e, assertThrows(ExecutionException.class, rateLimit::join).getCause());
		assertTrue(rateLimit.isComplete());
		assertFalse(rateLimit.isSuccess());
		assertSame(e, rateLimit.getError());
	}

	@Test
	public void testJoinThrows() throws Throwable {
		final var rateLimit = new IuRateLimitter(1, Duration.ofMillis(50L));
		final var e = new Throwable();
		final var task = createTaskController(c -> {
			Thread.sleep(10L);
			throw e;
		}, rateLimit.getExpires());
		rateLimit.accept(task);
		assertSame(e, assertThrows(ExecutionException.class, rateLimit::join).getCause());
	}

	@Test
	public void testStartAndElapsed() throws Throwable {
		final var rateLimit = new IuRateLimitter(1, Duration.ofMillis(50L));
		assertFalse(rateLimit.getStart().isAfter(Instant.now()));
		Thread.sleep(1L);
		assertTrue(rateLimit.getElapsed().compareTo(Duration.ZERO) > 0);
	}

	@Test
	public void testRemaining() throws Throwable {
		final var timeout = Duration.ofMillis(50L);
		final var rateLimit = new IuRateLimitter(1, timeout);
		assertTrue(rateLimit.getRemaining().compareTo(Duration.ZERO) > 0);
		assertTrue(rateLimit.getRemaining().compareTo(timeout) <= 0);
	}

	@Test
	public void testFastTimeout() throws ExecutionException, InterruptedException, TimeoutException {
		final var timeout = Duration.ofMillis(50L);
		final var rateLimit = new IuRateLimitter(1, timeout);
		final var timeoutException = assertThrows(TimeoutException.class,
				() -> rateLimit.accept(createTaskController(c -> {
				}, Instant.now())));
		assertSame(timeoutException,
				assertThrows(TimeoutException.class, () -> rateLimit.failFast()).getSuppressed()[0]);
	}

	@Test
	public void testSlowTimeout() throws ExecutionException, InterruptedException, TimeoutException {
		final var timeout = Duration.ofMillis(50L);
		final var rateLimit = new IuRateLimitter(1, timeout);
		rateLimit.accept(createTaskController(c -> {
			Thread.sleep(100L);
		}, rateLimit.getExpires()));
		final var timeoutException = assertThrows(TimeoutException.class, rateLimit::join);
		assertSame(timeoutException,
				assertThrows(TimeoutException.class, () -> rateLimit.failFast()).getSuppressed()[0]);
	}

	@Test
	public void testAcceptThrowsObservedError() throws Throwable {
		final var timeout = Duration.ofMillis(50L);
		final var rateLimit = new IuRateLimitter(1, timeout);
		final var e = new Throwable();
		final var task = createTaskController(c -> {
			throw e;
		}, rateLimit.getExpires());
		Thread.sleep(25L);
		assertSame(e, assertThrows(ExecutionException.class, () -> rateLimit.accept(task)).getCause());
		assertSame(e, assertThrows(ExecutionException.class, rateLimit::join).getCause());
	}

	@Test
	public void testMisbehavingJoin() throws ExecutionException, InterruptedException, TimeoutException {
		final var task = mock(IuTaskController.class); // <- expensive, ~1s
		when(task.isExpired()).thenReturn(true);
		final var timeout = Duration.ofMillis(50L);
		final var rateLimit = new IuRateLimitter(1, timeout);
		rateLimit.accept(task);
	}

	@Test
	public void testRemainingTracksTimeouts() throws ExecutionException, InterruptedException, TimeoutException {
		final var timeout = Duration.ofMillis(100L);
		final var halfTimeout = timeout.dividedBy(2L);
		final var rateLimit = new IuRateLimitter(1, timeout);
		final var expires = rateLimit.getExpires();
		final var task = createTaskController(c -> {
			Thread.sleep(75L);
		}, Instant.now().plus(halfTimeout));
		rateLimit.accept(task);
		assertTrue(rateLimit.getRemaining().compareTo(halfTimeout) > 0);
		assertTrue(rateLimit.getRemaining().compareTo(timeout) <= 0);
		Thread.sleep(50L);
		assertTrue(rateLimit.getRemaining().isZero());
		assertTrue(rateLimit.getExpires().isBefore(expires));
		Thread.sleep(50L);
		assertTrue(rateLimit.getRemaining().isNegative());
		assertSame(expires, rateLimit.getExpires());
	}

	@Test
	public void testPause() throws InterruptedException, ExecutionException, TimeoutException {
		final var timeout = Duration.ofMillis(200L);
		final var halfTimeout = timeout.dividedBy(2L);
		final var rateLimit = new IuRateLimitter(1, timeout);
		rateLimit.accept(createTaskController(c -> {
			rateLimit.pause();
		}, rateLimit.getExpires()));
		Thread.sleep(halfTimeout.toMillis());
		rateLimit.unpause();
		rateLimit.join();

		final var elapsed = rateLimit.getElapsed();
		assertTrue(elapsed.minus(halfTimeout).toMillis() < 25L, elapsed::toString);
		rateLimit.pause();
		assertTrue(rateLimit.getElapsed().minus(elapsed).toMillis() <= 1L);
	}

	@Test
	public void testInterrupt() throws InterruptedException, ExecutionException, TimeoutException {
		final var timeout = Duration.ofMillis(100L);
		final var halfTimeout = timeout.dividedBy(2L);
		final var rateLimit = new IuRateLimitter(1, timeout);
		rateLimit.accept(createTaskController(c -> {
			c.pause();
			System.out.println(c.getElapsed());
		}, rateLimit.getExpires()));
		Thread.sleep(halfTimeout.toMillis());
		rateLimit.interrupt();
		assertInstanceOf(InterruptedException.class,
				assertThrows(ExecutionException.class, rateLimit::join).getCause());
	}

	@Test
	public void testPassesSuppressed() throws InterruptedException, ExecutionException, TimeoutException {
		final var timeout = Duration.ofMillis(100L);
		final var e = new Throwable();
		final var e2 = new Throwable();
		final var ex = new ExecutionException(e);
		ex.addSuppressed(e2);
		final var task = mock(IuTaskController.class);
		doThrow(ex).when(task).join();

		final var rateLimit = new IuRateLimitter(1, timeout);
		rateLimit.accept(task);

		final var ex2 = assertThrows(ExecutionException.class, rateLimit::join);
		assertSame(e, ex2.getCause());
		assertSame(e2, ex2.getSuppressed()[0]);
	}

	@Test
	public void testObserveInvalidCompletedTask() throws ExecutionException, InterruptedException, TimeoutException {
		final var timeout = Duration.ofMillis(50L);
		final var task = mock(IuTaskController.class);
		when(task.isComplete()).thenReturn(false, true);

		final var rateLimit = new IuRateLimitter(1, timeout);
		rateLimit.accept(task);
		assertInstanceOf(IllegalStateException.class,
				assertThrows(ExecutionException.class, rateLimit::join).getCause());
	}

	@Test
	public void testObserveInvalidExpiredTask() throws ExecutionException, InterruptedException, TimeoutException {
		final var timeout = Duration.ofMillis(50L);
		final var task = mock(IuTaskController.class);
		when(task.isExpired()).thenReturn(false, true);

		final var rateLimit = new IuRateLimitter(1, timeout);
		rateLimit.accept(task);
		rateLimit.join();
	}

}
