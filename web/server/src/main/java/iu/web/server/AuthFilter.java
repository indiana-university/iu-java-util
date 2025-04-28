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
import java.security.Principal;
import java.util.Objects;

import javax.security.auth.Subject;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import edu.iu.web.IuWebAuthenticator;

class AuthFilter extends Filter {

	private static final ThreadLocal<Subject> AUTH_SUBJECT = new ThreadLocal<>();

	private final IuWebAuthenticator authenticator;

	AuthFilter(IuWebAuthenticator authenticator) {
		this.authenticator = authenticator;
	}

	/**
	 * Gets the authenticated {@link Principal} for the current request.
	 * 
	 * @return {@link Principal}
	 */
	static Subject getAuthenticatedSubject() {
		return Objects.requireNonNull(AUTH_SUBJECT.get(), "not active");
	}

	@Override
	public String description() {
		return "Authentication filter";
	}

	@Override
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		try {
			AUTH_SUBJECT.set(authenticator.authenticate(exchange));
			chain.doFilter(exchange);
		} finally {
			AUTH_SUBJECT.remove();
		}
	}

}
