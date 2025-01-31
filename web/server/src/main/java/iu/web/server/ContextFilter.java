package iu.web.server;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import edu.iu.IuAuthorizationFailedException;
import edu.iu.IuBadRequestException;
import edu.iu.IuException;
import edu.iu.IuNotFoundException;
import edu.iu.IuOutOfServiceException;
import edu.iu.IuWebUtils;
import edu.iu.logging.IuLogContext;
import edu.iu.web.IuWebContext;

/**
 * Binds {@link IuWebContext} to the active request.
 */
class ContextFilter extends Filter {

	private static final Logger LOG = Logger.getLogger(ContextFilter.class.getName());
	private static final ThreadLocal<IuWebContext> CONTEXT = new ThreadLocal<>();

	private final Iterable<IuWebContext> webContexts;
	private final Iterable<URI> acceptUris;

	/**
	 * Constructor.
	 * 
	 * @param webContexts {@link IuWebContext}
	 * @param acceptUris  one or more allowed root {@link URI}s
	 */
	ContextFilter(Iterable<IuWebContext> webContexts, Iterable<URI> acceptUris) {
		this.webContexts = webContexts;
		this.acceptUris = acceptUris;
	}

	/**
	 * Gets the active web context for the current request.
	 * 
	 * @return {@link IuWebContext}
	 */
	static IuWebContext getActiveWebContext() {
		return Objects.requireNonNull(CONTEXT.get(), "not active");
	}

	/**
	 * Describes the request attributes of a {@link HttpExchange}.
	 * 
	 * @param exchange {@link HttpExchange}
	 * @return description
	 */
	static String describeRequest(HttpExchange exchange) {
		StringBuilder sb = new StringBuilder("HTTP request");
		sb.append("\n  Protocol: ");
		sb.append(exchange.getProtocol());
		sb.append("\n  Method: ");
		sb.append(exchange.getRequestMethod());

		final var httpPrincipal = exchange.getPrincipal();
		if (httpPrincipal != null) {
			sb.append("\n  Principal Realm: ");
			sb.append(httpPrincipal.getRealm());
			sb.append("\n  Principal Username: ");
			sb.append(httpPrincipal.getUsername());
		}

		sb.append("\n  Remote Address: ");
		sb.append(exchange.getRemoteAddress());
		sb.append("\n  Local Address: ");
		sb.append(exchange.getLocalAddress());

		final var context = exchange.getHttpContext();
		sb.append("\n  Context Path: ");
		sb.append(context.getPath());
		sb.append("\n  Request URI: ");
		sb.append(exchange.getRequestURI());

		final var headers = exchange.getRequestHeaders();
		sb.append("\n  Headers:");
		for (final var headerEntry : headers.entrySet())
			for (final var headerValue : headerEntry.getValue()) {
				sb.append("\n    ");
				sb.append(headerEntry.getKey());
				sb.append(" = ");
				sb.append(headerValue);
			}

		return sb.toString();
	}

	/**
	 * Describes the response attributes of a {@link HttpExchange}.
	 * 
	 * @param exchange {@link HttpExchange}
	 * @return description
	 */
	static String describeResponse(HttpExchange exchange) {
		StringBuilder sb = new StringBuilder("HTTP ");
		sb.append(IuWebUtils.describeStatus(exchange.getResponseCode()));
		final var headers = exchange.getResponseHeaders();
		sb.append("\n  Headers:");
		for (final var headerEntry : headers.entrySet())
			for (final var headerValue : headerEntry.getValue()) {
				sb.append("\n    ");
				sb.append(headerEntry.getKey());
				sb.append(" = ");
				sb.append(headerValue);
			}
		return sb.toString();
	}

	void handleError(Throwable error, HttpExchange exchange) {
		final Level level;
		final int status;
		if (error instanceof IuBadRequestException) {
			status = 400;
			level = Level.WARNING;
		} else if (error instanceof IuAuthorizationFailedException) {
			status = 403;
			level = Level.INFO;
		} else if (error instanceof IuNotFoundException) {
			status = 400;
			level = Level.INFO;
		} else if (error instanceof IuOutOfServiceException) {
			status = 400;
			level = Level.CONFIG;
		} else {
			status = 500;
			level = Level.SEVERE;
		}

		final var errorState = new ErrorState();
		errorState.setStatus(status);
		// TODO: bind other attributes

		LOG.log(level, error, () -> describeResponse(exchange));

//		if (resp.isCommitted())
//			try {
//				resp.flushBuffer();
//			} catch (Throwable e) {
//				LOG.log(Level.INFO, e, () -> "Failed to flush buffer setting error status on committed response");
//			}
//
	}

	@Override
	public String description() {
		return getClass().getName();
	}

	/**
	 * Gets the web context relative to path component of a request URI.
	 * 
	 * @param path path component of an incoming request URI
	 * @return {@link IuWebContext}, null if not found
	 */
	IuWebContext getWebContext(String path) {
		IuWebContext match = null;
		for (final var webContext : webContexts) {
			final var contextPath = webContext.getPath();
			if (path.equals(contextPath))
				return webContext;

			if (path.startsWith(contextPath) //
					&& (match == null || //
							match.getPath().startsWith(contextPath)))
				match = webContext;
		}

		return match;
	}

	private URI replaceHost(URI url, String host, int port) {
		StringBuilder sb = new StringBuilder(url.toString());
		int ss = sb.indexOf("//") + 2;
		if (ss <= 1)
			throw new IllegalArgumentException(url.toString());
		int se = sb.indexOf("/", ss);
		if (se == -1)
			se = sb.length();
		if (se <= ss)
			throw new IllegalArgumentException(url.toString());
		sb.delete(ss, se);
		if (port > 0) {
			sb.insert(ss, port);
			sb.insert(ss, ':');
		}
		sb.insert(ss, host);
		return URI.create(sb.toString());
	}

	@Override
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		final var originalUrl = exchange.getRequestURI();
//		HttpServletRequest hreq = (HttpServletRequest) req;
//		String originalUrl = hreq.getRequestURL().toString();

		final var forwardedHost = exchange.getRequestHeaders().get("X-Forwarded-Host");
		final URI requestUri;
		if (forwardedHost != null) {
			requestUri = replaceHost(originalUrl, forwardedHost.getFirst(), -1);
			LOG.info(() -> "Replaced host in " + originalUrl + " with value from x-forwarded-host header" + ": "
					+ requestUri);
		} else
			requestUri = originalUrl;

		var uriAccepted = false;
		for (final var acceptUri : acceptUris)
			if (IuWebUtils.isRootOf(acceptUri, requestUri)) {
				LOG.fine(() -> "Accepting " + requestUri + "; matched " + acceptUri);
				uriAccepted = true;
				break;
			}

		if (!uriAccepted) {
			LOG.info(() -> "Rejecting " + requestUri + ", not in acceptable URL list " + acceptUris);
			handleError(new IuNotFoundException(), exchange);
			return;
		}

		final var webContext = getWebContext(requestUri.getPath());

		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();

		try {
			current.setContextClassLoader(webContext.getLoader());
			CONTEXT.set(webContext);

			// TODO: create response wrapper
			// TODO: override streams
			
			IuException.checked(IOException.class,
					() -> IuLogContext.follow(new HttpExchangeLogContext(exchange), requestUri.toString(), () -> {
						LOG.log(Level.FINE, () -> "Incoming Web Request " + describeRequest(exchange));
						chain.doFilter(exchange);
						return null;
					}));
			
			// TODO: finish response wrapper
			
		} catch (Throwable e) {
			handleError(e, exchange);
		} finally {
			CONTEXT.remove();
			current.setContextClassLoader(restore);
		}
	}

}
