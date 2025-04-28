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
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpServer;

import edu.iu.IuException;
import jakarta.annotation.Resource;

/**
 * {@link HttpServer} configuration wrapper.
 */
@Resource
public class IuHttpListener implements AutoCloseable {

	private static final Logger LOG = Logger.getLogger(IuHttpListener.class.getName());

	@Resource
	private String host = "localhost";
	@Resource
	private int port = 8780;
	@Resource
	private int backlog;
	@Resource
	private Duration stopDelay = Duration.ofSeconds(30L);

	@Resource
	private UpstreamProxyFilter allowFilter;
	@Resource
	private ContextFilter contextFilter;
	@Resource
	private ThreadGuardFilter threadGuardFilter;
	@Resource
	private AuthFilter authFilter;

	private volatile HttpServer server;

	/**
	 * Starts an {@link HttpServer}.
	 */
	public synchronized void start() {
		final var localAddress = host.isEmpty() ? new InetSocketAddress(port) : new InetSocketAddress(host, port);

		// TODO: TLS
		if (server != null)
			throw new IllegalStateException("already started");
		else
			server = IuException.unchecked(() -> HttpServer.create(localAddress, backlog));

		final var httpContext = server.createContext("/");
		httpContext.getFilters().add(allowFilter);
		httpContext.getFilters().add(contextFilter);
		httpContext.getFilters().add(threadGuardFilter);
		httpContext.getFilters().add(authFilter);
		httpContext.setHandler(exchange -> ContextFilter.getActiveWebContext().getHandler().handle(exchange));

		final var threadGroup = new ThreadGroup("iu-java-web-server");
		final var threads = threadGuardFilter.threadLimit();
		final var exec = new ThreadPoolExecutor(Integer.max(threads / 10, 2), Integer.max(threads, 2), 0L,
				TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(Integer.max(threads / 2, 10)), new ThreadFactory() {
					volatile int num;

					@Override
					public Thread newThread(Runnable r) {
						int num;
						synchronized (this) {
							num = ++this.num;
						}
						return new Thread(threadGroup, r, "iu-java-web-server/" + num);
					}
				}) {
			@Override
			public void execute(Runnable command) {
				try {
					super.execute(command);
				} catch (Throwable e) {
					LOG.log(Level.SEVERE, e, () -> "executor submit failure " + command);
					throw IuException.unchecked(e);
				}
			}
		};

		server.setExecutor(exec);
		server.start();

		LOG.fine(() -> "started " + this);
	}

	@Override
	public synchronized void close() throws Exception {
		final var server = this.server;
		if (server != null) {
			this.server = null;
			server.stop((int) stopDelay.toSeconds());
			LOG.fine(() -> "stopped " + this + "; " + server);
		}
	}

	@Override
	public String toString() {
		return "IuHttpListener [stopDelay=" + stopDelay + ", server=" + server + "]";
	}

}
