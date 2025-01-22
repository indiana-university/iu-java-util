package iu.web.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

import org.apache.commons.text.StringEscapeUtils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import edu.iu.IuText;

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
	 * @param localAddress local address
	 * @param backlog      {@link HttpServer#bind(InetSocketAddress, int) backlog}
	 * @param stopDelay    seconds to wait for all request to complete on close
	 * @return {@link IuHttpListener}
	 * @throws IOException If an error occurs binding to server socket
	 */
	public static IuHttpListener create(InetSocketAddress localAddress, int backlog, int stopDelay) throws IOException {
		final var server = HttpServer.create(localAddress, backlog);
		server.createContext("/", new HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				exchange.getResponseHeaders().add("content-type", "text/html");
				exchange.sendResponseHeaders(200, 0);
				try (final var body = exchange.getResponseBody()) {
					body.write(IuText.utf8("<html><body><p>TODO: implement "
							+ StringEscapeUtils.escapeHtml4(exchange.getRequestURI().toString())
							+ "</p></body></html>"));
				}
			}
		});
		server.setExecutor(null); // creates a default executor
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
