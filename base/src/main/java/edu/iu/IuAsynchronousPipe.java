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
import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * {@link Consumer#accept Accepts} values for asynchronous retrieval via
 * {@link Stream}.
 * 
 * <p>
 * <img alt="UML Class Diagram" src="doc-files/edu.iu.IuAsynchronousPipe.svg" />
 * </p>
 * 
 * <p>
 * A common use case is passing large sets of homogeneous values between
 * heterogeneous components.
 * </p>
 * 
 * <p>
 * <img alt="UML Sequence Diagram" src="doc-files/Asynchronous Pipe.svg" />
 * </p>
 * 
 * <p>
 * Within an asynchronous process: the <strong>controlling component</strong>
 * creates a pipe and passes to the <strong>receiving component</strong>. The
 * <strong>receiving component</strong> detaches a {@link #stream() Stream}
 * through which values are retrieved. Once <strong>connected</strong> in this
 * fashion, the <strong>controlling component</strong> can asynchronously
 * {@link #accept(Object) supply} values to the <strong>receiving
 * component</strong>.
 * </p>
 * 
 * <p>
 * Any time after creating the pipe,
 * </p>
 * <ul>
 * <li>the <strong>controlling component</strong> <em>may</em>:
 * <ul>
 * <li>asynchronously retrieve values from an external source and
 * {@link #accept(Object) supply} them to the pipe.</li>
 * <li>{@link #pauseController(int, Duration) pause} until
 * <ul>
 * <li>some or all of the values supplied to the pipe have been
 * <strong>received</strong>.</li>
 * <li>the {@link #stream() stream} is {@link Stream#close() closed} by the
 * <strong>receiving component</strong>.</li>
 * </ul>
 * </li>
 * </ul>
 * </li>
 * <li>The <strong>receiving component</strong> <em>may</em>:
 * <ul>
 * <li>{@link Spliterator#trySplit() split} the stream.</li>
 * <li>{@link #pauseReceiver(int, Duration) pause} until values have been
 * {@link #accept(Object) accepted}.</li>
 * <li>block until values sufficient to complete a {@link Stream terminal
 * operation} have been {@link #accept(Object) accepted}.</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * <p>
 * Although {@link IuAsynchronousPipe} may be used as a simple queue on a single
 * thread, it is intended to be used concurrently on multiple threads in order
 * to distribute load related to loading and consuming large sets of homogeneous
 * values.
 * </p>
 * 
 * @param <T> value type
 * 
 * @see IuParallelWorkloadController
 */
public class IuAsynchronousPipe<T> implements Consumer<T>, AutoCloseable {

	private class Splitr implements Spliterator<T> {

		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			T next;
			synchronized (IuAsynchronousPipe.this) {
				while ((next = queue.poll()) == null && !closed)
					IuException.unchecked(() -> IuAsynchronousPipe.this.wait(500L));
				
				if (error != null)
					throw IuException.unchecked(error);
			}

			if (next != null) {
				receivedCount++;
				action.accept(next);
			}

			synchronized (IuAsynchronousPipe.this) {
				if (!completed //
						&& (completed = closed && queue.isEmpty()))
					streamClose.run();

				IuAsynchronousPipe.this.notifyAll();

				return !completed || next != null;
			}
		}

		@Override
		public Spliterator<T> trySplit() {
			if (!queue.isEmpty()) {
				final Queue<T> newQueue = new ConcurrentLinkedQueue<>();

				synchronized (IuAsynchronousPipe.this) {
					final Queue<T> queue = IuAsynchronousPipe.this.queue;
					IuAsynchronousPipe.this.queue = newQueue;

					receivedCount += queue.size();
					IuAsynchronousPipe.this.notifyAll();

					return queue.spliterator();
				}
			} else
				return null;
		}

		@Override
		public long estimateSize() {
			if (closed)
				return queue.size();
			else
				return Long.MAX_VALUE;
		}

		@Override
		public int characteristics() {
			if (closed)
				return CONCURRENT | SIZED | SUBSIZED;
			else
				return CONCURRENT;
		}
	}

	private volatile Stream<T> stream;
	private volatile Queue<T> queue = new ConcurrentLinkedQueue<>();
	private volatile long acceptedCount;
	private volatile long receivedCount;
	private volatile Throwable error;
	private volatile boolean completed;
	private volatile boolean closed;

	private final Runnable streamClose;

	/**
	 * Default constructor.
	 */
	public IuAsynchronousPipe() {
		final Stream<T> stream = StreamSupport.stream(new Splitr(), false).onClose(() -> {
			close();

			synchronized (this) {
				completed = true;
				this.stream = null;
				notifyAll();
			}
		});

		this.streamClose = stream::close;
		this.stream = stream;
	}

	/**
	 * Gets a sequential {@link Stream} for <strong>receiving</strong> values as
	 * they are {@link #accept(Object) accepted} by the pipe.
	 * 
	 * <p>
	 * For a parallel stream, call {@link Stream#parallel()} on the stream returned
	 * by this method.
	 * </p>
	 * 
	 * <p>
	 * The {@link Stream} returned by this method is not controlled by the pipe and
	 * <em>may</em> be retrieved <em>once</em>. The <strong>receiving
	 * component</strong> in control of the stream, and <em>should</em> call this
	 * method synchronously during initialization from the thread that
	 * {@link #IuAsynchronousPipe() creates the pipe}. All aggregation details are
	 * out scope; all {@link Stream} operations are supported natively and without
	 * interference by an internally managed {@link Spliterator}.
	 * </p>
	 * 
	 * @return {@link Stream}
	 * @throws IllegalStateException if this method is invoked more than once
	 */
	public synchronized Stream<T> stream() throws IllegalStateException {
		Stream<T> stream = this.stream;
		if (stream == null)
			throw new IllegalStateException("Stream has already been retreived");

		this.stream = null;
		return stream;
	}

	/**
	 * Gets a count of all values accepted by the pipe since opening.
	 * 
	 * @return count of accepted values
	 */
	public long getAcceptedCount() {
		return acceptedCount;
	}

	/**
	 * Gets a count of all values received from the pipe since opening.
	 * 
	 * @return count of received values
	 */
	public long getReceivedCount() {
		return receivedCount;
	}

	/**
	 * Gets a count of values accepted by the pipe that have not yet been received.
	 * 
	 * @return count of pending values
	 */
	public synchronized long getPendingCount() {
		return acceptedCount - receivedCount;
	}

	/**
	 * Pauses execution on the current thread until values have been received via
	 * {@link #stream()}.
	 * 
	 * <p>
	 * Typically, the <strong>controlling component</strong> will invoke this method
	 * during a processing loop to manage resource utilization rate relative to the
	 * rate values are being <strong>retrieved</strong>, then invoke
	 * {@link #pauseController(Instant)} to pause until the <strong>receiving
	 * component</strong> has received all values or invoked {@link Stream#close()}.
	 * </p>
	 * 
	 * <p>
	 * The basic <strong>controller</strong> loop example below checks the pending
	 * count before iterating, and if there are 100 pending values in the pipe
	 * pauses until 10 of those values have been have been received, or up to PT1S,
	 * before scanning for and providing more values. Finally, the controller pauses
	 * after all values have been provided until either its
	 * {@link IuParallelWorkloadController workload controller} expires, or all
	 * values have been received. The PT1S pause in this example represents a
	 * keep-alive pulse, for example if the loop is a live iterator over a connected
	 * resource then one business resource per second is typically a sufficient
	 * keep-alive interval.
	 * </p>
	 * 
	 * <pre>
	 * for (final var value : source.getValues()) {
	 * 	pipe.accept(value);
	 * 	if (pipe.getPendingCount() > 100)
	 * 		try {
	 * 			pipe.pauseController(10, Duration.ofSeconds(1L));
	 * 		} catch (TimeoutException e) {
	 * 			// keep-alive
	 * 		}
	 * }
	 * pipe.pauseController(workload.getExpires());
	 * </pre>
	 * 
	 * @param receivedCount count of received values to wait for; returns without
	 *                      delay if &lt;= 0
	 * @param timeout       amount of time to wait; <em>should</em> be positive
	 * 
	 * @return the actual number of values received while paused
	 * @throws TimeoutException     if the timeout interval expires before
	 *                              {@code receivedCount} values are received
	 * @throws InterruptedException if the current thread is interrupted while
	 *                              waiting for values to be received
	 */
	public long pauseController(long receivedCount, Duration timeout) throws TimeoutException, InterruptedException {
		if (receivedCount <= 0)
			return 0;

		final var initialReceivedCount = this.receivedCount;
		final var targetReceivedCount = initialReceivedCount + receivedCount;

		IuObject.waitFor(this, () -> completed //
				|| this.receivedCount >= targetReceivedCount, timeout,
				() -> new TimeoutException("Timed out after receiving " + (this.receivedCount - initialReceivedCount)
						+ " of " + receivedCount + " values in " + timeout));

		if (error != null)
			throw IuException.unchecked(error);

		return this.receivedCount - initialReceivedCount;
	}

	/**
	 * Pauses execution until either a timeout interval expires or all values have
	 * been received from the pipe.
	 * 
	 * <p>
	 * Typically, the <strong>controlling component</strong> will invoke
	 * {@link #pauseController(int, Duration)} during a processing loop to manage
	 * resource utilization rate relative to the rate values are being
	 * <strong>retrieved</strong>, then invoke this method to pause until the
	 * <strong>receiving component</strong> has received all values or invoked
	 * {@link Stream#close()}.
	 * </p>
	 * 
	 * <p>
	 * The basic <strong>controller</strong> loop example below checks the pending
	 * count before iterating, and if there are 100 pending values in the pipe
	 * pauses until 10 of those values have been have been received, or up to PT1S,
	 * before scanning for and providing more values. Finally, the controller pauses
	 * after all values have been provided until either its
	 * {@link IuParallelWorkloadController workload controller} expires, or all
	 * values have been received. The PT1S pause in this example represents a
	 * keep-alive pulse, for example if the loop is a live iterator over a connected
	 * resource then one business resource per second is typically a sufficient
	 * keep-alive interval.
	 * </p>
	 * 
	 * <pre>
	 * for (final var value : source.getValues()) {
	 * 	pipe.accept(value);
	 * 	if (pipe.getPendingCount() > 100)
	 * 		try {
	 * 			pipe.pauseController(10, Duration.ofSeconds(1L));
	 * 		} catch (TimeoutException e) {
	 * 			// keep-alive
	 * 		}
	 * }
	 * pipe.pauseController(workload.getExpires());
	 * </pre>
	 * 
	 * @param expires instant the timeout interval expires
	 * 
	 * @return the number of values received while paused
	 * @throws InterruptedException if the current thread is interrupted while
	 *                              waiting for values to be received
	 */
	public long pauseController(Instant expires) throws InterruptedException {
		if (completed)
			return 0;

		final var initialReceivedCount = this.receivedCount;
		synchronized (this) {
			while (!completed) {
				final var now = Instant.now();
				if (now.isBefore(expires)) {
					final var waitFor = Duration.between(now, expires);
					this.wait(waitFor.toMillis(), waitFor.toNanosPart() % 1_000_000);
				} else
					break;
			}
			
			if (error != null)
				throw IuException.unchecked(error);
		}
		return this.receivedCount - initialReceivedCount;
	}

	/**
	 * Pauses execution on the current thread until new values are
	 * {@link #accept(Object) accepted} onto the pipe.
	 * 
	 * <p>
	 * This method is useful for breaking up output into segments, i.e., via
	 * {@link Spliterator#trySplit()}, as in the example below:
	 * </p>
	 * 
	 * <pre>
	 * final var pipeSplitter = pipe.stream().spliterator();
	 * while (!pipe.isClosed()) {
	 * 	pipe.pauseReceiver(targetSplitSize, workload.getRemaining());
	 * 	Spliterator&lt;String&gt; split = pipeSplitter.trySplit();
	 * 	if (split != null)
	 * 		final var segmentStream = StreamSupport.stream(split, true);
	 * 		// perform terminal operation on segmentStream
	 * }
	 * final var tailStream = StreamSupport.stream(pipeSplitter, true);
	 * // perform terminal operation on tailStream
	 * </pre>
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
	 */
	public long pauseReceiver(long acceptedCount, Duration timeout) throws TimeoutException, InterruptedException {
		if (acceptedCount <= 0)
			return 0;

		final var initialAcceptedCount = this.acceptedCount;
		final var targetAcceptedCount = initialAcceptedCount + acceptedCount;

		IuObject.waitFor(this, () -> closed || this.acceptedCount >= targetAcceptedCount, timeout,
				() -> new TimeoutException("Timed out waiting for " + (this.acceptedCount - initialAcceptedCount)
						+ " of " + acceptedCount + " values in " + timeout));

		if (error != null)
			throw IuException.unchecked(error);

		return this.acceptedCount - initialAcceptedCount;
	}

	/**
	 * Pauses execution until either a timeout interval expires or the pipe has been
	 * closed.
	 * 
	 * <p>
	 * This method is useful for waiting until the <strong>controlling
	 * component</strong> has completed all work before collecting values from the
	 * stream, to give the <strong>receiving component</strong> time-sensitive
	 * control over directly blocking via the stream.
	 * </p>
	 * 
	 * <p>
	 * For example, to give the <strong>controlling component</strong> up to 15
	 * seconds lead time, or for all values to be provided, before collecting from
	 * the pipe:
	 * </p>
	 * 
	 * <pre>
	 * pipe.pauseReceiver(Instant.now().plus(Duration.ofSeconds(15L));
	 * final var values = pipe.stream().collect(aCollector);
	 * </pre>
	 * 
	 * @param expires instant the timeout interval expires
	 * 
	 * @return the number of values accepted onto the pipe while paused
	 * @throws InterruptedException if the current thread is interrupted while
	 *                              waiting for the pipe to close
	 */
	public long pauseReceiver(Instant expires) throws InterruptedException {
		if (closed)
			return 0;

		final var initialAcceptedCount = this.acceptedCount;
		synchronized (this) {
			while (!closed) {
				final var now = Instant.now();
				if (now.isBefore(expires)) {
					final var waitFor = Duration.between(now, expires);
					this.wait(waitFor.toMillis(), waitFor.toNanosPart() % 1_000_000);
				} else
					break;
			}
			
			if (error != null)
				throw IuException.unchecked(error);
		}
		return this.acceptedCount - initialAcceptedCount;
	}

	/**
	 * Determines if this pipe is closed.
	 * 
	 * @return true if closed; else return false
	 */
	public boolean isClosed() {
		return closed;
	}

	/**
	 * Determines if both this pipe and its {@link #stream() stream} are closed.
	 * 
	 * @return true if closed; else return false
	 */
	public boolean isCompleted() {
		return completed;
	}

	/**
	 * Used by the <strong>controlling component</strong> to <strong>supply</strong>
	 * values to the <strong>receiving component</strong> via the {@link Consumer}
	 * interface.
	 * 
	 * <p>
	 * Values {@link #accept(Object) accepted} by the pipe may be received via
	 * {@link #stream()}.
	 * </p>
	 * 
	 * @param value <strong>supplied</strong> by the <strong>controlling
	 *              component</strong>.
	 */
	@Override
	public void accept(T value) {
		if (closed)
			throw new IllegalStateException("closed");

		queue.offer(value);

		synchronized (this) {
			acceptedCount++;
			this.notifyAll();
		}
	}

	/**
	 * Reports an error that occurred on either end of the pipe.
	 * 
	 * <p>
	 * The error will interrupt all activity and cause the pipe to close.
	 * </p>
	 * 
	 * @param e error
	 */
	public synchronized void error(Throwable e) {
		error = e;
		completed = true;
		close();
	}

	/**
	 * Used by the <strong>controlling component</strong> to close the pipe.
	 * 
	 * <p>
	 * The <strong>receiving component</strong> <em>should</em> use
	 * {@link Stream#close()} instead of this method to close the pipe.
	 * </p>
	 * 
	 * <p>
	 * Closing the pipe prevents further values from being {@link #accept(Object)
	 * accepted}, then unpauses all threads.
	 * </p>
	 * 
	 * @see #pauseController(int, Duration)
	 * @see #pauseReceiver(int, Duration)
	 */
	@Override
	public synchronized void close() {
		closed = true;
		if (getPendingCount() <= 0)
			completed = true;
		this.notifyAll();
	}

	@Override
	public String toString() {
		return "IuAsynchronousPipe [acceptedCount=" + acceptedCount + ", receivedCount=" + receivedCount + ", queued="
				+ queue.size() + ", completed=" + completed + ", closed=" + closed + "]";
	}

}
