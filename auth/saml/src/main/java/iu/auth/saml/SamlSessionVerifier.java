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
package iu.auth.saml;

import java.net.URI;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.IuObject;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.saml.IuSamlSessionVerifier;
import edu.iu.auth.session.IuSession;

/**
 * SAML session implementation to support session management
 */
final class SamlSessionVerifier implements IuSamlSessionVerifier {

	private final Logger LOG = Logger.getLogger(SamlSessionVerifier.class.getName());

	private final SamlServiceProvider serviceProvider;

	/**
	 * Constructor.
	 * 
	 * @param postUri HTTP POST Binding URI
	 */
	SamlSessionVerifier(URI postUri) {
		this.serviceProvider = SamlServiceProvider.withBinding(postUri);
	}

	@Override
	public URI initRequest(IuSession session, URI entryPointUri) {
		Objects.requireNonNull(entryPointUri, "Missing entryPointUri");

		final var detail = session.getDetail(SamlSessionDetails.class);
		IuObject.require(detail.getRelayState(), Objects::isNull, "relayState is already initialized");
		IuObject.require(detail.getSessionId(), Objects::isNull, "sessionId is already initialized");
		IuObject.require(detail.getEntryPointUri(), Objects::isNull, "entryPointUri is already initialized");
		IuObject.require(detail.isInvalid(), a -> !a, "invalid session");
		IuObject.require(detail, a -> !SamlPrincipal.isBound(a), "principal attributes have already been bound");

		final var relayState = IdGenerator.generateId();
		final var sessionId = IdGenerator.generateId();
		detail.setRelayState(relayState);
		detail.setSessionId(sessionId);
		detail.setEntryPointUri(entryPointUri);

		return serviceProvider.getAuthnRequest(relayState, sessionId);
	}

	@Override
	public URI verifyResponse(IuSession session, String remoteAddr, String samlResponse, String relayState)
			throws IuAuthenticationException {
		final var detail = session.getDetail(SamlSessionDetails.class);
		final var sessionId = Objects.requireNonNull(detail.getSessionId(), "Missing sessionId");
		final var entryPointUri = Objects.requireNonNull(detail.getEntryPointUri(), "Missing entryPointUri");
		IuObject.require(detail.isInvalid(), a -> !a, "invalid session");
		IuObject.require(detail, a -> !SamlPrincipal.isBound(a), "principal attributes have already been bound");

		IuObject.once(Objects.requireNonNull(relayState, "Missing RelayState parameter"),
				Objects.requireNonNull(detail.getRelayState(), "Missing relayState in session"), "RelayState mismatch");

		try {
			serviceProvider
					.verifyResponse(IuWebUtils.getInetAddress(remoteAddr),
							Objects.requireNonNull(samlResponse, "Missing SAMLResponse parameter"), sessionId)
					.bind(detail);
		} catch (Throwable e) {
			LOG.log(Level.INFO, "Invalid SAML Response", e);
			detail.setInvalid(true);

			final var challenge = new IuAuthenticationException(null, e);
			challenge.setLocation(entryPointUri);
			throw challenge;
		}

		return entryPointUri;
	}

	@Override
	public SamlPrincipal getPrincipalIdentity(IuSession session) throws IuAuthenticationException {
		final var detail = session.getDetail(SamlSessionDetails.class);
		IuObject.require(detail.isInvalid(), a -> !a, "invalid session");
		IuObject.require(detail, SamlPrincipal::isBound, "Session missing principal");
		final var entryPointUri = Objects.requireNonNull(detail.getEntryPointUri(), "Missing entryPointUri");

		final var id = SamlPrincipal.from(detail);
		try {
			IuPrincipalIdentity.verify(id, serviceProvider.getRealm());
		} catch (IuAuthenticationException e) {
			e.setLocation(entryPointUri);
			throw e;
		}

		return id;
	}

}
