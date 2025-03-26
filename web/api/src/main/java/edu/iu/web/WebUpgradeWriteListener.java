
package edu.iu.web;

import java.io.IOException;

/**
 * Interface for listening to write events on a web upgrade connection.
 */
public interface WebUpgradeWriteListener {

	/**
	 * Called when it is possible to write data.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	void onWritePossible() throws IOException;

	/**
	 * Called when an error occurs during writing.
	 *
	 * @param t the throwable representing the error
	 */
	void onError(Throwable t);

}
