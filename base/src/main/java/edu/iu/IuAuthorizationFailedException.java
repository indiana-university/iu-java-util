package edu.iu;

/**
 * Thrown by an application or authorization layer to represent an access
 * control verification failure.
 * 
 * <p>
 * <em>Should</em> be caught and handled as 403 FORBIDDEN by an outbound web
 * request boundary. <em>May</em> be handled by application-layer business logic
 * if custom authorization handling requirements are specified. <em>Should
 * not</em> be thrown as the result of a server or downstream service error.
 * </p>
 */
public class IuAuthorizationFailedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Default constructor.
	 */
	public IuAuthorizationFailedException() {
	}

	/**
	 * Default constructor.
	 * 
	 * @param message message
	 */
	public IuAuthorizationFailedException(String message) {
		super(message);
	}

	/**
	 * Default constructor.
	 * 
	 * @param cause cause
	 */
	public IuAuthorizationFailedException(Throwable cause) {
		super(cause);
	}

	/**
	 * Default constructor.
	 * 
	 * @param message message
	 * @param cause   cause
	 */
	public IuAuthorizationFailedException(String message, Throwable cause) {
		super(message, cause);
	}

}
