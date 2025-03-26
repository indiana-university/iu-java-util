
package edu.iu.web;

/**
 * Provides an immutable bridge for exchanging data related to an HTTP request
 * between a module receiving the request from the network, and another module
 * capable of handling the request.
 */
public interface WebInvoker {

	/**
	 * Get the class loader associated with this web invoker.
	 * 
	 * @return class loader
	 */
	ClassLoader getClassLoader();

	/**
	 * Invoke a web request.
	 * 
	 * @param request Request data.
	 * @return Response data.
	 * @throws Throwable if an unhandled error occurs invoking the web request
	 */
	WebResponse invoke(WebRequest request) throws Throwable;

}
