
package edu.iu.web;

import java.io.IOException;

/**
 * Interface for listening to read events on a web upgrade connection.
 */
public interface WebUpgradeReadListener {

	/**
	 * Called when data is available to be read.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	void onDataAvailable() throws IOException;

	/**
	 * Called when all data has been read.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	void onAllDataRead() throws IOException;

	/**
	 * Called when an error occurs during reading.
	 *
	 * @param t the throwable representing the error
	 */
	void onError(Throwable t);

}
