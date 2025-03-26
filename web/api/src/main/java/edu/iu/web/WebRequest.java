package edu.iu.web;

import java.lang.reflect.InvocationHandler;
import java.net.URL;
import java.util.Map;

/**
 * Provides an immutable representation of an HTTP request.
 */
public interface WebRequest {

	/**
	 * Gets the URL for the endpoint on which the call was invoked.
	 * 
	 * @return URL
	 */
	URL getEndpointUrl();

	/**
	 * Gets the application code for the web component to invoke.
	 * 
	 * @return application code
	 */
	String getApplication();

	/**
	 * Gets the name of the web component to invoke.
	 * 
	 * @return component name
	 */
	String getComponentName();

	/**
	 * Gets the path requested relative to the component's web context.
	 * 
	 * @return request path
	 */
	String getRequestPath();

	/**
	 * Gets the full external URL that invoked the call.
	 * 
	 * @return URL
	 */
	URL getCalledUrl();

	/**
	 * Gets the remote caller' host name.
	 * 
	 * @return remote host
	 */
	String getCallerHostName();

	/**
	 * Gets the remote caller's IP address.
	 * 
	 * @return IP address
	 */
	String getCallerIpAddress();

	/**
	 * Gets the caller's port number.
	 * 
	 * @return port number.
	 */
	int getCallerPort();

	/**
	 * Gets the local address on the listening endpoint where the request was
	 * received.
	 * 
	 * @return IP address
	 */
	String getLocalAddress();

	/**
	 * Gets the local port number on the listening endpoint where the request was
	 * received.
	 * 
	 * @return port number.
	 */
	int getLocalPort();

	/**
	 * Gets the HTTP method.
	 * 
	 * @return HTTP method.
	 */
	public String getMethod();

	/**
	 * Gets the HTTP request headers.
	 * 
	 * @return request headers.
	 */
	Map<String, Iterable<String>> getHeaders();

	/**
	 * Gets buffered request content.
	 * 
	 * @return buffered content.
	 */
	byte[] getContent();

	/**
	 * Gets the content type for the buffered content.
	 * 
	 * @return content type
	 */
	String getContentType();

	/**
	 * Gets the content encoding.
	 * 
	 * @return content encoding
	 */
	String getEncoding();

	/**
	 * Determines if the request should be handled asynchronously.
	 * 
	 * @return true for asynchronous, false for synchronous.
	 */
	boolean isAsynchronous();

	/**
	 * Gets the session loader proxy.
	 * 
	 * @return session loader proxy.
	 */
	InvocationHandler getSessionLoaderProxy();

}
