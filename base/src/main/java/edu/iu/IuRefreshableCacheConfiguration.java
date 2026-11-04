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

/**
 * Provides runtime tuning parameters to a {@link IuRefreshableCache}.
 *
 * <p>
 * All methods supply a default, so an implementation only overrides the values
 * it needs to change. The cache reads one configuration snapshot for each
 * operation, allowing a supplier to expose current values without recreating
 * the cache. The defensive call cache is <em>short-lived</em> by
 * design: {@link #getRefreshTtl() refresh TTL} <em>should</em> be short enough
 * that callers see reasonably current data from a stable backing source;
 * {@link #getCacheTtl() cache TTL} <em>should</em> be no longer than
 * {@code PT30M}; and {@link #getCallTtl() call TTL} <em>should</em> be short.
 * </p>
 *
 * <p>
 * Values are validated by the invocation that reads them, so an invalid value
 * fails that invocation and leaves the cache intact. See
 * {@link IuRefreshableCache} for what each value controls, when a change takes
 * effect, and how the values trade off against one another.
 * </p>
 */
public interface IuRefreshableCacheConfiguration {

	/**
	 * Configuration with the default cache, call timeout, and executor limits.
	 */
	static final IuRefreshableCacheConfiguration DEFAULT = new IuRefreshableCacheConfiguration() {
	};

	/**
	 * Configuration with the default call timeout and executor limits, but without
	 * a result cache.
	 */
	static final IuRefreshableCacheConfiguration NO_CACHE = new IuRefreshableCacheConfiguration() {
		@Override
		public Duration getRefreshTtl() {
			return null;
		}
	};

	/**
	 * Default {@link #getRefreshTtl() refresh TTL}: five minutes.
	 */
	Duration REFRESH_TTL = Duration.ofMinutes(5L);

	/**
	 * Default {@link #getCacheTtl() cache TTL}: thirty minutes.
	 */
	Duration CACHE_TTL = Duration.ofMinutes(30L);

	/**
	 * Default {@link #getCallTtl() call TTL}: two minutes.
	 */
	Duration CALL_TTL = Duration.ofMinutes(2L);

	/**
	 * Default {@link #getThreads() thread count}: eight.
	 */
	int THREADS = 8;

	/**
	 * Default {@link #getPending() pending queue size}: 256.
	 */
	int PENDING = 256;

	/**
	 * Gets the interval after which a cached result becomes stale and the next
	 * caller triggers a refresh.
	 *
	 * <p>
	 * This is the staleness bound under healthy conditions: a value older than
	 * this is still served immediately, but triggers a background refresh. A change
	 * applies to entries already cached, in both directions, without discarding
	 * them, because staleness is evaluated against the interval in effect when an
	 * entry is read.
	 * </p>
	 *
	 * <p>
	 * Returns null to disable the defensive call cache entirely, in which case
	 * every invocation results in a remote call and {@link #getCacheTtl()} is not
	 * used.
	 * </p>
	 *
	 * @return refresh interval; null to disable caching. Must be positive when
	 *         non-null.
	 */
	default Duration getRefreshTtl() {
		return REFRESH_TTL;
	}

	/**
	 * Gets the maximum length of time a cached result remains available once it has
	 * stopped being refreshed successfully.
	 *
	 * <p>
	 * The interval between {@link #getRefreshTtl()} and this value is the
	 * downstream service outage tolerance window: a successful refresh restarts the
	 * window, so a healthy entry never expires.
	 * </p>
	 *
	 * <p>
	 * This value also bounds how long a queued invalidation hint is retained, and
	 * therefore how much work each cached lookup performs when evaluating pending
	 * invalidations. A long cache TTL combined with a high rate of invalidating
	 * calls is the combination to avoid.
	 * </p>
	 *
	 * <p>
	 * A change applies as each entry is stored, so an entry already cached takes
	 * the new value at its next successful refresh rather than immediately.
	 * </p>
	 *
	 * @return short-term cache time to live; should be no longer than
	 *         {@code PT30M} and must be longer than {@link #getRefreshTtl()}
	 */
	default Duration getCacheTtl() {
		return CACHE_TTL;
	}

	/**
	 * Gets the maximum length of time to wait for a remote call to complete.
	 *
	 * <p>
	 * Applies to a caller waiting on an uncached value and to a background refresh
	 * alike: a refresh still in flight after this interval is cancelled and
	 * replaced, so a hung call cannot block every later refresh for its key, nor
	 * occupy a {@link #getThreads() thread} indefinitely.
	 * </p>
	 *
	 * @return short call timeout; must be positive
	 */
	default Duration getCallTtl() {
		return CALL_TTL;
	}

	/**
	 * Gets the number of threads available for performing remote calls.
	 *
	 * <p>
	 * Changing this value, or {@link #getPending()}, replaces the call pool; the
	 * replaced pool is shut down gracefully so calls already in flight run to
	 * completion. Idle threads time out, so an idle cache holds no threads.
	 * </p>
	 *
	 * @return thread count; must be positive
	 */
	default int getThreads() {
		return THREADS;
	}

	/**
	 * Gets the maximum number of remote calls that may be queued while all
	 * {@link #getThreads() threads} are busy.
	 *
	 * <p>
	 * A full queue rejects the dispatch. For a key that already holds a value that
	 * is absorbed — the stale value is served instead — so this value acts as a
	 * load-shedding control as well as a resource bound: too tight converts
	 * pressure into staleness, too loose converts it into latency.
	 * </p>
	 *
	 * @return pending queue size; must be positive
	 */
	default int getPending() {
		return PENDING;
	}

}
