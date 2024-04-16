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
package iu.auth.bundle;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.auth.session.IuSessionAttribute;
import edu.iu.auth.session.IuSessionHeader;
import edu.iu.auth.session.IuSessionToken;
import edu.iu.auth.spi.IuSessionSpi;

/**
 * Delegating SPI implementation.
 */
public class SessionSpiDelegate implements IuSessionSpi {

	private static final IuSessionSpi DELEGATE = Bootstrap.load(IuSessionSpi.class);

	/**
	 * Default constructor.
	 */
	public SessionSpiDelegate() {
	}

	@Override
	public String register(Set<String> realm, Subject provider) {
		return DELEGATE.register(realm, provider);
	}

	@Override
	public void register(URI issuer, URI jwksUri, Duration tokenTtl, Duration refreshInterval) {
		DELEGATE.register(issuer, jwksUri, tokenTtl, refreshInterval);
	}

	@Override
	public IuSessionToken create(IuSessionHeader header) {
		return DELEGATE.create(header);
	}

	@Override
	public IuSessionToken refresh(Subject subject, String refreshToken) {
		return DELEGATE.refresh(subject, refreshToken);
	}

	@Override
	public IuSessionToken authorize(String audience, String accessToken) {
		return DELEGATE.authorize(audience, accessToken);
	}

	@Override
	public <T> IuSessionAttribute<T> createAttribute(String name, String attributeName, T attributeValue) {
		return DELEGATE.createAttribute(name, attributeName, attributeValue);
	}

}
