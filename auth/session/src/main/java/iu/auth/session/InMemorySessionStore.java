/*
 * Copyright © 2025 Indiana University
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
package iu.auth.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import edu.iu.IuDataStore;
import edu.iu.IuText;

/**
 * In-memory session store implementation.
 */
public class InMemorySessionStore implements IuDataStore {
	private static final Map<String, SessionToken> SESSION_TOKENS = new ConcurrentHashMap<>();
	private static final Timer PURGE_TIMER = new Timer("session-purge", true);
	static {
		PURGE_TIMER.schedule(new PurgeTask(), TimeUnit.SECONDS.toMillis(15L), TimeUnit.SECONDS.toMillis(15L));
	}
	
	/**
	 * Default constructor
	 */
	public InMemorySessionStore() {
	}

	/**
	 * Purges all expired stored sessions.
	 */
	static class PurgeTask extends TimerTask {
		/**
		 * Default constructor
		 */
		PurgeTask() {
		}

		@Override
		public void run() {
			final var purgeTime = Instant.now();
			final var i = SESSION_TOKENS.values().iterator();
			while (i.hasNext())
				if (i.next().inactivePurgeTime().isBefore(purgeTime))
					i.remove();
		}
	}

	@Override
	public Iterable<?> list() {
		return SESSION_TOKENS.keySet();
	}

	@Override
	public byte[] get(byte[] key) {
		Objects.requireNonNull(key, "key is required");
		final var session = SESSION_TOKENS.get(IuText.base64(key));
		if (session != null && session.inactivePurgeTime().isBefore(Instant.now())) {
			SESSION_TOKENS.remove(IuText.base64(key));
		}
		return session != null ? session.token() : null;
	}

	@Override
	public void put(byte[] key, byte[] data) {
		Objects.requireNonNull(key, "key is required");
		if (data == null)
			SESSION_TOKENS.remove(IuText.base64(key));
		else
			SESSION_TOKENS.put(IuText.base64(key), new SessionToken(data, Instant.now().plus(Duration.ofMinutes(15))));
	}

	@Override
	public void put(byte[] key, byte[] value, Duration ttl) {
		SESSION_TOKENS.put(IuText.base64(key), new SessionToken(value, Instant.now().plus(ttl)));

	}

}
