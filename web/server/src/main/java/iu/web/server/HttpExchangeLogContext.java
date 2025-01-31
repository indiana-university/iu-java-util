package iu.web.server;

import java.security.Principal;
import java.util.logging.Level;

import com.sun.net.httpserver.HttpExchange;

import edu.iu.IuObject;
import edu.iu.logging.IuLogContext;

/**
 * Tracks log context {@link HttpExchange}.
 */
class HttpExchangeLogContext implements IuLogContext {

	private static volatile int requestCounter;

	private final String requestId;
	private final HttpExchange exchange;

	/**
	 * Constructor.
	 * 
	 * @param exchange {@link HttpExchange}
	 */
	HttpExchangeLogContext(HttpExchange exchange) {
		synchronized (getClass()) {
			requestId = "http-" + ++requestCounter;
		}
		this.exchange = exchange;
	}

	@Override
	public String getRequestId() {
		return "http-" + requestId;
	}

	@Override
	public Level getLevel() {
		return Level.INFO;
	}

	@Override
	public String getCallerIpAddress() {
		return exchange.getRemoteAddress().getAddress().getHostAddress();
	}

	@Override
	public String getCalledUrl() {
		return exchange.getRequestURI().toString();
	}

	@Override
	public String getCallerPrincipalName() {
		return exchange.getPrincipal().getUsername();
	}

	@Override
	public String getImpersonatedPrincipalName() {
		return IuObject.convert((Principal) exchange.getAttribute("iu.web.server.impersonatedPrincipal"),
				Principal::getName);
	}

}
