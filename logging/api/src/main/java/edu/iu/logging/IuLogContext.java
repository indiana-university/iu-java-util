package edu.iu.logging;

import static iu.logging.boot.LoggingBootstrap.impl;

import java.util.function.Supplier;
import java.util.logging.Level;

import edu.iu.IuException;
import edu.iu.UnsafeSupplier;

/**
 * Provides thread-bound context information for log events.
 */
public interface IuLogContext {

	/**
	 * Gets the active context on the current thread.
	 * 
	 * @return {@link IuLogContext}
	 */
	public static IuLogContext getActiveContext() {
		return IuException.uncheckedInvocation(() -> {
			final var impl = impl();
			return (IuLogContext) impl.getMethod("getActiveContext", Class.class).invoke(null, IuLogContext.class);
		});
	}

	/**
	 * Pushes a new logging context onto the current thread and uses it to follow a
	 * synchronous processing task.
	 * 
	 * @param <T>      return type
	 * @param context  {@link IuLogContext}
	 * @param msg      short message to augment trace behavior
	 * @param supplier processing task to follow
	 * @return value from {@link UnsafeSupplier#get()}
	 * @throws Throwable from {@link UnsafeSupplier#get()}
	 */
	@SuppressWarnings("unchecked")
	public static <T> T follow(IuLogContext context, String msg, UnsafeSupplier<T> supplier) throws Throwable {
		final var impl = impl();
		final var follow = impl.getMethod("follow", Object.class, String.class, Object.class);
		return (T) IuException.checkedInvocation(() -> follow.invoke(null, context, msg, supplier));
	}

	/**
	 * Adds a message to the process trace without logging.
	 * 
	 * @param messageSupplier Message {@link Supplier}
	 */
	public static void trace(Supplier<String> messageSupplier) {
		IuException.uncheckedInvocation(() -> {
			impl().getMethod("trace", Supplier.class).invoke(null, messageSupplier);
			return null;
		});
	}

	/**
	 * Forces all log events.
	 */
	public static void flushLogFiles() {
		IuException.uncheckedInvocation(() -> {
			final var impl = impl();
			final var flushLogFiles = impl.getMethod("flushLogFiles");
			flushLogFiles.invoke(null);
			return null;
		});
	}

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
	 * Gets the endpoint identifier.
	 * 
	 * @return endpoint identifier
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
	 * Gets log level to use for reporting information messages and process trace
	 * dumps related to this logging context.
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
