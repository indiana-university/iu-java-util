package edu.iu.web.server;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.logging.Logger;

import edu.iu.UnsafeRunnable;
import iu.web.server.IuHttpListener;
import jakarta.annotation.Priority;
import jakarta.annotation.Resource;

/**
 * HTTP server bootstrap.
 */
@Resource
@Priority(5000) // Persistent
public final class IuHttpServer implements UnsafeRunnable, AutoCloseable {

	private static final Logger LOG = Logger.getLogger(IuHttpServer.class.getName());

	@Resource
	private String host = "";
	@Resource
	private int port = 8780;
	@Resource
	private int backlog;
	@Resource
	private Duration stopDelay = Duration.ofSeconds(15L);

	private volatile boolean closed;

	/**
	 * Default constructor.
	 */
	IuHttpServer() {
	}

	@Override
	public synchronized void run() throws Exception {
		final var address = host.isEmpty() ? new InetSocketAddress(port) : new InetSocketAddress(host, port);

		LOG.config(() -> "starting " + this);

		try (final var listener = IuHttpListener.create(address, backlog, (int) stopDelay.toSeconds())) {
			LOG.fine(() -> "started " + this + "; " + listener);

			while (!closed)
				wait(5000L);

			LOG.fine(() -> "stopping " + this + "; " + listener);
		}

		LOG.config(() -> "stopped " + this);
	}

	@Override
	public synchronized void close() throws Exception {
		closed = true;
		notifyAll();
	}

	@Override
	public String toString() {
		return "IuHttpServer [host=" + host + ", port=" + port + ", backlog=" + backlog + ", stopDelay=" + stopDelay
				+ ", closed=" + closed + "]";
	}

}
