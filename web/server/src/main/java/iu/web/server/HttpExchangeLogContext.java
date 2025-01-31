package iu.web.server;

import java.util.logging.Level;

import com.sun.net.httpserver.HttpExchange;

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
		final var clientIp = exchange.getRequestHeaders().get("X-Cluster-Client-Ip");
		if (clientIp != null)
			return clientIp.getFirst();
		else
			return exchange.getRemoteAddress().getAddress().getHostAddress();
	}

	@Override
	public String getCalledUrl() {
		return exchange.getRequestURI().toString();
	}

	@Override
	public String getCallerPrincipalName() {
		final var authSubject = AuthFilter.getAuthenticatedSubject();
		if (authSubject == null)
			return null;

		final var firstPrincipal = authSubject.getPrincipals().stream().findFirst();
		if (firstPrincipal.isEmpty())
			return null;

		return firstPrincipal.get().getName();
	}

	@Override
	public String getImpersonatedPrincipalName() {
		final var authSubject = AuthFilter.getAuthenticatedSubject();
		if (authSubject == null)
			return null;

		final var principalIterator = authSubject.getPrincipals().iterator();
		if (!principalIterator.hasNext())
			return null;

		principalIterator.next(); // skip first principal
		if (!principalIterator.hasNext())
			return null;

		return principalIterator.next().getName();
	}

}
