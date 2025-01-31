package iu.web.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpServer;

import edu.iu.IuException;
import edu.iu.logging.IuLogContext;
import edu.iu.web.IuWebContext;

/**
 * {@link HttpServer} configuration wrapper.
 */
public final class IuHttpListener implements AutoCloseable {

	private static final Logger LOG = Logger.getLogger(IuHttpListener.class.getName());

	private final int stopDelay;

	private volatile HttpServer server;

	/**
	 * Constructor.
	 * 
	 * @param externalUri   external root {@link URI}
	 * @param localAddress  local address
	 * @param authenticator {@link Authenticator} to apply to all context
	 * @param iuWebContext  contexts to initialize
	 * @param threads       number of threads to allocate for handling requests
	 * @param backlog       {@link HttpServer#bind(InetSocketAddress, int) backlog}
	 * @param stopDelay     seconds to wait for all request to complete on close
	 * @return {@link IuHttpListener}
	 * @throws IOException If an error occurs binding to server socket
	 */
	public static IuHttpListener create(URI externalUri, InetSocketAddress localAddress, Authenticator authenticator,
			Iterable<IuWebContext> iuWebContext, int threads, int backlog, int stopDelay) throws IOException {
		final var server = HttpServer.create(localAddress, backlog);

		final Set<String> used = new HashSet<>();
		final Queue<IuWebContext> webContexts = new ArrayDeque<>();
		final var contextFilter = new ContextFilter(webContexts);
		var root = false;
		for (final var webContext : iuWebContext) {
			final var path = webContext.getPath();
			if (!path.startsWith("/"))
				throw new IllegalArgumentException("invalid context path " + path);
			if (!used.add(path))
				throw new IllegalArgumentException("duplicate context path " + path);

			final var httpContext = server.createContext(path);
			httpContext.setAuthenticator(authenticator);
			httpContext.getFilters().add(contextFilter);
			httpContext.setHandler(webContext.getHandler());
			if (!root && path.equals("/"))
				root = true;

			final var current = Thread.currentThread();
			final var restore = current.getContextClassLoader();

			try {
				current.setContextClassLoader(webContext.getLoader());
				IuLogContext.initializeContext(null, false, externalUri + path, webContext.getApplication(),
						webContext.getEnvironment(), webContext.getModule(), webContext.getRuntime(),
						webContext.getComponent());
			} finally {
				current.setContextClassLoader(restore);
			}

			webContexts.add(webContext);
		}

		if (!root) {
			final var rootContext = server.createContext("/");
			rootContext.setHandler(new NotFoundHandler());
		}

		final var threadGroup = new ThreadGroup("iu-java-web-server");
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

		final var listener = new IuHttpListener(server, stopDelay);
		LOG.fine(() -> "started " + listener);
		return listener;
	}

	private IuHttpListener(HttpServer server, int stopDelay) {
		this.server = server;
		this.stopDelay = stopDelay;
	}

	@Override
	public synchronized void close() throws Exception {
		final var server = this.server;
		if (server != null) {
			this.server = null;
			server.stop(stopDelay);
			LOG.fine(() -> "stopped " + this + "; " + server);
		}
	}

	@Override
	public String toString() {
		return "IuHttpListener [stopDelay=" + stopDelay + ", server=" + server + "]";
	}

}
