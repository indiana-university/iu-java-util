package edu.iu;

import java.util.Queue;
import java.util.Spliterator;
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
 * <strong>Subjects</strong> are backed externally by a <strong>controlling
 * component</strong>, which is responsible for both appending new values to a
 * <strong>source</strong> capable of supplying a {@link Spliterator}, and
 * independently {@link #accept(Object) supplying} the same values to be
 * distributed to all active subscribers. The <strong>subject</strong> makes no
 * guarantee to the <strong>controlling component</strong> when or if a
 * <strong>subscriber</strong> will transition from non-blocking access to
 * available values to potentially blocking access via
 * {@link IuAsynchronousPipe}. It is, however, guaranteed that all values
 * available from the point in time the <strong>subscriber</strong> begins
 * processing the {@link Stream} until <strong>unsubscribing</strong> will be
 * supplied exactly once.
 * </p>
 * 
 * <p>
 * For example:
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
 * 		queue.offer(t);
 * 		subject.accept(t);
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

	private final Supplier<Spliterator<T>> source;
	private final Queue<Subscriber> subscribers = new ConcurrentLinkedQueue<>();
	private boolean closed;

	private class Subscriber implements Spliterator<T> {
		private final Spliterator<T> sourceSplit = source.get();
		private final IuAsynchronousPipe<T> pipe = new IuAsynchronousPipe<T>();

		private volatile Spliterator<T> pipedSplit;

		@Override
		public synchronized boolean tryAdvance(Consumer<? super T> action) {
			if (pipedSplit == null)
				if (sourceSplit.tryAdvance(action))
					return true;
				else
					pipedSplit = pipe.stream().spliterator();

			return pipedSplit.tryAdvance(action);
		}

		@Override
		public synchronized Spliterator<T> trySplit() {
			if (pipedSplit == null) {
				pipedSplit = pipe.stream().spliterator();
				return sourceSplit;
			}

			return pipedSplit.trySplit();
		}

		@Override
		public long estimateSize() {
			if (pipedSplit == null)
				return Long.MAX_VALUE;
			else
				return pipedSplit.estimateSize();
		}

		@Override
		public int characteristics() {
			if (pipedSplit == null)
				return CONCURRENT;
			else
				return pipedSplit.characteristics();
		}
	}

	/**
	 * Creates a new <strong>subject</strong>.
	 * 
	 * @param source supplies the initial split backing new
	 *               <strong>subscriber</strong> streams.
	 */
	public IuAsynchronousSubject(Supplier<Spliterator<T>> source) {
		this.source = source;
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

		return StreamSupport.stream(subscriber, false).onClose(() -> subscribers.remove(subscriber));
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
	public void accept(T value) {
		if (closed)
			throw new IllegalStateException("closed");

		subscribers.forEach(subscriber -> subscriber.pipe.accept(value));
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
			e = IuException.suppress(e, () -> subscribers.poll().pipe.close());

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
			IuException.suppress(e, () -> subscribers.poll().pipe.error(e));

		closed = true;
	}

}