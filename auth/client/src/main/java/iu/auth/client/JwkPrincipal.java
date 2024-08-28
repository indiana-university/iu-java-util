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
package iu.auth.client;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.crypt.WebKey;
import iu.auth.principal.IdentityPrincipalVerifier;

/**
 * {@link IuWebKey} implementation.
 * <p>
 * MAY be used with {@link IdentityPrincipalVerifier} to represent a JWKS URI
 * reference to an OIDC token issuer that publishes a well-known key with no
 * other certifying identity data.
 * </p>
 */
final class JwkPrincipal implements IuPrincipalIdentity {

	private final URI jwksUri;
	private final String keyId;

	/**
	 * Constructor.
	 * 
	 * @param jwksUri JWKS URI
	 * @param keyId   key ID
	 */
	JwkPrincipal(URI jwksUri, String keyId) {
		this.jwksUri = jwksUri;
		this.keyId = IuObject.once(jwksUri.getFragment(), keyId);
	}

	@Override
	public String getName() {
		if (jwksUri.getFragment() == null)
			return jwksUri + "#" + keyId;
		else
			return jwksUri.toString();
	}

	@Override
	public Subject getSubject() {
		final var jwk = IuIterable.filter(WebKey.readJwks(jwksUri), k -> keyId.equals(k.getKeyId())).iterator().next();
		if (!jwk.wellKnown().equals(jwk))
			throw new IllegalStateException("Public jwk must not include non-public key data");
		return new Subject(true, Set.of(this), Set.of(jwk), Set.of());
	}

	@Override
	public Instant getIssuedAt() {
		return null;
	}

	@Override
	public Instant getAuthTime() {
		return null;
	}

	@Override
	public Instant getExpires() {
		return null;
	}

}