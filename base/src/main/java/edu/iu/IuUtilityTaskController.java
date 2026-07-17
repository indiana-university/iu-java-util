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

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Controller for executing <strong>utility tasks</strong>.
 * 
 * <p>
 * A <strong>utility task</strong> is timed, potentially blocking, and
 * non-business critical. For example, establishing a database connection.
 * <strong>Utility tasks</strong> are not required to complete before the JVM
 * can exit.
 * </p>
 * 
 * <p>
 * To ensure threads are tied to the lowest level parent group possible, the
 * utility executor <em>should</em> be initialized by the main bootstrap method
 * or closest reasonable equivalent, i.e., ServletListener, as in the example
 * below.
 * </p>
 * 
 * <pre>
 * Class.forName(IuUtilityWorkloadController.class.getName());
 * </pre>
 * 
 * <p>
 * <strong>Implementation Note:</strong> The executor backing this workload
 * controller is deliberately small and cannot be configured. <strong>Utility
 * tasks</strong> <strong>should</strong> typically complete in 5ms or less
 * under normal environment conditions, and only run into exhaustion issues when
 * downstream resources slow down. The small size of the utility executor allow
 * {@link RejectedExecutionException} provide fail-fast behavior as preferable
 * to destabilizing the entire application.
 * </p>
 * 
 * @param <T> result type
 */
public class IuUtilityTaskController<T> implements UnsafeSupplier<T> {

	private static final Timer TIMER;
	private static final ExecutorService EXEC;

	static {
		final var threadGroup = new ThreadGroup("iu-java-util");
		final var threadFactory = new ThreadFactory() {
			private int c;

			@Override
			public Thread newThread(Runnable r) {
				final var thread = new Thread(threadGroup, r, "iu-java-util/" + ++c);
				thread.setDaemon(true);
				return thread;
			}
		};
		TIMER = new Timer("iu-java-util", true);
		EXEC = new ThreadPoolExecutor(4, 16, 15L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(256, false),
				threadFactory);
	}

	private final Instant expires;
	private final TimerTask interrupt;
	private volatile Thread thread;
	private volatile Optional<T> result;
	private volatile Throwable error;

	/**
	 * Runs a <strong>utility task</strong>.
	 * 
	 * @param task    <strong>utility task</strong>
	 * @param expires {@link Instant} the task must be completed by
	 * @throws TimeoutException at {@code expires} if the task has not completed
	 *                          normally
	 * @throws Throwable        if thrown from the task
	 */
	public static void doBefore(UnsafeRunnable task, Instant expires) throws TimeoutException, Throwable {
		new IuUtilityTaskController<>(() -> {
			task.run();
			return null;
		}, expires).get();
	}

	/**
	 * Gets a value from a <strong>utility factory</strong>.
	 * 
	 * @param <T>     value type
	 * 
	 * @param factory <strong>utility factory</strong>
	 * @param expires {@link Instant} the value must be supplied by
	 * @return value
	 * @throws TimeoutException at {@code expires} if the value has not been
	 *                          supplied
	 * @throws Throwable        if thrown from the factory
	 */
	public static <T> T getBefore(UnsafeSupplier<T> factory, Instant expires) throws TimeoutException, Throwable {
		return new IuUtilityTaskController<>(factory, expires).get();
	}

	/**
	 * Creates a <strong>utility task</strong> controller.
	 * 
	 * @param task    {@link UnsafeSupplier} <strong>utility task</strong>
	 * @param expires {@link Instant} the task will expire
	 */
	public IuUtilityTaskController(UnsafeSupplier<T> task, Instant expires) {
		this.expires = expires;

		final var context = Thread.currentThread().getContextClassLoader();
		interrupt = new TimerTask() {
			@Override
			public void run() {
				synchronized (IuUtilityTaskController.this) {
					thread.interrupt();
				}
			}
		};

		final var callerStackTrace = new Throwable("caller stack trace");
		TIMER.schedule(interrupt, Date.from(expires.plusMillis(250L)));
		EXEC.submit(new Runnable() {
			@Override
			public void run() {
				thread = Thread.currentThread();

				final var restoreContext = thread.getContextClassLoader();
				try {
					thread.setContextClassLoader(context);
					result = Optional.ofNullable(task.get());
				} catch (Throwable e) {
					e.addSuppressed(callerStackTrace);
					error = e;
				} finally {
					thread.setContextClassLoader(restoreContext);
					interrupt.cancel();

					synchronized (IuUtilityTaskController.this) {
						thread = null;
						IuUtilityTaskController.this.notifyAll();
					}
				}
			}
		});
	}

	@Override
	public T get() throws Throwable {
		IuObject.waitFor(this, () -> result != null || error != null, expires);
		if (error != null)
			throw error;
		else
			return result.orElse(null);
	}

}
