package edu.iu;

/**
 * Thrown by an application or incoming request handler to represent a session
 * activation failure due to a <em>planned</em> downstream service or
 * application outage.
 * 
 * <p>
 * <em>Should</em> be caught and handled as 503 SERVICE UNVAILABLE by an
 * outbound web request boundary. <em>Should not</em> be handled by
 * application-layer business logic.<em>Should not</em> be thrown as the result
 * of a server or downstream service error.
 * </p>
 */
public class IuOutOfServiceException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Default constructor.
	 */
	public IuOutOfServiceException() {
	}

	/**
	 * Default constructor.
	 * 
	 * @param message message
	 */
	public IuOutOfServiceException(String message) {
		super(message);
	}

	/**
	 * Default constructor.
	 * 
	 * @param cause cause
	 */
	public IuOutOfServiceException(Throwable cause) {
		super(cause);
	}

	/**
	 * Default constructor.
	 * 
	 * @param message message
	 * @param cause   cause
	 */
	public IuOutOfServiceException(String message, Throwable cause) {
		super(message, cause);
	}

}
