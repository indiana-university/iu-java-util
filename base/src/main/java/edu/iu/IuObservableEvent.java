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
package edu.iu;

import java.net.URI;
import java.time.Instant;

/**
 * Provides common metadata for observable events.
 */
public interface IuObservableEvent {

	/**
	 * Gets a unique identifier for the event.
	 * 
	 * @return unique identifier
	 */
	String getId();

	/**
	 * Gets the time the event occurred.
	 * 
	 * @return {@link Instant}
	 */
	Instant getTime();

	/**
	 * Gets the start time associated with the event, i.e., transaction begin time.
	 * 
	 * @return {@link Instant}
	 */
	Instant getStartTime();

	/**
	 * Gets a name of the context-independent type of the event.
	 * 
	 * @return event type
	 */
	String getType();

	/**
	 * Gets the URI associated with the event, i.e., HTTP request URI.
	 * 
	 * @return {@link URI}
	 */
	default URI getUri() {
		return null;
	}

	/**
	 * Gets the name of the event's context, i.e., application/environment code.
	 * 
	 * @return context name
	 */
	default String getContext() {
		return null;
	}

	/**
	 * Gets the name of the action associated with the event, within the
	 * application's context.
	 * 
	 * @return action name
	 */
	default String getAction() {
		return null;
	}

}
