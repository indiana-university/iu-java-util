
package edu.iu.web;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Interface representing a socket wrapper for web upgrade connections.
 */
public interface WebUpgradeSocketWrapper {

	/**
	 * Reads data from the socket into a byte array.
	 *
	 * @param block whether the read operation should block
	 * @param b     the byte array to read data into
	 * @param off   the start offset in the array
	 * @param len   the maximum number of bytes to read
	 * @return the number of bytes read, or -1 if the end of the stream is reached
	 * @throws IOException if an I/O error occurs
	 */
	int read(boolean block, byte[] b, int off, int len) throws IOException;

	/**
	 * Reads data from the socket into a ByteBuffer.
	 *
	 * @param block whether the read operation should block
	 * @param to    the ByteBuffer to read data into
	 * @return the number of bytes read, or -1 if the end of the stream is reached
	 * @throws IOException if an I/O error occurs
	 */
	int read(boolean block, ByteBuffer to) throws IOException;

	/**
	 * Checks if the socket is ready for reading.
	 *
	 * @return true if the socket is ready for reading, false otherwise
	 * @throws IOException if an I/O error occurs
	 */
	boolean isReadyForRead() throws IOException;

	/**
	 * Sets the application buffer handler for reading.
	 *
	 * @param handler the application buffer handler
	 */
	void setAppReadBufHandler(WebApplicationBufferHandler handler);

	/**
	 * Closes the socket.
	 */
	void close();

	/**
	 * Checks if the socket is closed.
	 *
	 * @return true if the socket is closed, false otherwise
	 */
	boolean isClosed();

	/**
	 * Writes data from a ByteBuffer to the socket.
	 *
	 * @param block whether the write operation should block
	 * @param from  the ByteBuffer to write data from
	 * @throws IOException if an I/O error occurs
	 */
	void doWrite(boolean block, ByteBuffer from) throws IOException;

	/**
	 * Flushes the socket.
	 *
	 * @param block whether the flush operation should block
	 * @throws IOException if an I/O error occurs
	 */
	void flush(boolean block) throws IOException;

}
