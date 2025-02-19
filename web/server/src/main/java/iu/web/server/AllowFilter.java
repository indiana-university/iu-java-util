package iu.web.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;

import edu.iu.IuForwardedHeader;
import edu.iu.IuNotFoundException;
import edu.iu.IuWebUtils;
import edu.iu.web.IuWebContext;
import jakarta.annotation.Resource;

/**
 * Binds {@link IuWebContext} to the active request.
 */
@Resource
class AllowFilter extends BaseFilter {

	private static final Logger LOG = Logger.getLogger(AllowFilter.class.getName());

	private final Iterable<URI> allowedRootUris;
	private final boolean expectXForwardedHost;
	private final boolean expectForwardedHost;
	private final Iterable<String> allowProxy;
	private final Iterable<String> allowOrigin;

	/**
	 * Constructor.
	 * 
	 * @param allowedRootUris      one or more allowed root {@link URI}s
	 * @param expectXForwardedHost True to support the X-Forwarded-Host request
	 *                             header for overriding the value in the Host
	 *                             header when allow-listing. MUST be false unless
	 *                             the request is strictly expected to come from an
	 *                             upstream proxy that sets this header.
	 * @param expectForwardedHost  True to support the host attribute in the
	 *                             Forwarded request header for overriding the value
	 *                             in the Host header when allow-listing. MUST be
	 *                             false unless the request is strictly expected to
	 *                             come from an upstream proxy that sets this
	 *                             header.
	 * @param allowProxy           Allow-list of upstream proxy IP addresses for
	 *                             restricting access when expectForwardedHost is
	 *                             true. null to ignore the
	 *                             {@link IuForwardedHeader#getBy() by} attribute,
	 *                             empty list to deny all.
	 * @param allowOrigin          Allow-list of domains permitted to submit
	 *                             cross-origin fetch requests.
	 */
	AllowFilter(Iterable<URI> allowedRootUris, boolean expectXForwardedHost, boolean expectForwardedHost,
			Iterable<String> allowProxy, Iterable<String> allowOrigin) {
		this.allowedRootUris = allowedRootUris;
		this.expectXForwardedHost = expectXForwardedHost;
		this.expectForwardedHost = expectForwardedHost;
		this.allowProxy = allowProxy;
		this.allowOrigin = allowOrigin;
	}

	@Override
	public String description() {
		return getClass().getName();
	}

	/**
	 * Replaces the authority section in the request URI with external-facing host
	 * address of an upstream proxy.
	 * 
	 * @param originalUri         Original request URI
	 * @param externalHostAddress external host address; may be null to leave the
	 *                            request URI unmodified
	 * @return modified {@link URI}
	 */
	URI replaceAuthority(URI originalUri, InetSocketAddress externalHostAddress) {
		if (externalHostAddress == null)
			return originalUri;

		final var newUriBuilder = new StringBuilder(originalUri.toString());

		final var startOfAuthority = newUriBuilder.indexOf("//") + 2;
		final var endOfAuthority = newUriBuilder.indexOf("/", startOfAuthority);
		if (endOfAuthority > startOfAuthority)
			newUriBuilder.delete(startOfAuthority, endOfAuthority);

		final var port = externalHostAddress.getPort();
		if (port > 0) {
			newUriBuilder.insert(startOfAuthority, port);
			newUriBuilder.insert(startOfAuthority, ':');
		}

		newUriBuilder.insert(startOfAuthority, externalHostAddress.getHostName());

		final var requestUri = URI.create(newUriBuilder.toString());
		LOG.info(() -> "replaced host authority in " + originalUri + " with " + requestUri);
		return requestUri;
	}

	/**
	 * Determines whether or not a request URI
	 * 
	 * @param originalUri         Original request URI
	 * @param externalHostAddress external host address; may be null to leave the
	 *                            request URI unmodified
	 * @return true if the request URI matches the allow list
	 */
	boolean isAllowed(final URI originalUri, InetSocketAddress externalHostAddress) {
		final URI requestUri;
		if (externalHostAddress != null) {
			requestUri = replaceAuthority(originalUri, externalHostAddress);
		} else
			requestUri = originalUri;

		for (final var acceptUri : allowedRootUris)
			if (IuWebUtils.isRootOf(acceptUri, requestUri)) {
				LOG.fine(() -> "accepting " + requestUri + "; matched " + acceptUri);
				return true;
			}
		return false;
	}

	@Override
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		final boolean uriAllowed;

		final URI requestUri;
		if (expectXForwardedHost)
			try {
				final var originalUrl = exchange.getRequestURI();
				final var forwardedHost = exchange.getRequestHeaders().get("X-Forwarded-Host");
				if (forwardedHost != null) {
					requestUri = replaceAuthority(originalUrl, forwardedHost.getFirst());
					LOG.info(() -> "replaced host in " + originalUrl + " with value from x-forwarded-host header" + ": "
							+ requestUri);
				} else
					requestUri = originalUrl;

				var allow = false;
				for (final var acceptUri : allowedRootUris)
					if (IuWebUtils.isRootOf(acceptUri, requestUri)) {
						LOG.fine(() -> "Accepting " + requestUri + "; matched " + acceptUri);
						allow = true;
						break;
					}
				uriAllowed = allow;

			} catch (Throwable e) {
				handleError(null, null, new IuNotFoundException(e), exchange, null);
				return;
			}

		if (!uriAllowed) {
			LOG.info(() -> "rejecting " + requestUri + ", not in allow list " + allowedRootUris);
			handleError(null, null, new IuNotFoundException(), exchange, null);
			return;
		}

		chain.doFilter(exchange);
	}

}
