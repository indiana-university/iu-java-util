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
package iu.auth.pki;

import java.net.URI;
import java.security.cert.CertPathValidator;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.crypt.WebKey;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies {@link PkiPrincipal} identities.
 */
final class PkiVerifier implements PrincipalVerifier<PkiPrincipal> {
	private final boolean authoritative;
	private final String realm;
	private final PKIXParameters trustParams;

	/**
	 * Constructor.
	 * 
	 * @param authoritative authoritative flag, indicates private key possession
	 * @param realm         authentication realm
	 * @param trustParams   PKIX certificate chain verifier parameters
	 */
	PkiVerifier(boolean authoritative, String realm, PKIXParameters trustParams) {
		this.authoritative = authoritative;
		this.realm = realm;
		this.trustParams = trustParams;
	}

	@Override
	public String getAuthScheme() {
		return null;
	}

	@Override
	public URI getAuthenticationEndpoint() {
		return null;
	}

	@Override
	public Class<PkiPrincipal> getType() {
		return PkiPrincipal.class;
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public boolean isAuthoritative() {
		return authoritative;
	}

	@Override
	public void verify(PkiPrincipal pki, String realm) {
		final var sub = pki.getSubject();
		final var wellKnown = sub.getPublicCredentials(WebKey.class).iterator().next();

		final var anchor = X500Utils.getCommonName(
				trustParams.getTrustAnchors().iterator().next().getTrustedCert().getSubjectX500Principal());

		// establish and verify trust of signing key
		final List<Certificate> pathToAnchor = new ArrayList<>();
		var found = false;
		for (final var cert : wellKnown.getCertificateChain()) {
			pathToAnchor.add(cert);
			if (anchor.equals(X500Utils.getCommonName(cert.getIssuerX500Principal()))) {
				IuException.unchecked(() -> CertPathValidator.getInstance("PKIX")
						.validate(CertificateFactory.getInstance("X.509").generateCertPath(pathToAnchor), trustParams));
				found = true;
				break;
			}
		}

		if (!found)
			throw new IllegalArgumentException("issuer not found");

		if (authoritative) {
			final var privIter = sub.getPrivateCredentials(WebKey.class).iterator();
			if (!privIter.hasNext())
				throw new IllegalArgumentException("missing private key");

			final var key = privIter.next();
			Objects.requireNonNull(key.getPrivateKey(), "missing private key");
			IuObject.once(wellKnown, key.wellKnown());
		}
	}

}
