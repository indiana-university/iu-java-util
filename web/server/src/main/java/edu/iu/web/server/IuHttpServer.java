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
package edu.iu.web.server;

import java.util.logging.Logger;

import edu.iu.UnsafeRunnable;
import iu.web.server.IuHttpListener;
import jakarta.annotation.Priority;
import jakarta.annotation.Resource;

/**
 * HTTP server bootstrap.
 */
@Resource
@Priority(-5000)
public final class IuHttpServer implements UnsafeRunnable, AutoCloseable {

	private static final Logger LOG = Logger.getLogger(IuHttpServer.class.getName());

	@Resource
	private IuHttpListener iuHttpListener;

	private volatile boolean started;
	private volatile boolean online;
	private volatile boolean closed;

	/**
	 * Default constructor.
	 */
	IuHttpServer() {
	}

	@Override
	public synchronized void run() throws Exception {
		LOG.config(() -> "starting " + this);

		try {
			iuHttpListener.start();

			started = true;
			setOnline(true);
			LOG.fine(() -> "started " + this);

			while (!closed)
				wait(500L);

			LOG.fine(() -> "stopping " + this);
		} finally {
			iuHttpListener.close();
		}

		LOG.config(() -> "stopped " + this);
	}

	/**
	 * Flags the server as online.
	 * 
	 * @param online true to flag the server as online; false to flag as offline
	 */
	public synchronized void setOnline(boolean online) {
		if (!started)
			throw new IllegalStateException("not started");
		if (closed)
			throw new IllegalStateException("closed");

		this.online = online;
		notifyAll();
	}

	/**
	 * Determines whether or not the server has been started and is flagged as
	 * online.
	 * 
	 * @return true if the server is online, else false
	 */
	public boolean isOnline() {
		return online;
	}

	@Override
	public synchronized void close() throws Exception {
		closed = true;
		online = false;
		notifyAll();
	}

	@Override
	public String toString() {
		return "IuHttpServer [iuHttpListener=" + iuHttpListener + ", online=" + online + ", closed=" + closed + "]";
	}

}
