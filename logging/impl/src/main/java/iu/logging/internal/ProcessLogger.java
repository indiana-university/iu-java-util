/*
 * Copyright © 2026 Indiana University
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
package iu.logging.internal;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;

import edu.iu.IuObject;
import edu.iu.UnsafeSupplier;
import iu.logging.Bootstrap;
import iu.logging.LogContext;
import iu.logging.LogEnvironment;

/**
 * Tracks log messages by bounded execution context.
 */
public final class ProcessLogger {

	private static final Logger LOG = Logger.getLogger(ProcessLogger.class.getName());

	private static class TracedMessage {
		private final Instant timestamp;
		private final long free;
		private final String message;
		private final int depth;

		private TracedMessage(String message, int depth) {
			this(message, depth, Instant.now(), Runtime.getRuntime().freeMemory());
		}

		private TracedMessage(String message, int depth, Instant timestamp, long free) {
			this.message = message;
			this.depth = depth;
			this.timestamp = timestamp;
			this.free = free;
		}

		private void append(StringBuilder sb, Instant start, Instant lastTime, long lastFree) {
			final var mark = sb.length();

			for (int i = 0; i < depth; i++)
				sb.append(' ');
			sb.append(this.message);

			final var limit = mark + 80;
			if (sb.length() < limit)
				for (int i = sb.length(); i < limit; i++)
					sb.append('.');
			else
				sb.setLength(limit);

			for (int i = mark; i < limit; i++)
				if (sb.charAt(i) < ' ')
					sb.setCharAt(i, ' ');

			final var diffTime = Duration.between(lastTime, timestamp);
			sb.append(' ');
			sb.append(intervalToString(diffTime));
			sb.append(' ');
			sb.append(intervalToString(Duration.between(start, timestamp)));

			final var diffFree = free - lastFree;
			sb.append(' ');
			sb.append(sizeToString(free));
			sb.append(' ');
			sb.append(sizeToString(diffFree));

			sb.append(System.lineSeparator());
		}
	}

	private static class ProcessState {
		private final String requestId;
		private final LogEnvironment logEnvironment;
		private final LogContext logContext;
		private final String header;
		private final Instant start = Instant.now();
		private final long startFree = Runtime.getRuntime().freeMemory();
		private final long startTot = Runtime.getRuntime().totalMemory();
		private final long startMax = Runtime.getRuntime().maxMemory();
		private volatile Instant end;
		private long endFree;
		private long endTot;
		private long endMax;
		private final Queue<Object> children = new ConcurrentLinkedQueue<>();
		private final int depth;
		private final AtomicInteger subRequestId = new AtomicInteger();
		private final AtomicInteger joinId = new AtomicInteger();

		/**
		 * Counts messages traced anywhere in this process' tree.
		 *
		 * <p>
		 * Shared with the root process rather than held per nesting level, so that the
		 * budget bounds the trace a single {@link #export()} renders instead of
		 * bounding it once per sub-process.
		 * </p>
		 */
		private final AtomicInteger traced;

		private ProcessState(LogContext logContext, String header) {
			this.header = header;

			final var active = ACTIVE_PROCESS_STATE.get();
			if (active == null) {
				requestId = Long.toString(REQUEST_ID.incrementAndGet());
				depth = 0;
				traced = new AtomicInteger();
			} else {
				active.children.add(this);
				requestId = active.requestId + '.' + Integer.toString(active.subRequestId.incrementAndGet());
				depth = active.depth + 1;
				traced = active.traced;
			}

			this.logContext = logContext;
			this.logEnvironment = Bootstrap.getEnvironment();

			final var application = logEnvironment.getApplication();
			final var begin = new StringBuilder();
			begin.append((depth == 0) ? "begin " : ">").append(requestId);
			if (application != null)
				begin.append(' ').append(application);
			begin.append(": ").append(header);

			children.add(new TracedMessage(begin.toString(), Integer.max(0, depth - 1)));
		}

		private void end() {
			final var runtime = Runtime.getRuntime();
			endFree = runtime.freeMemory();
			endTot = runtime.totalMemory();
			endMax = runtime.maxMemory();
			end = Instant.now();

			final var application = logEnvironment.getApplication();
			final var end = new StringBuilder();
			end.append((depth == 0) ? "end " : "<").append(requestId);
			if (application != null)
				end.append(' ').append(application);
			end.append(": ").append(header);

			children.add(new TracedMessage(end.toString(), depth - 1));
		}

		@Override
		public String toString() {
			var lastTime = start;
			var lastFree = startFree;

			final var sb = new StringBuilder();

			sb.append("init: ").append(start).append(" ").append(memoryToString(startFree, startTot, startMax));
			sb.append(System.lineSeparator());

			final Deque<Iterator<?>> inProgress = new ArrayDeque<>();
			Iterator<?> current = children.iterator();
			while (current.hasNext() //
					|| !inProgress.isEmpty()) {

				// the interrupted iterator may itself be exhausted, i.e. when a sub-process
				// was the last message appended before the trace was captured
				if (!current.hasNext()) {
					current = inProgress.pop();
					continue;
				}

				final TracedMessage message;
				final var next = current.next();
				if (next instanceof TracedMessage) {
					message = (TracedMessage) next;
					message.append(sb, start, lastTime, lastFree);

					lastTime = message.timestamp;
					lastFree = message.free;
				} else {
					final var state = (ProcessState) next;

					inProgress.push(current);

					current = state.children.iterator();

					lastTime = state.start;
					lastFree = state.startFree;
				}
			}

			if (end != null) {
				sb.append("final: ");
				sb.append(Duration.between(start, end));
				sb.append(' ');
				sb.append(sizeToString(endFree - startFree));
				sb.append(' ');
				sb.append(memoryToString(endFree, endTot, endMax));
			}

			return sb.toString();
		}
	}

	private static final String[] SIZE_INTERVALS = new String[] { "Ki", "Mi", "Gi", "Ti", };

	private static final ThreadLocal<ProcessState> ACTIVE_PROCESS_STATE = new ThreadLocal<>();

	private static final ThreadLocal<Integer> ACTIVE_JOIN_ID = new ThreadLocal<>();

	private static final AtomicLong REQUEST_ID = new AtomicLong();

	/**
	 * Largest number of messages retained in one process trace.
	 *
	 * <p>
	 * The trace is fed by every record {@link IuLogHandler#publish(java.util.logging.LogRecord)
	 * published} within the process, so its size tracks the process' log volume
	 * rather than anything the caller chose to trace. Without a cap, a process
	 * that is long-lived or high-volume relative to the number of warnings it
	 * produces retains the whole of it, and {@link IuLogEvent} copies the whole of
	 * it again for every record at {@link java.util.logging.Level#WARNING} or
	 * above.
	 * </p>
	 */
	static final int MAX_TRACED_MESSAGES = 10000;

	private static final ThreadLocal<DecimalFormat> DF3 = new ThreadLocal<DecimalFormat>() {
		@Override
		protected DecimalFormat initialValue() {
			return new DecimalFormat("000");
		}
	};

	private static final ThreadLocal<DecimalFormat> DF2 = new ThreadLocal<DecimalFormat>() {
		@Override
		protected DecimalFormat initialValue() {
			return new DecimalFormat("00");
		}
	};

	private ProcessLogger() {
	}

	/**
	 * Prints a size in bytes rounded to largest non-zero unit.
	 * 
	 * @param bytes size in bytes
	 * @return approximated size using largest non-zero unit
	 */
	static String sizeToString(long bytes) {
		final var sb = new StringBuilder();
		var i = -1;
		var mod = 0;
		if (bytes < 0) {
			sb.append('-');
			bytes = Math.abs(bytes);
		}

		while (bytes / 1024 > 0 && i < SIZE_INTERVALS.length - 1) {
			i++;
			mod = (int) (bytes % 1024);
			bytes /= 1024;
		}

		sb.append(bytes);
		if (mod > 0) {
			sb.append('.');
			sb.append(DF3.get().format(mod * 1000 / 1024));
		}

		if (i >= 0)
			sb.append(SIZE_INTERVALS[i]);

		sb.append("B");

		return sb.toString();
	}

	/**
	 * Formats a {@link Duration}
	 * 
	 * @param interval {@link Duration}
	 * @return formatted text
	 */
	static String intervalToString(Duration interval) {
		final var sb = new StringBuilder();

		final var df = DF3.get();
		sb.append('.');
		sb.append(df.format(interval.toMillisPart()));

		final var df2 = DF2.get();
		sb.insert(0, df2.format(interval.toSecondsPart()));
		sb.insert(0, ':');
		sb.insert(0, df2.format(interval.toMinutesPart()));

		final var hours = interval.toHoursPart();
		if (hours > 0) {
			sb.insert(0, ':');
			sb.insert(0, df2.format(hours % 24));
		}

		final var days = interval.toDays();
		if (days > 0) {
			sb.insert(0, " days, ");
			sb.insert(0, days);
		}

		return sb.toString();
	}

	/**
	 * Formats runtime memory stats for printing with the process trace.
	 * 
	 * @param free free memory
	 * @param tot  total memory
	 * @param max  maximum memory
	 * @return formatted memory stats representation
	 */
	static String memoryToString(long free, long tot, long max) {
		final var sb = new StringBuilder();
		sb.append(sizeToString(free));
		sb.append('/');
		sb.append(sizeToString(tot));
		sb.append('/');
		sb.append(sizeToString(max));
		sb.append(" - ");
		sb.append(free * 100 / tot);
		sb.append("% free");
		return sb.toString();
	}

	/**
	 * Retrieves a value from an {@link UnsafeSupplier} using the provided
	 * {@link LogContext} bound to the current thread.
	 * 
	 * <p>
	 * The process is closed out and its trace reported whether the supplier returns
	 * or throws; the outermost process reports the accumulated trace, a nested one
	 * only that it ended. A nested process does not inherit the
	 * {@link #join(Object, Runnable) join} ID of the task it opens inside, since
	 * its own messages belong to neither the forking thread nor the join.
	 * </p>
	 *
	 * @param <T>      return type
	 * @param context  {@link LogContext}
	 * @param header   header message for the process log
	 * @param supplier {@link UnsafeSupplier}
	 * @return return value
	 * @throws Throwable If an error occurs
	 */
	public static <T> T follow(LogContext context, String header, UnsafeSupplier<T> supplier) throws Throwable {
		final var restoreState = ACTIVE_PROCESS_STATE.get();
		final var restoreJoinId = ACTIVE_JOIN_ID.get();
		try {
			// constructed before the thread-locals are replaced: it reads the process it
			// nests inside, and the join ID a nested process must not inherit
			final var state = new ProcessState(context, header);

			ACTIVE_PROCESS_STATE.set(state);
			ACTIVE_JOIN_ID.remove();
			LOG.info(() -> "begin " + state.requestId + ": " + header);

			final T rv;
			try {
				rv = supplier.get();
			} catch (Throwable e) {
				// closed out and reported on this path too: a process that failed is the one
				// whose trace is worth the most, and the finally below unbinds it before the
				// caller can capture it
				state.end();
				report(state, header, restoreState == null, "failed");
				throw e;
			}

			state.end();
			report(state, header, restoreState == null, "complete");

			return rv;

		} finally {
			if (restoreState == null)
				ACTIVE_PROCESS_STATE.remove();
			else
				ACTIVE_PROCESS_STATE.set(restoreState);

			if (restoreJoinId == null)
				ACTIVE_JOIN_ID.remove();
			else
				ACTIVE_JOIN_ID.set(restoreJoinId);
		}
	}

	// the outcome is reported only by the boundary that renders the trace: a nested
	// process ends within a process whose own outcome is not yet known
	private static void report(ProcessState state, String header, boolean outermost, String outcome) {
		if (outermost)
			LOG.info(() -> outcome + " " + state.requestId + ": " + header + System.lineSeparator() + state);
		else
			LOG.info(() -> "end " + state.requestId + ": " + header);
	}

	/**
	 * Captures the process bound to the current thread, as an opaque handle that
	 * may be {@link #join(Object, Runnable) joined} from another thread.
	 *
	 * <p>
	 * Returns null when the current thread is not
	 * {@link #follow(LogContext, String, UnsafeSupplier) following} a process;
	 * {@link #join(Object, Runnable) joining} a null handle is valid and simply
	 * detaches the joining thread from any process already bound to it.
	 * </p>
	 *
	 * @return opaque handle on the active process; null if not following
	 */
	public static Object fork() {
		return ACTIVE_PROCESS_STATE.get();
	}

	/**
	 * Binds a {@link #fork() forked} process to the current thread for the duration
	 * of a task.
	 *
	 * <p>
	 * Messages {@link #trace(Supplier) traced} by the task are appended to the
	 * forked process' trace, and log events published by the task report the forked
	 * process' {@link #getActiveContext() context}. Any process already bound to the
	 * current thread is restored when the task completes, so a task may be joined by
	 * the same thread that forked it, i.e. when a work queue falls back to executing
	 * on the submitting thread.
	 * </p>
	 *
	 * <p>
	 * Each joined task is assigned the next sequential join ID for the forked
	 * process, and messages it traces are prefixed with {@code joinId + "> "} so
	 * that work forked onto other threads can be told apart from the forking
	 * thread's own messages once merged into a single trace.
	 * </p>
	 *
	 * <p>
	 * It is up to the forking thread to ensure joined tasks complete before it stops
	 * {@link #follow(LogContext, String, UnsafeSupplier) following} the forked
	 * process; messages traced after the process ends are not guaranteed to be
	 * reported.
	 * </p>
	 *
	 * @param forkedContext opaque handle returned by {@link #fork()}
	 * @param task          task to run with the forked process bound
	 * @throws IllegalArgumentException if {@code forkedContext} did not come from
	 *                                  {@link #fork()}, i.e. a handle retained
	 *                                  across a logging reconfiguration, which
	 *                                  names a {@link ProcessState} class this
	 *                                  module no longer defines
	 */
	public static void join(Object forkedContext, Runnable task) {
		// checked rather than cast so the diagnostic names the contract, and checked
		// before any thread-local is touched so a rejected handle leaves the joining
		// thread exactly as it was
		if (forkedContext != null //
				&& !(forkedContext instanceof ProcessState))
			throw new IllegalArgumentException(
					forkedContext.getClass().getName() + " did not come from fork(); see IuLogContext.fork()");

		final var forked = (ProcessState) forkedContext;
		final var restoreState = ACTIVE_PROCESS_STATE.get();
		final var restoreJoinId = ACTIVE_JOIN_ID.get();
		try {
			if (forked == null) {
				ACTIVE_PROCESS_STATE.remove();
				ACTIVE_JOIN_ID.remove();
			} else {
				ACTIVE_PROCESS_STATE.set(forked);
				ACTIVE_JOIN_ID.set(forked.joinId.incrementAndGet());
			}

			task.run();

		} finally {
			if (restoreState == null)
				ACTIVE_PROCESS_STATE.remove();
			else
				ACTIVE_PROCESS_STATE.set(restoreState);

			if (restoreJoinId == null)
				ACTIVE_JOIN_ID.remove();
			else
				ACTIVE_JOIN_ID.set(restoreJoinId);
		}
	}

	/**
	 * Gets an external proxy to the active log context.
	 * 
	 * @return Proxy instance of logContext for inspecting the active context
	 */
	public static LogContext getActiveContext() {
		return IuObject.convert(ACTIVE_PROCESS_STATE.get(), a -> a.logContext);
	}

	/**
	 * Captures the current process trace.
	 * 
	 * @return process trace
	 */
	public static String export() {
		final var state = ACTIVE_PROCESS_STATE.get();
		if (state == null)
			return null;
		else
			return state.toString();
	}

	/**
	 * Adds a message to the active process trace without logging.
	 *
	 * <p>
	 * Messages traced by a {@link #join(Object, Runnable) joined} task are prefixed
	 * with that task's join ID.
	 * </p>
	 *
	 * @param messageSupplier message supplier
	 */
	public static void trace(Supplier<String> messageSupplier) {
		final var message = messageSupplier.get();
		if (message == null)
			return;

		final var state = ACTIVE_PROCESS_STATE.get();
		if (state == null)
			return;

		final var traced = state.traced.incrementAndGet();
		if (traced > MAX_TRACED_MESSAGES) {
			// the marker is offered by whichever caller first crosses the budget, so it
			// appears exactly once however many threads are tracing into this process
			if (traced == MAX_TRACED_MESSAGES + 1)
				state.children.offer(new TracedMessage( //
						"... process trace truncated at " + MAX_TRACED_MESSAGES + " messages", //
						state.depth));
			return;
		}

		final var joinId = ACTIVE_JOIN_ID.get();
		state.children.offer(new TracedMessage( //
				(joinId == null) ? message : joinId + "> " + message, //
				state.depth));
	}

}
