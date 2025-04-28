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
import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import edu.iu.IuException;
import edu.iu.IuNotFoundException;
import edu.iu.IuWebUtils;
import edu.iu.logging.IuLogContext;
import edu.iu.web.IuWebContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * Binds {@link IuWebContext} to the active request.
 */
@Resource
public class ContextFilter extends Filter {

	private static final Logger LOG = Logger.getLogger(ContextFilter.class.getName());
	private static final ThreadLocal<IuWebContext> CONTEXT = new ThreadLocal<>();

	@Resource
	private Iterable<IuWebContext> webContexts;

	/**
	 * Constructor.
	 */
	ContextFilter() {
	}

	/**
	 * Validates context URIs and initializes logging for all class loaders.
	 */
	@PostConstruct
	void init() {
		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();

		final Set<URI> used = new HashSet<>();
		for (final var webContext : webContexts) {
			final var root = webContext.getRootUri();
			if (!used.add(root))
				throw new IllegalArgumentException("duplicate context root " + root);

			Objects.requireNonNull(root.getHost(), "missing host");

			final var path = Objects.requireNonNull(root.getPath(), "missing path");
			if (!path.startsWith("/"))
				throw new IllegalArgumentException("invalid context path " + path);

			try {
				current.setContextClassLoader(webContext.getLoader());
				IuLogContext.initializeContext(null, false, root.toString(), webContext.getApplication(),
						webContext.getEnvironment(), webContext.getModule(), webContext.getRuntime(),
						webContext.getComponent());
			} finally {
				current.setContextClassLoader(restore);
			}
		}
	}

	/**
	 * Gets the active web context for the current request.
	 * 
	 * @return {@link IuWebContext}
	 */
	static IuWebContext getActiveWebContext() {
		return Objects.requireNonNull(CONTEXT.get(), "not active");
	}

	@Override
	public String description() {
		return getClass().getName();
	}

	/**
	 * Gets all {@link IuWebContext}
	 * 
	 * @return All {@link IuWebContext}
	 */
	Iterable<IuWebContext> getWebContexts() {
		return webContexts;
	}

	/**
	 * Gets the web context relative to path component of a request URI.
	 * 
	 * @param requestUri incoming request {@link URI}
	 * @return most specific {@link IuWebContext}, null if not found
	 */
	IuWebContext getWebContext(URI requestUri) {
		IuWebContext match = null;
		
		for (final var webContext : webContexts) {
			final var contextRoot = webContext.getRootUri();
			if (IuWebUtils.isRootOf(contextRoot, requestUri) //
					&& (match == null || //
							IuWebUtils.isRootOf(match.getRootUri(), contextRoot)))
				match = webContext;
		}

		return match;
	}

	@Override
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		final var requestUri = UpstreamProxyFilter.getRequestUri();

		final var webContext = getWebContext(requestUri);
		if (webContext == null) {
			WebServerUtils.handleError(null, null, new IuNotFoundException(), exchange, null);
			return;
		}

		// TODO: handle CORS pre-flight request

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
