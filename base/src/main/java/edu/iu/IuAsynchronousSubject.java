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

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Provides <strong>subscriber</strong> {@link Stream} instances over a shared
 * source <strong>subject</strong>.
 * 
 * <p>
 * Each <strong>subject</strong> is backed externally by a <strong>controlling
 * component</strong> that:
 * </p>
 * <ul>
 * <li>Provides an <strong>initial {@link Spliterator split}</strong> of
 * available values at the point in time a new {@link #subscribe() subscription}
 * is created.</li>
 * <li>{@link #accept(Object) Accepts} new values to be distributed to active
 * <strong>subscribers</strong>.</li>
 * </ul>
 * 
 * <p>
 * After the <strong>initial {@link Spliterator split}</strong> is created, but
 * before the values available at {@link #subscribe() subscription} time have
 * all been {@link Spliterator#tryAdvance(Consumer) advanced}, newly
 * {@link #accept(Object) accepted} values are offered to a queue. Values
 * <em>may</em> be removed from the queue if also advanced from the initial
 * split. Queued values will be polled and advanced after the last split of the
 * initial split advances its last value.
 * </p>
 * 
 * <p>
 * After all queued values have been advanced, the <strong>subscriber</strong>
 * transitions to a dedicated {@link IuAsynchronousPipe} and passes new values
 * through to {@link IuAsynchronousPipe#accept(Object)}.
 * </p>
 * 
 * <p>
 * The <strong>subject</strong> makes no guarantee to the <strong>controlling
 * component</strong> when or if a <strong>subscriber</strong> will transition
 * from non-blocking access to available values, to potentially blocking access
 * via {@link IuAsynchronousPipe}. It is, however, guaranteed that all values
 * available from the point in time the <strong>subscriber</strong> begins
 * processing the {@link Stream} until <strong>unsubscribing</strong> will be
 * supplied exactly once regardless of how the value is actually delivered.
 * </p>
 * 
 * <img src="doc-files/IuAsynchronousSubject.svg" alt="UML Sequence Diagram">
 * 
 * <p>
 * New values <em>should</em> be {@link #accept(Object) accepted} before
 * appending the external source, to gracefully avoid a potential race condition
 * between the initial split and an internal appended values queue. For example:
 * </p>
 * 
 * <pre>
 * class MyControllingComponent&lt;T&gt; implements Consumer&lt;T&gt; {
 * 	private final Queue&lt;T&gt; queue = new ConcurrentLinkedQueue&lt;&gt;();
 * 	private final IuAsynchronousSubject&lt;T&gt; subject =
 * 		new IuAsynchronousSubject&lt;&gt;(queue::spliterator);
 * 
 * 	{@literal @}Override
 * 	public void accept(T t) {
 * 		subject.accept(t);
 * 		queue.offer(t);
 * 	}
 * }
 * </pre>
 * 
 * <p>
 * When the <strong>source</strong> is sequential, <strong>subscribers</strong>
 * <em>may</em> expect to receive values in the order supplied.
 * </p>
 * 
 * <p>
 * Each <strong>subscriber</strong> {@link Stream} provides all values that may
 * be retrieved without blocking, then transitions to an
 * {@link IuAsynchronousPipe} managed by the <strong>subject</strong> to block
 * until new values become available.
 * </p>
 * 
 * <p>
 * A <strong>subscriber's</strong> {@link Stream} may be {@link Stream#close()
 * closed} without affecting the status of the <strong>controlling
 * component</strong>, or of any other <strong>subscriber</strong> {@link Stream
 * streams}.
 * </p>
 * 
 * <p>
 * This class is thread-safe and intended for use by high-volume
 * parallel-processing workloads.
 * </p>
 * 
 * @param <T> value type
 * @see IuAsynchronousPipe
 */
public class IuAsynchronousSubject<T> implements Consumer<T>, AutoCloseable {

	private class SourceSplit implements Spliterator<T> {
		private final Subscriber subscriber;
		private volatile Spliterator<T> delegate;

		private SourceSplit(Spliterator<T> delegate, Subscriber subscriber) {
			this.subscriber = subscriber;
			this.delegate = delegate;
			subscriber.children.offer(this);
		}

		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			if (delegate != null //
					&& delegate.tryAdvance(subscriber.cancelAcceptedValueAfterAction(action))) {

				if (delegate.estimateSize() == 0)
					delegate = null;

				return true;
			} else
				delegate = null;

			return subscriber.continueAdvance(action);
		}

		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			if (delegate != null) {
				delegate.forEachRemaining(subscriber.cancelAcceptedValueAfterAction(action));
				delegate = null;
			}

			subscriber.continueForEach(action);
		}

		@Override
		public Spliterator<T> trySplit() {
			if (delegate == null)
				return null;

			final var split = delegate.trySplit();
			if (split == null)
				return null;
			else
				return new SourceSplit(split, subscriber);
		}

		@Override
		public long estimateSize() {
			var count = 0L;

			final var delegate = this.delegate;
			if (delegate != null)
				count += delegate.estimateSize();

			if (subscriber.isExhausted() //
					|| (subscriber.children.size() == 1 //
							&& subscriber.children.contains(this)))
				count += subscriber.acceptedSize();

			return count;
		}

		@Override
		public int characteristics() {
			final var delegate = this.delegate;
			if (delegate != null)
				return delegate.characteristics();
			else
				return SIZED;
		}

	}

	private class Subscriber implements Consumer<T>, Spliterator<T>, IuAsynchronousSubscription<T> {
		private final Stream<T> stream;
		private final Runnable cancel;
		private final Queue<SourceSplit> children = new ConcurrentLinkedDeque<>();
		private final Queue<T> accepted = new ConcurrentLinkedQueue<>();
		private volatile Spliterator<T> delegate;
		private volatile long acceptedCount;
		private volatile Throwable error;
		private volatile boolean closed;
		private volatile IuAsynchronousPipe<T> pipe;
		private volatile Spliterator<T> pipedSplit;

		private Subscriber() {
			final var delegate = initialSplitSupplier.get();
			if (delegate.estimateSize() > 0)
				this.delegate = delegate;

			final var ref = new WeakReference<Consumer<T>>(this);
			listeners.add(ref);
			cancel = () -> listeners.remove(ref);

			stream = StreamSupport.stream(this, false).onClose(this::close);
		}

		@Override
		public void accept(T t) {
			synchronized (this) {
				if (canAccept())
					accepted.offer(t);
				else {
					bootstrapPipe();
					pipe.accept(t);
				}
				acceptedCount++;
				this.notifyAll();
			}
		}

		@Override
		public Spliterator<T> trySplit() {
			final var delegate = this.delegate;
			if (delegate != null) {
				final var split = delegate.trySplit();
				if (split != null)
					return new SourceSplit(split, this);
			}

			if (error != null)
				throw IuException.unchecked(error);
			else if (pipedSplit != null)
				return pipedSplit.trySplit();
			else
				return null;
		}

		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			final var delegate = this.delegate;
			if (delegate != null //
					&& delegate.tryAdvance(cancelAcceptedValueAfterAction(action))) {

				if (delegate.estimateSize() == 0)
					this.delegate = null;

				return true;
			} else
				this.delegate = null;

			if (continueAdvance(action)) {
				if (!canAdvance())
					bootstrapPipe();

				return true;
			}

			bootstrapPipe();

			if (error != null)
				throw IuException.unchecked(error);
			else if (pipedSplit != null)
				return pipedSplit.tryAdvance(action);
			else
				return false;
		}

		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			if (delegate != null) {
				delegate.forEachRemaining(cancelAcceptedValueAfterAction(action));
				delegate = null;
			}

			continueForEach(action);

			bootstrapPipe();

			if (error != null)
				throw IuException.unchecked(error);
			else if (pipedSplit != null)
				pipedSplit.forEachRemaining(action);
		}

		@Override
		public synchronized long available() {
			var count = 0;

			if (delegate != null //
					&& delegate.hasCharacteristics(SIZED))
				count += delegate.estimateSize();

			if (areChildrenExhausted())
				count += acceptedSize();

			if (pipe != null)
				count += pipe.getPendingCount();

			return count;
		}

		@Override
		public synchronized long estimateSize() {
			if (pipedSplit == null) {
				if (!closed && error == null)
					return Long.MAX_VALUE;
				else
					return available();
			} else
				return pipedSplit.estimateSize();
		}

		@Override
		public int characteristics() {
			if (pipedSplit == null)
				if (!isClosed())
					return CONCURRENT;
				else
					return IMMUTABLE | SIZED;
			else
				return pipedSplit.characteristics();
		}

		@Override
		public Stream<T> stream() {
			return stream;
		}

		@Override
		public long pause(long acceptedCount, Duration timeout) throws TimeoutException, InterruptedException {
			if (acceptedCount <= 0L)
				return 0L;

			final var now = Instant.now();
			final var expires = now.plus(timeout);

			final var initCount = this.acceptedCount;
			final var targetCount = initCount + acceptedCount;
			IuObject.waitFor(this, //
					() -> isClosed() //
							|| this.acceptedCount >= targetCount //
					, expires);

			return this.acceptedCount - initCount;
		}

		@Override
		public long pause(Instant expires) throws InterruptedException {
			final var initCount = acceptedCount;

			synchronized (this) {
				while (!isClosed()) {
					final var now = Instant.now();
					if (now.isBefore(expires)) {
						final var waitFor = Duration.between(now, expires);
						this.wait(waitFor.toMillis(), waitFor.toNanosPart() % 1_000_000);
					} else
						break;
				}
			}

			return acceptedCount - initCount;
		}

		@Override
		public synchronized boolean isClosed() {
			if (pipe == null)
				return closed || error != null;
			else
				return pipe.isClosed();
		}

		@Override
		public synchronized void error(Throwable e) {
			if (pipe != null)
				pipe.error(e);
			else
				error = e;

			this.notifyAll();
		}

		@Override
		public synchronized void close() {
			cancel.run();

			if (pipe != null)
				pipe.close();
			else
				closed = true;

			this.notifyAll();
		}

		private boolean isExhausted() {
			if (delegate != null)
				return false;

			return areChildrenExhausted();
		}

		private boolean canAdvance() {
			return !isExhausted() || !accepted.isEmpty();
		}

		private boolean canAccept() {
			return delegate != null || !accepted.isEmpty();
		}

		private boolean areChildrenExhausted() {
			final var i = children.iterator();
			while (i.hasNext())
				if (i.next().delegate == null)
					i.remove();
				else
					return false;

			return true;
		}

		private int acceptedSize() {
			return accepted.size();
		}

		private Consumer<? super T> cancelAcceptedValueAfterAction(Consumer<? super T> action) {
			return value -> {
				action.accept(value);

				synchronized (this) {
					accepted.remove(value);
					this.notifyAll();
				}
			};
		}

		private boolean continueAdvance(Consumer<? super T> action) {
			if (isExhausted()) {
				final var value = accepted.poll();
				if (value != null) {
					action.accept(value);
					synchronized (this) {
						this.notifyAll();
					}
					return true;
				}
			}
			return false;
		}

		private void continueForEach(Consumer<? super T> action) {
			if (isExhausted()) {
				while (!accepted.isEmpty()) {
					action.accept(accepted.poll());
					synchronized (this) {
						this.notifyAll();
					}
				}
			}
		}

		private synchronized void bootstrapPipe() {
			if (pipe == null //
					&& error == null //
					&& !closed) {
				pipe = new IuAsynchronousPipe<>();
				pipedSplit = pipe.stream().spliterator();
			}
		}

	}

	private final Supplier<Spliterator<T>> initialSplitSupplier;
	private final Queue<Reference<Consumer<T>>> listeners = new ConcurrentLinkedQueue<>();
//	private final Queue<Subscriber> subscribers = new ConcurrentLinkedQueue<>();
	private boolean closed;

	/**
	 * Creates a new <strong>subject</strong>.
	 * 
	 * @param initialSplitSupplier supplies the initial split backing new
	 *                             <strong>subscriber</strong> streams.
	 */
	public IuAsynchronousSubject(Supplier<Spliterator<T>> initialSplitSupplier) {
		this.initialSplitSupplier = initialSplitSupplier;
	}

	/**
	 * Registers a <strong>listener</strong>.
	 * 
	 * @param listener {@link Consumer}, will be provided all values available on
	 *                 the subject, in order, before return. After return,
	 *                 {@link Consumer#accept(Object)} will be invoked inline each
	 *                 time a new value is {@link #accept(Object) accepted} by the
	 *                 <strong>subject</strong>.
	 * @return thunk for canceling future calls to {@link Consumer#accept(Object)}
	 *         on the listener.
	 */
	public Runnable listen(Consumer<T> listener) {
		final var split = initialSplitSupplier.get();
		while (split.tryAdvance(listener))
			;

		final var ref = new WeakReference<>(listener);
		listeners.add(ref);
		return () -> listeners.remove(ref);
	}

	/**
	 * <strong>Subscribes</strong> to a {@link Stream} that supplies all values
	 * available without blocking then blocks until new values are available or the
	 * <strong>subject</strong> is {@link #close() closed}.
	 * 
	 * @return {@link IuAsynchronousSubscription}
	 */
	public IuAsynchronousSubscription<T> subscribe() {
		if (closed)
			throw new IllegalStateException("closed");

		return new Subscriber();
	}

	/**
	 * Distributes a value to all potentially blocking <strong>subscribers</strong>
	 * that have completed the transition to an {@link IuAsynchronousPipe}.
	 * 
	 * <p>
	 * This method does not supply values to <strong>subscribers</strong> that
	 * haven't yet completed the transition. The <strong>controlling
	 * component</strong> is responsible for independently supplying those values to
	 * it's {@link Spliterator}-supplying backing <strong>source</strong>.
	 * </p>
	 * 
	 * @param value value to supply to all <strong>subscribers</strong>
	 */
	@Override
	public synchronized void accept(T value) {
		if (closed)
			throw new IllegalStateException("closed");

		final var i = listeners.iterator();
		while (i.hasNext()) {
			final var ref = i.next();
			final var listener = ref.get();
			if (listener == null)
				i.remove();
			else
				listener.accept(value);
		}
	}

	/**
	 * Closes the <strong>subject</strong>.
	 * 
	 * <p>
	 * Once closed:
	 * </p>
	 * <ul>
	 * <li>Existing <strong>subscribers</strong> may finish retrieving all values
	 * already supplied</li>
	 * <li>Blocking <strong>subscriber</strong> {@link Stream}s will be terminated
	 * gracefully</li>
	 * <li>No new <strong>subscribers</strong> may be created</li>
	 * <li>No new <strong>values</strong> may be supplied</li>
	 * </ul>
	 */
	@Override
	public synchronized void close() {
		if (closed)
			return;

		closed = true;

		Throwable e = null;
		while (!listeners.isEmpty())
			e = IuException.suppress(e, () -> {
				final var ref = listeners.poll();
				final var listener = ref.get();
				if (listener instanceof AutoCloseable closeableListener)
					closeableListener.close();
			});

		if (e != null)
			throw IuException.unchecked(e);
	}

	/**
	 * Reports a fatal error to all <strong>subscribers</strong> and {@link #close()
	 * closes} the <strong>subject</strong>.
	 * 
	 * @param error fatal error
	 */
	public synchronized void error(final Throwable error) {
		Throwable e = null;
		while (!listeners.isEmpty())
			e = IuException.suppress(e, () -> {
				final var ref = listeners.poll();
				final var listener = ref.get();
				if (listener instanceof Subscriber subscriber)
					subscriber.error(error);
				else if (listener instanceof AutoCloseable closeableListener)
					closeableListener.close();
			});

		closed = true;

		if (e != null)
			throw IuException.unchecked(e);
	}

}
