package iu.web.server;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import edu.iu.IuNotFoundException;

/**
 * Provides default behavior for the root context.
 */
class NotFoundHandler implements HttpHandler {

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		throw new IuNotFoundException();
	}

}
