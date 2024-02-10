package edu.iu.auth;

/**
 * Thrown by an incoming request handler to represent an authentication failure.
 * 
 * <p>
 * <em>Should</em> be caught and handled as 401 UNAUTHORIZED by an outbound web
 * request boundary. <em>Should not</em> be handled by application-layer
 * business logic.<em>Should not</em> be thrown by components not directly
 * responsible for authentication.
 * </p>
 */
public class IuAuthenticationChallengeException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * Default constructor.
	 * 
	 * @param message message
	 */
	public IuAuthenticationChallengeException(String message) {
		super(message);
	}

	/**
	 * Default constructor.
	 * 
	 * @param message message
	 * @param cause   cause
	 */
	public IuAuthenticationChallengeException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Gets the <strong>WWW-Authenticate</strong> header value to report to the user
	 * agent authentication failure.
	 * 
	 * @return <strong>WWW-Authenticate</strong> header value
	 */
	@Override
	public String getMessage() {
		return super.getMessage();
	}

}
