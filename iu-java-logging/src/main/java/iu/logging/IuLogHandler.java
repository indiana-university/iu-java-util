package iu.logging;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import edu.iu.logging.LoggingEnvironment;
import edu.iu.logging.ProcessLogger;

public class IuLogHandler extends ConsoleHandler {

	private static final Map<String, LogFilePublishers> LOG_FILES = new HashMap<>();
	private static final Map<ClassLoader, String> LOG_PATH_PREFIX = new WeakHashMap<>();
	private static final Deque<IuLogEvent> LOG_EVENTS = new ConcurrentLinkedDeque<>();;

	private static final Timer PURGE_TIMER;

	private static int corePoolSize = 8;
	private static int maximumPoolSize = 16;
	private static long keepAliveTime = 5000L;
	private static int DEFAULT_EVENT_BUFFER_SIZE = 10;

	private static ThreadGroup threadGroup;
	private static ThreadPoolExecutor executor;

	static {
		threadGroup = new ThreadGroup("iu-logging");
		ThreadFactory tf = new ThreadFactory() {
			int tn = 0;

			@Override
			public Thread newThread(Runnable r) {
				Thread newThread = new Thread(threadGroup, r, "(iu-logging/" + Integer.toString(++tn) + ')');
				newThread.setDaemon(true);
				return newThread;
			}
		};
		executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MILLISECONDS,
				new LinkedBlockingDeque<Runnable>(), tf);

		PURGE_TIMER = new Timer("iu-logging-purge", true);
		PURGE_TIMER.schedule(new TimerTask() {
			@Override
			public void run() {
//				int bufferSize = LoggingEnvironment.getLogEventBufferSize();
				int bufferSize = DEFAULT_EVENT_BUFFER_SIZE;
				long now = System.currentTimeMillis();
				Iterator<IuLogEvent> i = LOG_EVENTS.iterator();
				int n = 0;
				while (i.hasNext()) {
					IuLogEvent e = i.next();
//					int l = e.level.intValue();
					int l = e.getLevel().intValue();
					long ttl;
					if (l >= Level.SEVERE.intValue())
						ttl = TimeUnit.DAYS.toMillis(3L);
					else if (l >= Level.INFO.intValue())
						ttl = TimeUnit.DAYS.toMillis(1L);
					else if (l >= Level.FINE.intValue())
						ttl = TimeUnit.HOURS.toMillis(2L);
					else
						ttl = TimeUnit.MINUTES.toMillis(30L);

//					if (now - e.timestamp > ttl || n >= bufferSize)
					if (now - e.getInstant().toEpochMilli() > ttl || n >= bufferSize)
						i.remove();
					else
						n++;
				}
			}
		}, TimeUnit.SECONDS.toMillis(15L), TimeUnit.SECONDS.toMillis(15L));
	}

	private static class LogFilePublishers {

		private final LogFilePublisher error;
		private final LogFilePublisher info;
		private final LogFilePublisher debug;
//		private final LogFilePublisher json; // TODO - JSON log analysis tool
		private final LogFilePublisher sql;

		private LogFilePublishers(String logPathPrefix) {
			error = new LogFilePublisher(logPathPrefix + "_error.log", 15728640L, 10);
			info = new LogFilePublisher(logPathPrefix + ".log", 10485760, 10);
			debug = new LogFilePublisher(logPathPrefix + "_debug.log", 10485760, 10);
//			json = new LogFilePublisher(logPathPrefix + "_json.log", 10485760, 2);
			sql = new LogFilePublisher(logPathPrefix + "_sql.log", 10485760, 10);
		}
	}

	public static Iterable<IuLogEvent> getLogEvents() {
		return Collections.unmodifiableCollection(LOG_EVENTS);
	}

	public static void handleFileWriteError(String message, Throwable e) {
		StringBuilder sb = new StringBuilder();
		sb.append(new Date());
		sb.append(" iu-logging ");
		sb.append(LoggingEnvironment.getEndpoint());
		sb.append("-");
		sb.append(LoggingEnvironment.getEnvironment());
		sb.append(" failure: ");
		sb.append(message);
		System.err.println(sb);
		if (e != null) {
			e.printStackTrace();
			System.err.println();
		}
	}

//	private static LogFilePublishers getLogFiles() {
//		ClassLoader tcl = Thread.currentThread().getContextClassLoader();
//		if (tcl == null)
//			tcl = IuLogHandler.class.getClassLoader();
//
//		String logPathPrefix = LOG_PATH_PREFIX.get(tcl);
//		if (logPathPrefix == null) {
//			String logPath = LoggingEnvironment.getLogPath();
//			if (logPath == null)
//				return null;
//
//			try {
//				File logFile = new File(logPath).getCanonicalFile();
//				logFile.mkdirs();
//				logPath = logFile.getPath();
//
//			} catch (IOException e) {
//				handleFileWriteError("Failed to create IU log path " + logPath, e);
//			}
//
//			try {
//				String application = LoggingEnvironment.getApplication();
//				synchronized (LOG_PATH_PREFIX) {
//					LOG_PATH_PREFIX.put(tcl, logPathPrefix = logPath + "/" + application);
//				}
//			} catch (IllegalArgumentException e) {
//				handleFileWriteError("missing application, skipping log file setup", e);
//			}
//		}
//
//		LogFilePublishers logFiles = LOG_FILES.get(logPathPrefix);
//		if (logFiles == null) {
//			logFiles = new LogFilePublishers(logPathPrefix);
//			synchronized (LOG_FILES) {
//				LOG_FILES.put(logPathPrefix, logFiles);
//			}
//		}
//
//		return logFiles;
//	}

//	private static void publishAsynchronously(LogRecord record) {
//		if (!LoggingFilters.isLocal())
//			return;
//
//		if (record.getMessage() == null)
//			return;
//
//		String loggerName = record.getLoggerName();
//		Level level = record.getLevel();
//
//		boolean sql = LoggingFilters.isSql(loggerName, level);
//		boolean loggable = LoggingFilters.isLoggable(loggerName, level);
//		if (!sql && !loggable)
//			return;
//		boolean loggable = LoggingFilters.isLoggable(record, this);
//
//		IuLogEvent event = new IuLogEvent() {
//			
//			
//		};
//		if (loggable)
//			ProcessLogger.trace(() -> event.message);
//		LOG_EVENTS.push(event);
//	}

	private static void publishAsynchronously(LogRecord record, Handler handler) {
		if (!LoggingFilters.isLocal())
			return;

		if (record.getMessage() == null)
			return;

		String loggerName = record.getLoggerName();
		Level level = record.getLevel();

		boolean sql = LoggingFilters.isSql(loggerName, level);
		boolean loggable = LoggingFilters.isLoggable(record, handler);
		if (!sql && !loggable)
			return;

		IuLogEvent event = new IuLogEvent() {
			
			
		};
//		if (loggable)
//			ProcessLogger.trace(() -> event.message);
		LOG_EVENTS.push(event);
	}
	
	public IuLogHandler() {
//		setLevel(LoggingEnvironment.getLogLevel());
		setLevel(Level.ALL);
	}

	@Override
	public boolean isLoggable(LogRecord record) {
		if (!LoggingFilters.isLocal())
			return false;

		String loggerName = record.getLoggerName();
		Level level = record.getLevel();
//		return LoggingFilters.isSql(loggerName, level) || LoggingFilters.isLoggable(loggerName, level);
		return LoggingFilters.isSql(loggerName, level) || LoggingFilters.isLoggable(record, this);
	}

	@Override
	public void publish(LogRecord record) {
		publishAsynchronously(record, this);
	}
}
