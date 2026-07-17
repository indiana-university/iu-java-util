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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Provides access to {@link IuAsynchronousSubject#subscribe() subscription}
 * resources for an {@link IuAsynchronousSubject}.
 * 
 * @param <T> value type
 */
public interface IuAsynchronousSubscription<T> extends AutoCloseable {

	/**
	 * Gets a stream over all values, including those
	 * {@link IuAsynchronousSubject#accept(Object) accepted} after the subscription
	 * was created.
	 * 
	 * @return {@link Stream}
	 */
	Stream<T> stream();

	/**
	 * Determines whether or not the subscription is closed.
	 * 
	 * <p>
	 * Once closed, all remaining values can be advanced without blocking.
	 * </p>
	 * 
	 * @return true if close; else false
	 */
	boolean isClosed();

	/**
	 * Gets an estimated number of values that may be advanced by the stream without
	 * blocking.
	 * 
	 * @return available values
	 */
	long available();

	/**
	 * Pauses execution on the current thread until new values are
	 * {@link IuAsynchronousSubject#accept(Object) accepted}.
	 * 
	 * <p>
	 * This method has no effect on a subscription not yet backed by
	 * {@link IuAsynchronousPipe}.
	 * </p>
	 * 
	 * @param acceptedCount count of newly accepted values to wait for; returns
	 *                      without delay if &lt;= 0
	 * @param timeout       amount of time to wait; <em>should</em> be positive
	 * 
	 * @return the actual number of values accepted while paused
	 * @throws TimeoutException     if the timeout interval expires before
	 *                              {@code receivedCount} values are received
	 * @throws InterruptedException if the current thread is interrupted while
	 *                              waiting for values to be received
	 * @see IuAsynchronousPipe#pauseReceiver(int, Duration)
	 */
	long pause(long acceptedCount, Duration timeout) throws TimeoutException, InterruptedException;

	/**
	 * Pauses execution until either a timeout interval expires or the subject is
	 * closed.
	 * 
	 * <p>
	 * This method has no effect on a subscription not yet backed by
	 * {@link IuAsynchronousPipe}.
	 * </p>
	 * 
	 * @param expires instant the timeout interval expires
	 * 
	 * @return the number of values accepted onto the pipe while paused
	 * @throws InterruptedException if the current thread is interrupted while
	 *                              waiting for the pipe to close
	 * @see IuAsynchronousPipe#pauseReceiver(Instant)
	 */
	long pause(Instant expires) throws InterruptedException;

	/**
	 * Reports an error that occurred that should terminate the subscription.
	 * 
	 * @param e {@link Throwable}
	 */
	void error(Throwable e);

	@Override
	void close();

}
