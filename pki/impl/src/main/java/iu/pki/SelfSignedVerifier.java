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
package iu.pki;

import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuDigest;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.X500Utils;
import edu.iu.pki.IuPkiVerifier;
import edu.iu.pki.KeyUsage;

/**
 * Verifies {@link WebKey} instances containing a self-signed end-entity
 * certificate.
 */
public final class SelfSignedVerifier implements IuPkiVerifier {

	private static final Logger LOG = Logger.getLogger(SelfSignedVerifier.class.getName());

	private final byte[] pkhash;
	private final String authorityCommonName;
	private final PKIXParameters trustParams;

	/**
	 * Constructor.
	 * 
	 * @param authorityJwk {@link WebKey}
	 */
	public SelfSignedVerifier(WebKey authorityJwk) {
		final var privateKey = authorityJwk.getPrivateKey();
		if (privateKey == null)
			pkhash = null;
		else
			pkhash = IuDigest.sha256(privateKey.getEncoded());

		final var certificateChain = Objects.requireNonNull( //
				WebCertificateReference.verify(authorityJwk), "missing certificate chain");

		final var cert = certificateChain[0];
		authorityCommonName = X500Utils.getCommonName(cert.getSubjectX500Principal());

		final var keyUsage = new KeyUsage(cert);
		if (!keyUsage.matches(Use.SIGN))
			throw new IllegalArgumentException("X.509 certificate not valid for digital signature");

		final var anchor = new TrustAnchor(cert, null);
		final var pkix = IuException.unchecked(() -> new PKIXParameters(Set.of(anchor)));
		pkix.setRevocationEnabled(false);
		this.trustParams = pkix;
	}

	/**
	 * Verifies a {@link WebKey} as self-signed and matching the same keys as the
	 * key which initialized this verifier.
	 * 
	 * @param jwk {@link WebKey}
	 */
	public void verify(WebKey jwk) {
		final var cert = jwk.getCertificateChain()[0];
		final var commonName = X500Utils.getCommonName(cert.getSubjectX500Principal());

		final var requirePrivateKey = pkhash != null;
		if (requirePrivateKey) {
			final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "missing private key");
			if (!IuObject.equals(pkhash, IuDigest.sha256(privateKey.getEncoded()))) {
				final var msg = "pki:invalid-private:" + authorityCommonName + " rejected " + commonName;
				LOG.log(Level.INFO, msg);
				throw new IllegalArgumentException(msg);
			}
		}

		try {
			IuException.checked(CertPathValidatorException.class, () -> {
				final var validator = CertPathValidator.getInstance("PKIX");
				final var certFactory = CertificateFactory.getInstance("X.509");
				final var certPath = certFactory.generateCertPath(List.of(cert));
				final var result = (PKIXCertPathValidatorResult) validator.validate(certPath, trustParams);

				LOG.info(() -> "pki:" + (requirePrivateKey ? "auth" : "verify") + ":" + commonName + "; trustAnchor: "
						+ X500Utils.getCommonName(result.getTrustAnchor().getTrustedCert().getSubjectX500Principal()));
			});
		} catch (CertPathValidatorException e) {
			final var msg = "pki:invalid:" + authorityCommonName + " rejected " + commonName;
			LOG.log(Level.INFO, msg, e);
			throw new IllegalArgumentException(msg, e);
		}
	}

	@Override
	public String toString() {
		return "SelfSignedVerifier[" + authorityCommonName + "]";
	}

}
