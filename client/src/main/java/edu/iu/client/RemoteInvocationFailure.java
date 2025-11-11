package edu.iu.client;

/**
 * Represents {@link Throwable} stack trace details for passing via remote call
 */
public interface RemoteInvocationFailure {

	/**
	 * Gets the remote name
	 * 
	 * @return remote name
	 */
	String getRemoteName();

	/**
	 * Gets the remote method
	 * 
	 * @return remote method
	 */
	String getRemoteMethod();

	/**
	 * Gets the message
	 * 
	 * @return message
	 */
	String getMessage();

	/**
	 * Gets the exception type
	 * 
	 * @return exception type
	 */
	String getExceptionType();

	/**
	 * Gets the stack trace
	 * 
	 * @return stack trace
	 */
	Iterable<RemoteInvocationDetail> getStackTrace();

	/**
	 * Gets the cause
	 * 
	 * @return cause
	 */
	RemoteInvocationFailure getCause();

	/**
	 * Gets suppressed failures
	 * 
	 * @return suppressed failures
	 */
	Iterable<RemoteInvocationFailure> getSuppressed();

}
