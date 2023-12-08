package edu.iu;

import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Provides access to all values available via a shared source, then blocking
 * access to receive new values as they are applied to the source.
 * 
 * @param <T> value type
 */
public class IuAsynchronousSubject<T> implements Consumer<T>, AutoCloseable {

	private final Supplier<Spliterator<T>> source;
	private final Queue<Subscriber> subscribers = new ConcurrentLinkedQueue<>();
	private boolean closed;

	private class Subscriber implements Spliterator<T>, AutoCloseable {
		private final Spliterator<T> sourceSplit = source.get();
		private final IuAsynchronousPipe<T> pipe = new IuAsynchronousPipe<T>();

		private volatile Stream<T> stream;
		private volatile Spliterator<T> pipedSplit;

		private Subscriber() {
			subscribers.offer(this);
		}

		@Override
		public synchronized boolean tryAdvance(Consumer<? super T> action) {
			if (pipedSplit == null) {
				if (sourceSplit.tryAdvance(action))
					return true;

				stream = pipe.stream();
				pipedSplit = stream.spliterator();
			}

			return pipedSplit.tryAdvance(action);
		}

		@Override
		public synchronized Spliterator<T> trySplit() {
			if (pipedSplit == null)
				return sourceSplit.trySplit();
			else
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

		@Override
		public void close() throws Exception {
			subscribers.remove(this);

			if (stream == null)
				pipe.close();
			else
				stream.close();
		}

	}

	/**
	 * Creates a new subject.
	 * 
	 * @param source will be invoked to supply the initial split backing new
	 *               subscription streams.
	 */
	public IuAsynchronousSubject(Supplier<Spliterator<T>> source) {
		this.source = source;
	}

	/**
	 * Gets a stream over all values available so far which then blocks waiting for
	 * new values to be received.
	 * 
	 * @return {@link Stream}
	 */
	public Stream<T> subscribe() {
		if (closed)
			throw new IllegalStateException("closed");

		return StreamSupport.stream(new Subscriber(), false);
	}

	@Override
	public void accept(T t) {
		if (closed)
			throw new IllegalStateException("closed");

		subscribers.forEach(subscriber -> subscriber.pipe.accept(t));
	}

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
	 * Report a fatal error to all subscribers and terminate all subscriptions.
	 * 
	 * @param e fatal error
	 */
	public synchronized void error(Throwable e) {
		while (!subscribers.isEmpty())
			IuException.suppress(e, () -> subscribers.poll().pipe.error(e));
		closed = true;
	}

}