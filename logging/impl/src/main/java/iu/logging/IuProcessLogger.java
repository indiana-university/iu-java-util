package iu.logging;

import java.util.function.Supplier;

/**
 * Implements static methods of IuLogContext.
 */
public final class IuProcessLogger {

	private IuProcessLogger() {
	}

	/**
	 * Gets an external proxy to the active log context.
	 * 
	 * @param logContext Reference to the public IuLogContext interface for proxying
	 *                   context attributes.
	 * @return Proxy instance of logContext for inspecting the active context
	 */
	public static Object getActiveContext(Class<?> logContext) {
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Gets the active log context.
	 * 
	 * @return {@link LogContext}
	 */
	public static LogContext getActiveContext() {
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Captures the current process trace.
	 * 
	 * @return process trace
	 */
	public static String export() {
		throw new UnsupportedOperationException("TODO");
	}

	/**
	 * Adds a message to the active process trace without logging.
	 * 
	 * @param messageSupplier message supplier
	 */
	public static void trace(Supplier<String> messageSupplier) {
		throw new UnsupportedOperationException("TODO");
	}

}
