package edu.iu;

import java.net.URI;
import java.time.Instant;

/**
 * Provides common metadata for observable events.
 */
public interface IuObservableEvent {

	/**
	 * Gets a unique identifier for the event.
	 * 
	 * @return unique identifier
	 */
	String getId();

	/**
	 * Gets the time the event occurred.
	 * 
	 * @return {@link Instant}
	 */
	Instant getTime();

	/**
	 * Gets the start time associated with the event, i.e., transaction begin time.
	 * 
	 * @return {@link Instant}
	 */
	Instant getStartTime();

	/**
	 * Gets a name of the context-independent type of the event.
	 * 
	 * @return event type
	 */
	String getType();

	/**
	 * Gets the URI associated with the event, i.e., HTTP request URI.
	 * 
	 * @return {@link URI}
	 */
	default URI getUri() {
		return null;
	}

	/**
	 * Gets the name of the event's context, i.e., application/environment code.
	 * 
	 * @return context name
	 */
	default String getContext() {
		return null;
	}

	/**
	 * Gets the name of the action associated with the event, within the
	 * application's context.
	 * 
	 * @return action name
	 */
	default String getAction() {
		return null;
	}

}
