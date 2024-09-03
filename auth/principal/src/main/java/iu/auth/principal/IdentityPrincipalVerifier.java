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
package iu.auth.principal;

import java.net.URI;

import edu.iu.IuObject;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;

/**
 * Verifies an explicit hard reference to a configured non-authorizative
 * {@link IuPrincipalIdentity}.
 * 
 * @param <T> Principal identity type; MUST be final and not an interface
 */
public final class IdentityPrincipalVerifier<T extends IuPrincipalIdentity> implements PrincipalVerifier<T> {

	private final Class<T> type;
	private final T id;

	/**
	 * Constructor.
	 * 
	 * @param type Principal identity type
	 * @param id   {@link IuPrincipalIdentity}
	 */
	IdentityPrincipalVerifier(Class<T> type, T id) {
		IuObject.requireFinalImpl(type);
		this.type = type;
		this.id = id;
	}

	@Override
	public String getAuthScheme() {
		return null;
	}

	@Override
	public URI getAuthenticationEndpoint() {
		return null;
	}

	@Override
	public Class<T> getType() {
		return type;
	}

	@Override
	public String getRealm() {
		return id.getName();
	}

	@Override
	public boolean isAuthoritative() {
		return false;
	}

	@Override
	public void verify(T id) throws IuAuthenticationException {
		if (id != this.id)
			throw new IllegalArgumentException();
	}

}