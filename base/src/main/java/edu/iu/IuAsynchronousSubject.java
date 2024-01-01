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

import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
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

	private static abstract class DelegatingSource<T> {

		private volatile Spliterator<T> delegate;

		private DelegatingSource(Spliterator<T> delegate) {
			if (delegate.estimateSize() > 0)
				this.delegate = delegate;
		}

		protected abstract Consumer<? super T> delegate(Consumer<? super T> action);

		protected abstract Spliterator<T> delegate(Spliterator<T> split);

		protected abstract boolean continueAdvance(Consumer<? super T> action);

		protected abstract void continueForEach(Consumer<? super T> action);

		protected Spliterator<T> delegate() {
			return delegate;
		}

		public synchronized boolean tryAdvance(Consumer<? super T> action) {
			if (delegate != null //
					&& delegate.tryAdvance(delegate(action))) {

				if (delegate.estimateSize() == 0)
					delegate = null;

				return true;
			} else
				delegate = null;

			return continueAdvance(action);
		}

		public synchronized void forEachRemaining(Consumer<? super T> action) {
			if (delegate != null) {
				delegate.forEachRemaining(delegate(action));
				delegate = null;
			}

			continueForEach(action);
		}

		public synchronized Spliterator<T> trySplit() {
			if (delegate == null)
				return null;

			final var split = delegate.trySplit();
			if (split == null)
				return null;
			else
				return delegate(split);
		}
	}

	private static class Source<T> extends DelegatingSource<T> {
		private final Queue<SourceSplit<T>> children = new ConcurrentLinkedDeque<>();
		private final Queue<T> accepted = new ConcurrentLinkedQueue<>();

		private Source(Spliterator<T> delegate) {
			super(delegate);
		}

		@Override
		protected Consumer<? super T> delegate(Consumer<? super T> action) {
			return value -> {
				action.accept(value);

				synchronized (this) {
					accepted.remove(value);
				}
			};
		}

		@Override
		protected Spliterator<T> delegate(Spliterator<T> split) {
			return new SourceSplit<>(split, this);
		}

		@Override
		protected boolean continueAdvance(Consumer<? super T> action) {
			if (isExhausted()) {
				final var value = accepted.poll();
				if (value != null) {
					action.accept(value);
					return true;
				}
			}
			return false;
		}

		@Override
		protected void continueForEach(Consumer<? super T> action) {
			if (isExhausted())
				while (!accepted.isEmpty())
					action.accept(accepted.poll());
		}

		private boolean isExhausted() {
			if (delegate() != null)
				return false;

			final var i = children.iterator();
			while (i.hasNext())
				if (i.next().delegate() == null)
					i.remove();
				else
					return false;

			return true;
		}
	}

	private static class SourceSplit<T> extends DelegatingSource<T> implements Spliterator<T> {
		private final Source<T> source;

		private SourceSplit(Spliterator<T> delegate, Source<T> source) {
			super(delegate);
			this.source = source;
			source.children.offer(this);
		}

		@Override
		protected Consumer<? super T> delegate(Consumer<? super T> action) {
			return source.delegate(action);
		}

		@Override
		protected Spliterator<T> delegate(Spliterator<T> split) {
			return source.delegate(split);
		}

		@Override
		protected boolean continueAdvance(Consumer<? super T> action) {
			return source.continueAdvance(action);
		}

		@Override
		protected void continueForEach(Consumer<? super T> action) {
			source.continueForEach(action);
		}

		@Override
		public long estimateSize() {
			final var delegate = delegate();
			if (delegate != null)
				return delegate.estimateSize();
			else
				return 0L;
		}

		@Override
		public int characteristics() {
			final var delegate = delegate();
			if (delegate != null)
				return delegate.characteristics();
			else
				return SIZED;
		}
	}

	private class Subscriber implements Spliterator<T> {
		private volatile Source<T> source;
		private volatile Throwable error;
		private volatile boolean closed;
		private volatile IuAsynchronousPipe<T> pipe;
		private volatile Spliterator<T> pipedSplit;

		private Subscriber() {
			final var delegate = initialSplitSupplier.get();
			if (delegate.estimateSize() > 0)
				source = new Source<>(delegate);
		}

		@Override
		public Spliterator<T> trySplit() {
			final var sourceSplit = this.source;
			if (sourceSplit != null)
				return sourceSplit.trySplit();

			if (error != null)
				throw IuException.unchecked(error);
			else if (pipedSplit != null)
				return pipedSplit.trySplit();
			else
				return null;
		}

		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			final var sourceSplit = this.source;
			if (sourceSplit != null) {
				final var sourceResult = sourceSplit.tryAdvance(action);

				if (sourceResult) {
					if (sourceSplit.isExhausted() && sourceSplit.accepted.isEmpty())
						bootstrapPipe();

					return true;
				}

				bootstrapPipe();
			}

			if (error != null)
				throw IuException.unchecked(error);
			else if (pipedSplit != null)
				return pipedSplit.tryAdvance(action);
			else
				return false;
		}

		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			final var sourceSplit = this.source;
			if (sourceSplit != null)
				sourceSplit.forEachRemaining(action);

			bootstrapPipe();

			if (error != null)
				throw IuException.unchecked(error);
			else if (pipedSplit != null)
				pipedSplit.forEachRemaining(action);
		}

		@Override
		public long estimateSize() {
			if (pipedSplit == null)
				if (source != null)
					return Long.MAX_VALUE;
				else
					return 0L;
			else
				return pipedSplit.estimateSize();
		}

		@Override
		public int characteristics() {
			if (pipedSplit == null)
				if (source != null)
					return CONCURRENT;
				else
					return IMMUTABLE | SIZED;
			else
				return pipedSplit.characteristics();
		}

		private synchronized void bootstrapPipe() {
			this.source = null;
			if (pipe == null && error == null && !closed) {
				pipe = new IuAsynchronousPipe<>();
				pipedSplit = pipe.stream().spliterator();
			}
		}

		private synchronized void accept(T t) {
			if (source != null)
				source.accepted.offer(t);
			else {
				bootstrapPipe();
				pipe.accept(t);
			}
		}

		private synchronized void close() {
			if (pipe != null)
				pipe.close();
			else
				closed = true;
		}

		private void error(Throwable e) {
			if (pipe != null)
				pipe.error(e);
			else
				error = e;
		}
	}

	private final Supplier<Spliterator<T>> initialSplitSupplier;
	private final Queue<Subscriber> subscribers = new ConcurrentLinkedQueue<>();
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
	 * <strong>Subscribes</strong> to a {@link Stream} that supplies all values
	 * available without blocking then blocks until new values are available or the
	 * <strong>subject</strong> is {@link #close() closed}.
	 * 
	 * @return {@link Stream}
	 */
	public Stream<T> subscribe() {
		if (closed)
			throw new IllegalStateException("closed");

		final var subscriber = new Subscriber();
		subscribers.offer(subscriber);

		return StreamSupport.stream(subscriber, false).onClose(() -> {
			subscriber.close();
			subscribers.remove(subscriber);
		});
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

		subscribers.forEach(subscriber -> subscriber.accept(value));
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
		while (!subscribers.isEmpty())
			e = IuException.suppress(e, () -> subscribers.poll().close());

		if (e != null)
			throw IuException.unchecked(e);
	}

	/**
	 * Reports a fatal error to all <strong>subscribers</strong> and {@link #close()
	 * closes} the <strong>subject</strong>.
	 * 
	 * @param e fatal error
	 */
	public synchronized void error(Throwable e) {
		while (!subscribers.isEmpty())
			IuException.suppress(e, () -> subscribers.poll().error(e));

		closed = true;
	}

}