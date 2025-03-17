package edu.iu.web;

import java.net.URI;

import com.sun.net.httpserver.HttpHandler;

/**
 * Encapsulates a reference to an {@link HttpHandler} by context path.
 */
public interface IuWebContext {

	/**
	 * Gets the application.
	 * 
	 * @return application code
	 */
	String getApplication();

	/**
	 * Gets the environment.
	 * 
	 * @return environment code
	 */
	String getEnvironment();

	/**
	 * Gets the module.
	 * 
	 * @return module name
	 */
	String getModule();

	/**
	 * Gets the runtime.
	 * 
	 * @return runtime name
	 */
	String getRuntime();

	/**
	 * Gets the component.
	 * 
	 * @return component name
	 */
	String getComponent();

	/**
	 * Gets the support pre-text.
	 * 
	 * @return support pre-text
	 */
	String getSupportPreText();

	/**
	 * Gets the support URL.
	 * 
	 * @return support URL
	 */
	String getSupportUrl();

	/**
	 * Gets the support label.
	 * 
	 * @return support label
	 */
	String getSupportLabel();

	/**
	 * Gets the context path relative to the server root.
	 * 
	 * @return context path, with leading slash
	 */
	URI getRootUri();

	/**
	 * Allow-list of domains permitted to submit cross-origin fetch requests.
	 * 
	 * @return set of domains
	 */
	Iterable<String> getOriginAllow();

	/**
	 * Gets the {@link HttpHandler} for requests relative to {@link #getRootUri()}.
	 * 
	 * @return {@link HttpHandler}
	 */
	HttpHandler getHandler();

	/**
	 * Gets the {@link ClassLoader} to use for binding threads to the context.
	 * 
	 * @return {@link ClassLoader}
	 */
	ClassLoader getLoader();

}
