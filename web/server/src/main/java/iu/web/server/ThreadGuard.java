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
package iu.web.server;

import java.time.Duration;
import java.time.Instant;

import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonObject;

/**
 * Tracks thread state for {@link ThreadGuardFilter}.
 */
final class ThreadGuard {

	/**
	 * Global state monitor
	 */
	static final ThreadGuard GLOBAL_GUARD = new ThreadGuard("global");

	private final String name;

	/**
	 * Constructor.
	 * 
	 * @param name logical component name for organizing monitor state
	 */
	ThreadGuard(String name) {
		this.name = name;
	}

	private volatile int active;
	private volatile int unreportedFailures;
	private volatile int unmonitoredFailures;
	private volatile int unmonitoredSuccess;
	private int totalSuccess;
	private int totalFailures;
	private Instant lastSuccess;
	private Instant lastFailure;
	private Instant firstUnmonitoredSuccess;
	private Instant firstUnmonitoredFailure;
	private int maxActive;
	private Duration maxTime;
	private Duration avgTime;

	/**
	 * Gets {@link #name}.
	 * 
	 * @return {@link #name}
	 */
	String name() {
		return name;
	}

	/**
	 * Gets {@link #active}.
	 * 
	 * @return {@link #active}
	 */
	int active() {
		return active;
	}

	/**
	 * Reports that a new incoming request has been activated.
	 */
	synchronized void activate() {
		synchronized (GLOBAL_GUARD) {
			active++;
			if (active > maxActive)
				maxActive = active;
			if (this != GLOBAL_GUARD)
				GLOBAL_GUARD.activate();
		}
	}

	/**
	 * Reports that an activated request has been completed.
	 * 
	 * @param time length of time request was active
	 */
	synchronized void deactivate(Duration time) {
		synchronized (GLOBAL_GUARD) {
			active--;
			if (this != GLOBAL_GUARD)
				GLOBAL_GUARD.deactivate(time);
		}

		lastSuccess = Instant.now();
		if (firstUnmonitoredSuccess == null)
			firstUnmonitoredSuccess = lastSuccess;

		if (time.compareTo(maxTime) > 0)
			maxTime = time;
		avgTime = Duration
				.ofNanos((avgTime.toNanos() * unmonitoredSuccess + time.toNanos()) / (unmonitoredSuccess + 1));

		unmonitoredSuccess++;
		totalSuccess++;
	}

	/**
	 * Reports a thread allocation failure.
	 */
	void fail() {
		lastFailure = Instant.now();
		if (firstUnmonitoredFailure == null)
			firstUnmonitoredFailure = lastFailure;
		unreportedFailures++;
		unmonitoredFailures++;
		totalFailures++;
	}

	/**
	 * Reports failures.
	 * 
	 * @return number of failures observed since the last invocation
	 */
	int reportFailures() {
		final var unreportedFailures = this.unreportedFailures;
		this.unreportedFailures = 0;
		return unreportedFailures;
	}

	/**
	 * Clears all unmonitored state.
	 */
	void clearUnmonitored() {
		this.unmonitoredFailures = 0;
		firstUnmonitoredFailure = null;
		unmonitoredSuccess = 0;
		firstUnmonitoredSuccess = null;
	}

	/**
	 * Exports current state metadata as JSON.
	 * 
	 * @return {@link JsonObject}
	 */
	JsonObject toJson() {
		final var b = IuJson.object();
		IuJson.add(b, "name", name);
		IuJson.add(b, "active", active);
		IuJson.add(b, "unmonitoredFailures", unmonitoredFailures);
		IuJson.add(b, "unmonitoredSuccess", unmonitoredSuccess);
		IuJson.add(b, "totalFailures", totalFailures);
		IuJson.add(b, "totalSuccess", totalSuccess);
		IuJson.add(b, "firstUnmonitoredFailure", () -> firstUnmonitoredFailure, IuJsonAdapter.of(Instant.class));
		IuJson.add(b, "lastFailure", () -> lastFailure, IuJsonAdapter.of(Instant.class));
		return b.build();
	}

	@Override
	public String toString() {
		return toJson().toString();
	}

}
