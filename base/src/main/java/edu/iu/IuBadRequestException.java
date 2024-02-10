package edu.iu;

/**
 * Thrown by an application or incoming request handler to represent an invalid
 * incoming requests.
 * 
 * <p>
 * <em>Should</em> be caught and handled as 400 BAD REUEST by an outbound web
 * request boundary. <em>Should not</em> be handled by application-layer
 * business logic.<em>Should not</em> be thrown as the result of a server or
 * downstream service error.
 * </p>
 */
public class IuBadRequestException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Default constructor.
	 */
	public IuBadRequestException() {
	}

	/**
	 * Default constructor.
	 * 
	 * @param message message
	 */
	public IuBadRequestException(String message) {
		super(message);
	}

	/**
	 * Default constructor.
	 * 
	 * @param cause cause
	 */
	public IuBadRequestException(Throwable cause) {
		super(cause);
	}

	/**
	 * Default constructor.
	 * 
	 * @param message message
	 * @param cause   cause
	 */
	public IuBadRequestException(String message, Throwable cause) {
		super(message, cause);
	}

}
