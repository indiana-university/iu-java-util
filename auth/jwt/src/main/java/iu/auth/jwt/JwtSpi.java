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

import java.lang.reflect.Type;
import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.security.auth.x500.X500Principal;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.jwt.IuWebToken;
import edu.iu.auth.jwt.IuWebToken.Builder;
import edu.iu.auth.jwt.IuWebTokenIssuer;
import edu.iu.auth.jwt.IuWebTokenIssuer.ClaimDefinition;
import edu.iu.auth.spi.IuJwtSpi;

/**
 * {@link IuJwtSpi} service provider implementation
 */
public class JwtSpi implements IuJwtSpi {

	private final Map<String, IuWebTokenIssuer> ISSUERS = new HashMap<>();

	@Override
	public IuWebToken parse(String jwt) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public synchronized void register(IuWebTokenIssuer issuer) {
		final var subject = Objects.requireNonNull(issuer.getIssuer());

		final var principal = subject.getPrincipals().stream() //
				.filter(p -> (p instanceof IuPrincipalIdentity) //
						|| (p instanceof IuApiCredentials) //
						|| (p instanceof X500Principal))
				.findFirst();
		final var name = getCommonName(principal);

		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public ClaimDefinition<String> stringOrUri(Consumer<IuWebToken> verifier) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public ClaimDefinition<Instant> numericDate(Consumer<IuWebToken> verifier) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public <T> ClaimDefinition<T> claimDefinition(Type type, ClaimDefinition<?> valueDefinition,
			BiConsumer<T, IuWebToken> verifier) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Builder issue(String issuer) {
		final var registeredIssuer = Objects.requireNonNull(ISSUERS.get(issuer), "issuer not registered " + issuer);
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

}
