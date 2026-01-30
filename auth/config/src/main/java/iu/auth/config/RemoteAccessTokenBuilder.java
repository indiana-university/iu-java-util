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

import java.util.Objects;

import edu.iu.auth.oauth.IuAuthorizationDetails;
import edu.iu.auth.oauth.IuCallerAttributes;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import iu.crypt.JwtBuilder;
import jakarta.json.JsonArrayBuilder;

/**
 * Builds {@link RemoteAccessToken} instances.
 * 
 * @param <B> builder type
 */
public class RemoteAccessTokenBuilder<B extends RemoteAccessTokenBuilder<B>> extends JwtBuilder<B> {

	private JsonArrayBuilder authorizationDetails = IuJson.array();

	/**
	 * Default constructor.
	 */
	protected RemoteAccessTokenBuilder() {
	}

	/**
	 * Sets the scope granted with this token.
	 * 
	 * @param scope scope
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public B scope(String scope) {
		param("scope", scope);
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
	protected <T extends IuAuthorizationDetails> B authorizationDetails(Class<T> type, T authorizationDetails) {
		final var json = IuJsonAdapter.adapt(type, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES)
				.toJson(authorizationDetails);
		Objects.requireNonNull(json.asJsonObject().get("type"), "missing type");
		this.authorizationDetails.add(json);
		return (B) this;
	}

	/**
	 * Adds caller attributes as authorization details.
	 * 
	 * @param callerAttributes {@link IuCallerAttributes}
	 * @return this
	 */
	public B caller(IuCallerAttributes callerAttributes) {
		return authorizationDetails(IuCallerAttributes.class, callerAttributes);
	}

	@Override
	protected void prepare() {
		super.prepare();
		final var authorizationDetails = this.authorizationDetails.build();
		if (!authorizationDetails.isEmpty())
			param("authorization_details", authorizationDetails);
	}

	@Override
	public RemoteAccessToken build() {
		prepare();
		return new RemoteAccessToken(toJson());
	}

}
