package edu.iu.web.server;

import java.util.logging.Logger;

import edu.iu.UnsafeRunnable;
import iu.web.server.IuHttpListener;
import jakarta.annotation.Priority;
import jakarta.annotation.Resource;

/**
 * HTTP server bootstrap.
 */
@Resource
@Priority(-5000)
public final class IuHttpServer implements UnsafeRunnable, AutoCloseable {

	private static final Logger LOG = Logger.getLogger(IuHttpServer.class.getName());

	@Resource
	private IuHttpListener iuHttpListener;

	private volatile boolean started;
	private volatile boolean online;
	private volatile boolean closed;

	/**
	 * Default constructor.
	 */
	IuHttpServer() {
	}

	@Override
	public synchronized void run() throws Exception {
		LOG.config(() -> "starting " + this);

		try {
			iuHttpListener.start();

			started = true;
			setOnline(true);
			LOG.fine(() -> "started " + this);

			while (!closed)
				wait(500L);

			LOG.fine(() -> "stopping " + this);
		} finally {
			iuHttpListener.close();
		}

		LOG.config(() -> "stopped " + this);
	}

	/**
	 * Flags the server as online.
	 * 
	 * @param online true to flag the server as online; false to flag as offline
	 */
	public synchronized void setOnline(boolean online) {
		if (!started)
			throw new IllegalStateException("not started");
		if (closed)
			throw new IllegalStateException("closed");

		this.online = online;
		notifyAll();
	}

	/**
	 * Determines whether or not the server has been started and is flagged as
	 * online.
	 * 
	 * @return true if the server is online, else false
	 */
	public boolean isOnline() {
		return online;
	}

	@Override
	public synchronized void close() throws Exception {
		closed = true;
		online = false;
		notifyAll();
	}

	@Override
	public String toString() {
		return "IuHttpServer [iuHttpListener=" + iuHttpListener + ", online=" + online + ", closed=" + closed + "]";
	}

}
