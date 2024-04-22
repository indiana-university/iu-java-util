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
package iu.auth.basic;

import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuIterable;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.basic.IuBasicAuthCredentials;
import edu.iu.auth.basic.IuExpiredCredentialsException;

/**
 * Handle to a
 * {@link IuBasicAuthCredentials#registerClientCredentials(Iterable, String, TemporalAmount)
 * client credential authentication realm}.
 */
class ClientCredentialSource implements Iterable<BasicAuthCredentials> {

	private final String realm;
	private final Iterable<BasicAuthCredentials> clientCredentials;
	private final TemporalAmount expirationPolicy;

	/**
	 * Constructor.
	 * 
	 * @param realm             authentication realm
	 * @param clientCredentials client secrets
	 * @param expirationPolicy  maximum length of time to allow secrets to remain
	 *                          valid
	 */
	ClientCredentialSource(String realm, Iterable<? extends IuBasicAuthCredentials> clientCredentials,
			TemporalAmount expirationPolicy) {
		this.realm = realm;
		this.clientCredentials = IuIterable.map(clientCredentials, this::validate);
		this.expirationPolicy = Objects.requireNonNull(expirationPolicy, "Expiration policy is required");

		for (final var unit : expirationPolicy.getUnits())
			if (expirationPolicy.get(unit) < 0)
				throw new IllegalArgumentException("Expiration policy must be non-negative");
	}

	/**
	 * Validates externally controlled client credentials as well-formed.
	 * 
	 * @param clientCredentials client credentials to validate
	 * @return validated credentials
	 */
	BasicAuthCredentials validate(IuBasicAuthCredentials clientCredentials) {
		final var name = Objects.requireNonNull(clientCredentials.getName(), "Missing client ID");

		final var password = Objects.requireNonNull(clientCredentials.getPassword(), "Missing client secret");
		if (password.length() < 12) // ASVS4 2.1
			throw new IllegalArgumentException("Client secret must contain at least 12 characters");
		else
			for (final var c : password.toCharArray())
				if (c < 0x20 || c > 0x7c)
					// RFC 6749 Appendix A.2 client_secret Syntax
					throw new IllegalArgumentException("Client secret must contain only printable ASCII characters");

		var notBefore = clientCredentials.getNotBefore();
		if (notBefore == null)
			notBefore = Instant.EPOCH;

		var expires = clientCredentials.getExpires();
		if (expires == null)
			expires = notBefore.plus(expirationPolicy);

		return new BasicAuthCredentials(name, password, notBefore, expires);
	}

	/**
	 * Verifies that a principal is an instance of {@link BasicAuthCredentials} and
	 * matches a non-expired registered client credentials entry.
	 * 
	 * @param principalIdentity ID principal to verify
	 * @throws IuAuthenticationException If credentials are well-formed but invalid
	 */
	void verify(IuPrincipalIdentity principalIdentity) throws IuAuthenticationException {
		final var basic = (BasicAuthCredentials) principalIdentity;

		final var name = Objects.requireNonNull(basic.getName(), "missing client id");
		final var password = Objects.requireNonNull(basic.getName(), "missing client secret");
		final var notBefore = basic.getNotBefore();
		final var expires = basic.getExpires();
		final var now = Instant.now();

		BasicAuthCredentials expired = null;
		for (final var registered : IuIterable.filter(clientCredentials,
				p -> p.getName().equals(name) && p.getPassword().equals(password))) {
			final var checkNotBefore = registered.getNotBefore();
			if (now.isBefore(checkNotBefore) //
					|| (notBefore != null //
							&& !notBefore.equals(checkNotBefore)))
				continue;

			final var checkExpires = registered.getExpires();
			if (expires != null && !expires.equals(checkExpires))
				continue;
			if (!now.isBefore(checkExpires))
				expired = registered;
			else
				return;
		}

		if (expired != null)
			throw new IuExpiredCredentialsException(IuWebUtils.createChallenge("Basic", Map.of("realm", realm)));
		else
			throw new IuAuthenticationException(IuWebUtils.createChallenge("Basic", Map.of("realm", realm)));
	}

	@Override
	public Iterator<BasicAuthCredentials> iterator() {
		return clientCredentials.iterator();
	}

}
