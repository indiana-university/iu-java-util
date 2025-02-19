package iu.web.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * HTTP server bootstrap.
 */
public final class IuHttpServer {

	private IuHttpServer() {
	}

	/**
	 * Bootstraps the HTTP server.
	 * 
	 * @param a ignored
	 * @throws IOException if the server cannot be created due to an I/O or network
	 *                     permissions error
	 */
	public static void main(String... a) throws IOException {
		Logger.getLogger(IuHttpServer.class.getName()).info("Hello. OK I start server now.");

		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8780), 0);
		server.createContext("/", new HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				exchange.getResponseHeaders().add("content-type", "text/plain");
				exchange.sendResponseHeaders(200, 0);
				try (final var body = exchange.getResponseBody()) {
					body.write(("Oh hi. " + exchange.getRequestURI() + "\n").getBytes());
				}
			}
		});
		server.setExecutor(null); // creates a default executor
		server.start();
	}

}
