
package edu.iu.web;

/**
 * Interface for handling web upgrade operations.
 */
public interface WebUpgradeHandler {

	/**
	 * Checks if the handler is internal.
	 *
	 * @return true if the handler is internal, false otherwise
	 */
	boolean isInternal();

	/**
	 * Initializes the handler with the given connection.
	 *
	 * @param connection the web upgrade connection
	 */
	void init(WebUpgradeConnection connection);

	/**
	 * Destroys the handler, releasing any resources.
	 */
	void destroy();

	/**
	 * Dispatches an upgrade with the given status.
	 *
	 * @param status the status of the upgrade
	 * @return a string representing the result of the dispatch
	 * @throws UnsupportedOperationException default if the method is not supported
	 *                                       by an implementation class
	 */
	default String upgradeDispatch(String status) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Sets the socket wrapper for the upgrade.
	 *
	 * @param wrapper the socket wrapper
	 * @throws UnsupportedOperationException default if the method is not supported
	 *                                       by an implementation class
	 */
	default void setSocketWrapper(WebUpgradeSocketWrapper wrapper) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Sets the SSL support for the upgrade.
	 *
	 * @param sslSupport the SSL support
	 * @throws UnsupportedOperationException default if the method is not supported
	 *                                       by an implementation class
	 */
	default void setSslSupport(WebUpgradeSSLSupport sslSupport) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Pauses the upgrade operation.
	 *
	 * @throws UnsupportedOperationException default if the method is not supported
	 *                                       by an implementation class
	 */
	default void pause() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Handles timeout for asynchronous operations.
	 *
	 * @param now the current time in milliseconds
	 * @throws UnsupportedOperationException default if the method is not supported
	 *                                       by an implementation class
	 */
	default void timeoutAsync(long now) {
		throw new UnsupportedOperationException();
	}
}
