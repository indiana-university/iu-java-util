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
package edu.iu.jdbc.monitor;

import java.net.URI;
import java.time.Instant;

import edu.iu.IdGenerator;
import edu.iu.IuClassLoaderContext;
import edu.iu.IuObservableEvent;

/**
 * {@link IuObservableEvent} implementation published by JDBC monitoring
 * proxies.
 *
 * <p>
 * Each monitored JDBC resource ({@link java.sql.Connection Connection},
 * {@link java.sql.Statement Statement}, {@link java.sql.PreparedStatement
 * PreparedStatement}, {@link java.sql.ResultSet ResultSet}) emits events via
 * {@link edu.iu.IuListener#observe(IuObservableEvent) IuListener.observe}.
 * </p>
 *
 * <p>
 * The lifecycle of a multi-part event sequence is modelled with two
 * {@link IuObservableEvent} instances that share the same {@link #getId() id}
 * and {@link #getStartTime() startTime}:
 * </p>
 * <ul>
 * <li>An <em>open</em> event (created by the constructor) with
 * {@link #getTime() time} {@code null}, signalling the start of the
 * sequence.</li>
 * <li>A <em>close</em> or <em>exec</em> event produced by {@link #end(String)}
 * with a non-{@code null} {@link #getTime() time}.</li>
 * </ul>
 */
public class IuJdbcObservableEvent implements IuObservableEvent {

	private final String id;
	private final Instant startTime;
	private final URI uri;
	private final String context;
	private final Instant closeTime;
	private final String type;
	private final String action;

	/**
	 * Constructor.
	 * 
	 * @param uri    outbound request URI
	 * @param type   event type
	 * @param action action
	 */
	public IuJdbcObservableEvent(URI uri, String type, String action) {
		this.id = IdGenerator.generateId();
		this.startTime = Instant.now();
		this.uri = uri;
		this.context = IuClassLoaderContext.getContext().getName();
		this.type = type;
		this.action = action;
		this.closeTime = null;
	}

	private IuJdbcObservableEvent(IuJdbcObservableEvent startEvent, String action) {
		this.id = startEvent.id;
		this.startTime = startEvent.startTime;
		this.uri = startEvent.uri;
		this.context = startEvent.context;
		this.type = startEvent.type;
		this.action = action;
		this.closeTime = Instant.now();
	}

	/**
	 * Creates a end-event associated with this open event.
	 * 
	 * @param action action
	 * @return end-event
	 */
	IuJdbcObservableEvent end(String action) {
		return new IuJdbcObservableEvent(this, action);
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public Instant getStartTime() {
		return startTime;
	}

	@Override
	public Instant getTime() {
		return closeTime;
	}

	@Override
	public String getType() {
		return type;
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
		return action;
	}

}
