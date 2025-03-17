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
package edu.iu;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Controls an asynchronous task.
 * 
 * <p>
 * Tasks are managed, e.g., by {@link IuParallelWorkloadController}, which
 * provides a single {@link IuTaskController} instance to both the
 * <strong>controlling thread</strong> and a managed <strong>task
 * thread</strong>. Either thread <em>may</em> distribute this
 * {@link IuTaskController} instance freely to other <strong>controlling
 * threads</strong> threads and <em>may</em> use the interface directly to
 * influence execution of the <strong>task thread</strong>
 * </p>
 * <ul>
 * <li>Any thread may {@link #pause()} execution until {@link #unpause()
 * unpaused} by another thread.</li>
 * <li>Any thread may {@link #interrupt()} all {@link #pause() paused} threads.
 * </li>
 * <li>Any thread <em>may</em> {@link #join() join} the <strong>task
 * thread</strong> to be notified when the task is {@link #isComplete()
 * complete}.</li>
 * <li>Completed tasks <em>must</em> report {@link #isSuccess() success} or
 * {@link #getError() error} status.</li>
 * </ul>
 * 
 * <img alt="UML Sequence Diagram" src="doc-files/Task Controller.svg" />
 */
public interface IuTaskController {

	/**
	 * Gets task execution start time.
	 * 
	 * @return start time; null if the task has not started executing
	 */
	Instant getStart();

	/**
	 * Gets time elapsed since the task started.
	 * 
	 * @return elapsed time; execution time if the task is completed, null if the
	 *         task has not started
	 */
	Duration getElapsed();

	/**
	 * Gets time remaining until the task expires.
	 * 
	 * @return remaining time until the task expires; zero if the task completed
	 *         within the timeout interval, negative to represent post-expiration
	 *         processing time if expired and incomplete or completed after the
	 *         timeout interval.
	 */
	Duration getRemaining();

	/**
	 * Gets the point in time the timeout interval expires, or expired.
	 * 
	 * @return {@link Instant}
	 */
	Instant getExpires();

	/**
	 * Determines if task was still executing when the timeout interval expired; the
	 * task <em>may</em> have {@link #isSuccess() completed successfully} after
	 * expiring.
	 * 
	 * <p>
	 * Attempting to {@link #join()} an {@link #isExpired() expired} task
	 * <em>should</em> result in {@link TimeoutException} being thrown.
	 * </p>
	 * 
	 * @return true if the task was still executing when the timeout interval
	 *         expired; else false, the task completed or is still executing and the
	 *         interval hasn't expired.
	 */
	boolean isExpired();

	/**
	 * Determines if task is completed.
	 * 
	 * @return true if the task has completed: either {@link #isSuccess()} will
	 *         returns true or {@link #getError()} returns a non-null value; false
	 *         indicates the task has not started or is still executing.
	 */
	boolean isComplete();

	/**
	 * Determines if the task completed successfully.
	 * 
	 * @return true if the task completed successfully.
	 */
	boolean isSuccess();

	/**
	 * Gets an error thrown by the task.
	 * 
	 * @return error, if thrown by the task; else null
	 */
	Throwable getError();

	/**
	 * Pauses the current thread until {@link #unpause() unpaused} or until the task
	 * is completed or expired.
	 * 
	 * <p>
	 * The state of the current thread is not guaranteed upon completion. The
	 * application using this interface is responsible for inspecting and/or
	 * updating after the pause is complete.
	 * </p>
	 * 
	 * @throws TimeoutException     If the timeout interval is reached while paused.
	 * @throws InterruptedException If the current thread is interrupted while
	 *                              paused.
	 */
	void pause() throws InterruptedException, TimeoutException;

	/**
	 * Unpauses all threads that invoked {@link #pause()} on this task.
	 */
	void unpause();

	/**
	 * Waits for task execution to complete.
	 * 
	 * <p>
	 * Attempting to {@link #join()} an {@link #isExpired() expired} task
	 * <em>should</em> result in {@link TimeoutException} being thrown.
	 * </p>
	 * 
	 * @throws ExecutionException   If any {@link Throwable} is thrown from the
	 *                              task; the thrown value will be the cause
	 * @throws InterruptedException If the task is interrupted.
	 * @throws TimeoutException     If the timeout interval expires.
	 */
	void join() throws ExecutionException, InterruptedException, TimeoutException;

	/**
	 * Interrupts all threads that invoked {@link #pause()} or {@link #join()} on
	 * this task.
	 */
	void interrupt();

}
