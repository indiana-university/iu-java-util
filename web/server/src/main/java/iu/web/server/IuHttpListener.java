package iu.web.server;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpServer;

import edu.iu.IuException;
import jakarta.annotation.Resource;

/**
 * {@link HttpServer} configuration wrapper.
 */
@Resource
public class IuHttpListener implements AutoCloseable {

	private static final Logger LOG = Logger.getLogger(IuHttpListener.class.getName());

	@Resource
	private String host = "localhost";
	@Resource
	private int port = 8780;
	@Resource
	private int backlog;
	@Resource
	private Duration stopDelay = Duration.ofSeconds(30L);

	@Resource
	private UpstreamProxyFilter allowFilter;
	@Resource
	private ContextFilter contextFilter;
	@Resource
	private ThreadGuardFilter threadGuardFilter;
	@Resource
	private AuthFilter authFilter;

	private volatile HttpServer server;

	/**
	 * Starts an {@link HttpServer}.
	 */
	public synchronized void start() {
		final var localAddress = host.isEmpty() ? new InetSocketAddress(port) : new InetSocketAddress(host, port);

		// TODO: TLS
		if (server != null)
			throw new IllegalStateException("already started");
		else
			server = IuException.unchecked(() -> HttpServer.create(localAddress, backlog));

		final var httpContext = server.createContext("/");
		httpContext.getFilters().add(allowFilter);
		httpContext.getFilters().add(contextFilter);
		httpContext.getFilters().add(threadGuardFilter);
		httpContext.getFilters().add(authFilter);
		httpContext.setHandler(exchange -> ContextFilter.getActiveWebContext().getHandler().handle(exchange));

		final var threadGroup = new ThreadGroup("iu-java-web-server");
		final var threads = threadGuardFilter.threadLimit();
		final var exec = new ThreadPoolExecutor(Integer.max(threads / 10, 2), Integer.max(threads, 2), 0L,
				TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(Integer.max(threads / 2, 10)), new ThreadFactory() {
					volatile int num;

					@Override
					public Thread newThread(Runnable r) {
						int num;
						synchronized (this) {
							num = ++this.num;
						}
						return new Thread(threadGroup, r, "iu-java-web-server/" + num);
					}
				}) {
			@Override
			public void execute(Runnable command) {
				try {
					super.execute(command);
				} catch (Throwable e) {
					LOG.log(Level.SEVERE, e, () -> "executor submit failure " + command);
					throw IuException.unchecked(e);
				}
			}
		};

		server.setExecutor(exec);
		server.start();

		LOG.fine(() -> "started " + this);
	}

	@Override
	public synchronized void close() throws Exception {
		final var server = this.server;
		if (server != null) {
			this.server = null;
			server.stop((int) stopDelay.toSeconds());
			LOG.fine(() -> "stopped " + this + "; " + server);
		}
	}

	@Override
	public String toString() {
		return "IuHttpListener [stopDelay=" + stopDelay + ", server=" + server + "]";
	}

}
