
package edu.iu.web;

import java.nio.ByteBuffer;

/**
 * Interface for handling a web application's buffer.
 */
public interface WebApplicationBufferHandler {

	/**
	 * Sets the ByteBuffer for the web application.
	 *
	 * @param buffer the ByteBuffer to set
	 */
	public void setByteBuffer(ByteBuffer buffer);

	/**
	 * Gets the ByteBuffer for the web application.
	 *
	 * @return the current ByteBuffer
	 */
	public ByteBuffer getByteBuffer();

	/**
	 * Expands the ByteBuffer by the specified size.
	 *
	 * @param size the size to expand the ByteBuffer by
	 */
	public void expand(int size);

}
