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

import java.net.URI;
import java.net.http.HttpRequest;
import java.security.Principal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.security.auth.Subject;

import edu.iu.IuException;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oidc.IuOpenIdClient;
import edu.iu.auth.oidc.IuOpenIdProvider;
import iu.auth.util.AccessTokenVerifier;
import iu.auth.util.HttpUtils;
import iu.auth.util.WellKnownKeySet;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

/**
 * {@link IuOpenIdProvider} implementation.
 */
public class OpenIdProvider implements IuOpenIdProvider {

	private final Logger LOG = Logger.getLogger(OpenIdProvider.class.getName());

	private final String issuer;
	private final IuOpenIdClient client;
	private final URI userinfoEndpoint;
	private final JsonObject config;
	private final AccessTokenVerifier idTokenVerifier;

	/**
	 * Constructor.
	 * 
	 * @param configUri provider configuration URI
	 * @param client    client configuration metadata
	 */
	public OpenIdProvider(URI configUri, IuOpenIdClient client) {
		this.config = HttpUtils.read(configUri).asJsonObject();
		this.client = client;

		LOG.info("OIDC Provider configuration:\n" + config.toString());

		this.issuer = config.getString("issuer");
		this.userinfoEndpoint = IuException.unchecked(() -> new URI(config.getString("userinfo_endpoint")));

		if (client != null)
			this.idTokenVerifier = new AccessTokenVerifier(issuer,
					new WellKnownKeySet(IuException.unchecked(() -> new URI(config.getString("jwks_uri"))),
							client::getTrustRefreshInterval));
		else
			this.idTokenVerifier = null;

	}

	@Override
	public IuAuthorizationClient createAuthorizationClient() {
		if (client == null)
			throw new IllegalStateException("Client not configured");

		return new OidcAuthorizationClient(config, client, idTokenVerifier);
	}

	@Override
	public String getIssuer() {
		return issuer;
	}

	@Override
	public Subject hydrate(String accessToken) throws IuAuthenticationException {
		final var userinfo = HttpUtils.read(HttpRequest.newBuilder(userinfoEndpoint) //
				.header("Authorization", "Bearer " + accessToken).build()).asJsonObject();
		final var principal = userinfo.getString("principal");
		final var sub = userinfo.getString("sub");

		final Set<String> seen = new HashSet<>();
		final Set<Principal> principals = new LinkedHashSet<>();
		final BiConsumer<String, Supplier<?>> claimConsumer = //
				(claimName, claimSupplier) -> {
					if (seen.add(claimName))
						principals.add(new OidcClaim<>(principal, claimName,
								Objects.requireNonNull(claimSupplier.get(), claimName)));
				};

		claimConsumer.accept("principal", () -> principal);
		claimConsumer.accept("sub", () -> sub);

		for (final var userinfoClaimEntry : userinfo.entrySet())
			claimConsumer.accept(userinfoClaimEntry.getKey(), () -> {
				final var claimJsonValue = userinfoClaimEntry.getValue();
				if (claimJsonValue instanceof JsonString)
					return ((JsonString) claimJsonValue).getString();
				else
					return claimJsonValue.toString();
			});

		return new Subject(true, principals, Set.of(), Set.of());
	}

}
