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
package iu.auth.jwt;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.AuthConfig;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import iu.auth.principal.PrincipalVerifier;

/**
 * Principal verifier for JWT.
 */
public final class JwtVerifier implements PrincipalVerifier<Jwt> {
	static {
		IuObject.assertNotOpen(JwtVerifier.class);
	}

	private final String jwtRealm;
	private final IuPrincipalIdentity audience;
	private final String realm;
	private final boolean authoritative;

	/**
	 * Constructor.
	 * 
	 * @param jwtRealm JWT authentication realm
	 * @param audience Audience principal
	 * @param realm    ID authentication realm
	 */
	public JwtVerifier(String jwtRealm, IuPrincipalIdentity audience, String realm) {
		this.jwtRealm = jwtRealm;
		this.audience = audience;
		this.realm = realm;
		this.authoritative = IuException.unchecked(() -> IuPrincipalIdentity.verify(audience, realm));
	}

	@Override
	public Class<Jwt> getType() {
		return Jwt.class;
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
	public String getRealm() {
		return jwtRealm;
	}

	@Override
	public boolean isAuthoritative() {
		return authoritative;
	}

	@Override
	public void verify(Jwt id, String realm) {
		IuObject.once(this.jwtRealm, realm, "invalid realm");
		IuObject.once(authoritative, IuException.unchecked(() -> IuPrincipalIdentity.verify(audience, this.realm)));

		IuObject.require(audience.getName(),
				aud -> IuIterable.filter(id.getAudience(), aud::equals).iterator().hasNext(),
				() -> "audience verification failed");

		final IuPrivateKeyPrincipal issuer = AuthConfig.get(id.getIssuer());
		final var strict = IuException
				.unchecked(() -> IuPrincipalIdentity.verify(issuer.getIdentity(), issuer.getRealm()));
		id.signature().verify(id.payload(), JwtSpi.getVerifyKey(issuer.getIdentity()));

		Objects.requireNonNull(id.getName(), "missing subject principal");

		// Allows 15 seconds leeway for clock drift
		final var earliest = Instant.now().minusSeconds(15L).truncatedTo(ChronoUnit.SECONDS);
		final var iat = IuObject.require(id.getIssuedAt(), earliest::isBefore);
		IuObject.require(id.getNotBefore(), earliest::isBefore);

		final var latest = earliest.plusSeconds(16L);
		final var exp = IuObject.require( //
				Objects.requireNonNull(id.getExpires(), "missing expiration time"), latest::isAfter);

		if (strict)
			IdGenerator.verifyId(id.getTokenId(), Duration.between(iat, exp).toMillis() + 15000L);
	}

	/**
	 * Gets the audience.
	 * 
	 * @return {@link IuPrincipalIdentity}
	 */
	IuPrincipalIdentity audience() {
		return audience;
	}

	/**
	 * Gets the audience principal verification realm.
	 * 
	 * @return audience principal verification realm
	 */
	String realm() {
		return realm;
	}
}
