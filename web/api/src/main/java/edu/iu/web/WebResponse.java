package edu.iu.web;

import java.io.OutputStream;
import java.util.Map;

/**
 * Provides an immutable representation of an HTTP response.
 */
public interface WebResponse {

	/**
	 * Gets the status code.
	 * 
	 * @return status code.
	 */
	int getStatus();

	/**
	 * Gets the message associated with the status code.
	 * 
	 * @return message
	 */
	String getMessage();

	/**
	 * Gets the response headers.
	 * 
	 * @return response headers.
	 */
	Map<String, Iterable<String>> getHeaders();

	/**
	 * Gets the buffered response content.
	 * 
	 * @return response content.
	 */
	byte[] getContent();

	/**
	 * Gets the web upgrade handler, if applicable.
	 * 
	 * @return web upgrade handler, null for normal HTTP.
	 */
	default WebUpgradeHandler getUpgradeHandler() {
		return null;
	}

	/**
	 * Gets the output stream for the response.
	 * 
	 * @return output stream
	 */
	OutputStream getOutputStream();

	/**
	 * Sets the status code.
	 * 
	 * @param status status code
	 */
	void setStatus(int status);

	/**
	 * Sets the message associated with the status code.
	 * 
	 * @param message message
	 */
	void setMessage(String message);

	/**
	 * Adds a response header.
	 * 
	 * @param name  header name
	 * @param value header value
	 */
	void addHeader(String string, String contentType);

	/**
	 * Sets the upgrade handler.
	 * 
	 * @param upgradeHandler web upgrade handler
	 */
	void setUpgradeHandler(WebUpgradeHandler upgradeHandler);

}
