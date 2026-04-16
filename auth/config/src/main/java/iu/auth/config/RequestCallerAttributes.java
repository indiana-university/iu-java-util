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
package iu.auth.config;

import java.net.URI;

import edu.iu.IuRequestAttributes;
import edu.iu.auth.oauth.IuCallerAttributes;

/**
 * {@link IuCallerAttributes} implementation backed by {@link IuRequestAttributes}.
 */
public class RequestCallerAttributes implements IuCallerAttributes {

	private final IuRequestAttributes requestAttributes;
	private final String authnPrincipal;
	private final String impersonatedPrincipal;

	/**
	 * Constructor.
	 * 
	 * @param requestAttributes     {@link IuRequestAttributes}
	 * @param authnPrincipal        authenticated principal name
	 * @param impersonatedPrincipal impersonated principal name
	 */
	public RequestCallerAttributes(IuRequestAttributes requestAttributes, String authnPrincipal,
			String impersonatedPrincipal) {
		this.requestAttributes = requestAttributes;
		this.authnPrincipal = authnPrincipal;
		this.impersonatedPrincipal = impersonatedPrincipal;
	}

	@Override
	public URI getRequestUri() {
		return requestAttributes.getRequestUri();
	}

	@Override
	public String getRemoteAddr() {
		return requestAttributes.getRemoteAddr();
	}

	@Override
	public String getUserAgent() {
		return requestAttributes.getUserAgent();
	}

	@Override
	public String getAuthnPrincipal() {
		return authnPrincipal;
	}

	@Override
	public String getImpersonatedPrincipal() {
		return impersonatedPrincipal;
	}

}
