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
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.IuBadRequestException;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.saml.IuSamlSessionVerifier;
import edu.iu.auth.session.IuSession;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import iu.auth.config.AuthConfig;

/**
 * SAML session implementation to support session management
 */
final class SamlSessionVerifier implements IuSamlSessionVerifier {

	private final Logger LOG = Logger.getLogger(SamlSessionVerifier.class.getName());

	private final SamlServiceProvider serviceProvider;

	
	private SamlPrincipal id;
	private boolean invalid;

	/**
	 * Constructor.
	 * 
	 * @param postUri       HTTP POST Binding URI
	 */
	SamlSessionVerifier(URI postUri) {
		this.serviceProvider = SamlServiceProvider.withBinding(postUri);
	}


	@Override
	public URI initRequest(IuSession session, URI entryPointUri) {
		SamlSessionDetails detail = session.getDetail(SamlSessionDetails.class);
		IuObject.require(id, Objects::isNull);
		IuObject.require(detail.getRelayState(), Objects::isNull);
		IuObject.require(detail.getSessionId(), Objects::isNull);

		final var relayState = IdGenerator.generateId();
		final var sessionId = IdGenerator.generateId();
		detail.setRelayState(relayState);
		detail.setSessionId(sessionId);
		return serviceProvider.getAuthnRequest(relayState, sessionId);
	}

	@Override
	public URI verifyResponse(IuSession session, String remoteAddr, String samlResponse, String relayState)
			throws IuAuthenticationException {
		SamlSessionDetails detail = session.getDetail(SamlSessionDetails.class);
		final var  entryPointUri = detail.getEntryPointUri();
		
		try {
			final var sessionId = detail.getSessionId();
			IuObject.require(id, Objects::isNull);
			IuObject.once(Objects.requireNonNull(relayState), Objects.requireNonNull(detail.getRelayState()));

			id = serviceProvider.verifyResponse( //
					IuWebUtils.getInetAddress(remoteAddr), //
					Objects.requireNonNull(samlResponse), //
					Objects.requireNonNull(sessionId));

		} catch (Throwable e) {
			LOG.log(Level.INFO, "Invalid SAML Response", e);
			invalid = true;

			final var challenge = new IuAuthenticationException(null, e);
			challenge.setLocation(entryPointUri);
			throw challenge;
		}

		return entryPointUri;
	}

	@Override
	public SamlPrincipal getPrincipalIdentity(IuSession session) throws IuAuthenticationException {
		SamlSessionDetails detail = session.getDetail(SamlSessionDetails.class);
		final var entryPointUri = detail.getEntryPointUri();
		if (invalid)
			throw new IuBadRequestException("Session failed due to an invalid SAML response, check POST logs");

		try {
			IuPrincipalIdentity.verify(id, serviceProvider.getRealm());
		} catch (IuAuthenticationException e) {
			e.setLocation(entryPointUri);
			throw e;
		}
		return id;
	}

}
