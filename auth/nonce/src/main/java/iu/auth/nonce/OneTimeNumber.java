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
package iu.auth.nonce;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.auth.IuOneTimeNumber;
import edu.iu.auth.nonce.IuAuthorizationChallenge;
import edu.iu.auth.nonce.IuOneTimeNumberConfig;

/**
 * {@link IuOneTimeNumber} implementation class.
 */
final class OneTimeNumber implements IuOneTimeNumber, AutoCloseable {

	private static class ActiveCount {
		private volatile int count;
	}

	private static volatile int c;

	private final Logger LOG = Logger.getLogger(OneTimeNumber.class.getName());

	private IuOneTimeNumberConfig config;
	private Map<String, IuAuthorizationChallenge> issuedChallenges;
	private Timer purge;

	private transient Map<String, ActiveCount> activeCount = new HashMap<>();

	/**
	 * Constructor
	 * 
	 * @param config {@link IuOneTimeNumber}
	 */
	OneTimeNumber(IuOneTimeNumberConfig config) {
		this.config = config;
		issuedChallenges = new ConcurrentHashMap<>();

		purge = new Timer("iu-java-auth-nonce/" + (++c), true);
		purge.schedule(new TimerTask() {
			@Override
			public void run() {
				final var i = issuedChallenges.keySet().iterator();
				while (i.hasNext())
					try {
						IdGenerator.verifyId(i.next(), config.getTimeToLive().toMillis());
					} catch (Throwable e) {
						i.remove();
					}
			}
		}, 500L, 500L);

		config.subscribe(this::receive);
	}

	@Override
	public String create(String remoteAddress, String userAgent) {
		if (issuedChallenges == null)
			return IdGenerator.generateId();

		ActiveCount activeCount;
		synchronized (this.activeCount) {
			activeCount = this.activeCount.compute(remoteAddress, (k, v) -> v == null ? new ActiveCount() : v);
			synchronized (activeCount) {
				activeCount.count++;
			}
		}

		try {
			if (activeCount.count > config.getMaxConcurrency()) {
				LOG.info("Blocking excessive nonce generation; " + remoteAddress);
				return IdGenerator.generateId();
			}

			try {
				final var issuedChallenge = new IssuedChallenge(remoteAddress, userAgent);
				prune(issuedChallenge.getClientThumbprint());

				final var nonce = issuedChallenge.getNonce();
				issuedChallenges.put(nonce, issuedChallenge);

				config.publish(issuedChallenge);

				return nonce;
			} catch (Throwable e) {
				LOG.log(Level.INFO, "Discarding nonce", e);
				return IdGenerator.generateId();
			}

		} finally {
			synchronized (this.activeCount) {
				synchronized (activeCount) {
					activeCount.count--;
				}
				if (activeCount.count <= 0)
					this.activeCount.remove(remoteAddress);
			}
		}

	}

	@Override
	public void validate(String remoteAddress, String userAgent, String nonce) {
		if (issuedChallenges == null)
			throw new IllegalStateException();

		IdGenerator.verifyId(nonce, config.getTimeToLive().toMillis());
		config.publish(new UsedChallenge(nonce));

		if (!Arrays.equals(IssuedChallenge.thumbprint(remoteAddress, userAgent),
				Objects.requireNonNull(this.issuedChallenges.remove(nonce)).getClientThumbprint()))
			throw new IllegalArgumentException();
	}

	@Override
	public void close() {
		final var purge = this.purge;
		this.purge = null;
		if (purge != null)
			purge.cancel();

		issuedChallenges = null;
	}

	private void receive(IuAuthorizationChallenge message) {
		if (issuedChallenges == null)
			throw new IllegalStateException();

		final var nonce = message.getNonce();
		IdGenerator.verifyId(nonce, config.getTimeToLive().toMillis());

		final var thumbprint = message.getClientThumbprint();
		if (thumbprint == null)
			issuedChallenges.remove(message.getNonce());
		else {
			prune(thumbprint);
			issuedChallenges.put(message.getNonce(), message);
		}
	}

	private void prune(byte[] thumbprint) {
		final var i = issuedChallenges.entrySet().iterator();

		Queue<String> toPrune = null;
		while (i.hasNext()) {
			final var entry = i.next();
			final var verifyThumbprint = entry.getValue().getClientThumbprint();
			if (Arrays.equals(thumbprint, verifyThumbprint)) {
				final var nonce = entry.getKey();
				try {
					IdGenerator.verifyId(nonce, 250L);
					// artificial delay prevents excessive requests
					Thread.sleep(25L);
				} catch (Throwable e) {
					if (toPrune == null)
						toPrune = new ArrayDeque<>();
					toPrune.add(nonce);
				}
			}
		}

		if (toPrune != null)
			toPrune.forEach(issuedChallenges::remove);
	}

}
