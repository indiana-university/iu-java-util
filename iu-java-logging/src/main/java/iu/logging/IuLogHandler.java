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
		System.err.println("Setting up purge timer");
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
					System.err.println("Purge Timer processing. " + e.getMessage());
					long ttl;
					if (l >= Level.SEVERE.intValue())
						ttl = severePurgeTime();
					else if (l >= Level.INFO.intValue())
						ttl = infoPurgeTime();
					else if (l >= Level.FINE.intValue())
						ttl = finePurgeTime();
					else
						ttl = defaultPurgeTime();

//					if (now - e.timestamp > ttl || n >= bufferSize)
					System.err.println("ttl: " + ttl + " removing if this value is greater than ttl: " + (now - e.getInstant().toEpochMilli()));
					if (now - e.getInstant().toEpochMilli() > ttl || n >= bufferSize)
						i.remove();
					else
						n++;
				}
			}
		}, TimeUnit.SECONDS.toMillis(1L), TimeUnit.SECONDS.toMillis(1L));
	}

	static long severePurgeTime() {
		System.err.println("called severePurgeTime");
		return TimeUnit.DAYS.toMillis(3L);
	}
	static long infoPurgeTime() {
		return TimeUnit.DAYS.toMillis(1L);
	}
	static long finePurgeTime() {
		return TimeUnit.HOURS.toMillis(2L);
	}
	static long defaultPurgeTime() {
		return TimeUnit.MINUTES.toMillis(30L);
	}

	/**
	 * Get the log events.
	 * 
	 * @return Iterable&lt;IuLogEvent&gt; representing the log events that have been
	 *         tracked so far.
	 */
	public static Iterable<IuLogEvent> getLogEvents() {
//		System.err.println("GETTING LOG_EVENTS");
		return Collections.unmodifiableCollection(LOG_EVENTS);
	}

//	/**
//	 * Print the stack trace and an error message to System.err
//	 * 
//	 * @param message String providing additional information related to the error
//	 * @param e       Throwable the error that was thrown.
//	 */
//	public static void handleFileWriteError(String message, Throwable e) {
//		StringBuilder sb = new StringBuilder();
//		sb.append(new Date());
//		sb.append(" iu-logging ");
////		sb.append(LogEventFactory.getEndpoint()); // Endpoint was removed because it is not in the vpp file
//		sb.append("-");
//		sb.append(LogEventFactory.getEnvironment());
//		sb.append(" failure: ");
//		sb.append(message);
//		System.err.println(sb);
//		if (e != null) {
//			e.printStackTrace();
//			System.err.println();
//		}
//	}

	/**
	 * default constructor for IuLogHandler.
	 */
	public IuLogHandler() {
	}

//	/**
//	 * Determine if a given LogRecord is loggable for a given Handler.
//	 * 
//	 * @param record  LogRecord to be logged.
//	 * @param handler Handler to check for logability of this record.
//	 * @return Boolean representing whether this LogRecord is loggable for the given
//	 *         Handler.
//	 */
//	public static boolean isLoggable(LogRecord record, Handler handler) {
//		// System.err.println("IuLogHandler.isLoggable(record, handler)");
//		return handler.isLoggable(record);
//	}

	/**
	 * Determine if the given LogRecord is loggable.
	 * 
	 * @param record LogRecord to be logged.
	 * @return boolean representing whether the given Log Record is loggable for
	 *         this Handler.
	 */
	@Override
	public boolean isLoggable(LogRecord record) {
//		System.err.println("RUNNING IuLogHandler.isLoggable(record)");
//		if (!LoggingFilters.isLocal())
//			return false;

		return super.isLoggable(record);
	}

	/**
	 * Publish the LogRecord
	 * 
	 * @param record LogRecord to be published.
	 */
	@Override
	public void publish(LogRecord record) {
//		System.err.println("IuLogHandler publish(record)");
		// TODO: removing loggable check broke the tests. There isn't a handler being
		// found in getEnvironmentProperties call within createEvent.
		// Mainly because of the loggable check, it gets past the test error because
		// there was a FINER log from junit that was trying to be published between when
		// bootstrap finished and the first test ran.
		if (LoggingFilters.isLocal() && isLoggable(record)) {
			LogEvent event = LogEventFactory.createEvent(record);
			// then add event to queue.
			LOG_EVENTS.offer(event);
		}
		super.publish(record);
	}
}
