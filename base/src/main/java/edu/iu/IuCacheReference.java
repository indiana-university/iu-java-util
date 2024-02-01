package edu.iu;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.time.Instant;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link SoftReference} implementation with timed purge
 * 
 * @param <V> value type
 */
public class IuCacheReference<V> extends SoftReference<Object> {

	private static final Logger LOG = Logger.getLogger(IuCacheReference.class.getName());

	private static final Object NULL = new Object();
	private static final Timer PURGE_TIMER = new Timer("iu-cache-purge", true);
	private static final ReferenceQueue<Object> REFQ = new ReferenceQueue<>();

	static {
		PURGE_TIMER.schedule(new TimerTask() {
			@Override
			public void run() {
				purgeQueue();
			}
		}, 500L, 500L);
	}

	/**
	 * Clears all references enqueued by the garbage collector.
	 */
	static void purgeQueue() {
		IuCacheReference<?> ref;
		while ((ref = (IuCacheReference<?>) REFQ.poll()) != null)
			ref.expire();
		LOG.info("purge queue");
	}

	private final UnsafeRunnable onExpire;
	private final TimerTask expireTask;
	private volatile boolean expired;

	/**
	 * Constructor.
	 * 
	 * @param key      key, will be held by hard reference
	 * @param value    value, will be held by soft reference
	 * @param expires  expiration time
	 * @param onExpire thunk to invoke when the reference expires; <em>should</em>
	 *                 execute quickly, i.e., to remove a map entry relative the
	 *                 provided key
	 */
	public IuCacheReference(V value, Instant expires, UnsafeRunnable onExpire) {
		super(value == null ? NULL : value, REFQ);
		this.onExpire = onExpire;

		PURGE_TIMER.schedule(expireTask = new TimerTask() {
			@Override
			public void run() {
				expire();
			}
		}, Date.from(expires));
	}

	@SuppressWarnings("unchecked")
	@Override
	public V get() {
		final var value = super.get();
		if (value == null) {
			expire();
			return null;
		} else if (value == NULL)
			return null;
		else
			return (V) value;
	}

	private synchronized void expire() {
		if (!expired)
			try {
				expired = true;
				expireTask.cancel();
				clear();
				onExpire.run();
			} catch (Throwable e) {
				LOG.log(Level.INFO, e, () -> "Unhandled error in cache reference expiration thunk " + onExpire);
			}
	}

}
