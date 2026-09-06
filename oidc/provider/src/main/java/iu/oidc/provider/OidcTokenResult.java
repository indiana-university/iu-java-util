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

import edu.iu.jwt.IuAuthorizationDetails;

/**
 * What a token request came to, for a transport to write back.
 *
 * <p>
 * Sealed over two outcomes: tokens were issued, or the request was refused.
 * Unlike this provider's other endpoints, a refusal is a result rather than
 * something thrown for a transport's error boundary to render &mdash; a token
 * request comes from a client that parses the response, and RFC 6749 &sect;5.2
 * defines the error object it parses, so the endpoint has to say what goes in
 * it rather than leaving the transport to compose one.
 * </p>
 *
 * <h2>Rendering is the transport's</h2>
 *
 * <p>
 * Both outcomes are RFC 6749 JSON documents, and neither is rendered here. The
 * fields are named exactly as the wire spells them, so an integration
 * serializes a record and nothing has to be looked up; how it serializes
 * &mdash; and how it writes the status, the {@code application/json} content
 * type, the {@code UTF-8} encoding and the {@code Cache-Control: no-store} that
 * RFC 6749 &sect;5.1 requires &mdash; is its own, the same way the discovery
 * document and the UserInfo response are.
 * </p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.1">RFC 6749
 *      &sect;5.1</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">RFC 6749
 *      &sect;5.2</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9396#section-7">RFC 9396
 *      &sect;7</a>
 */
public sealed interface OidcTokenResult {

	/**
	 * Tokens were issued; answer 200.
	 *
	 * <p>
	 * {@code idToken} is set only when {@code openid} was granted, and only for a
	 * grant that answers for an end user; {@code refreshToken} only when
	 * {@code offline_access} was granted and the original authentication is young
	 * enough for one to be worth issuing. Both are {@code null} otherwise and are
	 * left out of the response document rather than written empty.
	 * </p>
	 *
	 * @param accessToken          {@code access_token}
	 * @param tokenType            {@code token_type}, always {@code Bearer}
	 * @param expiresIn            {@code expires_in}, in seconds
	 * @param scope                {@code scope} actually granted, space-delimited,
	 *                             which may be narrower than what was asked for
	 * @param idToken              {@code id_token}; null when none was issued
	 * @param refreshToken         {@code refresh_token}; null when none was issued
	 * @param authorizationDetails {@code authorization_details} the grant released,
	 *                             which RFC 9396 &sect;7 has the response state
	 *                             since what was granted may be narrower than what
	 *                             was asked for; null when the grant released none
	 */
	record Issued(String accessToken, String tokenType, long expiresIn, String scope, String idToken,
			String refreshToken, Iterable<? extends IuAuthorizationDetails> authorizationDetails)
			implements OidcTokenResult {
	}

	/**
	 * The request was refused; answer {@code status} with the error object.
	 *
	 * <p>
	 * {@code challenge} is set only for {@code invalid_client}, which RFC 6749
	 * answers 401 for and which a transport <em>must</em> accompany with a
	 * {@code WWW-Authenticate} header. Every other refusal is a 400 or a 403 and
	 * carries none.
	 * </p>
	 *
	 * @param error            {@code error} code
	 * @param errorDescription {@code error_description}
	 * @param status           HTTP status to answer
	 * @param challenge        {@code WWW-Authenticate} value; null when the refusal
	 *                         warrants none
	 */
	record Error(String error, String errorDescription, int status, String challenge) implements OidcTokenResult {
	}

}
