/*
 * Copyright © 2026 Indiana University
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
package iu.client;

import java.net.URI;
import java.time.Instant;

import edu.iu.IdGenerator;
import edu.iu.IuClassLoaderContext;
import edu.iu.IuObservableEvent;

/**
 * Observable HTTP client event.
 */
public class IuHttpClientEvent implements IuObservableEvent {

	private final String id;
	private final Instant startTime;
	private final URI uri;
	private final String context;
	private final Instant responseTime;
	private final int statusCode;

	/**
	 * Constructor.
	 * 
	 * @param uri outbound request URI
	 */
	public IuHttpClientEvent(URI uri) {
		this.id = IdGenerator.generateId();
		this.startTime = Instant.now();
		this.uri = uri;
		this.context = IuClassLoaderContext.getContext().getName();
		this.responseTime = null;
		this.statusCode = 0;
	}

	private IuHttpClientEvent(IuHttpClientEvent startEvent, int statusCode) {
		this.id = startEvent.id;
		this.startTime = startEvent.startTime;
		this.uri = startEvent.uri;
		this.context = startEvent.context;
		this.responseTime = Instant.now();
		this.statusCode = statusCode;
	}

	/**
	 * Updates the event to indicate a response was received
	 * 
	 * @param statusCode HTTP status code
	 * @return updated event
	 */
	public IuHttpClientEvent received(int statusCode) {
		return new IuHttpClientEvent(this, statusCode);
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public Instant getTime() {
		return responseTime;
	}

	@Override
	public Instant getStartTime() {
		return startTime;
	}

	@Override
	public String getType() {
		return "http.client";
	}

	@Override
	public URI getUri() {
		return uri;
	}

	@Override
	public String getContext() {
		return context;
	}

	@Override
	public String getAction() {
		if (responseTime == null)
			return "send";

		if (statusCode == 0)
			return "incomplete";

		return (statusCode < 400 ? "receive " : "error ") + statusCode;
	}

	@Override
	public String toString() {
		return "IuHttpClientEvent [id=" + id + ", startTime=" + startTime + ", uri=" + uri + ", context=" + context
				+ ", responseTime=" + responseTime + ", statusCode=" + statusCode + "]";
	}

}
