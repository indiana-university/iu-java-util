package edu.iu.logging;

import static iu.logging.boot.LoggingBootstrap.impl;

import java.net.URI;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

import edu.iu.IuException;

/**
 * Encapsulates a fully serialized log event, processed from {@link LogRecord}
 * with {@link IuLogContext} attributes applied.
 */
public interface IuLogEvent {

	/**
	 * Subscribes to all log events at this node.
	 * 
	 * @return {@link Stream} of log events.
	 */
	@SuppressWarnings("unchecked")
	static Stream<IuLogEvent> subscribe() {
		return (Stream<IuLogEvent>) IuException
				.uncheckedInvocation(() -> impl().getMethod("subscribe", Class.class).invoke(null, IuLogEvent.class));
	}

	/**
	 * Gets the log level
	 * 
	 * @return {@link Level}
	 */
	Level getLevel();

	/**
	 * Gets the logger name
	 * 
	 * @return logger name
	 */
	String getLoggerName();

	/**
	 * Gets the process identifier
	 * 
	 * @return process identifier
	 */
	String getProcessId();

	/**
	 * Gets the environment code
	 * 
	 * @return environment code
	 */
	String getEnvironment();

	/**
	 * Gets the application code
	 * 
	 * @return application code
	 */
	String getApplication();

	/**
	 * Gets the module code
	 * 
	 * @return module code
	 */
	String getModule();

	/**
	 * Gets the component code
	 * 
	 * @return component code
	 */
	String getComponent();

	/**
	 * Gets the node identifier
	 * 
	 * @return node identifier
	 */
	String getNodeId();

	/**
	 * Gets the thread name
	 * 
	 * @return thread name
	 */
	String getThread();

	/**
	 * Gets the caller IP address
	 * 
	 * @return caller IP address
	 */
	String getCallerIpAddress();

	/**
	 * Gets the called URL
	 * 
	 * @return called URL
	 */
	URI getCalledUrl();

	/**
	 * Gets the caller principal name
	 * 
	 * @return caller principal name
	 */
	String getCallerPrincipalName();

	/**
	 * Gets the impersonated principal name
	 * 
	 * @return impersonated principal name
	 */
	String getImpersonatedPrincipalName();

	/**
	 * Gets the timestamp
	 * 
	 * @return timestamp
	 */
	Instant getTimestamp();

	/**
	 * Gets the source class name
	 * 
	 * @return source class name
	 */
	String getSourceClassName();

	/**
	 * Gets the source method name
	 * 
	 * @return source method name
	 */
	String getSourceMethodName();

	/**
	 * Gets the log message
	 * 
	 * @return log message
	 */
	String getMessage();

	/**
	 * Gets the process log
	 * 
	 * @return process log
	 */
	String getProcessLog();

	/**
	 * Gets the error message
	 * 
	 * @return error message
	 */
	String getError();

}