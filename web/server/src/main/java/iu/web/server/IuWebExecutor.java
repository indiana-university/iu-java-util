package iu.web.server;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuException;

class IuWebExecutor extends ThreadPoolExecutor {

	private static final Logger LOG = Logger.getLogger(IuWebExecutor.class.getName());

	private static volatile int instanceCount;

	private static class Factory implements ThreadFactory {
		private final int serial = ++instanceCount;
		private volatile int num;

		private final ThreadGroup threadGroup = new ThreadGroup("iu-java-web-server");

		@Override
		public Thread newThread(Runnable r) {
			synchronized (this) {
				num = ++num;
			}
			return new Thread(threadGroup, r, "iu-java-web-server-" + serial + "/" + num);
		}
	}

	IuWebExecutor(int threads, Duration timeout) {
		super( //
				Integer.max(threads / 10, 2), // spawn 10% of max threads before queueing
				Integer.max(threads, 2), // limit total threads at 100%
				timeout.toNanos(), TimeUnit.NANOSECONDS, //
				new ArrayBlockingQueue<>( //
						Integer.max(threads / 2, 10) //
				), new Factory());
	}

	@Override
	public void execute(Runnable command) {
		try {
			super.execute(command);
		} catch (Throwable e) {
			LOG.log(Level.SEVERE, e, () -> "executor submit failure " + command);
			throw IuException.unchecked(e);
		}
	}
}
