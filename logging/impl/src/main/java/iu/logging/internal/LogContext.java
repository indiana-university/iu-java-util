package iu.logging.internal;

import java.util.logging.Level;

/**
 * Provides thread-bound context information for log events.
 * <p>
 * Mirrors iu.util.logging/edu.iu.logging.IuLogContext
 * </p>
 */
public interface LogContext {

	/**
	 * Gets the unique identifier for the active request
	 * 
	 * @return unique request ID
	 */
	String getRequestId();

	/**
	 * Gets the node identifier.
	 * 
	 * @return Unique node identifier
	 */
	String getNodeId();

	/**
	 * Gets the endpoint.
	 * 
	 * @return endpoint
	 */
	String getEndpoint();

	/**
	 * Gets the application code.
	 * 
	 * @return application code
	 */
	String getApplication();

	/**
	 * Gets the application environment.
	 * 
	 * @return application environment
	 */
	String getEnvironment();

	/**
	 * Gets the module code.
	 * 
	 * @return module code
	 */
	String getModule();

	/**
	 * Gets the component code.
	 * 
	 * @return component code
	 */
	String getComponent();

	/**
	 * Gets log level to use for reporting informational messages related to this
	 * logging context.
	 * 
	 * @return {@link Level}
	 */
	Level getLevel();

	/**
	 * Determines whether or not to enable extended debug logging appropriate for
	 * development environments.
	 * 
	 * @return true to enable extended debug logging; else false
	 */
	boolean isDevelopment();

	/**
	 * Gets the caller IP address to report with logged messages
	 * 
	 * @return caller IP address
	 */
	String getCallerIpAddress();

	/**
	 * Gets the called URL to report with logged messages
	 * 
	 * @return called URL
	 */
	String getCalledUrl();

	/**
	 * Gets the caller principal name to report with logged messages
	 * 
	 * @return caller principal name
	 */
	String getCallerPrincipalName();

	/**
	 * Gets the impersonated principal name to report with logged messages
	 * 
	 * @return impersonated principal name
	 */
	String getImpersonatedPrincipalName();

}
