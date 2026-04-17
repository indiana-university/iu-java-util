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
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.X500Utils;
import edu.iu.pki.IuCertificateAuthority;
import edu.iu.pki.IuPkiVerifier;
import edu.iu.pki.KeyUsage;

/**
 * Verifies {@link PkiPrincipal} identities.
 */
public final class CaVerifier implements IuPkiVerifier {

	private static final Logger LOG = Logger.getLogger(CaVerifier.class.getName());

	private final X509Certificate cert;
	private final PKIXParameters trustParams;

	/**
	 * Constructor.
	 * 
	 * @param ca CA principal identity
	 */
	public CaVerifier(IuCertificateAuthority ca) {
		cert = ca.getCertificate();
		final var keyUsage = new KeyUsage(cert);
		if (!keyUsage.isCA())
			throw new IllegalArgumentException("X.509 certificate is not a valid CA signing cert");

		final var anchor = new TrustAnchor(cert, null);
		final var pkix = IuException.unchecked(() -> new PKIXParameters(Set.of(anchor)));
		final Queue<X509CRL> crl = new ArrayDeque<>();
		ca.getCrl().forEach(crl::offer);
		pkix.addCertStore(IuException
				.unchecked(() -> CertStore.getInstance("Collection", new CollectionCertStoreParameters(crl))));
		this.trustParams = pkix;
	}

	@Override
	public void verify(WebKey jwk) {
		final List<Certificate> pathToAnchor = new ArrayList<>();
		final var issuerPrincipal = cert.getSubjectX500Principal();
		for (final var cert : WebCertificateReference.verify(jwk)) {
			final var commonName = X500Utils.getCommonName(cert.getSubjectX500Principal());
			pathToAnchor.add(cert);
			if (issuerPrincipal.equals(cert.getIssuerX500Principal()))
				try {
					final var result = (PKIXCertPathValidatorResult) IuException
							.checked(CertPathValidatorException.class, () -> {
								final var validator = CertPathValidator.getInstance("PKIX");
								final var certFactory = CertificateFactory.getInstance("X.509");
								return validator.validate(certFactory.generateCertPath(pathToAnchor), trustParams);
							});

					LOG.info(() -> "ca:verify:" + commonName + "; trustAnchor: " + X500Utils
							.getCommonName(result.getTrustAnchor().getTrustedCert().getSubjectX500Principal()));
					return;

				} catch (CertPathValidatorException e) {
					final var msg = "ca:invalid:" + issuerPrincipal + " rejected " + commonName;
					LOG.log(Level.INFO, msg, e);
					throw new IllegalArgumentException(msg, e);
				}
		}

		final var msg = "ca:no-match:" + issuerPrincipal + " rejected " + jwk.getKeyId();
		LOG.log(Level.INFO, msg);
		throw new IllegalArgumentException(msg);
	}

	@Override
	public String toString() {
		return "CaVerifier [" + cert.getSubjectX500Principal() + "]";
	}

}
