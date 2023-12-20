package iu.logging;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Class containing logging filters.
 */
public class LoggingFilters {

	private static final Map<ClassLoader, Class<?>> LOGGABLE_HANDLERS = new WeakHashMap<>();

	private LoggingFilters() {}
	/**
	 * Determine whether a log message is local.
	 * 
	 * @return boolean Indicate whether a log message is local.
	 */
	public static boolean isLocal() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader != null) {
			Class<?> loggableHandler = LOGGABLE_HANDLERS.get(loader);
			if (loggableHandler == null)
				try {
					loggableHandler = loader.loadClass(LoggingFilters.class.getName());
					synchronized (LOGGABLE_HANDLERS) {
						LOGGABLE_HANDLERS.put(loader, loggableHandler);
					}
				} catch (ClassNotFoundException e) {
					loggableHandler = Object.class;
				}
			if (loggableHandler != LoggingFilters.class)
				return false;
		}
		return true;
	}

//	public static boolean isLoggable(String loggerName, Level logLevel) {
//		int levelValue = LoggingEnvironment.getLogLevel(loggerName).intValue();
//		return levelValue != Level.OFF.intValue() && logLevel.intValue() >= levelValue;
//	}

//	/**
//	 * Determine whether a given LogRecord is loggable for the given Handler.
//	 * 
//	 * @param record
//	 * @param handler
//	 * @return boolean Indicate whether the LogRecord is loggable for the given
//	 *         Handler.
//	 */
//	public static boolean isLoggable(LogRecord record, Handler handler) {
//		return handler.isLoggable(record);
//	}

//	public static boolean isSql(String loggerName, Level logLevel) {
//		return logLevel.intValue() >= Level.FINER.intValue() && loggerName != null
//				&& (loggerName.contains("iu.sql") || loggerName.contains("iu.jdbc"));
//	}

}