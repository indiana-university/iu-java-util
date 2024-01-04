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
import java.util.Deque;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

import edu.iu.IuAsynchronousSubject;
import edu.iu.IuAsynchronousSubscription;
import edu.iu.logging.IuLogEvent;

/**
 * Main handler for console logs and accessing event queue.
 */
public class IuLogHandler extends Handler {

//	private static final Map<String, LogFilePublishers> LOG_FILES = new HashMap<>();
//	private static final Map<ClassLoader, String> LOG_PATH_PREFIX = new WeakHashMap<>();
	private static final Deque<IuLogEvent> LOG_EVENTS = new ConcurrentLinkedDeque<>();
	private static final IuAsynchronousSubject<IuLogEvent> LOG_EVENT_SUBJECT = new IuAsynchronousSubject<IuLogEvent>(
			() -> LOG_EVENTS.spliterator());

	private static final Timer PURGE_TIMER;

//	private static int corePoolSize = 8;
//	private static int maximumPoolSize = 16;
//	private static long keepAliveTime = 5000L;
//
//	private static ThreadGroup threadGroup;
//	private static ThreadPoolExecutor executor;

	static {
//		threadGroup = new ThreadGroup("iu-logging");
//		ThreadFactory tf = new ThreadFactory() {
//			int tn = 0;
//
//			@Override
//			public Thread newThread(Runnable r) {
//				Thread newThread = new Thread(threadGroup, r, "(iu-logging/" + Integer.toString(++tn) + ')');
//				newThread.setDaemon(true);
//				return newThread;
//			}
//		};
//		executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MILLISECONDS,
//				new LinkedBlockingDeque<Runnable>(), tf);

		PURGE_TIMER = new Timer("iu-logging-purge", true);
		PURGE_TIMER.schedule(new TimerTask() {
			@Override
			public void run() {
				purgeByTime();
			}
		}, TimeUnit.SECONDS.toMillis(15L), TimeUnit.SECONDS.toMillis(15L));
	}

	/**
	 * Purge logs based on the time a given log level's logs should be kept around
	 * and how many log events are in the queue.
	 */
	static void purgeByTime() {
		int bufferSize = defaultEventBufferSize();
		long now = System.currentTimeMillis();
		Iterator<IuLogEvent> i = LOG_EVENTS.iterator();
		int n = 0;
		while (i.hasNext()) {
			IuLogEvent e = i.next();
			int l = e.getLevel().intValue();
			long ttl;
			if (l >= Level.SEVERE.intValue())
				ttl = severePurgeTime();
			else if (l >= Level.INFO.intValue())
				ttl = infoPurgeTime();
			else if (l >= Level.FINE.intValue())
				ttl = finePurgeTime();
			else
				ttl = defaultPurgeTime();

			if (now - e.getInstant().toEpochMilli() > ttl || n >= bufferSize)
				i.remove();
			else
				n++;
		}
	}

	/**
	 * The default time to live for SEVERE logs.
	 * 
	 * @return long The number of milliseconds to keep SEVERE logs.
	 */
	static long severePurgeTime() {
		return TimeUnit.DAYS.toMillis(3L);
	}

	/**
	 * The default time to live for INFO logs.
	 * 
	 * @return long The number of milliseconds to keep INFO logs.
	 */
	static long infoPurgeTime() {
		return TimeUnit.DAYS.toMillis(1L);
	}

	/**
	 * The default time to live for FINE logs.
	 * 
	 * @return long The number of milliseconds to keep FINE logs.
	 */
	static long finePurgeTime() {
		return TimeUnit.HOURS.toMillis(2L);
	}

	/**
	 * The default time to live for logs lower than FINE.
	 * 
	 * @return long The number of milliseconds to keep log lower than FINE.
	 */
	static long defaultPurgeTime() {
		return TimeUnit.MINUTES.toMillis(30L);
	}

	/**
	 * The maximum number of log events to buffer before purging the excess.
	 * 
	 * @return int The number of log events to buffer before purging the excess.
	 */
	static int defaultEventBufferSize() {
		return 50;
	}

	/**
	 * Get the log events.
	 * 
	 * @return Iterable&lt;IuLogEvent&gt; representing the log events that have been
	 *         tracked so far.
	 */
	public static Iterable<IuLogEvent> getLogEvents() {
		return Collections.unmodifiableCollection(LOG_EVENTS);
	}

	/**
	 * Get LogEvents as a Stream.
	 * 
	 * @return Stream&lt;IuLogEvent&gt; that will contain any log events from now
	 *         until the stream is closed.
	 */
	public static IuAsynchronousSubscription<IuLogEvent> subscribe() {
		return LOG_EVENT_SUBJECT.subscribe();
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
	 * constructor for IuLogHandler.
	 */
	public IuLogHandler() {
	}

	/**
	 * Determine if the given LogRecord is loggable.
	 * 
	 * @param record LogRecord to be logged.
	 * @return boolean representing whether the given Log Record is loggable for
	 *         this Handler.
	 */
	@Override
	public boolean isLoggable(LogRecord record) {
		return super.isLoggable(record);
	}

	/**
	 * Publish the LogRecord
	 * 
	 * @param record LogRecord to be published.
	 */
	@Override
	public void publish(LogRecord record) {
		if (LoggingFilters.isLocal() && isLoggable(record)) {
			LogEvent event = LogEventFactory.createEvent(record);
			LOG_EVENTS.offer(event);
			LOG_EVENT_SUBJECT.accept(event);
		}
	}

	/**
	 * Flush. No-op.
	 */
	@Override
	public void flush() {
	}

	/**
	 * Handle close activities, including closing the subject and setting the queue
	 * to null.
	 */
	@Override
	public void close() throws SecurityException {
		// TODO Auto-generated method stub
		// close subject
		// set queue to null
	}
}
