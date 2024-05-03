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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.basic.IuClientCredentials;
import iu.auth.principal.PrincipalVerifier;

/**
 * Manages an externally sourced
 * {@link IuClientCredentials#register(Iterable, String, TemporalAmount) client
 * credential authentication realm}.
 */
final class ClientCredentialSource implements PrincipalVerifier<BasicAuthCredentials> {

	private final Logger LOG = Logger.getLogger(ClientCredentialSource.class.getName());

	private final String realm;
	private final Iterable<? extends IuClientCredentials> clientCredentials;
	private final TemporalAmount expirationPolicy;

	/**
	 * Constructor.
	 * 
	 * @param realm             authentication realm
	 * @param clientCredentials client secrets
	 * @param expirationPolicy  maximum length of time to allow secrets to remain
	 *                          valid
	 */
	ClientCredentialSource(String realm, Iterable<? extends IuClientCredentials> clientCredentials,
			TemporalAmount expirationPolicy) {
		this.realm = realm;
		this.clientCredentials = clientCredentials;
		this.expirationPolicy = Objects.requireNonNull(expirationPolicy, "Expiration policy is required");

		for (final var unit : expirationPolicy.getUnits())
			if (expirationPolicy.get(unit) < 0)
				throw new IllegalArgumentException("Expiration policy must be non-negative");
	}

	@Override
	public Class<BasicAuthCredentials> getType() {
		return BasicAuthCredentials.class;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public boolean isAuthoritative() {
		return true;
	}

	@Override
	public void verify(BasicAuthCredentials basic, String realm) throws IuAuthenticationException {
		final var name = Objects.requireNonNull(basic.getName(), "missing client id");
		final var password = Objects.requireNonNull(basic.getPassword(), "missing client secret");

		for (final var credentials : clientCredentials)
			try {
				validate(credentials);

				if (credentials.getId().equals(name) //
						&& credentials.getSecret().equals(password))
					return;

			} catch (Throwable e) {
				LOG.log(Level.CONFIG, "Invalid client credentials entry for realm " + realm, e);
			}

		throw new IuAuthenticationException(challenge());
	}

	/**
	 * Validates externally controlled client credentials as well-formed.
	 * 
	 * @param clientCredentials client credentials to validate
	 */
	void validate(IuClientCredentials clientCredentials) {
		final var clientId = Objects.requireNonNull(clientCredentials.getId(), "Missing client ID");
		if (clientId.length() < 1)
			throw new IllegalArgumentException("Client ID must not be empty");
		else
			for (final var c : clientId.toCharArray())
				if (c < 0x20 || c > 0x7e)
					// RFC 6749 Appendix A.2 client_id Syntax
					throw new IllegalArgumentException("Client ID must contain only printable ASCII characters");

		final var clientSecret = Objects.requireNonNull(clientCredentials.getSecret(), "Missing client secret");
		if (clientSecret.length() < 12) // ASVS4 2.1
			throw new IllegalArgumentException("Client secret must contain at least 12 characters");
		else
			for (final var c : clientSecret.toCharArray())
				if (c < 0x20 || c > 0x7e)
					// RFC 6749 Appendix A.2 client_secret Syntax
					throw new IllegalArgumentException("Client secret must contain only printable ASCII characters");

		final var now = Instant.now();

		var notBefore = clientCredentials.getNotBefore();
		if (notBefore == null)
			notBefore = Instant.EPOCH;

		if (now.isBefore(notBefore))
			throw new IllegalArgumentException("Client credentials are not valid until " + notBefore);

		final var maxExpires = notBefore.plus(expirationPolicy);

		var expires = clientCredentials.getExpires();
		if (expires == null //
				|| expires.isAfter(maxExpires))
			expires = maxExpires;

		if (!now.isBefore(expires))
			throw new IllegalArgumentException("Client credentials expired at " + expires);
	}

	/**
	 * Creates a WWW-Authenticate challenge string for this realm.
	 * 
	 * @return authentication challenge
	 */
	String challenge() {
		final Map<String, String> params = new LinkedHashMap<>();
		params.put("realm", realm);
		params.put("charset", "US-ASCII");
		return IuWebUtils.createChallenge("Basic", params);
	}

}
