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
package edu.iu.client;

import java.time.Duration;

/**
 * Provides tuning parameters to a {@link RemoteInvocationHandler}.
 *
 * <p>
 * All methods supply a default, so an implementation only overrides the values
 * it needs to change. The defensive call cache is <em>short-lived</em> by
 * design: {@link #getRefreshTtl() refresh TTL} <em>should</em> be short enough
 * that callers see reasonably current data, and
 * {@link #getCacheTtl() cache TTL} <em>should</em> be long enough to cover a
 * downstream outage without forcing callers to block.
 * </p>
 */
public interface RemoteInvocationConfiguration {

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
	 * Returns null to disable the defensive call cache entirely, in which case
	 * every invocation results in a remote call and
	 * {@link #getCacheTtl()} is not used.
	 * </p>
	 *
	 * @return refresh interval; null to disable caching. Must be positive when
	 *         non-null.
	 */
	default Duration getRefreshTtl() {
		return REFRESH_TTL;
	}

	/**
	 * Gets the maximum length of time a cached result remains available once it
	 * has stopped being refreshed successfully.
	 *
	 * <p>
	 * The interval between {@link #getRefreshTtl()} and this value is the
	 * downstream service outage tolerance window: a successful refresh restarts
	 * the window, so a healthy entry never expires.
	 * </p>
	 *
	 * @return cache time to live; must be longer than {@link #getRefreshTtl()}
	 */
	default Duration getCacheTtl() {
		return CACHE_TTL;
	}

	/**
	 * Gets the maximum length of time to wait for a remote call to complete.
	 *
	 * @return call timeout; must be positive
	 */
	default Duration getCallTtl() {
		return CALL_TTL;
	}

	/**
	 * Gets the number of threads available for performing remote calls.
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
	 * @return pending queue size; must be positive
	 */
	default int getPending() {
		return PENDING;
	}

}
