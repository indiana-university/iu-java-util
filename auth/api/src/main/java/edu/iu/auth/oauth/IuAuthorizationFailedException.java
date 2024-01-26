package edu.iu.auth.oauth;

/**
 * Thrown when an OAuth authorization grant fails.
 */
public class IuAuthorizationFailedException extends Exception {

	private static final long serialVersionUID = 1L;

	/**
	 * HTTP status code.
	 */
	private final int status;
	
	/**
	 * Authentication realm.
	 */
	private final String realm;
	
	/**
	 * Error code.
	 */
	private final String error;

	/**
	 * Constructor.
	 * 
	 * @param status  HTTP status code to deliver to the client when this error is
	 *                encountered
	 * @param realm   authentication realm
	 * @param error   error code
	 * @param message error description
	 * @param cause   optional root cause
	 */
	public IuAuthorizationFailedException(int status, String realm, String error, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
		this.realm = realm;
		this.error = error;
	}

	/**
	 * Gets the HTTP status code to deliver to the client when this error is
	 * encountered.
	 * 
	 * @return HTTP status code
	 */
	public int getStatus() {
		return status;
	}

	/**
	 * Gets the authentication realm.
	 * 
	 * @return authentication realm
	 */
	public String getRealm() {
		return realm;
	}

	/**
	 * Gets the error code.
	 * 
	 * @return error code
	 */
	public String getError() {
		return error;
	}

}
