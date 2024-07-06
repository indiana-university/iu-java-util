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
import edu.iu.IuIterable;
import edu.iu.auth.config.IuCertificateAuthority;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.auth.config.X500Utils;
import edu.iu.crypt.WebCertificateReference;
import iu.auth.config.IuTrustedIssuer;
import iu.auth.principal.PrincipalVerifier;

/**
 * Verifies {@link PkiPrincipal} identities.
 */
final class CaVerifier implements PrincipalVerifier<PkiPrincipal>, IuTrustedIssuer {

	private static final Logger LOG = Logger.getLogger(CaVerifier.class.getName());

	private final X509Certificate cert;
	private final PKIXParameters trustParams;

	/**
	 * Constructor.
	 * 
	 * @param ca CA principal identity
	 */
	CaVerifier(IuCertificateAuthority ca) {
		cert = ca.getCertificate();
		if (cert.getBasicConstraints() == -1)
			throw new IllegalArgumentException("Not a CA certificate");

		final var keyUsage = cert.getKeyUsage();
		if (keyUsage == null || //
				!keyUsage[5])
			throw new IllegalArgumentException("Key usage doesn't permit certificate signing");
		if (!keyUsage[6])
			throw new IllegalArgumentException("Key usage doesn't permit CRL signing");

		final var anchor = new TrustAnchor(ca.getCertificate(), null);
		final var pkix = IuException.unchecked(() -> new PKIXParameters(Set.of(anchor)));
		final Queue<X509CRL> crl = new ArrayDeque<>();
		ca.getCrl().forEach(crl::offer);
		pkix.addCertStore(IuException
				.unchecked(() -> CertStore.getInstance("Collection", new CollectionCertStoreParameters(crl))));
		this.trustParams = pkix;
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
		return X500Utils.getCommonName(cert.getSubjectX500Principal());
	}

	@Override
	public boolean isAuthoritative() {
		return false;
	}

	@Override
	public void verify(PkiPrincipal pki) {
		final var issuerPrincipal = cert.getSubjectX500Principal();
		final List<Certificate> pathToAnchor = new ArrayList<>();
		for (final var cert : WebCertificateReference.verify(pki.getJwk())) {
			pathToAnchor.add(cert);
			if (issuerPrincipal.equals(cert.getIssuerX500Principal())) {
				final var result = (PKIXCertPathValidatorResult) IuException.unchecked(() -> {
					final var validator = CertPathValidator.getInstance("PKIX");
					final var certFactory = CertificateFactory.getInstance("X.509");
					return validator.validate(certFactory.generateCertPath(pathToAnchor), trustParams);
				});

				LOG.info(() -> "ca:verify:" + pki.getName() + "; trustAnchor: "
						+ result.getTrustAnchor().getTrustedCert().getSubjectX500Principal().getName());
				return;
			}
		}

		throw new IllegalArgumentException("issuer not trusted");
	}

	@Override
	public PkiPrincipal getPrincipal(IuPrivateKeyPrincipal pkp) {
		final var issuerPrincipal = cert.getSubjectX500Principal();

		final List<Certificate> pathToAnchor = new ArrayList<>();
		for (final var cert : WebCertificateReference.verify(pkp.getJwk())) {
			pathToAnchor.add(cert);
			if (issuerPrincipal.equals(cert.getIssuerX500Principal()))
				try {
					final var validator = CertPathValidator.getInstance("PKIX");
					final var certFactory = CertificateFactory.getInstance("X.509");
					validator.validate(certFactory.generateCertPath(pathToAnchor), trustParams);
					return new PkiPrincipal(pkp);
				} catch (Throwable e) {
					LOG.log(Level.INFO, e, () -> "ca:invalid:" + getRealm());
				}
		}
		return null;
	}

}
