package edu.iu.auth;

/**
 * Thrown by an incoming request handler to represent an authentication failure.
 * 
 * <p>
 * <em>Should</em> be caught and handled as 302 FOUND by an outbound web request
 * boundary. <em>Should not</em> be handled by application-layer business
 * logic.<em>Should not</em> be thrown by components not directly responsible
 * for authentication.
 * </p>
 */
public class IuAuthenticationRedirectException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * Default constructor.
	 * 
	 * @param message message
	 */
	public IuAuthenticationRedirectException(String message) {
		super(message);
	}

	/**
	 * Default constructor.
	 * 
	 * @param message message
	 * @param cause   cause
	 */
	public IuAuthenticationRedirectException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Gets the <strong>Location</strong> header value to redirect the user to in
	 * order initiate authentication.
	 * 
	 * @return <strong>Location</strong> header value
	 */
	@Override
	public String getMessage() {
		return super.getMessage();
	}

}
