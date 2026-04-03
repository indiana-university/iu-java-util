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
package iu.auth.config;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.auth.oauth.IuAuthorizationDetails;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import edu.iu.crypt.WebKey.Algorithm;
import iu.crypt.JwtBuilder;
import jakarta.json.JsonArrayBuilder;

/**
 * Builds {@link RemoteAccessToken} instances.
 * 
 * @param <B> builder type
 */
public class OidcIdTokenBuilder<B extends OidcIdTokenBuilder<B>> extends JwtBuilder<B> {

	private JsonArrayBuilder authorizationDetails = IuJson.array();

	private final Algorithm alg;
	private final String clientId;
	private final Duration maxAge;
	private String nonce;
	private String accessToken;

	/**
	 * Default constructor.
	 * 
	 * @param alg      Signature algorithm used to verify ID Token authenticity
	 * @param clientId Client ID to issue the token on behalf of
	 * @param maxAge   Max length of time since last successful authentication
	 */
	protected OidcIdTokenBuilder(Algorithm alg, String clientId, Duration maxAge) {
		this.alg = alg;
		this.clientId = clientId;
		this.maxAge = maxAge;
	}

	@Override
	public B nonce(String nonce) {
		this.nonce = nonce;
		return super.nonce(nonce);
	}

	/**
	 * Sets the full display name.
	 * 
	 * @param name full display name
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public B fullName(String name) {
		param("name", name);
		return (B) this;
	}

	/**
	 * Sets the preferred email address.
	 * 
	 * @param email preferred email address
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public B email(String email) {
		param("email", email);
		return (B) this;
	}

	/**
	 * Sets the point in time authentication occurred.
	 * 
	 * @param authTime Sets the point in time authentication occurred
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public B authTime(Instant authTime) {
		param("auth_time", authTime.getEpochSecond(), IuJsonAdapter.of(Long.class));
		return (B) this;
	}

	/**
	 * Sets the roles associated with the authorized principal.
	 * 
	 * @param roles roles
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public B roles(String... roles) {
		param("roles", roles, IuJsonAdapter.of(String[].class));
		return (B) this;
	}

	/**
	 * Gets the access token
	 * 
	 * @param accessToken access token
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public B accessToken(String accessToken) {
		this.accessToken = IuObject.once(this.accessToken, accessToken);

		final var sha = IuException.unchecked(() -> MessageDigest.getInstance("SHA-" + alg.size));
		final var hash = sha.digest(IuText.ascii(accessToken));
		final var halfHash = Arrays.copyOfRange(hash, 0, alg.size / 16);
		param("at_hash", IuText.base64Url(halfHash));

		return (B) this;
	}

	/**
	 * Provides authorization details.
	 * 
	 * @param <T>                  details type
	 * @param type                 details interface class
	 * @param authorizationDetails authorization details
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public <T extends IuAuthorizationDetails> B authorizationDetails(Class<T> type, T authorizationDetails) {
		final var json = IuJsonAdapter.adapt(type, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES)
				.toJson(authorizationDetails);
		Objects.requireNonNull(json.asJsonObject().get("type"), "missing type");
		this.authorizationDetails.add(json);
		return (B) this;
	}

	@Override
	protected void prepare() {
		super.prepare();
		final var authorizationDetails = this.authorizationDetails.build();
		if (!authorizationDetails.isEmpty())
			param("authorization_details", authorizationDetails);
	}

	@Override
	public OidcIdToken build() {
		prepare();
		return new OidcIdToken(alg, clientId, nonce, accessToken, maxAge, toJson());
	}

}
