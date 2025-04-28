/*
 * Copyright © 2025 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.web.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import edu.iu.IuForwardedHeader;
import edu.iu.IuWebUtils;
import jakarta.annotation.Resource;

/**
 * Performs proxy host-level allow filtering.
 * <p>
 * This filter sets a {@link #getRequestUri() request URI} downstream filters
 * can use for selecting an application resource.
 */
@Resource
public class UpstreamProxyFilter extends Filter {

	private static final Logger LOG = Logger.getLogger(UpstreamProxyFilter.class.getName());

	private static final ThreadLocal<ProxyRequest> REQUEST = new ThreadLocal<>();

	/**
	 * May be set to an extended HTTP header name to support an <a href=
	 * "https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For">X-Forwarded-For</a>
	 * request header to trace remote client address.
	 * <p>
	 * When non-empty, the header value associated with the configured header name
	 * MAY be used to designate the remote client IP address.
	 * </p>
	 */
	@Resource
	private String xForwardedForHeader = "";

	/**
	 * May be set to an extended HTTP header name to support an
	 * <a href="">X-Forwarded-Host</a> request header for overriding the value in
	 * the Host header when selecting context
	 */
	@Resource
	private String xForwardedHostHeader = "";

	/**
	 * Allow-list of upstream proxy IP addresses to allow with xForwardedForHeader
	 * or {@link IuForwardedHeader#getBy() Forwarded by} attribute.
	 */
	@Resource
	private Set<String> allowProxy = Set.of();

	/**
	 * True to support the Forwarded request header for overriding host name and
	 * remote IP address from an allowed proxy.
	 */
	@Resource
	private boolean expectForwarded;

	/**
	 * Constructor.
	 */
	UpstreamProxyFilter() {
	}

	/**
	 * Gets the active request.
	 * 
	 * @return {@link ProxyRequest}
	 */
	static URI getRequestUri() {
		return Objects.requireNonNull(REQUEST.get(), "not active").requestUri;
	}

	/**
	 * Gets the remote client IP address, and optional port.
	 * 
	 * @return {@link InetSocketAddress}
	 */
	static InetSocketAddress getRemoteAddress() {
		return Objects.requireNonNull(REQUEST.get(), "not active").remoteAddress;
	}

	@Override
	public String description() {
		return getClass().getName();
	}

	/**
	 * Determines if an upstream proxy is expected.
	 * <p>
	 * When not expected, proxy headers should be ignored and logged as
	 * {@link Level#INFO}.
	 * </p>
	 * 
	 * @return true if a proxy is expected; otherwise false
	 */
	boolean isProxyExpected() {
		return expectForwarded //
				|| !xForwardedForHeader.isEmpty() //
				|| !xForwardedHostHeader.isEmpty() //
		;
	}

	@Override
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		final var requestHeaders = exchange.getRequestHeaders();
		final var request = new ProxyRequest(exchange.getRequestURI(), exchange.getRemoteAddress());
		try {
			REQUEST.set(request);

			if (!xForwardedHostHeader.isEmpty())
				request.handleXForwardedHost(requestHeaders.get(xForwardedHostHeader));

			request.handleForwarded(requestHeaders.get("Forwarded"));

			if (!xForwardedForHeader.isEmpty())
				request.handleXForwardedFor(requestHeaders.get(xForwardedForHeader));

			chain.doFilter(exchange);

		} finally {
			REQUEST.remove();
		}
	}

}
