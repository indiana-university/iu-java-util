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
