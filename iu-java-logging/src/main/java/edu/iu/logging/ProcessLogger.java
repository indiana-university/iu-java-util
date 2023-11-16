
package edu.iu.logging;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProcessLogger {

	private static final Logger LOG = Logger.getLogger(ProcessLogger.class.getName());
	private static final ThreadLocal<Deque<ProcessLogger>> ACTIVE = new ThreadLocal<>();

	private static final String[] SIZE_INTERVALS = new String[] { "k", "M", "G", "T", "E", };

	public static void ntrace(String prefix, String suffix, long interval) {
		if (!LOG.isLoggable(Level.FINE))
			return;

		Deque<ProcessLogger> activeQueue = ACTIVE.get();
		if (activeQueue == null || activeQueue.isEmpty())
			return;
		ProcessLogger active = activeQueue.peek();

		Map<String, Long> ntraceCount = active.ntraceCount;
		if (ntraceCount == null)
			active.ntraceCount = ntraceCount = new LinkedHashMap<>();

		String k = prefix + suffix;
		Long nc = ntraceCount.get(k);
		if (nc == null)
			nc = 0L;
		ntraceCount.put(k, ++nc);
		if (nc % interval == 0)
			LOG.fine(prefix + nc + suffix);
	}

	public static class ProcessCounter {
		private long start;
		private long count;
		private long min;
		private long max;
		private long avg;
		private long last;
		private String longest;

		private ProcessCounter() {
		}

		public long getStart() {
			return start;
		}

		public long getCount() {
			return count;
		}

		public long getMin() {
			return min;
		}

		public long getMax() {
			return max;
		}

		public long getAvg() {
			return avg;
		}

		public long getLast() {
			return last;
		}

		public String getLongest() {
			return longest;
		}
	}

	public static void countBegin(String name) {
		Deque<ProcessLogger> activeQueue = ACTIVE.get();
		if (activeQueue == null || activeQueue.isEmpty())
			return;

		ProcessLogger active = activeQueue.peek();
		if (active == null)
			return;

		Map<String, ProcessCounter> counters = active.counters;
		if (counters == null)
			active.counters = counters = new LinkedHashMap<>();

		ProcessCounter pc = counters.get(name);
		if (pc == null)
			counters.put(name, pc = new ProcessCounter());

		pc.start = System.currentTimeMillis();
		pc.count++;
	}

	public static ProcessCounter countEnd(String name, String detail) {
		Deque<ProcessLogger> activeQueue = ACTIVE.get();
		if (activeQueue == null || activeQueue.isEmpty())
			return null;

		ProcessLogger active = activeQueue.peek();
		if (active == null)
			return null;

		Map<String, ProcessCounter> counters = active.counters;
		if (counters == null)
			return null;

		ProcessCounter pc = counters.get(name);
		if (pc == null)
			return null;

		long elapsed = System.currentTimeMillis() - pc.start;
		pc.last = elapsed;
		if (elapsed < pc.min || pc.min == 0L) {
			pc.min = elapsed;
		}
		if (elapsed > pc.max) {
			pc.max = elapsed;
			pc.longest = detail;
		}
		pc.avg = (pc.avg * (pc.count - 1) + elapsed) / pc.count;
		return pc;
	}

	private static String sizeToString(long bytes) {
		DecimalFormat df = new DecimalFormat("000");
		StringBuilder sb = new StringBuilder();
		int i = -1;
		int mod = 0;
		if (bytes < 0) {
			sb.append('-');
			bytes = Math.abs(bytes);
		}
		while (bytes / 1024 > 0 && i < SIZE_INTERVALS.length) {
			i++;
			mod = (int) (bytes % 1024);
			bytes /= 1024;
		}
		sb.append(bytes);
		if (mod > 0) {
			sb.append('.');
			sb.append(df.format(mod * 1000 / 1024));
		}
		if (i >= 0) {
			sb.append(SIZE_INTERVALS[i]);
		}
		return sb.toString();
	}

	private static String memoryToString(long free, long tot, long max) {
		StringBuilder sb = new StringBuilder();
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

	private static String intervalToString(long millis) {
		DecimalFormat df = new DecimalFormat("000");
		StringBuilder sb = new StringBuilder();
		sb.append('.');
		sb.append(df.format(millis % 1000));
		df.applyPattern("00");
		long sec = millis / 1000;
		sb.insert(0, df.format(sec % 60));
		long min = sec / 60;
		sb.insert(0, ':');
		sb.insert(0, df.format(min % 60));
		long hours = min / 60;
		if (hours > 0) {
			sb.insert(0, ':');
			sb.insert(0, df.format(hours % 24));
		}
		long days = hours / 24;
		if (days > 0) {
			sb.insert(0, " days, ");
			sb.insert(0, days);
		}
		return sb.toString();
	}

	private static String dateTimeRangeToString(long a, long b) {
		StringBuilder sb = new StringBuilder();

		Date da = new Date(a);
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		sb.append(df.format(da));

		Calendar ca = Calendar.getInstance();
		ca.setTime(da);
		Date db = new Date(b);
		Calendar cb = Calendar.getInstance();
		cb.setTime(db);
		if (ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
				&& ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR))
			df.applyPattern("HH:mm:ss.SSS");

		sb.append(" - ").append(df.format(db));

		return sb.toString();
	}

	public static <T> T follow(Level level, String msg, Callable<T> supplier) throws Throwable {
		String application = LoggingEnvironment.getApplication();
		if (application == null)
			return supplier.call();

		Deque<ProcessLogger> activeQueue = ACTIVE.get();

		ProcessLogger outerProcessLogger = activeQueue == null ? null : activeQueue.peek();
		if (outerProcessLogger != null && application.equals(outerProcessLogger.application)) {
			try {
				if (LOG.isLoggable(outerProcessLogger.level))
					LOG.log(outerProcessLogger.level, '>' + (msg == null ? "" : msg));
				outerProcessLogger.nest++;
				return supplier.call();
			} finally {
				outerProcessLogger.nest--;
				if (LOG.isLoggable(outerProcessLogger.level))
					LOG.log(outerProcessLogger.level, '<' + (msg == null ? "" : msg));
			}
		}

		Thread current = Thread.currentThread();
		ClassLoader loader = current.getContextClassLoader();

		if (activeQueue != null) {
			ProcessLogger processLogger = null;
			for (ProcessLogger p : activeQueue)
				if (p.application.equals(application)) {
					processLogger = p;
					break;
				}

			if (processLogger != null) {
				Consumer<String> outerNestedTrace = c -> {
					try {
						current.setContextClassLoader(outerProcessLogger.loader);
						if (LOG.isLoggable(outerProcessLogger.level))
							LOG.log(outerProcessLogger.level, c + (msg == null ? "" : msg));
					} finally {
						current.setContextClassLoader(loader);
					}
				};

				try {
					outerNestedTrace.accept('>' + application);

					activeQueue.push(processLogger);

					if (LOG.isLoggable(outerProcessLogger.level))
						LOG.log(outerProcessLogger.level,
								outerProcessLogger.application + '>' + (msg == null ? "" : msg));

					outerProcessLogger.nest++;
					processLogger.nest++;

					return supplier.call();

				} finally {
					processLogger.nest--;
					outerProcessLogger.nest--;

					if (LOG.isLoggable(outerProcessLogger.level))
						LOG.log(outerProcessLogger.level,
								outerProcessLogger.application + '<' + (msg == null ? "" : msg));

					ProcessLogger p = activeQueue.pop();
					assert p == processLogger : p + " " + processLogger;

					outerNestedTrace.accept('<' + application);
				}
			}
		}

		ProcessLogger processLogger = new ProcessLogger();
		processLogger.level = level;
		processLogger.application = application;
		processLogger.loader = current.getContextClassLoader();

		Consumer<String> outerNestedTrace = null;
		if (outerProcessLogger != null) {
			outerNestedTrace = c -> {
				try {
					current.setContextClassLoader(outerProcessLogger.loader);
					if (LOG.isLoggable(processLogger.level))
						LOG.log(processLogger.level, c + (msg == null ? "" : msg));
				} finally {
					current.setContextClassLoader(loader);
				}
			};
			outerNestedTrace.accept('>' + application);
			outerProcessLogger.nest++;
		}

		if (activeQueue == null)
			ACTIVE.set(activeQueue = new ArrayDeque<>());
		activeQueue.push(processLogger);
		try {
			processLogger.buffer.append("Process Trace (").append(application).append("): ");
			if (msg != null)
				processLogger.buffer.append(msg);

			StackTraceElement[] st = Thread.currentThread().getStackTrace();
			String pn = null;
			if (st != null)
				for (StackTraceElement ste : st) {
					String cn = ste.getClassName();
					boolean internal = cn.startsWith("edu.iu.spi") || cn.startsWith("edu.iu.logging")
							|| cn.startsWith("iu.") || cn.startsWith("java.") || cn.startsWith("sun.")
							|| cn.startsWith("com.sun.") || cn.startsWith("jdk.");

					if (!internal)
						processLogger.buffer.append("\n  at ").append(ste);

					if (pn == null)
						if (internal)
							continue;
						else {
							int iod = cn.lastIndexOf('.');
							pn = iod == -1 ? "" : cn.substring(0, iod);
						}
					else if (!internal && !cn.startsWith(pn))
						break;
				}

			processLogger.buffer.append("\nInitial Memory Usage: ");
			processLogger.buffer
					.append(memoryToString(processLogger.startFree, processLogger.startTot, processLogger.startMax));

			if (LOG.isLoggable(processLogger.level))
				LOG.log(processLogger.level, "Processing Started\n" + processLogger.buffer.toString());

			if (outerProcessLogger != null && LOG.isLoggable(outerProcessLogger.level))
				LOG.log(outerProcessLogger.level, outerProcessLogger.application + (msg == null ? ">" : ">" + msg));

			return supplier.call();

		} finally {
			if (outerProcessLogger != null && LOG.isLoggable(outerProcessLogger.level))
				LOG.log(outerProcessLogger.level, outerProcessLogger.application + (msg == null ? "<" : "<" + msg));

			processLogger.elapse();

			processLogger.buffer.append('\n');
			String message = "Processing Complete";
			processLogger.buffer.append(message);
			for (int i = message.length(); i < 80; i++) {
				processLogger.buffer.append('.');
			}
			processLogger.buffer.append(' ');
			processLogger.buffer.append(intervalToString(processLogger.diffTime));
			processLogger.buffer.append(' ');
			processLogger.buffer.append(intervalToString(processLogger.lastTime - processLogger.startTime));
			processLogger.buffer.append(' ');
			processLogger.buffer.append(sizeToString(processLogger.lastFree));
			processLogger.buffer.append(' ');
			processLogger.buffer.append(sizeToString(processLogger.diffFree));

			String ntraceSummary = processLogger.ntraceSummary();
			if (ntraceSummary != null)
				processLogger.buffer.append("\nMonitors:").append(ntraceSummary);

			String countSummary = processLogger.countSummary();
			if (countSummary != null)
				processLogger.buffer.append("\nCounters:").append(countSummary);

			processLogger.buffer.append("\nElapsed Time: ");
			processLogger.buffer.append(intervalToString(processLogger.lastTime - processLogger.startTime));
			processLogger.buffer.append("\nMemory Usage: ");
			processLogger.buffer
					.append(memoryToString(processLogger.lastFree, processLogger.lastTot, processLogger.lastMax));
			processLogger.buffer.append("\nMemory Delta: ");
			processLogger.buffer.append(memoryToString(processLogger.lastFree - processLogger.startFree,
					processLogger.lastTot, processLogger.lastMax));
			processLogger.buffer.append(" - tot delta: ");
			processLogger.buffer.append(sizeToString(processLogger.lastTot - processLogger.startTot));

			if (LOG.isLoggable(processLogger.level))
				LOG.log(processLogger.level, processLogger.buffer.toString());

			ProcessLogger p = activeQueue.pop();
			assert p == processLogger : p + " " + processLogger;
			if (activeQueue.isEmpty())
				ACTIVE.remove();

			if (outerProcessLogger != null) {
				outerProcessLogger.nest--;
				outerNestedTrace.accept('<' + application);
			}

			if (LoggingEnvironment.isDevelopment() && processLogger.level.intValue() >= Level.FINE.intValue())
				System.out.println(intervalToString(processLogger.lastTime - processLogger.startTime) + ","
						+ dateTimeRangeToString(processLogger.startTime, processLogger.lastTime) + ","
						+ LoggingEnvironment.getNodeId() + "," + Thread.currentThread().getName());
		}
	}

	public static void trace(Supplier<String> messageSupplier) {
		Deque<ProcessLogger> psq = ACTIVE.get();
		ProcessLogger processLogger = psq == null ? null : psq.peek();
		if (processLogger == null)
			return;

		processLogger.elapse();
		processLogger.buffer.append('\n');

		StringBuilder message = new StringBuilder();
		for (int i = 0; i < processLogger.nest; i++)
			message.append(' ');

		if (messageSupplier != null) {
			String s = messageSupplier.get();
			if (s != null)
				message.append(s);
		}

		if (message.length() < 80)
			for (int i = message.length(); i < 80; i++)
				message.append('.');
		else
			message.setLength(80);

		for (int i = 0; i < message.length(); i++)
			if (message.charAt(i) < ' ')
				message.setCharAt(i, ' ');

		processLogger.buffer.append(message);
		processLogger.buffer.append(' ');
		processLogger.buffer.append(intervalToString(processLogger.diffTime));
		processLogger.buffer.append(' ');
		processLogger.buffer.append(intervalToString(processLogger.lastTime - processLogger.startTime));
		processLogger.buffer.append(' ');
		processLogger.buffer.append(sizeToString(processLogger.lastFree));
		processLogger.buffer.append(' ');
		processLogger.buffer.append(sizeToString(processLogger.diffFree));
	}

	public static Level getLevel() {
		ProcessLogger p = ACTIVE.get() == null ? null : ACTIVE.get().peek();
		if (p != null)
			return p.level;
		else
			return Level.FINEST;
	}

	public static void setLevel(Level level) {
		ProcessLogger p = ACTIVE.get() == null ? null : ACTIVE.get().peek();
		if (p != null && p.level.intValue() < level.intValue()) {
			if (LOG.isLoggable(level) && !LOG.isLoggable(p.level))
				LOG.log(level, () -> "Processing In Progress\nSet log level to " + level + "\n" + p.buffer.toString());
			p.level = level;
		}
	}

	public static String export() {
		Deque<ProcessLogger> psq = ACTIVE.get();
		ProcessLogger ps = psq == null ? null : psq.peek();
		if (ps == null)
			return null;
		else
			return ps.buffer.toString();
	}

	String application;
	ClassLoader loader;
	Level level = Level.INFO;
	int nest;
	long startTime;
	long startFree;
	long startTot;
	long startMax;
	long lastTime;
	long lastFree;
	long lastTot;
	long lastMax;
	long diffTime;
	long diffFree;
	long diffTot;
	private Map<String, Long> ntraceCount;
	private Map<String, ProcessCounter> counters;

	StringBuilder buffer = new StringBuilder();

	private ProcessLogger() {
		startTime = System.currentTimeMillis();
		startFree = Runtime.getRuntime().freeMemory();
		startTot = Runtime.getRuntime().totalMemory();
		startMax = Runtime.getRuntime().maxMemory();
		lastTime = startTime;
		lastFree = startFree;
		lastTot = startTot;
		lastMax = startMax;
	}

	private void elapse() {
		long nTime = System.currentTimeMillis();
		long nFree = Runtime.getRuntime().freeMemory();
		long nTot = Runtime.getRuntime().totalMemory();
		long nMax = Runtime.getRuntime().maxMemory();
		diffTime = nTime - lastTime;
		diffFree = nFree - lastFree;
		diffTot = nTot - lastTot;
		lastTime = nTime;
		lastFree = nFree;
		lastTot = nTot;
		lastMax = nMax;
	}

	private String ntraceSummary() {
		Map<String, Long> ntraceCount = this.ntraceCount;
		if (ntraceCount == null)
			return null;

		StringBuilder sb = new StringBuilder();
		this.ntraceCount = null;
		ntraceCount.forEach((k, v) -> sb.append("\n  ").append(k).append(": ").append(v));
		return sb.toString();
	}

	private String countSummary() {
		Map<String, ProcessCounter> counters = this.counters;
		if (counters == null)
			return null;

		StringBuilder sb = new StringBuilder();
		this.counters = null;
		counters.forEach((name, pc) -> {
			sb.append("\n  ");
			sb.append(name);
			sb.append(": ");
			sb.append(pc.count);
			sb.append(" (");
			sb.append(intervalToString(pc.min));
			sb.append("/");
			sb.append(intervalToString(pc.max));
			sb.append("/");
			sb.append(intervalToString(pc.avg));
			sb.append(")");
			if (pc.longest != null && !pc.longest.isEmpty()) {
				sb.append("\n    longest : ");
				sb.append(pc.longest);
			}
		});
		return sb.toString();
	}

}
