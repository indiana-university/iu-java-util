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
package edu.iu;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import iu.ParallelTaskController;

/**
 * Controls parallel processing over a bounded workload.
 * 
 * <p>
 * <img alt="UML Class Diagram" src=
 * "doc-files/edu.iu.IuParallelWorkloadController.svg" />
 * </p>
 * 
 * <p>
 * Each controller is {@link AutoCloseable closeable}, timed, and operates in a
 * dedicated task executor. It's expected for all tasks related to the workload
 * to complete within the established a given {@link Duration timeout interval},
 * after which active tasks will be interrupted and related resources will be
 * torn down.
 * </p>
 * 
 * <p>
 * All tasks <em>should</em> handle exceptions. By default, any
 * {@link Throwable} not handled by task execution notifies an immediate stop to
 * all work and for the controller to close. The exception that caused the
 * failure will be thrown as the cause of {@link ExecutionException} from
 * {@link #await()}.
 * </p>
 * 
 * @see IuAsynchronousPipe
 * @see IuTaskController
 * @see IuRateLimitter
 */
public class IuParallelWorkloadController
		implements UnsafeFunction<UnsafeConsumer<IuTaskController>, IuTaskController>, AutoCloseable {

	private final static Logger LOG = Logger.getLogger(IuParallelWorkloadController.class.getName());

	private final Instant start;
	private final Instant expires;
	private final Duration timeout;

	private Duration gracefulShutdown = Duration.ofMillis(500L);
	private Duration gracefulTermination = Duration.ofSeconds(2L);
	private Duration gracefulDestroy = Duration.ofSeconds(3L);
	private Consumer<Throwable> failedExecutionHandler = this::defaultHandleFailedExecution;

	private final int size;
	private final ThreadLocal<Integer> usageCount = new ThreadLocal<>();

	private Logger log = LOG;

	private volatile long spawned;
	private volatile long pending;
	private volatile long completed;

	private volatile Throwable severeFailure;
	private volatile boolean closed;
	private ThreadGroup threadGroup;
	private ThreadPoolExecutor exec;
	private Timer closeTimer;

	private class CloseTask extends TimerTask {

		@Override
		public void run() {
			final Logger log = IuParallelWorkloadController.this.log;
			try {
				log.config(() -> "Terminating all execution due to parallel workload timeout");
				close();
			} catch (Throwable e) {
				log.log(Level.WARNING, e, () -> "Execution errors detected by parallel workload timeout");
				if (severeFailure == null)
					severeFailure = e;
				else
					severeFailure.addSuppressed(e);
			}
		}

	}

	/**
	 * Creates a new workload controller.
	 * 
	 * @param name    descriptive name of the workload, for logging and error
	 *                reporting
	 * @param size    maximum number of parallel tasks to execute at the same time
	 * @param timeout total time to live for all workload-related tasks
	 */
	public IuParallelWorkloadController(String name, int size, Duration timeout) {
		if (timeout.isNegative() || timeout.isZero())
			throw new IllegalArgumentException("timeout must be positive");
		if (size < 1)
			throw new IllegalArgumentException("size must be positive");

		this.size = size;
		this.timeout = timeout;

		threadGroup = new ThreadGroup(name);

		final Timer closeTimer = new Timer(name + "/closeTimer");
		closeTimer.schedule(new CloseTask(), timeout.toMillis());
		this.closeTimer = closeTimer;

		exec = new ThreadPoolExecutor(size, Math.max(5, size * 2), timeout.toMillis(), TimeUnit.MILLISECONDS,
				new SynchronousQueue<>(), r -> {
					long threadNum;
					synchronized (IuParallelWorkloadController.this) {
						threadNum = spawned++;
					}

					Thread thread = new Thread(threadGroup, r, name + '/' + threadNum);
					log.config("Spawned " + thread.getName());
					return thread;
				});

		start = Instant.now();
		expires = start.plus(timeout);
	}

	// BEGIN customization hooks

	/**
	 * Provides an alternative logger to use for tracing workload events.
	 * 
	 * <p>
	 * Uses a class-level delegating default logger until this method provides an
	 * alternative.
	 * </p>
	 * 
	 * @param log alternative logger
	 */
	public void setLog(Logger log) {
		if (this.log != LOG)
			throw new IllegalStateException("Logger already initialized");

		Objects.requireNonNull(log, "log").config(() -> "Logger configured " + this);
		this.log = log;
	}

	/**
	 * Sets the time to wait for worker threads to complete after closing the
	 * controller, before shutting down the thread pool.
	 * 
	 * @param gracefulShutdown {@link Duration}
	 */
	public void setGracefulShutdown(Duration gracefulShutdown) {
		if (Objects.requireNonNull(gracefulShutdown, "gracefulShutdown").compareTo(Duration.ZERO) <= 0)
			throw new IllegalArgumentException("Must be positive");
		this.gracefulShutdown = gracefulShutdown;
	}

	/**
	 * Sets the time to wait for the thread pool to shut down, after closing the
	 * controller and waiting for worker threads to
	 * {@link #setGracefulShutdown(Duration) complete gracefully}, before
	 * interrupting all threads managed by this controller.
	 * 
	 * @param gracefulTermination {@link Duration}
	 */
	public void setGracefulTermination(Duration gracefulTermination) {
		if (Objects.requireNonNull(gracefulTermination, "gracefulTermination").compareTo(Duration.ZERO) <= 0)
			throw new IllegalArgumentException("Must be positive");
		this.gracefulTermination = gracefulTermination;
	}

	/**
	 * Sets the time to wait for the thread pool to shut down, after closing the
	 * controller, waiting for worker threads to
	 * {@link #setGracefulShutdown(Duration) complete gracefully}, and interrupting
	 * all threads managed by this controller.
	 * 
	 * <p>
	 * After the graceful destroy period has passed, all threads managed by this
	 * controller and still running will be abandoned and a {@link Level#WARNING}
	 * will be logged. This condition indicates a possible resource leak.
	 * </p>
	 * 
	 * @param gracefulDestroy {@link Duration}
	 */
	public void setGracefulDestroy(Duration gracefulDestroy) {
		if (Objects.requireNonNull(gracefulDestroy, "gracefulDestroy").compareTo(Duration.ZERO) <= 0)
			throw new IllegalArgumentException("Must be positive");
		this.gracefulDestroy = gracefulDestroy;
	}

	/**
	 * Overrides the default execution handler.
	 *
	 * @param failedExecutionHandler consumer to accept any task execution failures,
	 *                               <em>may</em> call
	 *                               {@link #defaultHandleFailedExecution(Throwable)}
	 *                               to inject default behavior.
	 */
	public void setFailedExecutionHandler(Consumer<Throwable> failedExecutionHandler) {
		this.failedExecutionHandler = failedExecutionHandler;
	}

	/**
	 * Invoked by default if any task throws an exception during execution.
	 * 
	 * @param cause task execution error
	 */
	public synchronized void defaultHandleFailedExecution(Throwable cause) {
		if (severeFailure == null)
			severeFailure = cause;
		else
			severeFailure.addSuppressed(cause);

		closeTimer.schedule(new CloseTask(), 0L);
	}

	/**
	 * Gets a count of threads spawned by this controller.
	 * 
	 * @return thread count
	 */
	public long getSpawnedThreadCount() {
		return spawned;
	}

	/**
	 * Gets a count of tasks submitted to this controller that have not yet
	 * completed.
	 * 
	 * @return pending task count
	 */
	public long getPendingTaskCount() {
		return pending;
	}

	/**
	 * Gets a count of tasks completed by this controller.
	 * 
	 * @return pending task count
	 */
	public long getCompletedTaskCount() {
		return completed;
	}

	/**
	 * Gets the time elapsed since the controller was created.
	 * 
	 * @return time elapsed
	 */
	public Duration getElapsed() {
		return Duration.between(start, Instant.now());
	}

	/**
	 * Gets the time remaining until the controller expires.
	 * 
	 * @return time remaining; may be zero or negative if already
	 *         {@link #isExpired()}
	 */
	public Duration getRemaining() {
		return Duration.between(Instant.now(), expires);
	}

	/**
	 * Gets the instant the timeout interval expires.
	 * 
	 * @return instant the timeout interval expires.
	 */
	public Instant getExpires() {
		return expires;
	}

	/**
	 * Determines whether or not the controller has expired.
	 * 
	 * <p>
	 * Once expired, all threads waiting on the controller will be notified and the
	 * controller will be closed. No more tasks may be submitted once the controller
	 * has expired; all remaining tasks will be interrupted and/or throw
	 * {@link IllegalStateException} with a message indicating a timeout.
	 * </p>
	 * 
	 * @return true if the controller is expired; else false
	 */
	public boolean isExpired() {
		return !Instant.now().isBefore(expires);
	}

	/**
	 * Wait until either all pending tasks have completed, or until the controller
	 * {@link #isExpired() expires}.
	 * 
	 * @throws ExecutionException   If a task used
	 *                              {@link #defaultHandleFailedExecution(Throwable)}
	 *                              to report an execution error. The first error
	 *                              reported will be the cause, and additional
	 *                              errors reported prior to shutdown will be
	 *                              suppressed.
	 * @throws InterruptedException if interrupted waiting for pending tasks to
	 *                              complete. If task execution was interrupted,
	 *                              that condition will be the cause of
	 *                              {@link ExecutionException};
	 *                              {@link InterruptedException} thrown directly
	 *                              indicates this thread, not a task thread, was
	 *                              interrupted.
	 * @throws TimeoutException     if the controller expires before all pending
	 *                              tasks complete normally. If task times out, that
	 *                              condition will be the cause of
	 *                              {@link ExecutionException};
	 *                              {@link TimeoutException} thrown directly
	 *                              indicates this thread, not a task thread, timed
	 *                              out.
	 */
	public synchronized void await() throws ExecutionException, InterruptedException, TimeoutException {
		// deadlock prevention: don't include the current thread if controlling a task
		int min = Thread.currentThread().getThreadGroup() == threadGroup ? 1 : 0;

		IuObject.waitFor(this, () -> severeFailure != null || pending <= min, expires, this::createTimeoutException);

		if (severeFailure != null)
			throw new ExecutionException(severeFailure);
	}

	/**
	 * Submits an asynchronous task for processing.
	 * 
	 * <p>
	 * This method will block until a thread is available for excuting the task, or
	 * until the controller has {@link #isExpired() expired}. Applications
	 * <em>should</em>, however, use {@link IuRateLimitter} or similar to restrict
	 * prevent the need for blocking, and <em>should</em> enforce SLOs on workload
	 * runtimes to ensure algorithm scalability can be calculated to approach but
	 * not reach the upper limit.
	 * </p>
	 * 
	 * <p>
	 * To ensure resources are released gracefully and efficiently when the workload
	 * timeout expires, both the thread the created the task and the thread
	 * executing the task <em>may</em> use {@link IuTaskController} to synchronize
	 * task execution.
	 * </p>
	 * 
	 * @param task task, will be provided a {@link IuTaskController} for
	 *             synchronizing task execution.
	 * @return {@link IuTaskController}
	 * @throws TimeoutException     if the workload expiration timeout is reached
	 *                              before a worker thread is available
	 * @throws InterruptedException if the current thread is interrupted before a
	 *                              worker thread picks up the task
	 */
	@Override
	public IuTaskController apply(UnsafeConsumer<IuTaskController> task) throws InterruptedException, TimeoutException {

		synchronized (this) {
			IuObject.waitFor(this, () -> closed || pending < size, expires, this::createTimeoutException);

			if (closed) {
				final var closedException = new RejectedExecutionException("Closed " + this);
				if (severeFailure != null)
					closedException.initCause(severeFailure);
				throw closedException;
			}

			pending++;
			this.notifyAll();
		}

		ParallelTaskController taskController = new ParallelTaskController(expires);
		exec.submit(new Runnable() {
			private final String descr = task.toString();

			{ // for coverage
				toString();
			}

			@Override
			public void run() {
				if (log.isLoggable(Level.FINE)) {
					Integer use = usageCount.get();
					if (use == null)
						usageCount.set(1);
					else {
						if (++use % 10_000 == 0)
							log.fine("used " + use + " times");
						usageCount.set(use);
					}
				}

				try {
					taskController.accept(() -> {
						log.finer(() -> "start " + descr);
						task.accept(taskController);
						log.finer(() -> "end " + descr + " " + taskController.getElapsed());

						synchronized (IuParallelWorkloadController.this) {
							pending--;
							completed++;
							IuParallelWorkloadController.this.notifyAll();
						}
					});
				} catch (Throwable e) {
					log.log(Level.INFO, e, () -> "fail " + descr + " " + taskController.getElapsed());

					synchronized (IuParallelWorkloadController.this) {
						failedExecutionHandler.accept(e);
						pending--;
						completed++;
						IuParallelWorkloadController.this.notifyAll();
					}
				}
			}

			@Override
			public String toString() {
				return IuParallelWorkloadController.this.toString() + " " + descr;
			}
		});

		return taskController;
	}

	/**
	 * Determines whether or not this controller is closed.
	 * 
	 * @return true if closed; false if still available for accepting new tasks
	 */
	public boolean isClosed() {
		return closed;
	}

	/**
	 * Shuts down all activity and releases resources related to the workload.
	 * 
	 * <p>
	 * This method is invoked from a timer when the controller {@link #isExpired()
	 * expires}. No more tasks can be submitted once the controller is closed.
	 * Repeat calls to this method have no effect.
	 * </p>
	 */
	@Override
	public void close() throws InterruptedException, TimeoutException {
		final ExecutorService exec;
		final ThreadGroup threadGroup;
		final Logger log;
		final Timer closeTimer;

		synchronized (this) {
			if (closed)
				return;

			log = this.log;
			this.log = LOG;

			log.fine("Close requested");

			exec = this.exec;
			threadGroup = this.threadGroup;
			closeTimer = this.closeTimer;

			this.exec = null;
			this.threadGroup = null;
			this.closeTimer = null;
			this.closed = true;

			closeTimer.cancel();

			this.notifyAll();
		}

		log.fine(() -> "Close reserved, pending = " + pending);
		final var endOfGracefulShutdown = Instant.now().plus(gracefulShutdown);
		while (pending > 0) {
			var now = Instant.now();
			if (now.isBefore(endOfGracefulShutdown)) {
				final var waitFor = Duration.between(now, endOfGracefulShutdown);
				synchronized (this) {
					this.wait(waitFor.toMillis(), waitFor.toNanosPart() % 1_000_000);
				}
			} else
				break;
		}

		if (pending > 0)
			// we are no longer tracking pending at this point, but not a WARNING
			// yet since thread pool shutdown should terminate abandoned resources
			log.info(() -> "Graceful shutdown timed out after " + gracefulShutdown);
		else
			log.fine("Graceful shutdown complete");

		try {
			log.fine("Executor shutdown requested");
			exec.shutdown();

			var termination = getRemaining();
			if (gracefulTermination.compareTo(termination) > 0)
				termination = gracefulTermination;

			if (!exec.awaitTermination(termination.toNanos(), TimeUnit.NANOSECONDS)) {
				log.info("Thread pool failed to terminate gracefully after " + termination + ", interrupting");

				threadGroup.interrupt();
				if (exec.awaitTermination(gracefulDestroy.toNanos(), TimeUnit.NANOSECONDS))
					log.info("Terminated gracefully after interrupt");
				else
					throw new TimeoutException(
							"Graceful thread termination timed out after " + termination.plus(gracefulDestroy) + ", "
									+ threadGroup.activeCount() + " still active after interrupt");
			} else
				log.fine("Terminated gracefully");

			log.fine("Executor shutdown complete");

		} finally {
			log.fine("Closed " + this);
		}
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder("[IuParallelWorkloadController ");
		if (!closed)
			sb.append(getElapsed()).append(' ');

		sb.append('(');
		sb.append(pending);
		sb.append('/').append(spawned);
		sb.append(" of ").append(size);
		sb.append(" -> ").append(completed);
		sb.append(") ");

		if (isExpired())
			sb.append("expired ");
		else
			sb.append("expires ");
		sb.append(timeout);

		if (closed)
			sb.append(" closed");
		else
			sb.append("+").append(gracefulShutdown);

		if (threadGroup != null)
			sb.append(" threadGroup: ").append(threadGroup.getName());

		sb.append("]");

		return sb.toString();
	}

	private TimeoutException createTimeoutException() {
		StringBuilder sb = new StringBuilder("Timed out in ");
		sb.append(Duration.between(start, expires));
		sb.append(" after completing ");

		final var completed = this.completed;
		sb.append(completed);
		sb.append(" task");
		if (completed != 1)
			sb.append('s');

		final var pending = this.pending;
		sb.append(", ").append(pending).append(" task");
		if (pending == 1)
			sb.append(" remaining");
		else
			sb.append("s remain");

		return new TimeoutException(sb.toString());
	}

}