/*
 * Copyright © 2026 Indiana University
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
package iu.oidc.client;

import java.io.IOException;
import java.net.http.HttpRequest.Builder;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuIterable;
import iu.oidc.client.config.IuOidcClientReference;

/**
 * Exchanges an access token for one answering for somebody else, through the
 * RFC 8693 token exchange grant.
 *
 * <p>
 * The second half of a two-step flow. An application authorizes its end user
 * normally, and holds an access token they are the subject of; to act as
 * somebody else it presents that token here as the {@code actor_token} and
 * names who it wants to answer for. What comes back has the named principal as
 * its subject and the original end user as its {@code act} claim, so a resource
 * server can tell the action was delegated and a relying party can show its
 * user whose session they are looking through.
 * </p>
 *
 * <p>
 * The subject is <em>asserted</em>, not proven: nobody holds a token for the
 * party being impersonated, which is the point of asking. What authorizes the
 * exchange is the role held by the principal the {@code actor_token} was issued
 * to, which is why a provider only honors this outside production.
 * </p>
 *
 * <p>
 * The exchanged token is deliberately narrower than the one that bought it. Its
 * scope cannot exceed what the presented token already carries, and no refresh
 * token descends from it &mdash; an impersonated session is extended by
 * exchanging again, while the token it descends from is still good, rather than
 * by refreshing on its own.
 * </p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8693">RFC 8693</a>
 */
public class OidcTokenExchangeGrant extends OidcTokenGrant {

	/**
	 * Token type identifier naming the subject by principal name.
	 *
	 * <p>
	 * Provider-defined, as RFC 8693 &sect;3 allows: the registry names token
	 * formats, and the subject of this exchange is not a token.
	 * </p>
	 */
	public static final String PRINCIPAL_NAME_TOKEN_TYPE = "https://iu.edu/oauth/token-type/principal-name";

	/** RFC 8693 &sect;3 token type identifier for an OAuth 2.0 access token. */
	public static final String ACCESS_TOKEN_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

	private final String impersonatedPrincipalName;
	private final String accessToken;

	/**
	 * Constructor.
	 *
	 * @param config                    {@link IuOidcClientReference}
	 * @param impersonatedPrincipalName name of the principal to answer for
	 * @param accessToken               access token issued to the end user this
	 *                                  exchange acts on behalf of, which becomes
	 *                                  the {@code act} claim on what comes back
	 */
	public OidcTokenExchangeGrant(IuOidcClientReference config, String impersonatedPrincipalName, String accessToken) {
		super(config);
		this.impersonatedPrincipalName = Objects.requireNonNull(impersonatedPrincipalName,
				"Missing impersonated principal name");
		this.accessToken = Objects.requireNonNull(accessToken, "Missing access token");
	}

	@Override
	protected void tokenAuth(Builder requestBuilder, Map<String, Iterable<String>> params) throws IOException {
		params.put("grant_type", IuIterable.iter("urn:ietf:params:oauth:grant-type:token-exchange"));
		addClientAuth(requestBuilder, params);
		params.put("subject_token", IuIterable.iter(impersonatedPrincipalName));
		params.put("subject_token_type", IuIterable.iter(PRINCIPAL_NAME_TOKEN_TYPE));
		params.put("actor_token", IuIterable.iter(accessToken));
		params.put("actor_token_type", IuIterable.iter(ACCESS_TOKEN_TOKEN_TYPE));
	}

	@Override
	protected boolean isAuthTimeRequired() {
		// an exchanged token dates the actor's authentication, not its subject's, and
		// a provider that hasn't started dating its access tokens leaves it nothing
		// true to carry
		return false;
	}

}
