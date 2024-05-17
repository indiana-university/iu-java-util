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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuObject;
import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.auth.oidc.IuAuthoritativeOpenIdClient;
import edu.iu.auth.oidc.IuAuthorizationClient;
import edu.iu.auth.oidc.IuOpenIdClient;
import edu.iu.auth.oidc.IuOpenIdProvider;
import edu.iu.auth.spi.IuOpenIdConnectSpi;
import iu.auth.IuAuthSpiFactory;
import iu.auth.principal.PrincipalVerifierRegistry;

/**
 * OpenID connect SPI implementation.
 */
public class OpenIdConnectSpi implements IuOpenIdConnectSpi {
	static {
		IuObject.assertNotOpen(OpenIdConnectSpi.class);
	}

	private static final Map<String, OpenIdProvider> PROVIDERS = new HashMap<>();

	/**
	 * Configures the client view of an OpenID provider.
	 * 
	 * <p>
	 * <em>May</em> be called exactly once per authentication realm.
	 * </p>
	 * 
	 * <p>
	 * When initialized using {@link IuAuthoritativeOpenIdClient}, an
	 * {@link IuAuthorizationClient} view of that configuration will be
	 * {@link IuAuthorizationClient#initialize(IuAuthorizationClient) initialized}
	 * for interacting with the OP as an authorization service. Use
	 * {@link IuAuthorizationSession#create(String, java.net.URI)} with a protected
	 * resource URI to use authorization code flow, or use
	 * {@link #clientCredentials()} for a grant based on the
	 * {@link IuAuthoritativeOpenIdClient#getCredentials() OIDC client's
	 * credentials}.
	 * </p>
	 * 
	 * @param client client configuration metadata
	 * @return Client view of the OpenID provider
	 */
	static IuOpenIdProvider from(IuOpenIdClient client) {
		return IuAuthSpiFactory.get(IuOpenIdConnectSpi.class).getOpenIdProvider(client);
	}

	/**
	 * 
	 * Gets the OpenID provider registered for an authentication realm.
	 * 
	 * @param realm authentication realm
	 * @return {@link OpenIdProvider}
	 */
	static OpenIdProvider getProvider(String realm) {
		return Objects.requireNonNull(PROVIDERS.get(realm), "Invalid OIDC realm");
	}

	/**
	 * Default constructor.
	 */
	public OpenIdConnectSpi() {
	}

	@Override
	public synchronized IuOpenIdProvider getOpenIdProvider(IuOpenIdClient client) {
		final var realm = Objects.requireNonNull(client.getRealm(), "Missing realm");

		if (PROVIDERS.containsKey(realm))
			throw new IllegalArgumentException("OpenID Provider already configured for " + realm);

		PrincipalVerifierRegistry
				.registerVerifier(new OidcPrincipalVerifier(client instanceof IuAuthoritativeOpenIdClient, realm));

		final var provider = new OpenIdProvider(client);
		PROVIDERS.put(realm, provider);
		return provider;
	}

}
