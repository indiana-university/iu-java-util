package edu.iu;

import java.time.Duration;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Controls parallel processing over a bounded workload.
 * 
 * <p>
 * <img alt="UML Class Diagram" src=
 * "doc-files/edu.iu.IuParallelWorkloadController.svg" />
 * </p>
 * 
 * <p>
 * Each controller is {@link AutoCloseable closeable}, timed, and operates in a
 * dedicated task executor. It's expected for all tasks related to the workload
 * to complete within the established a given {@link Duration timeout interval},
 * after which active tasks will be interrupted and related resources will be
 * torn down.
 * </p>
 */
public class IuParallelWorkloadController implements AutoCloseable {

	private final Logger log = Logger.getLogger(IuParallelWorkloadController.class.getName());

	private final long now = System.currentTimeMillis();
	private final long expires;

	private final int size;
	private final ThreadLocal<Integer> usageCount = new ThreadLocal<>();
	private final Queue<Throwable> asyncErrors = new ConcurrentLinkedQueue<>();

	private volatile Handler logHandler;
	private volatile Level logLevel;
	private volatile long spawned;
	private volatile long pending;
	private volatile long completed;

	private volatile boolean closed;
	private Timer closeTimer;
	private ThreadGroup threadGroup;
	private ExecutorService exec;

	/**
	 * Creates a new workload controller.
	 * 
	 * @param name    descriptive name of the workload, for logging and error
	 *                reporting
	 * @param size    maximum number of parallel tasks to execute at the same time
	 * @param timeout total time to live for all workload-related tasks
	 */
	public IuParallelWorkloadController(String name, int size, Duration timeout) {
		if (size < 1)
			throw new IllegalArgumentException("size must be positive");
		this.size = size;

		if (timeout.isNegative() || timeout.isZero())
			throw new IllegalArgumentException("timeout must be positive");
		this.expires = now + timeout.toMillis();

		final Timer closeTimer = new Timer(true);
		closeTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					close();
					log.info(() -> "Execution terminated by parallel workload timeout");
				} catch (ExecutionException e) {
					log.log(Level.WARNING, e, () -> "Execution errors detected by parallel workload timeout");
				}
			}
		}, timeout.toMillis());
		this.closeTimer = closeTimer;

		threadGroup = new ThreadGroup(name);
		exec = new ThreadPoolExecutor(size, size * 3 / 2, timeout.toMillis(), TimeUnit.MILLISECONDS,
				new SynchronousQueue<>(), r -> {
					long threadNum;
					synchronized (IuParallelWorkloadController.this) {
						threadNum = spawned++;
					}

					Thread thread = new Thread(threadGroup, r, name + '/' + threadNum);
					log.config("spawned " + thread.getName());
					return thread;
				});
	}

	/**
	 * Listens for log messages published by the workflow controller.
	 * 
	 * <p>
	 * May be used to create unified custom log reports for tracing workload
	 * activity.
	 * </p>
	 * 
	 * @param level        minimum log level to listen for
	 * @param logListener  accepts a log event with a handle to the thread that
	 *                     generated it
	 * @param errorHandler accepts all errors thrown from {@code logListener}; note
	 *                     that workload errors are not handled here, only errors
	 *                     directly related to external log handling
	 */
	public synchronized void listen(Level level, BiConsumer<Thread, LogRecord> logListener,
			Consumer<Throwable> errorHandler) {
		if (logHandler != null)
			throw new IllegalStateException("log listener already initialized");

		class LogEvent {
			final Thread thread;
			final LogRecord record;

			LogEvent(Thread thread, LogRecord record) {
				this.thread = thread;
				this.record = record;
			}
		}

		final Queue<LogEvent> logEvents = new ConcurrentLinkedQueue<>();
		final Handler logHandler = new Handler() {
			@Override
			public void publish(LogRecord record) {
				logEvents.offer(new LogEvent(Thread.currentThread(), record));
				synchronized (this) {
					this.notifyAll();
				}
			}

			@Override
			public void flush() {
				while (!logEvents.isEmpty())
					try {
						LogEvent event = logEvents.poll();
						logListener.accept(event.thread, event.record);
					} catch (Throwable e) {
						errorHandler.accept(e);
					}
			}

			@Override
			public void close() throws SecurityException {
			}
		};
		logLevel = log.getLevel();
		if (logLevel == null || level.intValue() < logLevel.intValue())
			log.setLevel(level);
		logHandler.setLevel(level);

		exec.submit(() -> {
			while (!closed || !logEvents.isEmpty()) {
				if (!closed) {
					long remaining = remaining().toMillis();
					if (remaining > 0L)
						synchronized (logHandler) {
							try {
								logHandler.wait(remaining);
							} catch (InterruptedException e) {
								errorHandler.accept(e);
							}
						}
				}

				logHandler.flush();
			}
		});

		log.addHandler(logHandler);
		this.logHandler = logHandler;
	}

	/**
	 * Gets a count of threads spawned by this controller.
	 * 
	 * @return thread count
	 */
	public long spawned() {
		return spawned;
	}

	/**
	 * Gets a count of tasks submitted to this controller that have not yet
	 * completed.
	 * 
	 * @return pending task count
	 */
	public long pending() {
		return pending;
	}

	/**
	 * Gets a count of tasks completed by this controller.
	 * 
	 * @return pending task count
	 */
	public long completed() {
		return completed;
	}

	/**
	 * Gets the time elapsed since the controller was created.
	 * 
	 * @return time elapsed
	 */
	public Duration elapsed() {
		return Duration.ofMillis(System.currentTimeMillis() - now);
	}

	/**
	 * Gets the time remaining until the controller expires.
	 * 
	 * @return time remaining; may be zero or negative if already {@link #expired()}
	 */
	public Duration remaining() {
		return Duration.ofMillis(Math.max(0L, expires - System.currentTimeMillis()));
	}

	/**
	 * Determines whether or not the controller has expired.
	 * 
	 * <p>
	 * Once expired, all threads waiting on the controller will be notified and the
	 * controller will be closed. No more tasks may be submitted once the controller
	 * has expired; all remaining tasks will be interrupted and/or throw
	 * {@link IllegalStateException} with a message indicating a timeout.
	 * </p>
	 * 
	 * @return true if the controller is expired; else false
	 */
	public boolean expired() {
		return System.currentTimeMillis() > expires;
	}

	/**
	 * Wait until either all pending tasks have completed, or until the controller
	 * {@link #expired() expires}.
	 */
	public synchronized void await() {
		// deadlock prevention: don't include the current thread if controlling a task
		int min = Thread.currentThread().getThreadGroup() == threadGroup ? 1 : 0;

		while (!expired() && pending > min)
			try {
				this.wait(remaining().toMillis());
			} catch (InterruptedException e) {
				throw new IllegalStateException("interrupted", e);
			}

		if (pending > min)
			throw new IllegalStateException(
					describeTimeout() + ", " + pending + " tasks remain; " + Thread.currentThread());
	}

	/**
	 * Submits an asynchronous task for processing.
	 * 
	 * <p>
	 * This method will block until a thread is available for excuting the task, or
	 * until the controller has {@link #expired() expired}.
	 * </p>
	 * 
	 * @param descr description of the task
	 * @param task  task
	 * @return join thunk, may be invoked to wait until the task completes and/or
	 *         reports an error
	 */
	public synchronized Runnable async(String descr, UnsafeRunnable task) {
		if (closed || expired())
			throw new IllegalStateException("closed");

		while (pending >= size)
			try {
				this.wait(remaining().toMillis());
			} catch (InterruptedException e) {
				throw new IllegalStateException(descr + " interrupted", e);
			}

		final Throwable callerStackTrace = new Throwable("caller stack trace");
		class Status {
			ClassLoader context = Thread.currentThread().getContextClassLoader();
			Thread thread;
			boolean done;
			Throwable throwable;
		}

		Status status = new Status();
		pending++;

		exec.submit(new Runnable() {
			@Override
			public void run() {
				status.thread = Thread.currentThread();

				if (log.isLoggable(Level.FINE)) {
					Integer use = usageCount.get();
					if (use == null)
						usageCount.set(1);
					else {
						if (++use % 10_000 == 0)
							log.fine("used " + use + " times");
						usageCount.set(use);
					}
				}

				ClassLoader contextToRestore = status.thread.getContextClassLoader();
				long now = System.currentTimeMillis();
				try {
					status.thread.setContextClassLoader(status.context);

					log.finer(() -> "start " + descr);
					task.run();
					log.finer(() -> "end " + descr + " " + Duration.ofMillis(System.currentTimeMillis() - now));
				} catch (Throwable e) {
					e.addSuppressed(callerStackTrace);
					status.throwable = e;

					asyncErrors.offer(e);
					log.log(Level.INFO, e,
							() -> "error " + descr + " " + Duration.ofMillis(System.currentTimeMillis() - now));
				} finally {
					status.thread.setContextClassLoader(contextToRestore);
					status.done = true;

					synchronized (IuParallelWorkloadController.this) {
						pending--;
						completed++;
						IuParallelWorkloadController.this.notifyAll();
					}
				}
			}

			@Override
			public String toString() {
				return descr;
			}
		});

		return () -> {
			if (status.throwable instanceof RuntimeException)
				throw (RuntimeException) status.throwable;
			if (status.throwable instanceof Error)
				throw (Error) status.throwable;
			if (status.throwable != null)
				throw new IllegalStateException("unhandled exception in " + descr + "; " + status.thread,
						status.throwable);

			if (status.done)
				return;

			synchronized (this) {
				try {
					long remaining = remaining().toMillis();
					while (!status.done && remaining > 0)
						this.wait(remaining);

					if (!status.done) {
						StringBuilder sb = new StringBuilder(describeTimeout() + "\nCurrent stack:");
						for (StackTraceElement element : status.thread.getStackTrace())
							sb.append("\n    at ").append(element);
						throw new IllegalStateException(sb.toString());
					}

				} catch (InterruptedException e) {
					throw new IllegalStateException("interrupted", e);
				}
			}
		};
	}

	/**
	 * Shuts down all activity and releases resources related to the workload.
	 * 
	 * <p>
	 * This method is invoked from a timer when the controller {@link #expired()
	 * expires}. No more tasks can be submitted once the controller is closed.
	 * Repeat calls to this method have no effect.
	 * </p>
	 * 
	 * @throws ExecutionException with all unhandled errors thrown from workload
	 *                            tasks: the first unhandled error is the cause,
	 *                            then all others are suppressed.
	 */
	@Override
	public void close() throws ExecutionException {
		if (closed)
			return;

		log.fine("close requested");
		final ExecutorService exec;
		final ThreadGroup threadGroup;
		final Handler logHandler;
		final Timer closeTimer;
		synchronized (this) {
			exec = this.exec;
			threadGroup = this.threadGroup;
			logHandler = this.logHandler;
			closeTimer = this.closeTimer;

			this.exec = null;
			this.threadGroup = null;
			this.logHandler = null;
			this.closeTimer = null;
			this.closed = true;

			if (closeTimer != null)
				closeTimer.cancel();

			this.notifyAll();
		}

		log.fine("close reserved");
		Throwable throwing = null;
		try {
			if (exec != null) {
				log.fine("executor shutdown requested");
				exec.shutdown();
				try {
					// waits up to 5s for graceful shutdown after timeout
					if (!exec.awaitTermination(Math.max(2000L, remaining().toMillis()), TimeUnit.MILLISECONDS)
							&& pending > 0) {
						log.info("interrupting");
						threadGroup.interrupt();
						if (!exec.awaitTermination(3L, TimeUnit.SECONDS))
							log.info("terminated gracefully after interrupt");
						else
							log.warning("graceful termination failed, threads still active after interrupt");
					} else
						log.fine("terminated gracefully");
				} catch (InterruptedException e) {
					throw new IllegalStateException(e);
				}
				log.fine("executor shutdown complete");
			}
		} catch (RuntimeException | Error e) {
			throwing = e;
		} finally {
			log.fine("closed");
			if (logHandler != null)
				try {
					log.setLevel(null);
					log.removeHandler(logHandler);
					logHandler.flush();
				} catch (RuntimeException | Error e) {
					if (throwing != null)
						throwing.addSuppressed(e);
					else
						throw e;
				}
		}

		if (!asyncErrors.isEmpty()) {
			Throwable cause = asyncErrors.poll();
			ExecutionException err = new ExecutionException("unhandled async errors", cause);
			asyncErrors.forEach(err::addSuppressed);
			throw err;
		}
	}

	private String describeTimeout() {
		return "Timed out in " + Duration.ofMillis(expires - now) + " after completing " + completed + " tasks";
	}

}