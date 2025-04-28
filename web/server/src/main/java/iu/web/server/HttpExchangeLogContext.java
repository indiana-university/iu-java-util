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
