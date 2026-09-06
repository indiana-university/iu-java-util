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
package edu.iu.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;

/**
 * Thrown by {@link IuHttp} when an HTTP request cannot be completed
 * successfully.
 *
 * <p>
 * Extends {@link IOException} so that callers <em>may</em> handle every
 * interruption of an upstream HTTP interaction uniformly, whether the request
 * never reached the server or the server responded in error. Use
 * {@link #getResponse()} to tell the two apart: it is null when the failure
 * occurred before a response was received.
 * </p>
 */
public class HttpException extends IOException {
	private static final long serialVersionUID = 1L;

	private final transient HttpResponse<InputStream> response;

	/**
	 * Pre-response constructor.
	 *
	 * <p>
	 * Leaves {@link #getResponse()} null.
	 * </p>
	 *
	 * @param message message
	 */
	public HttpException(String message) {
		super(message);
		response = null;
	}

	/**
	 * Pre-response constructor.
	 *
	 * <p>
	 * Reports a failure that occurred before a response was received, for example a
	 * connection, TLS, or timeout error. Leaves {@link #getResponse()} null.
	 * </p>
	 *
	 * @param message message
	 * @param cause   cause
	 */
	public HttpException(String message, Throwable cause) {
		super(message, cause);
		response = null;
	}

	/**
	 * Constructor.
	 *
	 * @param response error response, status code &gt;= 400
	 * @param message  detailed error message
	 */
	public HttpException(HttpResponse<InputStream> response, String message) {
		super(message);
		this.response = response;
	}

	/**
	 * Gets the HTTP response that failed.
	 *
	 * <p>
	 * Note that the response is {@code transient}: it does not survive
	 * serialization, and it is null when the request failed before a response was
	 * received. Callers <em>must</em> tolerate a null return value.
	 * </p>
	 *
	 * @return {@link HttpResponse}; null if the failure occurred before a response
	 *         was received
	 */
	public HttpResponse<InputStream> getResponse() {
		return response;
	}

}
