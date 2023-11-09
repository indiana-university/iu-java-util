package edu.iu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.opentest4j.AssertionFailedError;

import edu.iu.util.IdGenerator;

@SuppressWarnings("javadoc")
public class IuAsynchronousPipeTest {

	private static final Logger LOG;
	static {
		LOG = Logger.getLogger(IuAsynchronousPipeTest.class.getName());
		LOG.setUseParentHandlers(false);
		LOG.setLevel(Level.FINE);
	}

	// Committed values balance thorough verification with fast build times
	// This default configuration is set for high-latency; e.g. PSJOA CI
	// May be tuned to simulate timings for large parallel processing tasks
	private static final int N = 100;
	private static final int LOG_PER_N = 10;
	private static final int PARTIAL_SEND_SIZE = 25;
	private static final Duration SIMULATED_SOURCE_DELAY = Duration.ofMillis(25L);
	private static final Duration SIMULATED_SEND_DELAY = Duration.ofMillis(25L);
	private static final Duration SIMULATED_SEND_PER_ITEM = Duration.ofNanos(100_000L);
	private static final Duration TIME_OUT = Duration.ofMillis(2500L);
	private static final int POOL_SIZE = 16;

	// Largest verified scenario:
	// low-latency 100M sent via split collector within 15 minutes
//	private static final int N = 100_000_000;
//	private static final int LOG_PER_N = 25_000;
//	private static final int PARTIAL_SEND_SIZE = 50_000;
//	private static final Duration SIMULATED_SOURCE_DELAY = Duration.ofMillis(1L);
//	private static final Duration SIMULATED_SEND_DELAY = Duration.ofMillis(1L);
//	private static final Duration SIMULATED_SEND_PER_ITEM = Duration.ofNanos(1_000L);
//	private static final Duration TIME_OUT = Duration.ofMinutes(15L);
//	private static final int POOL_SIZE = 375;

	private IuParallelWorkloadController parallelController;
	private IuAsynchronousPipe<String> pipe;
	private Stream<String> stream;
	private List<String> controlList;
	private volatile int count;
	private PrintWriter logWriter;
	private Handler logHandler;

	private void log(Thread thread, LogRecord record) {
		StringBuilder sb = new StringBuilder();
		sb.append(parallelController.elapsed());
		sb.append(" ").append(thread.getName());
		sb.append(" ").append(record.getLevel());

		String className = record.getSourceClassName();
		if (className != null)
			sb.append(" ").append(className);

		String methodName = record.getSourceMethodName();
		if (methodName != null) {
			if (className == null)
				sb.append(" ");
			sb.append("#").append(record.getSourceMethodName());
		}

		sb.append(": ").append(record.getMessage());
		sb.append("\n");

		Throwable thrown = record.getThrown();
		if (thrown != null) {
			try (PrintWriter w = new PrintWriter(new Writer() {
				@Override
				public void write(char[] cbuf, int off, int len) throws IOException {
					for (int i = off; i < len; i++) {
						char c = cbuf[i];
						if (c != '\n' && sb.charAt(sb.length() - 1) == '\n')
							sb.append("  ");
						sb.append(c);
					}
				}

				@Override
				public void flush() throws IOException {
				}

				@Override
				public void close() throws IOException {
				}
			})) {
				thrown.printStackTrace(w);
			}
		}

		logWriter.write(sb.toString());
	}

	@BeforeEach
	public void setup(TestInfo testInfo) throws Throwable {
		Path logFile = Paths.get("target", "pipe.log");

		final PrintWriter logWriter = new PrintWriter(
				Files.newBufferedWriter(logFile, StandardOpenOption.APPEND, StandardOpenOption.CREATE));
		this.logWriter = logWriter;

		logHandler = new Handler() {
			@Override
			public void publish(LogRecord record) {
				log(Thread.currentThread(), record);
			}

			@Override
			public void flush() {
				logWriter.flush();
			}

			@Override
			public void close() throws SecurityException {
				logWriter.close();
			}
		};
		LOG.addHandler(logHandler);

		parallelController = new IuParallelWorkloadController(testInfo.getTestMethod().get().getName(), POOL_SIZE,
				TIME_OUT);
		parallelController.listen(Level.FINE, this::log, Throwable::printStackTrace);

		pipe = new IuAsynchronousPipe<>();
		stream = pipe.stream();
		controlList = new ArrayList<>(N);
		count = 0;

		LOG.config("setup complete");
	}

	@AfterEach
	public void tearDown() {
		LOG.config("begin teardown");
		try {
			Throwable pipeClose = null;

			pipe.close();
			try {
				pipe.pauseController(count, TIME_OUT);
			} catch (Exception e) {
				pipeClose = e;
			}

			LOG.config("cleared pipe");

			try {
				parallelController.close();
			} catch (ExecutionException e) {
				AssertionFailedError failure = new AssertionFailedError("parallel workload graceful close error", e);
				if (pipeClose != null)
					failure.addSuppressed(pipeClose);
				throw failure;
			}
			LOG.config("closed controller");

			if (pipeClose != null)
				throw new AssertionFailedError("last-ditch controller pause resulted in error", pipeClose);

		} finally {
			pipe = null;
			stream = null;
			controlList = null;
			count = 0;
			LOG.config("teardown complete\n");
			LOG.removeHandler(logHandler);
			logHandler.flush();
			logHandler.close();
			parallelController = null;
			logWriter = null;
			logHandler = null;
		}
	}

	@Test
	public void testSequential() throws Exception {
		simulateSequentialRun();
		simulateFullSend(collectAllSequential(stream));
	}

	@Test
	public void testSequentialAsyncReceiver() throws Exception {
		Runnable receiver = parallelController.async("receiver", () -> simulateFullSend(collectAllSequential(stream)));
		simulateSequentialRun();
		receiver.run();
	}

	@Test
	public void testSequentialAsyncController() throws Exception {
		Runnable controller = parallelController.async("controller", this::simulateSequentialRun);
		simulateFullSend(collectAllSequential(stream));
		controller.run();
	}

	@Test
	public void testSequentialAsyncBoth() throws Exception {
		Runnable receiver = parallelController.async("receiver", () -> simulateFullSend(collectAllSequential(stream)));
		Runnable controller = parallelController.async("controller", this::simulateSequentialRun);
		controller.run();
		receiver.run();
	}

	@Test
	public void testParallel() throws Exception {
		simulateParallelRun();
		simulatePartialSend(collectAllParallel(stream.parallel()));
		assertAllPartsSent();
	}

	@Test
	public void testParallelAsyncReceiver() throws Exception {
		Runnable receiver = parallelController.async("receiver",
				() -> simulatePartialSend(collectAllParallel(stream.parallel())));
		simulateParallelRun();
		receiver.run();
		assertAllPartsSent();
	}

	@Test
	public void testParallelAsyncSupplier() throws Exception {
		Runnable controller = parallelController.async("controller", this::simulateParallelRun);
		simulatePartialSend(collectAllParallel(stream.parallel()));
		controller.run();
		assertAllPartsSent();
	}

	@Test
	public void testParallelAsyncBoth() throws Exception {
		Runnable receiver = parallelController.async("receiver",
				() -> simulatePartialSend(collectAllParallel(stream.parallel())));
		Runnable controller = parallelController.async("controller", this::simulateParallelRun);
		controller.run();
		receiver.run();
		assertAllPartsSent();
	}

	@Test
	public void testParallelSplitCollector() throws Throwable {
		try {
			final Spliterator<String> pipeSplitter = stream.parallel().spliterator();
			parallelController.async("controller", this::simulateParallelRun);

			Queue<Runnable> pending = new ArrayDeque<>();
			while (!pipe.isClosed()) {
				while (pending.size() > POOL_SIZE)
					pending.poll().run();
				
				pipe.pauseReceiver(PARTIAL_SEND_SIZE, parallelController.remaining());
				Spliterator<String> split = pipeSplitter.trySplit();
				if (split != null)
					pending.offer(parallelController.async("partial send",
							() -> simulatePartialSend(collectAllParallel(StreamSupport.stream(split, true)))));
			}
			
			while (!pending.isEmpty())
				pending.poll().run();

			simulatePartialSend(collectAllParallel(StreamSupport.stream(pipeSplitter, true)));
			assertAllPartsSent();
		} finally {
			pipe.close();
			stream.close();
		}
	}

	// BEGIN private load simulator methods

	private void simulateRemoteWait(Duration max) {
		long remaining = parallelController.remaining().toMillis();
		if (remaining < 1)
			return;

		long maxMillis = Long.min(remaining, max.toMillis());
		long duration = ThreadLocalRandom.current().nextLong(maxMillis / 2, maxMillis);
		try {
			Thread.sleep(duration);
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	private void supplyFromExternalSource() {
		simulateRemoteWait(SIMULATED_SOURCE_DELAY);

		String next = IdGenerator.generateId();

		synchronized (this) {
			controlList.add(next);

			int c = ++count;
			if (c < N && c % LOG_PER_N == 0)
				LOG.fine(() -> "supplied " + c + " so far");
		}

		pipe.accept(next);
	}

	private void simulateSequentialRun() {
		try {
			for (int i = 0; !parallelController.expired() && i < N; i++)
				supplyFromExternalSource();

			assertFalse(parallelController.expired(), () -> "Timed out after completing " + count);

			LOG.info(() -> "supplied " + count + " sequentially");
		} finally {
			pipe.close();
		}
	}

	private void simulateParallelRun() {
		try {
			Queue<Runnable> pending = new ArrayDeque<>();
			for (int i = 0; i < N; i++) {
				while (pending.size() > POOL_SIZE)
					pending.poll().run();
				pending.offer(parallelController.async("controller", this::supplyFromExternalSource));
			}

			while (!pending.isEmpty())
				pending.poll().run();

			LOG.info("supplied " + count + " in parallel");
		} finally {
			pipe.close();
		}
	}

	private List<String> collectAllSequential(Stream<String> stream) {
		List<String> all = new ArrayList<>();
		stream.forEach(a -> {
			synchronized (all) {
				all.add(a);
			}
		});
		LOG.fine(() -> "collected " + all.size() + " sequentially");
		return all;
	}

	private void simulateFullSend(List<String> collectedSequentialItems) {
		int size = collectedSequentialItems.size();

		assertEquals(N, size);

		synchronized (this) {
			assertEquals(count, size);
			assertTrue(controlList.equals(collectedSequentialItems));
			controlList.clear();
		}

		simulateRemoteWait(Duration.ofMillis(SIMULATED_SEND_DELAY.toMillis()
				+ Duration.ofNanos(SIMULATED_SEND_PER_ITEM.toNanos() * size).toMillis()));

		LOG.info(() -> "sent " + size + " in full");
	}

	private Set<String> collectAllParallel(Stream<String> stream) {
		Set<String> all = new HashSet<>(N);
		stream.parallel().forEach(a -> {
			synchronized (all) {
				all.add(a);
			}
		});
		LOG.fine(() -> "collected " + all.size() + " in parallel");
		return all;
	}

	private void simulatePartialSend(Set<String> collectedParallelItems) {
		final int size = collectedParallelItems.size();
		if (size > 0) {
			synchronized (this) {
				final int beforeSize = controlList.size();
				assertTrue(controlList.removeAll(collectedParallelItems));
				assertEquals(beforeSize, size + controlList.size());
			}

			simulateRemoteWait(Duration.ofMillis(SIMULATED_SEND_DELAY.toMillis()
					+ Duration.ofNanos(SIMULATED_SEND_PER_ITEM.toNanos() * size).toMillis()));

			LOG.info(() -> "sent " + size + " in part");
		} else
			LOG.fine(() -> "skipped empty partial send");
	}

	private void assertAllPartsSent() {
		synchronized (controlList) {
			assertTrue(controlList.isEmpty());
		}

		assertEquals(N, count);
		assertTrue(pipe.isClosed());

		LOG.info(() -> "sent " + count + " in total of all parts");
	}

}
