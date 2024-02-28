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
package iu.auth.oauth;

import java.io.Serializable;

import edu.iu.IuObject;
import edu.iu.auth.oauth.IuAuthorizationScope;

/**
 * {@link IuAuthorizationScope} implementation.
 */
public class AuthorizedScope implements IuAuthorizationScope, Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * Authorized scope.
	 */
	private final String scope;

	/**
	 * Authentication realm the scope is valid for.
	 */
	private final String realm;

	/**
	 * Constructor.
	 * 
	 * @param scope Authorized scope
	 * @param realm Authentication realm the scope is valid for
	 */
	AuthorizedScope(String scope, String realm) {
		this.scope = scope;
		this.realm = realm;
	}

	@Override
	public String getName() {
		return scope;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(realm, scope);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		AuthorizedScope other = (AuthorizedScope) obj;
		return IuObject.equals(realm, other.realm) //
				&& IuObject.equals(scope, other.scope);
	}

	@Override
	public String toString() {
		return "OAuth Scope " + scope + ", for realm " + realm;
	}

}
