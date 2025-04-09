
package edu.iu.web;

import java.io.IOException;

/**
 * Interface representing a web upgrade connection, which allows for reading and
 * writing data during an HTTP upgrade.
 */
public interface WebUpgradeConnection extends AutoCloseable {

	// In

	/**
	 * Reads a byte of data from the connection.
	 *
	 * @return the byte read, or -1 if the end of the stream is reached
	 * @throws IOException if an I/O error occurs
	 */
	int read() throws IOException;

	/**
	 * Checks if the read operation is finished.
	 *
	 * @return true if the read operation is finished, false otherwise
	 */
	boolean isFinished();

	/**
	 * Checks if the connection is ready to be read.
	 *
	 * @return true if the connection is ready to be read, false otherwise
	 */
	boolean isReadReady();

	/**
	 * Sets a listener to be notified when the connection is ready to be read.
	 *
	 * @param readListener the listener to be notified
	 */
	void setReadListener(WebUpgradeReadListener readListener);

	// Out

	/**
	 * Writes a byte of data to the connection.
	 *
	 * @param b the byte to be written
	 * @throws IOException if an I/O error occurs
	 */
	void write(int b) throws IOException;

	/**
	 * Checks if the connection is ready to be written to.
	 *
	 * @return true if the connection is ready to be written to, false otherwise
	 */
	boolean isWriteReady();

	/**
	 * Sets a listener to be notified when the connection is ready to be written to.
	 *
	 * @param writeListener the listener to be notified
	 */
	void setWriteListener(WebUpgradeWriteListener writeListener);

}
