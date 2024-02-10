package edu.iu;

/**
 * Thrown by an application or incoming request handler to represent a missing
 * resource or unrecoverable security error, i.e., penetration attempt.
 * 
 * <p>
 * <em>Should</em> be caught and handled as 404 NOT FOUND by an outbound web
 * request boundary. <em>May</em> be handled by application-layer business
 * logic; in the event of an unrecoverable security error, the application
 * <em>must</em> log the attempt. <em>Should not</em> be thrown as the result of
 * a server or downstream service error.
 * </p>
 */
public class IuNotFoundException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Default constructor.
	 */
	public IuNotFoundException() {
	}

	/**
	 * Default constructor.
	 * 
	 * @param message message
	 */
	public IuNotFoundException(String message) {
		super(message);
	}

	/**
	 * Default constructor.
	 * 
	 * @param cause cause
	 */
	public IuNotFoundException(Throwable cause) {
		super(cause);
	}

	/**
	 * Default constructor.
	 * 
	 * @param message message
	 * @param cause   cause
	 */
	public IuNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

}
