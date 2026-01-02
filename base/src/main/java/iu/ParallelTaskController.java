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
package iu;

import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import edu.iu.IuObject;
import edu.iu.IuParallelWorkloadController;
import edu.iu.IuTaskController;
import edu.iu.UnsafeConsumer;
import edu.iu.UnsafeRunnable;

/**
 * Controls execution of a single task.
 * 
 * <p>
 * This class implements the composite aggregation of {@link IuTaskConroller} by
 * {@link IuParallelWorkloadController}.
 * </p>
 */
public class ParallelTaskController implements IuTaskController, UnsafeConsumer<UnsafeRunnable> {

	private final ClassLoader context = Thread.currentThread().getContextClassLoader();
	private final Throwable callerStackTrace = new Throwable("caller stack trace");
	private final Queue<Thread> paused = new ConcurrentLinkedQueue<>();

	private final Instant init = Instant.now();
	private final Instant expires;

	private volatile Instant start;
	private volatile Instant end;
	private volatile Thread thread;
	private volatile Throwable error;

	/**
	 * Creates a parallel task controller.
	 * 
	 * @param expires workload expiration time
	 */
	public ParallelTaskController(Instant expires) {
		this.expires = expires;
	}

	@Override
	public synchronized Instant getStart() {
		return start;
	}

	@Override
	public synchronized Duration getElapsed() {
		if (start == null)
			return null;

		if (end != null)
			return Duration.between(start, end);

		final var now = Instant.now();
		if (now.isAfter(expires))
			return Duration.between(start, expires);

		return Duration.between(start, now);
	}

	@Override
	public synchronized Duration getRemaining() {
		if (end == null)
			return Duration.between(Instant.now(), expires);

		if (end.isBefore(expires))
			return Duration.ZERO;

		return Duration.between(end, expires);
	}

	@Override
	public Instant getExpires() {
		return expires;
	}

	@Override
	public synchronized boolean isExpired() {
		if (end == null)
			return !Instant.now().isBefore(expires);
		else
			return !end.isBefore(expires);
	}

	@Override
	public boolean isComplete() {
		return end != null;
	}

	@Override
	public boolean isSuccess() {
		return isComplete() && error == null;
	}

	@Override
	public Throwable getError() {
		return error;
	}

	@Override
	public void join() throws ExecutionException, InterruptedException, TimeoutException {
		IuObject.waitFor(this, () -> error != null || end != null, expires, this::createTimeoutException);
		if (error != null)
			throw new ExecutionException(error);

		if (!end.isBefore(expires))
			throw createTimeoutException();
	}

	@Override
	public void pause() throws InterruptedException, TimeoutException {
		if (end != null) {
			if (!end.isBefore(expires))
				throw createTimeoutException();
			return;
		}

		final var current = Thread.currentThread();
		paused.add(current);
		IuObject.waitFor(this, () -> !paused.contains(current), expires, this::createTimeoutException);
	}

	@Override
	public synchronized void unpause() {
		paused.clear();
		this.notifyAll();
	}

	@Override
	public void interrupt() {
		paused.forEach(Thread::interrupt);
	}

	@Override
	public void accept(UnsafeRunnable task) throws Throwable {
		final var currentThread = Thread.currentThread();
		final var contextToRestore = currentThread.getContextClassLoader();

		synchronized (this) {
			assert thread == null : thread;
			thread = currentThread;
			start = Instant.now();
			this.notifyAll();
		}

		try {
			currentThread.setContextClassLoader(context);
			task.run();
		} catch (Throwable e) {
			e.addSuppressed(callerStackTrace);

			synchronized (this) {
				error = e;
				this.notifyAll();
			}

			throw e;

		} finally {
			currentThread.setContextClassLoader(contextToRestore);

			synchronized (this) {
				paused.clear();
				end = Instant.now();
				thread = null;
				this.notifyAll();
			}
		}
	}

	private synchronized TimeoutException createTimeoutException() {
		StringBuilder sb = new StringBuilder("Timed out in ");
		sb.append(Duration.between(init, expires));
		final var timeoutException = new TimeoutException(sb.toString());

		if (thread != null) {
			Throwable currentStack = new Throwable("current stack");
			currentStack.setStackTrace(thread.getStackTrace());
			timeoutException.initCause(currentStack);
		}

		return timeoutException;
	}

}
