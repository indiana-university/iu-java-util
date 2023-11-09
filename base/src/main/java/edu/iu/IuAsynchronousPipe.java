package edu.iu;

import java.time.Duration;
import java.util.Deque;
import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
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
 * through which values be retrieved. Once <strong>connected</strong> in this
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
 */
public class IuAsynchronousPipe<T> implements Consumer<T>, AutoCloseable {

	private class Splitr implements Spliterator<T> {

		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			if (queue.isEmpty() && closed) {
				while (!onComplete.isEmpty())
					onComplete.pop().run();

				return false;
			}

			while (queue.isEmpty() && !closed)
				synchronized (IuAsynchronousPipe.this) {
					try {
						IuAsynchronousPipe.this.wait(500L);
					} catch (InterruptedException e) {
						throw new IllegalStateException(e);
					}
				}

			while (!queue.isEmpty()) {
				action.accept(queue.poll());

				synchronized (IuAsynchronousPipe.this) {
					receivedCount++;
					IuAsynchronousPipe.this.notifyAll();
				}
			}

			return true;
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
	private volatile int acceptedCount;
	private volatile int receivedCount;
	private volatile boolean completed;
	private volatile boolean closed;

	private final Deque<Runnable> onComplete = new ConcurrentLinkedDeque<>();

	/**
	 * Default constructor.
	 */
	public IuAsynchronousPipe() {
		final Stream<T> stream = StreamSupport.stream(new Splitr(), false).onClose(() -> {
			close();
			if (!completed)
				synchronized (this) {
					completed = true;
					this.stream = null;
					notifyAll();
				}
		});

		onComplete.push(stream::close);
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
	public int getAcceptedCount() {
		return acceptedCount;
	}

	/**
	 * Gets a count of all values received from the pipe since opening.
	 * 
	 * @return count of received values
	 */
	public int getReceivedCount() {
		return receivedCount;
	}

	/**
	 * Gets a count of values accepted by the pipe that have not yet been received.
	 * 
	 * @return count of pending values
	 */
	public synchronized int getPendingCount() {
		return acceptedCount - receivedCount;
	}

	/**
	 * Pauses execution on the current thread until either values have been received
	 * from the pipe, a timeout interval passes, or the {@link #stream() stream} is
	 * closed.
	 * 
	 * <p>
	 * Upon return, the state of the pipe is not guaranteed and should be inspected
	 * again by the <strong>controlling component</strong>.
	 * </p>
	 * 
	 * @param receivedCount count of received values to wait for; returns without
	 *                      delay if &lt;= 0
	 * @param timeout       max amount of time to wait; returns without delay unless
	 *                      positive
	 */
	public synchronized void pauseController(int receivedCount, Duration timeout) {
		if (receivedCount <= 0)
			return;
		
		long ttl = timeout.toMillis();
		if (ttl <= 0)
			return;

		long expireTime = System.currentTimeMillis() + ttl;
		int targetAcceptedCount = this.receivedCount + receivedCount;

		long waitFor = expireTime - System.currentTimeMillis();
		while (waitFor > 0 && this.receivedCount < targetAcceptedCount && !completed) {
			try {
				wait(waitFor);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}

			waitFor = expireTime - System.currentTimeMillis();
		}
	}

	/**
	 * Pauses execution on the current thread until either new values are accepted
	 * onto the pipe, a timeout interval passes, or the pipe closes.
	 * 
	 * <p>
	 * Upon return, the state of the pipe is not guaranteed and should be inspected
	 * again by the <strong>receiving component</strong>.
	 * </p>
	 * 
	 * @param acceptedCount count of newly accepted values to wait for; returns
	 *                      without delay if &lt;= 0
	 * @param timeout       max amount of time to wait; returns without delay unless
	 *                      positive
	 */
	public synchronized void pauseReceiver(int acceptedCount, Duration timeout) {
		if (acceptedCount <= 0)
			return;
		
		long ttl = timeout.toMillis();
		if (ttl <= 0)
			return;

		long expireTime = System.currentTimeMillis() + ttl;
		int targetAcceptedCount = this.acceptedCount + acceptedCount;

		long waitFor = expireTime - System.currentTimeMillis();
		while (waitFor > 0 && this.acceptedCount < targetAcceptedCount && !closed) {
			try {
				wait(waitFor);
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}

			waitFor = expireTime - System.currentTimeMillis();
		}
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

	@Override
	public void close() {
		if (closed)
			return;

		closed = true;

		synchronized (this) {
			this.notifyAll();
		}
	}

}
