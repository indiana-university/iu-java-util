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
package iu.auth.oidc;

import edu.iu.IuObject;
import edu.iu.auth.oidc.IuOpenIdClaim;

/**
 * {@link IuOpenIdClaim} implementation.
 * 
 * @param <T> value type
 */
class OidcClaim<T> implements IuOpenIdClaim<T> {
	private static final long serialVersionUID = 1L;

	private final String name;
	private final String claimName;
	private final T claim;

	/**
	 * Constructor.
	 * 
	 * @param name      principal name
	 * @param claimName claim name
	 * @param claim     claim value
	 */
	OidcClaim(String name, String claimName, T claim) {
		this.name = name;
		this.claimName = claimName;
		this.claim = claim;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getClaimName() {
		return claimName;
	}

	@Override
	public T getClaim() {
		return claim;
	}

	@Override
	public String toString() {
		return "OIDC Claim of " + name + ": " + claimName + " = " + claim;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(claim, claimName, name);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		final var other = (OidcClaim<?>) obj;
		return IuObject.equals(claim, other.claim) && IuObject.equals(claimName, other.claimName)
				&& IuObject.equals(name, other.name);
	}

}
