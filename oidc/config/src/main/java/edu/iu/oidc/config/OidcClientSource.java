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

/**
 * Reads one relying party's registration, whole.
 *
 * <p>
 * The seam between a provider endpoint and wherever registrations are kept
 * &mdash; a secret store, a set of database tables, a static document. An
 * implementation assembles whatever it reads into an
 * {@link OidcClientConfiguration}: the client's own record, every endpoint it
 * registers, and for each endpoint the credentials it accepts, the roles a
 * request through it may act in, and the resources it may act on.
 * </p>
 *
 * <p>
 * How long a registration stays cached, and whether it is cached at all, is an
 * implementation's own. An endpoint reads through this on every request, so a
 * source backed by something expensive to read is the one that decides what to
 * do about it.
 * </p>
 */
public interface OidcClientSource {

	/**
	 * Gets one relying party's registration.
	 *
	 * <p>
	 * An unregistered client is one with nothing to read. An implementation may
	 * answer {@code null} or throw; an endpoint treats the two the same way, as a
	 * refusal indistinguishable from a client that never existed. A failure
	 * <em>should not</em> be cached, so a client registered a moment ago costs a
	 * fresh lookup rather than being refused until a cache expires.
	 * </p>
	 *
	 * @param clientId requested {@code client_id}
	 * @return registration, or {@code null} if no client is registered under that
	 *         ID
	 */
	OidcClientConfiguration client(String clientId);

}
