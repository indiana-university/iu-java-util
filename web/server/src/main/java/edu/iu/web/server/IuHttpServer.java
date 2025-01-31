package edu.iu.web.server;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.logging.Logger;

import com.sun.net.httpserver.Authenticator;

import edu.iu.UnsafeRunnable;
import edu.iu.web.IuWebContext;
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
	private URI externalUri;
	@Resource
	private String host = "";
	@Resource
	private int port = 8780;
	@Resource
	private int threads = 100;
	@Resource
	private int backlog;
	@Resource
	private Duration stopDelay = Duration.ofSeconds(15L);
	@Resource
	private Authenticator authenticator;
	@Resource
	private Iterable<IuWebContext> iuWebContext = Collections.emptySet();

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
		final var address = host.isEmpty() ? new InetSocketAddress(port) : new InetSocketAddress(host, port);

		LOG.config(() -> "starting " + this);

		try (final var listener = IuHttpListener.create(externalUri, address, authenticator, iuWebContext, threads, backlog,
				(int) stopDelay.toSeconds())) {
			started = true;
			setOnline(true);
			LOG.fine(() -> "started " + this + "; " + listener);

			while (!closed)
				wait(500L);

			LOG.fine(() -> "stopping " + this + "; " + listener);
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
		return "IuHttpServer [host=" + host + ", port=" + port + ", backlog=" + backlog + ", stopDelay=" + stopDelay
				+ ", online=" + online + ", closed=" + closed + "]";
	}

}
