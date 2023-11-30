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

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Unifies control over a bounded queue of {@link IuTaskController controlled
 * tasks}.
 * 
 * <p>
 * This rate limiter is fail-fast. All tasks are expected to complete normally
 * without error. The first error or timeout condition <em>should</em> be thrown
 * when the controlling process reaches its next blocking operation.
 * </p>
 * 
 * <p>
 * Once a fixed limit on incomplete tasks has been reached, new tasks cannot be
 * accepted without first removing and joining the task at the head of the
 * queue. Once any task has timed out or produced an error, any attempt to
 * {@link #accept(IuTaskController)} or {@link #join()} will throw that error.
 * Additional errors encountered after the first error will be suppressed.
 * </p>
 * 
 * <pre>
 * final var limit = new IuRateLimitter(10, Duration.ofSeconds(1L));
 * for (UnsafeRunnable task : tasks)
 * 	limit.accept(workload.accept(task));
 * limit.join();
 * </pre>
 */
public class IuRateLimitter implements UnsafeConsumer<IuTaskController>, IuTaskController {

	private final Instant start = Instant.now();
	private final Instant expires;
	private final int limit;
	private final Deque<IuTaskController> queue = new ConcurrentLinkedDeque<>();
	private final Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
	private final Queue<TimeoutException> timeouts = new ConcurrentLinkedQueue<>();

	/**
	 * Constructor.
	 * 
	 * @param limit   upper limit on the number of tasks that may be pending in the
	 *                queue.
	 * @param timeout timeout interval
	 */
	public IuRateLimitter(int limit, Duration timeout) {
		this(limit, Instant.now().plus(timeout));
	}

	/**
	 * Constructor.
	 * 
	 * @param limit   upper limit on the number of tasks that may be pending in the
	 *                queue.
	 * @param expires instant the workload timeout interval expires
	 */
	public IuRateLimitter(int limit, Instant expires) {
		if (limit <= 0)
			throw new IllegalArgumentException("Limit must be positive");

		this.limit = limit;
		this.expires = expires;
	}

	/**
	 * Checks for a non-blocking failure condition that would prevent a new task
	 * from being {@link #accept(IuTaskController) accepted} for processing.
	 * 
	 * <p>
	 * The method <em>should</em> be called returning control from any blocking
	 * operation, before scheduling a new task to be accepted by this rate limiter.
	 * If the task would be rejected, an error is thrown to prevent it from being
	 * scheduled.
	 * </p>
	 * 
	 * <p>
	 * At the instant of return, the following conditions are guaranteed to have
	 * been true:
	 * </p>
	 * <ul>
	 * <li>No {@link IuTaskController#getError() error} was observed by an
	 * {@link #accept(IuTaskController) accepted} task. {@link ExecutionException}
	 * will be thrown with the first error observed as the cause; additional
	 * observed errors will be suppressed.</li>
	 * <li>No tasks have expired without completing successfully.
	 * {@link TimeoutException} will be thrown; any related task
	 * {@link TimeoutException}s will be suppressed.</li>
	 * </ul>
	 * 
	 * @throws ExecutionException if an {@link IuTaskController#getError() error}
	 *                            has been observed by an
	 *                            {@link #accept(IuTaskController) accepted} task.
	 * @throws TimeoutException   if any task has expired; related task timeouts
	 *                            will be suppressed
	 */
	public void failFast() throws ExecutionException, TimeoutException {
		observeCompletedTasks();

		if (!errors.isEmpty()) {
			final var errorIterator = errors.iterator();
			final var executionException = new ExecutionException(errorIterator.next());
			errorIterator.forEachRemaining(executionException::addSuppressed);
			timeouts.forEach(executionException::addSuppressed);
			throw executionException;
		}

		final var now = Instant.now();
		if (!timeouts.isEmpty() //
				|| !now.isBefore(expires)) {
			final Duration timeout;
			if (now.isBefore(expires))
				timeout = Duration.between(start, now);
			else
				timeout = Duration.between(start, expires);

			final var timeoutException = new TimeoutException("Timed out after " + timeout);
			timeouts.forEach(timeoutException::addSuppressed);
			throw timeoutException;
		}
	}

	/**
	 * Accepts a task for parallel processing, blocking until an error is observed,
	 * a timeout interval expires, the task completes successfully, or until there
	 * is room in the queue.
	 * 
	 * <p>
	 * At the instant of return, the following conditions are guaranteed to have
	 * been true:
	 * </p>
	 * <ul>
	 * <li>{@link #failFast()} returned successfully.</li>
	 * <li>The new task did not observe an error
	 * <ul>
	 * <li>else {@link ExecutionException} is thrown with the observed error as the
	 * cause.</li>
	 * </ul>
	 * </li>
	 * <li>The new task did not {@link IuTaskController#isExpired() expire}
	 * <ul>
	 * <li>else {@link TimeoutException} is thrown.</li>
	 * </ul>
	 * </li>
	 * <li>The task either {@link IuTaskController#isComplete() completed}
	 * {@link IuTaskController#isSuccess() succesfully}, or was accepted for
	 * parallel processing.</li>
	 * <li>Accepted tasks <em>may</em> have been {@link IuTaskController#join()
	 * joined} to create room in the queue.</li>
	 * <li>No more than {@link #IuRateLimitter(int, Instant) limit} accepted tasks,
	 * including the new task, were incomplete.</li>
	 * </ul>
	 * 
	 * @param taskController task controller
	 * @throws ExecutionException   if an observed; the observed error is the cause
	 * @throws InterruptedException from {@link IuTaskController#join()}
	 * @throws TimeoutException     if a timeout interval expires
	 */
	@Override
	public void accept(IuTaskController taskController)
			throws ExecutionException, InterruptedException, TimeoutException {
		while (true) {
			failFast();

			final var complete = taskController.isComplete();
			final var error = taskController.getError();
			if (error != null) {
				errors.offer(error);
				throw new ExecutionException(error);
			}

			if (taskController.isExpired())
				try {
					taskController.join();
					// _should_ throw TimeoutException
				} catch (TimeoutException e) {
					timeouts.add(e);
					throw e;
				}

			if (complete)
				return;

			final IuTaskController overflowTask;
			synchronized (this) {
				if (queue.size() >= limit)
					overflowTask = queue.poll();
				else {
					queue.offer(taskController);
					return;
				}
			}

			joinAndObserve(overflowTask);
		}
	}

	@Override
	public Instant getStart() {
		return start;
	}

	@Override
	public Duration getElapsed() {
		return Duration.between(start, Instant.now());
	}

	@Override
	public Duration getRemaining() {
		observeCompletedTasks();

		final var now = Instant.now();
		if (isExpired())
			if (now.isBefore(expires))
				return Duration.ZERO;
			else
				return Duration.between(now, expires);
		else
			return Duration.between(now, expires);
	}

	@Override
	public Instant getExpires() {
		if (isExpired()) {
			final var now = Instant.now();
			if (now.isBefore(expires))
				return now;
		}
		return expires;
	}

	@Override
	public boolean isComplete() {
		observeCompletedTasks();
		return queue.isEmpty();
	}

	@Override
	public boolean isSuccess() {
		return isComplete() && errors.isEmpty();
	}

	@Override
	public Throwable getError() {
		return errors.peek();
	}

	@Override
	public boolean isExpired() {
		observeCompletedTasks();
		return !timeouts.isEmpty() || !Instant.now().isBefore(expires);
	}

	@Override
	public void join() throws ExecutionException, InterruptedException, TimeoutException {
		failFast();

		IuTaskController task;
		while ((task = queue.poll()) != null)
			joinAndObserve(task);
	}

	@Override
	public void pause() throws InterruptedException, TimeoutException {
		observeCompletedTasks();
		final var task = queue.peekFirst();
		if (task != null)
			task.pause();
	}

	@Override
	public void unpause() {
		observeCompletedTasks();
		queue.forEach(IuTaskController::unpause);
	}

	@Override
	public void interrupt() {
		observeCompletedTasks();
		queue.forEach(IuTaskController::interrupt);
	}

	private void joinAndObserve(IuTaskController taskController)
			throws ExecutionException, InterruptedException, TimeoutException {
		try {
			taskController.join();
		} catch (ExecutionException e) {
			errors.add(e.getCause());
			for (Throwable suppressed : e.getSuppressed())
				errors.offer(suppressed);
			throw e;
		} catch (TimeoutException e) {
			timeouts.add(e);
			throw e;
		}
	}

	private void observeCompleted(IuTaskController task) {
		final var error = task.getError();
		if (error != null)
			errors.offer(error);
		else if (!task.isSuccess())
			errors.offer(new IllegalStateException("Task completed unsuccessfully but didn't provide an error"));
	}

	/**
	 * Observes status (non-blocking) of all queued tasks, flags the process as
	 * expired, observes errors, and removes completed tasks from the queue.
	 */
	private void observeCompletedTasks() {
		final var i = queue.iterator();
		while (i.hasNext()) {
			final var next = i.next();

			if (next.isExpired()) {
				try {
					joinAndObserve(next);
				} catch (TimeoutException | ExecutionException | InterruptedException e) {
					// TimeoutException and ExecutionException are handled by joinAndObserve, silence re-throw
					// InterruptedException is not allowed when IuTaskController#isExpired()
				}
			}

			if (next.isComplete()) {
				i.remove();
				observeCompleted(next);
			}
		}
	}

}
