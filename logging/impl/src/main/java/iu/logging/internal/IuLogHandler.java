/*
 * Copyright © 2025 Indiana University
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.iu.IuAsynchronousSubject;
import edu.iu.IuAsynchronousSubscription;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuRuntimeEnvironment;

/**
 * Efficiently handles writing log output for IU JEE application components.
 */
public class IuLogHandler extends Handler implements AutoCloseable {

	private static volatile int c = 0;

	/**
	 * Key type for use with {@link IuLogHandler#filePublishers}.
	 */
	static class FilePublisherKey {
		private final String endpoint;
		private final String application;
		private final String environment;

		/**
		 * Constructor.
		 * 
		 * @param endpoint    endpoint
		 * @param application application
		 * @param environment environment;
		 */
		FilePublisherKey(String endpoint, String application, String environment) {
			this.endpoint = endpoint;
			this.application = application;
			this.environment = environment;
		}

		@Override
		public int hashCode() {
			return IuObject.hashCode(endpoint, application, environment);
		}

		@Override
		public boolean equals(Object obj) {
			if (!IuObject.typeCheck(this, obj))
				return false;
			final var other = (FilePublisherKey) obj;
			return IuObject.equals(endpoint, other.endpoint) //
					&& IuObject.equals(application, other.application) //
					&& IuObject.equals(environment, other.environment);
		}
	}

	/**
	 * Resolves a path relative to a given root, with characters that could
	 * potentially corrupt file names ({@code :} and {@code /}) stripped out.
	 * 
	 * @param path root path
	 * @param name one or more path entries
	 * @return relative path
	 */
	static Path resolvePath(Path path, String... name) {
		for (final var n : name)
			if (n != null)
				path = path.resolve(n.replaceAll("[:/]", "_"));
		return path;
	}

	private static class LogFilePublishers {
		private final LogFilePublisher debug;
		private final LogFilePublisher info;
		private final LogFilePublisher error;
		private final Map<String, LogFilePublisher> trace;

		private LogFilePublishers(Path logPath, String endpoint, String application, String environment) {
			final Path path = resolvePath(logPath, endpoint, environment);
			IuException.unchecked(() -> Files.createDirectories(path));

			final var maxSize = Long.parseLong(Objects
					.requireNonNullElse(IuRuntimeEnvironment.envOptional("iu.logging.file.maxSize"), "10485760"));
			final var nLimit = Integer.parseInt(
					Objects.requireNonNullElse(IuRuntimeEnvironment.envOptional("iu.logging.file.nLimit"), "10"));

			debug = new LogFilePublisher(path.resolve(filename(application, "debug")), maxSize, nLimit);
			info = new LogFilePublisher(path.resolve(filename(application, "info")), maxSize, nLimit);
			error = new LogFilePublisher(path.resolve(filename(application, "error")), maxSize, nLimit);

			trace = Objects.requireNonNullElseGet(IuObject.convert(
					IuRuntimeEnvironment.envOptional("iu.logging.file.trace"),
					a -> Stream.of(a.split(",")).collect(Collectors.toUnmodifiableMap(n -> n,
							name -> new LogFilePublisher(path.resolve(filename(application, name)), maxSize, nLimit)))),
					Collections::emptyMap);
		}
	}

	private static String filename(String application, String type) {
		final var sb = new StringBuilder();
		if (application != null)
			sb.append(application).append("_");
		sb.append(type).append(".log");
		return sb.toString();
	}

	private final Queue<IuLogEvent> logEvents = new ConcurrentLinkedQueue<>();
	private final IuAsynchronousSubject<IuLogEvent> subject = new IuAsynchronousSubject<>(logEvents::spliterator);
	private final Map<FilePublisherKey, LogFilePublishers> filePublishers = new ConcurrentHashMap<>();

	private final int maxEvents;
	private final Duration eventTtl;
	private final Duration closeWait;
	private final Thread purge;
	private volatile boolean consoleTaskActive;
	private volatile boolean fileTaskActive;
	private volatile boolean closed;

	/**
	 * Constructor
	 */
	public IuLogHandler() {
		final var c = ++IuLogHandler.c;

		final var startWait = env("iu.logging.startWait", Duration.ofSeconds(5L), Duration::parse);
		maxEvents = env("iu.logging.maxEvents", 100000, Integer::parseInt);
		eventTtl = env("iu.logging.eventTtl", Duration.ofDays(1L), Duration::parse);
		closeWait = env("iu.logging.closeWait", Duration.ofSeconds(15L), Duration::parse);

		purge = new Thread(this::purgeTask, "iu-java-logging-purge/" + c);
		purge.setDaemon(true);
		purge.start();

		final var consoleLevel = env("iu.logging.consoleLevel", Level.OFF, Level::parse);
		if (consoleLevel.intValue() < Level.OFF.intValue()) {
			final var console = new Thread(() -> this.consoleTask(consoleLevel), "iu-java-logging-console/" + c);
			console.setDaemon(true);
			console.start();
			IuException.unchecked(() -> IuObject.waitFor(this, () -> consoleTaskActive, startWait));
		}

		final var logPath = env("iu.logging.file.path", null, Path::of);
		if (logPath != null) {
			final var file = new Thread(() -> this.fileTask(logPath), "iu-java-logging-file/" + c);
			file.setDaemon(true);
			file.start();
			IuException.unchecked(() -> IuObject.waitFor(this, () -> fileTaskActive, startWait));
		}
	};

	private static <T> T env(String name, T defaultValue, Function<String, T> convert) {
		final var value = IuRuntimeEnvironment.envOptional(name);
		if (value == null)
			return defaultValue;
		else
			return convert.apply(value);
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
		synchronized (this) {
			consoleTaskActive = true;
		}

		subject.subscribe().stream().filter(a -> a.getLevel().intValue() >= level.intValue())
				.forEach(event -> System.out.println(event.export()));

		synchronized (this) {
			consoleTaskActive = false;
			this.notifyAll();
		}
	}

	/**
	 * Subscribes to log messages, and writes each to log files.
	 * 
	 * @param logPath root filesystem for log file output
	 */
	void fileTask(Path logPath) {
		synchronized (this) {
			fileTaskActive = true;
		}

		final var sub = subject.subscribe();
		sub.stream().forEach(event -> {
			final var endpoint = event.getEndpoint();
			final var application = event.getApplication();
			final var environment = event.getEnvironment();
			final var key = new FilePublisherKey(endpoint, application, environment);

			final var publishers = filePublishers.computeIfAbsent(key,
					a -> new LogFilePublishers(logPath, endpoint, application, environment));

			final var level = event.getLevel();
			final var formatted = event.format();
			if (level.intValue() >= Level.WARNING.intValue())
				publishers.error.publish(formatted);
			if (level.intValue() >= Level.INFO.intValue())
				publishers.info.publish(formatted);
			publishers.debug.publish(formatted);

			publishers.trace.entrySet().stream().filter(e -> e.getKey().equals(event.getLoggerName()))
					.map(e -> e.getValue()).forEach(p -> p.publish(formatted));

			final var avail = sub.available();
			if (avail == 0)
				flushFiles();
		});
		flushFiles();

		synchronized (this) {
			fileTaskActive = false;
			this.notifyAll();
		}
	}

	/**
	 * Synchronously flushes all file publisher buffers and related log files.
	 */
	void flushFiles() {
		filePublishers.values().forEach(a -> IuException.unchecked(() -> {
			a.error.flush();
			a.info.flush();
			a.debug.flush();
			for (final var trace : a.trace.values())
				trace.flush();
		}));
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
		flushFiles();
	}

	@Override
	public synchronized void close() {
		if (!closed) {
			closed = true;
			subject.close();
			IuException.unchecked(() -> IuObject.waitFor(this, //
					() -> !(consoleTaskActive //
							|| fileTaskActive),
					closeWait));
			logEvents.clear();
		}
	}

	@Override
	public String toString() {
		return "IuLogHandler [logEvents=" + logEvents.size() + ", maxEvents=" + maxEvents + ", eventTtl=" + eventTtl
				+ ", closeWait=" + closeWait + ", consoleTaskActive=" + consoleTaskActive + ", fileTaskActive="
				+ fileTaskActive + ", purge=" + purge.getName() + ", closed=" + closed + "]";
	}

}
