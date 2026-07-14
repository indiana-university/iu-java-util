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
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory data store implementation.
 */
public class InMemoryDataStore implements IuDataStore {

	private class PurgeTask extends TimerTask {
		@Override
		public void run() {
			final var purgeTime = Instant.now();
			final var i = data.values().iterator();
			while (i.hasNext())
				if (i.next().purgeTime.isBefore(purgeTime))
					i.remove();
		}
	}

	private static volatile int n;

	/**
	 * Map key type.
	 */
	static class Key {
		private byte[] key;

		/**
		 * Constructor.
		 * 
		 * @param key key data
		 */
		Key(byte[] key) {
			this.key = key;
		}

		@Override
		public int hashCode() {
			return IuObject.hashCode(key);
		}

		@Override
		public boolean equals(Object obj) {
			if (!IuObject.typeCheck(this, obj))
				return false;

			Key other = (Key) obj;
			return IuObject.equals(key, other.key);
		}

	}

	private static class PurgeableData {
		private byte[] data;
		private Instant purgeTime;

		private PurgeableData(byte[] data, Instant purgeTime) {
			this.data = data;
			this.purgeTime = purgeTime;
		}
	}

	private final Map<Key, PurgeableData> data = new ConcurrentHashMap<>();
	private final Timer purgeTimer = new Timer("in-memory-purge-" + (++n), true);

	/**
	 * Default constructor
	 */
	public InMemoryDataStore() {
		purgeTimer.schedule(new PurgeTask(), 1500L, 1500L);
	}

	@Override
	public Iterable<?> list() {
		return IuIterable.map(data.keySet(), k -> k.key);
	}

	@Override
	public byte[] get(byte[] key) {
		Objects.requireNonNull(key, "key is required");

		final var dkey = new Key(key);
		final var session = data.get(dkey);

		if (session != null //
				&& session.purgeTime.isBefore(Instant.now())) {
			data.remove(dkey);
			return null;
		}

		return session != null ? session.data : null;
	}

	@Override
	public void put(byte[] key, byte[] value) {
		put(key, value, Duration.ofMinutes(15L));
	}

	@Override
	public void put(byte[] key, byte[] value, Duration ttl) {
		Objects.requireNonNull(key, "key is required");

		final var dkey = new Key(key);
		if (value == null)
			this.data.remove(dkey);
		else
			this.data.put(dkey, new PurgeableData(value, Instant.now().plus(ttl)));
	}

}
