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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds a single value by {@link SoftReference} with timed expiration.
 * 
 * <p>
 * A cached value could be cleared by the garbage collector in order to prevent
 * {@link OutOfMemoryError} due to insufficient heap space.
 * </p>
 * 
 * @param <V> value type
 */
public class IuCachedValue<V> {

	private static final Logger LOG = Logger.getLogger(IuCachedValue.class.getName());

	private static final Object NULL = new Object();
	private static final Timer PURGE_TIMER = new Timer("iu-cache-purge", true);
	private static final ReferenceQueue<Object> REFQ = new ReferenceQueue<>();

	/**
	 * Counts expiration tasks cancelled since the last {@link Timer#purge()}.
	 *
	 * <p>
	 * Every cached value schedules its own expiration task, and replacing or
	 * clearing a value cancels that task. {@link Timer} only drops a cancelled task
	 * when it would have come due, so a cache that is refreshed actively — where
	 * values are replaced far more often than they are allowed to expire —
	 * accumulates cancelled tasks in the shared timer's queue for as long as the
	 * longest time to live in use. Purging clears them all at once but costs a scan
	 * of the whole queue, so cancellations are counted and purged in batches rather
	 * than swept one at a time.
	 * </p>
	 */
	private static final AtomicInteger CANCELLED = new AtomicInteger();

	/**
	 * Number of cancelled expiration tasks that must accumulate before the next
	 * sweep purges them.
	 */
	private static final int PURGE_THRESHOLD = 128;

	private static class Ref extends SoftReference<Object> {
		private final IuCachedValue<?> cachedValue;

		private Ref(Object referent, IuCachedValue<?> cachedValue) {
			super(referent == null ? NULL : referent, REFQ);
			this.cachedValue = cachedValue;
		}
	}

	static {
		PURGE_TIMER.schedule(new TimerTask() {
			@Override
			public void run() {
				Ref ref;
				while ((ref = (Ref) REFQ.poll()) != null)
					ref.cachedValue.clear();

				// reset before purging, so cancellations recorded while the purge is in
				// progress count toward the next batch rather than being discarded
				if (CANCELLED.get() >= PURGE_THRESHOLD) {
					CANCELLED.set(0);
					PURGE_TIMER.purge();
				}
			}
		}, 500L, 500L);
	}

	private final long expires;
	private volatile UnsafeRunnable onExpire;
	private volatile TimerTask expireTask;
	private volatile Ref reference;
	private volatile boolean expired;

	/**
	 * Constructor.
	 * 
	 * @param value      value
	 * @param timeToLive maximum length of time for the cached value to remain valid
	 * @param onExpire   thunk to invoke when the reference expires; <em>should</em>
	 *                   execute quickly, i.e., to remove a map entry relative the
	 *                   provided key
	 */
	public IuCachedValue(V value, Duration timeToLive, UnsafeRunnable onExpire) {
		final var ttl = timeToLive.toMillis();
		this.reference = new Ref(value, this);
		this.expires = System.currentTimeMillis() + ttl;
		this.onExpire = onExpire;

		PURGE_TIMER.schedule(expireTask = new TimerTask() {
			@Override
			public void run() {
				clear();
			}
		}, ttl);
	}

	/**
	 * Gets the cached value.
	 * 
	 * @return cached value; may be null if the cached value is null, the reference
	 *         was cleared by the garbage collector, or the expiration time is in
	 *         the past.
	 */
	@SuppressWarnings("unchecked")
	public V get() {
		final var reference = ref();
		if (reference == null)
			return null;

		final var value = reference.get();
		if (value == NULL)
			return null;
		else
			return (V) value;
	}

	/**
	 * Determines whether or not the cached value is still valid.
	 * 
	 * @return true if the reference is still valid; false if it has been cleared by
	 *         the garbage collection or the expiration time is in the past.
	 */
	public boolean isValid() {
		return ref() != null;
	}

	/**
	 * Determines if an object is equal to the referent.
	 * 
	 * @param o object
	 * @return true if the reference is still {@link #isValid() valid} and the
	 *         referent is equal to the object.
	 */
	public boolean has(Object o) {
		final var reference = ref();
		if (reference == null)
			return false;

		final var value = reference.get();
		if (value == NULL)
			return o == null;
		else
			return IuObject.equals(value, o);
	}

	/**
	 * Invalidates the cached value, invokes the onExpire thunk, and clears all
	 * related references and resource.
	 * 
	 * <p>
	 * This method has no effect if invoked on an invalid reference.
	 * </p>
	 */
	public synchronized void clear() {
		if (expired)
			return;

		// marked before the thunk runs, not after. The thunk reaches back into the
		// collection holding this value, and any read of this value while it is
		// being cleared resolves it as expired and calls back into this method; the
		// monitor is reentrant, so only this flag stops that from recurring.
		expired = true;

		try {
			// counted only when a scheduled run was actually prevented, since a task
			// that already came due is no longer in the timer's queue to purge
			if (expireTask.cancel())
				CANCELLED.incrementAndGet();

			reference.clear();
			onExpire.run();
		} catch (Throwable e) {
			LOG.log(Level.INFO, e, () -> "Unhandled error in cache reference expiration thunk " + onExpire);
		} finally {
			expireTask = null;
			reference = null;
			onExpire = null;
		}
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(get());
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;

		final var other = (IuCachedValue<?>) obj;
		return isValid() //
				&& other.isValid() //
				&& IuObject.equals(get(), other.get());
	}

	private Ref ref() {
		if (expires < System.currentTimeMillis()) {
			clear();
			return null;
		}

		final var reference = this.reference;
		if (reference == null)
			return null;

		final var value = reference.get();
		if (value == null) {
			clear();
			return null;
		} else
			return reference;
	}

}
