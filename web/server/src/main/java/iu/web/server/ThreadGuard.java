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
