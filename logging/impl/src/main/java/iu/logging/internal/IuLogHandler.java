package iu.logging.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import edu.iu.IuAsynchronousSubject;
import edu.iu.IuAsynchronousSubscription;
import edu.iu.IuRuntimeEnvironment;

/**
 * Efficiently handles writing log output for IU JEE application components.
 */
public class IuLogHandler extends Handler implements AutoCloseable {

	private static volatile int c = 0;

	private final Queue<IuLogEvent> logEvents = new ConcurrentLinkedQueue<>();
	private final IuAsynchronousSubject<IuLogEvent> subject = new IuAsynchronousSubject<>(logEvents::spliterator);

	private final int maxEvents;
	private final Duration eventTtl;
	private final Thread purge;
	private volatile boolean closed;

	/**
	 * Constructor
	 */
	public IuLogHandler() {
		final var c = ++IuLogHandler.c;

		maxEvents = env("iu.logging.maxEvents", 100000);
		eventTtl = env("iu.logging.eventTtl", Duration.ofDays(1L));
		purge = new Thread(this::purgeTask, "iu-java-logging-purge/" + c);
		purge.setDaemon(true);
		purge.start();

		final var consoleLevel = env("iu.logging.consoleLevel", Level.OFF);
		if (consoleLevel.intValue() < Level.OFF.intValue()) {
			final var console = new Thread(() -> this.consoleTask(consoleLevel), "iu-java-logging-console/" + c);
			console.setDaemon(true);
			console.start();
		}
	};

	/**
	 * Gets an integer value from the environment
	 * 
	 * @param name         environment property name
	 * @param defaultValue value to return if not supplied by the environment
	 * @return environment environment value parsed as an integer, if present; else
	 *         defaultValue
	 */
	static int env(String name, int defaultValue) {
		final var value = IuRuntimeEnvironment.envOptional(name);
		if (value == null)
			return defaultValue;
		else
			return Integer.parseInt(value);
	}

	/**
	 * Gets an {@link Duration} value from the environment
	 * 
	 * @param name         environment property name
	 * @param defaultValue value to return if not supplied by the environment
	 * @return environment environment value parsed as a {@link Duration}, if
	 *         present; else defaultValue
	 */
	static Duration env(String name, Duration defaultValue) {
		final var value = IuRuntimeEnvironment.envOptional(name);
		if (value == null)
			return defaultValue;
		else
			return Duration.parse(value);
	}

	/**
	 * Gets a {@link Level} value from the environment
	 * 
	 * @param name         environment property name
	 * @param defaultValue value to return if not supplied by the environment
	 * @return environment value parsed as a {@link Level}, if present; else
	 *         defaultValue
	 */
	static Level env(String name, Level defaultValue) {
		final var value = IuRuntimeEnvironment.envOptional(name);
		if (value == null)
			return defaultValue;
		else
			return Level.parse(value);
	}

	/**
	 * Subscribes to {@link IuLogEvent log events}.
	 * 
	 * @return {@link IuAsynchronousSubscription} of {@link IuLogEvent}
	 */
	public IuAsynchronousSubscription<IuLogEvent> subscribe() {
		return subject.subscribe();
	}

	/**
	 * Subscribes to log messages, and writes each to {@link System#out}.
	 * 
	 * @param level Maximum log level to print
	 */
	void consoleTask(Level level) {
		subject.subscribe().stream().filter(a -> a.getLevel().intValue() >= level.intValue())
				.forEach(event -> System.out.print(event.format()));
	}

	/**
	 * Repeatedly invokes {@link #purge} in a loop as long as this instance is open.
	 */
	void purgeTask() {
		while (!closed)
			synchronized (purge) {
				try {
					purge.wait(2000L);
					purge();
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
	}

	/**
	 * Purges log events from the queue.
	 * <p>
	 * Executes continuously in the background to prune older and excessive log
	 * events.
	 * </p>
	 */
	void purge() {
		final var now = Instant.now();

		{ // First purge from tail based on eventTtl
			final var notBefore = now.minus(eventTtl);
			final var iterator = logEvents.iterator();
			while (iterator.hasNext())
				if (iterator.next().getTimestamp().isBefore(notBefore))
					iterator.remove();
				else // assume chronological insert order
					break;
		}

		var toPurge = logEvents.size() - maxEvents;
		if (toPurge <= 0)
			return; // under size, nothing to do

		var level = Level.OFF;
		{ // determine the highest level that can be fully retained within the max size
			final Map<Level, Integer> countByLevel = new TreeMap<>(
					(a, b) -> -Integer.compare(a.intValue(), b.intValue()));
			logEvents.forEach(e -> countByLevel.compute(e.getLevel(), (a, count) -> count == null ? 1 : count + 1));

			var total = 0;
			for (final var countByLevelEntry : countByLevel.entrySet()) {
				total += countByLevelEntry.getValue();
				if (total <= maxEvents - toPurge)
					level = countByLevelEntry.getKey();
			}
		}

		final var iterator = logEvents.iterator();
		while (toPurge > 0)
			if (iterator.next().getLevel().intValue() < level.intValue()) {
				iterator.remove();
				toPurge--;
			}
	}

	@Override
	public void publish(LogRecord record) {
		if (closed)
			throw new IllegalStateException("closed");

		if (record.getMessage() == null)
			return;

		final var event = new IuLogEvent(record);
		ProcessLogger.trace(() -> event.getMessage());
		logEvents.offer(event);
		subject.accept(event);

		if (logEvents.size() >= maxEvents)
			synchronized (purge) {
				purge.notify();
			}
	}

	@Override
	public void flush() {
		purge();
	}

	@Override
	public synchronized void close() {
		if (!closed) {
			closed = true;
			subject.close();
			logEvents.clear();
		}
	}

}
