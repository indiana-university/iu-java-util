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

import java.util.Map;
import java.util.Optional;

/**
 * Supplies metadata directing how one key interacts with an
 * {@link IuRefreshableCache}.
 *
 * <p>
 * A hint is obtained once per {@link IuRefreshableCache#apply(Object)}
 * invocation, before the cache is read, so an implementation <em>should</em> be
 * inexpensive to produce and to evaluate. See {@link IuRefreshableCache} for how
 * each method below participates in the lookup lifecycle.
 * </p>
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface IuRefreshableCacheHint<K, V> {

	/**
	 * Caches refreshed value, retains all others values, doesn't inspect for
	 * embedded values.
	 */
	static final IuRefreshableCacheHint<?, ?> DEFAULT = new IuRefreshableCacheHint<>() {
	};

	/**
	 * Clears all cached results after successful refresh, doesn't cache refreshed
	 * value.
	 *
	 * <p>
	 * Recognized by the cache <em>by identity</em>: only this instance, as returned
	 * by {@link #clearAll()}, triggers an immediate full clear. A distinct
	 * implementation that returns true from {@link #shouldClear(Object)} for every
	 * key is not equivalent — it takes the deferred, per-entry invalidation path
	 * instead.
	 * </p>
	 */
	static final IuRefreshableCacheHint<?, ?> CLEAR_ALL = new IuRefreshableCacheHint<>() {
		@Override
		public boolean shouldClear(Object key) {
			return true;
		}
	};

	/**
	 * Uses default cache behavior.
	 * 
	 * @param <K> key type
	 * @param <V> value type
	 * @return default cache hint: don't skip, don't clear, no embedded values
	 */
	@SuppressWarnings("unchecked")
	static <K, V> IuRefreshableCacheHint<K, V> useDefaults() {
		return (IuRefreshableCacheHint<K, V>) DEFAULT;
	}

	/**
	 * Clears all cached results after a successful call, and doesn't cache the
	 * result of that call.
	 *
	 * <p>
	 * Returns the singleton {@link #CLEAR_ALL} instance, which the cache recognizes
	 * by identity in order to clear in line rather than by deferred per-entry
	 * evaluation. Prefer this over a predicate that matches every key when a call
	 * invalidates broadly: it is a constant-cost invalidation, whereas a deferred
	 * hint is re-evaluated by every subsequent cached lookup until it is purged.
	 * </p>
	 *
	 * <p>
	 * Clearing in line also discards every invalidation still pending, since
	 * emptying the cache subsumes them.
	 * </p>
	 *
	 * @param <K> key type
	 * @param <V> value type
	 * @return clear-all cache hint: clear all, don't cache, no embedded values
	 */
	@SuppressWarnings("unchecked")
	static <K, V> IuRefreshableCacheHint<K, V> clearAll() {
		return (IuRefreshableCacheHint<K, V>) CLEAR_ALL;
	}

	/**
	 * Determines if a key should be cleared from the cache.
	 *
	 * <p>
	 * This method serves two distinct purposes, distinguished by which key is
	 * passed.
	 * </p>
	 *
	 * <ul>
	 * <li>Evaluated against the key that produced this hint, before the call, it
	 * selects the <em>uncached</em> path: the result is resolved directly and never
	 * cached. This is how a key that mutates the backing source is declared.</li>
	 * <li>Evaluated against other keys, after that call succeeds, it selects which
	 * cached entries the call invalidated. A cleared entry is repopulated by the
	 * next caller, which waits for the new value.</li>
	 * </ul>
	 *
	 * <p>
	 * A hint is only queued for invalidation when its call <em>succeeds</em>, so a
	 * failed call never discards cached data.
	 * </p>
	 *
	 * <p>
	 * <strong>Implementation Note:</strong> during invalidation this method is
	 * invoked while the candidate entry's monitor is held, once per queued hint per
	 * lookup. It <em>must</em> return quickly, and <em>must not</em> invoke a remote
	 * method or touch the cache that produced it.
	 * </p>
	 *
	 * @param key key to evaluate; might not be the key that generated this hint
	 * @return true if the key should be cleared
	 */
	default boolean shouldClear(K key) {
		return false;
	}

	/**
	 * Restores a result value resolved elsewhere, typically by another node in the
	 * same cluster, in place of calling the cache's refresh function.
	 *
	 * <p>
	 * Consulted at the start of every refresh for the key, including the first, so
	 * a hint that always restores never contacts the backing source. A restored
	 * value short-circuits the refresh function, and is therefore never passed to
	 * {@link #inspect(Object)}.
	 * </p>
	 *
	 * <p>
	 * Not consulted on the uncached path — when caching is disabled, or when
	 * {@link #shouldClear(Object)} returns true for this hint's own key.
	 * </p>
	 *
	 * <p>
	 * <strong>Implementation Note:</strong> invoked on a pooled call thread,
	 * bounded by the configured call TTL along with the call it replaces.
	 * </p>
	 *
	 * @return restored value; null if no value was restored; empty optional caches
	 *         a null value
	 */
	default Optional<V> restore() {
		return null;
	}

	/**
	 * Inspects a cacheable result for cacheable embedded values.
	 *
	 * <p>
	 * Each embedded value is published under its own cache key as though it had
	 * been resolved directly, allowing one call to populate many entries and
	 * reducing load on the backing source.
	 * </p>
	 *
	 * <p>
	 * Each embedded value must be consistent with the result when read
	 * individually from the backing source. Values may fluctuate only between
	 * upstream transactions, never within the transaction represented by
	 * {@code result}. An embedded value that can differ from what a direct read
	 * would return <em>must not</em> be published here.
	 * </p>
	 *
	 * <p>
	 * <strong>Implementation Note:</strong> invoked on a pooled call thread, only
	 * when the result is itself being cached. Returning null or an empty map both
	 * mean the result exposes no related values.
	 * </p>
	 *
	 * @param result cacheable result
	 * @return mapping of embedded values by related cache key
	 */
	default Map<K, V> inspect(V result) {
		return Map.of();
	}

}
