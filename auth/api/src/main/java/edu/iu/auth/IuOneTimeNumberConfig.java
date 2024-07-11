package edu.iu.auth;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Provides configuration properties for tuning {@link IuOneTimeNumber}
 * instances.
 */
public interface IuOneTimeNumberConfig {

	/**
	 * Gets the maximum time to allow a pending one-time number value to be
	 * accepted.
	 * 
	 * @return {@link Duration}
	 */
	default Duration getTimeToLive() {
		return Duration.ofMinutes(2L);
	}

	/**
	 * Gets the maximum number of concurrent nonce requests to allow per client.
	 * 
	 * @return maximum number of concurrent nonce requests
	 */
	default int getMaxConcurrency() {
		return 5;
	}

	/**
	 * Subscribes the one-time number generator to external
	 * {@link IuAuthorizationChallenge} events.
	 * 
	 * @param challengeSubscriber Receives a {@link Consumer} for publishing
	 *                            {@link IuAuthorizationChallenge} events received
	 *                            from other nodes.
	 */
	default void subscribe(Consumer<IuAuthorizationChallenge> challengeSubscriber) {
	}

	/**
	 * Broadcasts a {@link IuAuthorizationChallenge} event to all subscribers.
	 * 
	 * @param challenge {@link IuAuthorizationChallenge} event
	 */
	default void publish(IuAuthorizationChallenge challenge) {
	}

}
