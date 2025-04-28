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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;

import edu.iu.IuForwardedHeader;
import edu.iu.IuWebUtils;

/**
 * Tracks {@link #requestUri} and {@link #remoteAddress} for an active
 * {@link HttpExchange}.
 */
class ProxyRequest {

	private static final Logger LOG = Logger.getLogger(ProxyRequest.class.getName());

	private final Set<String> allowProxy;
	private final boolean proxyExpected;

	private URI requestUri;
	private InetSocketAddress remoteAddress;
	private boolean remoteAllowed = true;

	/**
	 * Constructor.
	 * 
	 * @param proxyExpected true if upstream proxy request handling is configured at
	 *                      this node; else false
	 * @param allowProxy    {@link Set} of allowed proxy client IP addresses
	 * @param requestURI    {@link URI}
	 * @param remoteAddress {@link InetSocketAddress}
	 */
	ProxyRequest(boolean proxyExpected, Set<String> allowProxy, URI requestURI, InetSocketAddress remoteAddress) {
		this.proxyExpected = proxyExpected;
		this.allowProxy = allowProxy;
		this.requestUri = Objects.requireNonNull(requestURI, "missing requestUri");
		this.remoteAddress = Objects.requireNonNull(remoteAddress, "missing remoteAddress");
	}

	/**
	 * Gets {@link #requestUri}
	 * 
	 * @return {@link #requestUri}
	 */
	URI requestUri() {
		return requestUri;
	}

	/**
	 * Gets {@link #remoteAddress}
	 * 
	 * @return {@link #remoteAddress}
	 */
	InetSocketAddress remoteAddress() {
		return remoteAddress;
	}

	/**
	 * Checks if the remote address is allowed as an upstream proxy.
	 * 
	 * @return true of the remote address is an allowed proxy; else false
	 */
	boolean isRemoteAllowed() {
		if (!remoteAllowed)
			return false;

		if (!allowProxy.contains(remoteAddress.getHostString())) {
			LOG.log(proxyExpected ? Level.INFO : Level.FINE,
					() -> "remote client " + remoteAddress + " not in allowed proxy set");
			remoteAllowed = false;
		}

		return remoteAllowed;
	}

	/**
	 * Sets the remote address from an {@link IuWebUtils#parseNodeIdentifier(String)
	 * unparsed node identifier}.
	 * 
	 * @param nodeId unparsed node identifier; ignored if null
	 */
	void setRemoteAddress(String nodeId) {
		if (nodeId == null)
			return;

		final var address = IuWebUtils.parseNodeIdentifier(nodeId);
		if (address != null)
			setRemoteAddress(address);

		else if (remoteAllowed) {
			LOG.log(proxyExpected ? Level.INFO : Level.FINE, () -> "unknown proxy not allowed");
			remoteAllowed = false;
		}
	}

	/**
	 * Sets the remote address if the prior setting {@link #isRemoteAllowed() is an
	 * allowed upstream proxy}.
	 * 
	 * @param address new remote address
	 */
	void setRemoteAddress(InetSocketAddress address) {
		if (address.equals(remoteAddress) //
				|| !isRemoteAllowed())
			return;

		LOG.info(() -> "replacing remote address " + remoteAddress + " with " + address);
		remoteAddress = address;
		remoteAllowed = allowProxy.contains(address.getHostString());
	}

	/**
	 * Handles the X-Forwarded-Host HTTP header.
	 * 
	 * @param xForwardedHostHeader Header name
	 * @param xForwardedHost       Header values
	 */
	void handleXForwardedHost(String xForwardedHostHeader, Iterable<String> xForwardedHost) {
		if (xForwardedHost != null //
				&& isRemoteAllowed()) {
			try {
				final Iterator<String> hostIterator = xForwardedHost.iterator();
				final var host = hostIterator.next();
				if (hostIterator.hasNext())
					throw new IllegalArgumentException(
							"additional " + xForwardedHostHeader + " value seen, ignoring all headers");

				final var requestUri = WebServerUtils.replaceAuthority(this.requestUri, host);
				if (!requestUri.equals(this.requestUri)) {
					LOG.info(() -> "replaced host authority in " + this.requestUri + " with " + requestUri);
					this.requestUri = requestUri;
				}
			} catch (Throwable e) {
				LOG.log(Level.WARNING, e, () -> "invalid input in Forwarded header");
			}
		}
	}

	/**
	 * Handles the X-Forwarded-For and X-Forwarded-Host HTTP headers.
	 * 
	 * @param xForwardedForHeader Header name
	 * @param xForwardedFor       Header values, in order
	 */
	void handleXForwardedFor(String xForwardedForHeader, Iterable<String> xForwardedFor) {
		if (xForwardedFor != null //
				&& isRemoteAllowed())
			try {
				final Deque<String> forwardedHeaderStack = new ArrayDeque<>();
				for (final var values : xForwardedFor)
					for (final var value : values.split(","))
						forwardedHeaderStack.push(value.trim());

				while (!forwardedHeaderStack.isEmpty()) {
					final var xfor = forwardedHeaderStack.pop();
					if (!IuWebUtils.isIPAddress(xfor))
						throw new IllegalArgumentException("invalid IP address in " + xForwardedForHeader);
					else
						setRemoteAddress(new InetSocketAddress(IuWebUtils.getInetAddress(xfor), 0));
				}
			} catch (Throwable e) {
				LOG.log(Level.WARNING, e, () -> "invalid input in Forwarded header");
			}
	}

	/**
	 * Handles the Forwarded HTTP header.
	 * 
	 * @param forwardedHeaders Header values, in order
	 */
	void handleForwarded(Iterable<String> forwardedHeaders) {
		try {
			if (forwardedHeaders != null)
				if (!proxyExpected)
					LOG.log(Level.INFO, () -> "Forwarded header seen but not expected");
				else {
					final Deque<IuForwardedHeader> forwardedHeaderStack = new ArrayDeque<>();
					if (forwardedHeaders != null)
						for (final var values : forwardedHeaders)
							for (final var value : values.split(","))
								forwardedHeaderStack.push(IuWebUtils.parseForwardedHeader(value.trim()));

					while (!forwardedHeaderStack.isEmpty()) {
						final var forwarded = forwardedHeaderStack.pop();

						// set before host, so must allow-list by address
						setRemoteAddress(forwarded.getBy());

						final var host = forwarded.getHost();
						if (host != null && //
								isRemoteAllowed()) {
							final var requestUri = WebServerUtils.replaceAuthority(this.requestUri, host);
							if (requestUri.equals(this.requestUri)) {
								LOG.info(() -> "replaced host authority in " + this.requestUri + " with " + requestUri);
								this.requestUri = requestUri;
							}
						}

						// set after host, for address might not be a proxy
						setRemoteAddress(forwarded.getFor());
					}
				}
		} catch (Throwable e) {
			LOG.log(Level.WARNING, e, () -> "invalid input in Forwarded header");
		}
	}

}
