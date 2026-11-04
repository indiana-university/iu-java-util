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
package iu.oidc.provider;

/**
 * A UserInfo response, and what a transport must call it.
 *
 * <p>
 * Sealed over the two forms OpenID Connect defines. Which one a request gets is
 * the <em>client's</em> to decide, by what it registered, so a transport cannot
 * work it out and is told: a plain claims document is
 * {@value Json#CONTENT_TYPE}, and one that is signed or encrypted or both is
 * {@value Jwt#CONTENT_TYPE}. That is the one piece of content typing the
 * provider owns rather than the transport, and it is owned here because the
 * registration that settles it is only legible on this side.
 * </p>
 *
 * @see <a href=
 *      "https://openid.net/specs/openid-connect-core-1_0.html#UserInfoResponse">OpenID
 *      Connect Core 1.0 &sect;5.3.2</a>
 */
public sealed interface OidcUserinfoResult {

	/**
	 * Gets the response body.
	 *
	 * @return response body, to be written as UTF-8
	 */
	String content();

	/**
	 * Gets what the response body is to be called.
	 *
	 * @return {@code Content-Type} header value
	 */
	String contentType();

	/**
	 * The claims, as the caller serialized them, for a client that registered
	 * neither signing nor encryption.
	 *
	 * @param content serialized claims
	 */
	record Json(String content) implements OidcUserinfoResult {

		/** What a plain claims document is called. */
		public static final String CONTENT_TYPE = "application/json";

		@Override
		public String contentType() {
			return CONTENT_TYPE;
		}
	}

	/**
	 * The claims signed, encrypted, or both, for a client that registered one or
	 * the other.
	 *
	 * @param content JOSE compact serialization
	 */
	record Jwt(String content) implements OidcUserinfoResult {

		/** What a signed or encrypted claims document is called. */
		public static final String CONTENT_TYPE = "application/jwt";

		@Override
		public String contentType() {
			return CONTENT_TYPE;
		}
	}

}
