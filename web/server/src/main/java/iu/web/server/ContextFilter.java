package iu.web.server;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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

	/**
	 * Gets the active web context for the current request.
	 * 
	 * @return {@link IuWebContext}
	 */
	static IuWebContext getActiveWebContext() {
		return Objects.requireNonNull(CONTEXT.get(), "not active");
	}

	private final Iterable<IuWebContext> webContexts;

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

		LOG.log(level, error, () -> {
			StringBuilder sb = new StringBuilder("HTTP ");
			sb.append(EndpointUtil.describeStatus(status));
			if (aborted)
				sb.append(" (client aborted) ");
			else
				sb.append(' ');
			sb.append(req.getRequestURI());

			Set<String> headerNames = new HashSet<>();
			for (String headerName : resp.getHeaderNames())
				if (headerNames.add(headerName))
					for (String header : resp.getHeaders(headerName))
						sb.append('\n').append(headerName).append(": ").append(header);

			Throwable sentError = (Throwable) req.getAttribute("iu.endpoint.statusTrace");
			if (sentError != null) {
				if (_cause == null)
					cause = sentError;
				else {
					if (sentError.getMessage() != null && !sentError.getMessage().equals(_cause.getMessage()))
						sb.append("\n").append(sentError.getMessage());
					cause.addSuppressed(sentError);
				}
			}
			LOG.log(level, cause, sb::toString);
		});

		if (resp.isCommitted())
			try {
				resp.flushBuffer();
			} catch (Throwable e) {
				LOG.log(Level.INFO, e, () -> "Failed to flush buffer setting error status on committed response");
			}

	}

	/**
	 * Constructor.
	 * 
	 * @param webContexts {@link IuWebContext}
	 */
	ContextFilter(Iterable<IuWebContext> webContexts) {
		this.webContexts = webContexts;
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

	@Override
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		final var requestUri = exchange.getRequestURI();
		final var webContext = getWebContext(requestUri.getPath());

		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();

		try {
			current.setContextClassLoader(webContext.getLoader());
			CONTEXT.set(webContext);

			IuException.checked(IOException.class,
					() -> IuLogContext.follow(new HttpExchangeLogContext(exchange), requestUri.toString(), () -> {
						chain.doFilter(exchange);
						return null;
					}));
		} catch (Throwable e) {
			handleError(e, exchange);
		} finally {
			CONTEXT.remove();
			current.setContextClassLoader(restore);
		}
	}

}
