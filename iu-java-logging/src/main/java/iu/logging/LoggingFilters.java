package iu.logging;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import edu.iu.logging.LoggingEnvironment;

public class LoggingFilters {

	private static final Map<ClassLoader, Class<?>> LOGGABLE_HANDLERS = new WeakHashMap<>();

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
	
	public static boolean isLoggable(LogRecord record, Handler handler) {
		return handler.isLoggable(record);
	}

	public static boolean isSql(String loggerName, Level logLevel) {
		return logLevel.intValue() >= Level.FINER.intValue() && loggerName != null
				&& (loggerName.contains("iu.sql") || loggerName.contains("iu.jdbc"));
	}

}
