package edu.iu.web;

import com.sun.net.httpserver.HttpExchange;

/**
 * Handles web request processing errors.
 */
public interface IuWebErrorHandler {

	/**
	 * Handles an error.
	 * 
	 * @param nodeId        node identifier
	 * @param requestNumber request number
	 * @param error         web request processing error
	 * @param exchange      {@link HttpExchange}
	 * @param webContext    {@link IuWebContext}; null if not known
	 */
	void handleError(String nodeId, String requestNumber, Throwable error, HttpExchange exchange,
			IuWebContext webContext);

}
