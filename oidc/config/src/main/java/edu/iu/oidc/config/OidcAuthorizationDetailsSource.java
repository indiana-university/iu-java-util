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
package edu.iu.oidc.config;

import edu.iu.jwt.IuAuthorizationDetails;

/**
 * Decides what authorization details an end user actually gets, out of what a
 * client asked for.
 *
 * <p>
 * The seam between a provider endpoint and whatever an enterprise decides
 * authorization by. A client states what it wants as
 * {@code authorization_details}; a provider settles who the end user is; this
 * puts the two together and answers what was released. Everything about how
 * that decision is reached &mdash; which records the end user administers,
 * whose authority they hold, what a directory or a rules engine says &mdash; is
 * the deployment's, and none of it reaches the provider.
 * </p>
 *
 * <h2>Opaque on both sides</h2>
 *
 * <p>
 * Neither value is inspected, compared, or copied anywhere in between. A
 * provider reads {@code authorization_details} off a request already parsed and
 * validated by its transport, hands it here once the end user is known, and
 * records both what was asked and what came back on the grant. The only thing
 * the provider can even see is {@link IuAuthorizationDetails#getType() the type
 * discriminator}; everything a deployment carries alongside it belongs to the
 * deployment.
 * </p>
 *
 * <p>
 * {@link IuAuthorizationDetails} is what a {@code WebToken} claim is built from,
 * so details declared this way reach a stored grant and an issued token without
 * anything having to arrange it. A deployment carrying properties beyond the
 * type registers a claim adapter for its own implementation, since an adapter
 * resolved from this interface alone would serialize the type and nothing else.
 * </p>
 *
 * <p>
 * A deployment that grants nothing this way still needs one, and answers
 * {@code null}.
 * </p>
 *
 * @see <a href="https://www.rfc-editor.org/info/rfc9396">RFC 9396: OAuth 2.0
 *      Rich Authorization Requests</a>
 */
public interface OidcAuthorizationDetailsSource {

	/**
	 * Decides what to release to one end user.
	 *
	 * <p>
	 * Called once per grant, after the end user is authenticated and before an
	 * authorization code is issued, so what a client eventually redeems carries a
	 * decision already made rather than one still to make.
	 * </p>
	 *
	 * <p>
	 * Refusing is part of the contract rather than an implementation detail, since
	 * what is thrown decides who hears about it:
	 * </p>
	 * <ul>
	 * <li>{@link edu.iu.IuBadRequestException} &mdash; the client asked for
	 * something malformed. This is the only refusal relayed to the client, as an
	 * {@code invalid_authorization_details} error on its redirect URI, so the
	 * message reaches the client and should say what was wrong with the request
	 * without saying anything about the end user.</li>
	 * <li>{@link edu.iu.IuAuthorizationFailedException} &mdash; the request was
	 * well formed and this end user may not have it. Answered as forbidden, which
	 * is expected to be the common refusal.</li>
	 * <li>{@link edu.iu.IuOutOfServiceException} &mdash; whatever decides this is
	 * unreachable. Answered as unavailable, so a caller can retry.</li>
	 * <li>Anything else &mdash; a server error.</li>
	 * </ul>
	 *
	 * <p>
	 * Only the first is a decision the client can act on; the rest say no decision
	 * was reached, and telling a client otherwise would have it treat a failure as
	 * an answer.
	 * </p>
	 *
	 * @param details       authorization details the client requested, as its
	 *                      transport parsed them; {@code null} when the request
	 *                      asked for none
	 * @param principalName end user the provider authenticated, which is the
	 *                      {@code sub} of everything issued against this grant
	 * @return authorization details released; {@code null} to release none, which
	 *         is also the right answer to a request that asked for none
	 * @throws edu.iu.IuBadRequestException          if the requested details are
	 *                                               malformed
	 * @throws edu.iu.IuAuthorizationFailedException if this end user may not have
	 *                                               what was requested
	 * @throws edu.iu.IuOutOfServiceException        if the decision cannot be
	 *                                               reached right now
	 */
	Iterable<? extends IuAuthorizationDetails> authorize(Iterable<? extends IuAuthorizationDetails> details,
			String principalName);

}
