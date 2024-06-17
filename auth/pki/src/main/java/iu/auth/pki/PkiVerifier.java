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
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.crypt.WebCertificateReference;
import iu.auth.config.IuTrustedIssuer;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies {@link PkiPrincipal} identities.
 */
final class PkiVerifier implements PrincipalVerifier<PkiPrincipal>, IuTrustedIssuer {

	private static final Logger LOG = Logger.getLogger(PkiVerifier.class.getName());

	private final PkiPrincipal identity;
	private final PKIXParameters trustParams;

	/**
	 * Constructor.
	 * 
	 * @param identity    identity private key
	 * @param trustParams PKIX certificate chain verifier parameters
	 */
	PkiVerifier(PkiPrincipal identity, PKIXParameters trustParams) {
		if (trustParams.getTrustAnchors().size() != 1)
			throw new IllegalArgumentException();

		IuObject.once(Objects.requireNonNull(WebCertificateReference.verify(identity.key())[0]),
				Objects.requireNonNull(trustParams.getTrustAnchors().iterator().next().getTrustedCert()));

		this.identity = identity;
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
		return identity.getName();
	}

	@Override
	public boolean isAuthoritative() {
		return identity.key().getPrivateKey() != null;
	}

	@Override
	public void verify(PkiPrincipal pki) {
		if (pki == identity)
			return; // identity principal is always valid

		final var privateKey = identity.key().getPrivateKey();
		if (privateKey != null && !privateKey.equals(pki.key().getPrivateKey()))
			throw new IllegalArgumentException("identity mismatch");

		final var anchor = trustParams.getTrustAnchors().iterator().next();
		final var anchorName = X500Utils.getCommonName(anchor.getTrustedCert().getSubjectX500Principal());

		// establish and verify trust of signing key
		final List<Certificate> pathToAnchor = new ArrayList<>();
		for (final var cert : WebCertificateReference.verify(pki.key())) {
			pathToAnchor.add(cert);
			if (anchorName.equals(identity.getName())) {
				final var result = (PKIXCertPathValidatorResult) IuException.unchecked(() -> {
					final var validator = CertPathValidator.getInstance("PKIX");
					final var certFactory = CertificateFactory.getInstance("X.509");
					final var certPath = certFactory.generateCertPath(pathToAnchor);
					return validator.validate(certPath, trustParams);
				});

				LOG.info(() -> "pki:" + (privateKey != null ? "auth" : "verify") + ":" + pki.getName()
						+ "; trustAnchor: "
						+ result.getTrustAnchor().getTrustedCert().getSubjectX500Principal().getName());

				return;
			}
		}

		throw new IllegalArgumentException("issuer not trusted");
	}

	@Override
	public PkiPrincipal getPrincipal(IuPrivateKeyPrincipal privateKeyPrincipal) {
		final var key = identity.key();
		final var jwk = privateKeyPrincipal.getJwk();

		final var privateKey = key.getPrivateKey();
		if (!IuObject.equals(privateKey, jwk.getPrivateKey()))
			return null;

		final var certificateChain = WebCertificateReference.verify(jwk);
		if (!Arrays.equals(certificateChain, key.getCertificateChain()))
			return null;

		return identity;
	}

}
