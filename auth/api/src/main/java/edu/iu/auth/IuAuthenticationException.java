/*
 * Copyright © 2024 Indiana University
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
package edu.iu.auth;

/**
 * Thrown by an incoming request handler to represent an authentication failure.
 * 
 * <p>
 * <em>Should</em> be caught and handled as 302 FOUND or 401 UNAUTHORIZED by an
 * outbound web request boundary as appropriate to the authorization scenario
 * and user-agent context. <em>Should not</em> be handled by application-layer
 * business logic.<em>Should not</em> be thrown by components not directly
 * responsible for authentication.
 * </p>
 */
public class IuAuthenticationException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * Location header value.
	 */
	private final String location;

	/**
	 * Constructor.
	 * 
	 * @param challenge WWW-Authenticate header value for informing the remote
	 *                  client of the endpoint authentication requirements.
	 */
	public IuAuthenticationException(String challenge) {
		this(challenge, (String) null);
	}

	/**
	 * Constructor.
	 * 
	 * @param challenge WWW-Authenticate header value for informing the remote
	 *                  client of the endpoint authentication requirements.
	 * @param location  Location header value for redirecting the user-agent to next
	 *                  step in the authentication process, if appropriate for the
	 *                  context; may be null if the authorization scenario doesn't
	 *                  specify user-agent interaction.
	 */
	public IuAuthenticationException(String challenge, String location) {
		super(challenge);
		this.location = location;
	}

	/**
	 * Constructor.
	 * 
	 * @param challenge WWW-Authenticate header value for informing the remote
	 *                  client of the endpoint authentication requirements.
	 * @param cause     <em>Optional</em> exception or error describing the
	 *                  authentication failure.
	 */
	public IuAuthenticationException(String challenge, Throwable cause) {
		this(challenge, (String) null, cause);
	}

	/**
	 * Constructor.
	 * 
	 * @param challenge WWW-Authenticate header value for informing the remote
	 *                  client of the endpoint authentication requirements.
	 * @param location  Location header value for redirecting the user-agent to next
	 *                  step in the authentication process, if appropriate for the
	 *                  user's context; may be null if the authorization scenario
	 *                  doesn't specify user-agent interaction.
	 * @param cause     <em>Optional</em> exception or error describing the
	 *                  authentication failure.
	 */
	public IuAuthenticationException(String challenge, String location, Throwable cause) {
		super(challenge, cause);
		this.location = location;
	}

	/**
	 * Gets the <strong>WWW-Authenticate</strong> header value to report to the user
	 * agent authentication failure.
	 * 
	 * @return <strong>WWW-Authenticate</strong> header value
	 */
	@Override
	public String getMessage() {
		return super.getMessage();
	}

	/**
	 * Gets the Location header value for redirecting the user-agent to next step in
	 * the authentication process, if appropriate for the context; may be null if
	 * the authorization scenario doesn't specify user-agent interaction.
	 * 
	 * @return location
	 */
	public String getLocation() {
		return location;
	}

}
