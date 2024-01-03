package edu.iu;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("javadoc")
class Async {

	private static final ExecutorService EXEC;

	static {
		EXEC = new ThreadPoolExecutor(8, 8, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
	}

	private volatile boolean done;
	private volatile Throwable error;

	Async(UnsafeRunnable task) {
		EXEC.submit(() -> {
			try {
				task.run();
				done = true;
			} catch (Throwable e) {
				error = e;
			} finally {
				synchronized (this) {
					this.notifyAll();
				}
			}
		});
	}

	void await() throws Throwable {
		IuObject.waitFor(this, () -> done || error != null, Duration.ofSeconds(5L));
		if (error != null)
			throw error;
	}
}