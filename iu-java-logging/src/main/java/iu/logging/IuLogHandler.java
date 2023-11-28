/*
 * Copyright © 2023 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.logging;

import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import edu.iu.logging.LoggingEnvironment;

/**
 * Main handler for console logs and accessing event queue.
 */
public class IuLogHandler extends ConsoleHandler {

//	private static final Map<String, LogFilePublishers> LOG_FILES = new HashMap<>();
//	private static final Map<ClassLoader, String> LOG_PATH_PREFIX = new WeakHashMap<>();
	private static final Deque<IuLogEvent> LOG_EVENTS = new ConcurrentLinkedDeque<>();;

	private static final Timer PURGE_TIMER;

	private static int corePoolSize = 8;
	private static int maximumPoolSize = 16;
	private static long keepAliveTime = 5000L;
	private static int TODO_EVENT_BUFFER_SIZE = 50;

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
				System.err.println("RUNNING PURGE_TIMER");
//				int bufferSize = LoggingEnvironment.getLogEventBufferSize();
				int bufferSize = TODO_EVENT_BUFFER_SIZE;
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

	public static Iterable<IuLogEvent> getLogEvents() {
//		System.err.println("GETTING LOG_EVENTS");
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

	private static void publishAsynchronously(LogRecord record, Handler handler) {
//		System.err.println("PUBLISH ASYNCHRONOUSLY");
//		if (!LoggingFilters.isLocal())
//			return;

		if (record.getMessage() == null)
			return;

//		String loggerName = record.getLoggerName();
//		Level level = record.getLevel();

//		boolean sql = LoggingFilters.isSql(loggerName, level);
		boolean loggable = isLoggable(record, handler);
//		if (!sql && !loggable)
//			return;
		if (!loggable)
			return;

		IuLogEvent event = new IuLogEvent() {
			@Override
			public String getMessage() {
				return record.getMessage();
			}
		};
//		if (loggable)
//			ProcessLogger.trace(() -> event.message);
		LOG_EVENTS.push(event);
	}

	public IuLogHandler() {
//		setLevel(LoggingEnvironment.getLogLevel());
//		System.err.println("PUBLIC IuLogHandler()");
		setLevel(Level.ALL);
	}

	public static boolean isLoggable(LogRecord record, Handler handler) {
		return handler.isLoggable(record);
	}

	@Override
	public boolean isLoggable(LogRecord record) {
//		System.err.println("RUNNING IuLogHandler.isLoggable(record)");
//		if (!LoggingFilters.isLocal())
//			return false;

//		String loggerName = record.getLoggerName();
//		Level level = record.getLevel();
//		return LoggingFilters.isSql(loggerName, level) || LoggingFilters.isLoggable(loggerName, level);
//		return LoggingFilters.isSql(loggerName, level) || super.isLoggable(record);
		return super.isLoggable(record);
	}

	@Override
	public void publish(LogRecord record) {
//		System.err.println("IuLogHandler publish(record)");
		publishAsynchronously(record, this);
	}
}
