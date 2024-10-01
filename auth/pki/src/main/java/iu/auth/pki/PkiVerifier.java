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

import java.math.BigInteger;
import java.net.URI;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuDigest;
import edu.iu.IuException;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.auth.config.X500Utils;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Use;
import iu.auth.config.AuthConfig;
import iu.auth.config.IuTrustedIssuer;
import iu.auth.principal.PrincipalVerifier;
import iu.crypt.CryptJsonAdapters;

/**
 * Verifies {@link PkiPrincipal} end-entity identities.
 */
public final class PkiVerifier implements PrincipalVerifier<PkiPrincipal>, IuTrustedIssuer {

	private static final Logger LOG = Logger.getLogger(PkiVerifier.class.getName());

	private final byte[] pkhash;
	private final String realm;
	private final PKIXParameters trustParams;

	/**
	 * Constructor.
	 * 
	 * @param pkp private key principal
	 */
	public PkiVerifier(IuPrivateKeyPrincipal pkp) {
		final var jwk = pkp.getJwk();
		final var privateKey = jwk.getPrivateKey();
		if (privateKey == null)
			pkhash = null;
		else
			pkhash = IuDigest.sha256(privateKey.getEncoded());

		final var certificateChain = Objects.requireNonNull( //
				WebCertificateReference.verify(jwk), "missing certificate chain");

		final var cert = certificateChain[0];
		final var keyUsage = new KeyUsage(cert);
		if (!keyUsage.matches(Use.SIGN))
			throw new IllegalArgumentException("X.509 certificate not valid for digital signature");
		realm = X500Utils.getCommonName(cert.getSubjectX500Principal());
		if (!realm.equals(jwk.getKeyId()))
			throw new IllegalArgumentException("Key ID doesn't match CN");

		final var anchor = new TrustAnchor(cert, null);
		final var pkix = IuException.unchecked(() -> new PKIXParameters(Set.of(anchor)));
		pkix.setRevocationEnabled(false);
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
		return realm;
	}

	@Override
	public boolean isAuthoritative() {
		return pkhash != null;
	}

	@Override
	public void verify(PkiPrincipal pki) throws IuAuthenticationException {
		final var subject = pki.getSubject();

		final Queue<WebKey> keys = new ArrayDeque<>();
		if (isAuthoritative()) {
			final var privateKeys = subject.getPrivateCredentials(WebKey.class);
			if (privateKeys.isEmpty())
				throw new IllegalArgumentException("missing private key");

			for (final var jwk : privateKeys)
				if (!Arrays.equals(pkhash, IuDigest.sha256(jwk.getPrivateKey().getEncoded())))
					throw new IllegalArgumentException("private key mismatch");
				else
					keys.add(jwk);
		}

		final var publicKeys = subject.getPublicCredentials(WebKey.class);
		if (publicKeys.isEmpty())
			throw new IllegalArgumentException("missing public key");
		keys.addAll(publicKeys);

		final Set<BigInteger> trusted = new HashSet<>();
		for (final var jwk : keys) {
			final var cert = jwk.getCertificateChain()[0];
			if (trusted.add(new BigInteger(IuDigest.sha256(IuException.unchecked(cert::getEncoded)))))
				try {
					IuException.checked(CertPathValidatorException.class, () -> {
						final var validator = CertPathValidator.getInstance("PKIX");
						final var certFactory = CertificateFactory.getInstance("X.509");
						final var certPath = certFactory.generateCertPath(List.of(cert));
						final var result = (PKIXCertPathValidatorResult) validator.validate(certPath, trustParams);

						LOG.info(() -> "pki:" + (pkhash != null ? "auth" : "verify") + ":" + pki.getName()
								+ "; trustAnchor: " + X500Utils.getCommonName(
										result.getTrustAnchor().getTrustedCert().getSubjectX500Principal()));
					});
				} catch (CertPathValidatorException e) {
					throw new IuAuthenticationException(null, e);
				}
		}
	}

	@Override
	public PkiPrincipal getPrincipal(IuPrivateKeyPrincipal pkp) {
		final var jwk = pkp.getJwk();
		if (!realm.equals(pkp.getJwk().getKeyId()))
			return null;

		final var privateKey = jwk.getPrivateKey();
		if (isAuthoritative()) {
			if (privateKey == null || !Arrays.equals(pkhash, IuDigest.sha256(privateKey.getEncoded()))) {
				LOG.log(Level.FINE, new IllegalArgumentException("private key mismatch"), () -> "pki:invalid:" + realm);
				return null;
			}
		} else if (privateKey != null) {
			final var wellKnownJwk = pkp.getJwk().wellKnown();
			final var wellKnownPkpBuilder = IuJson.object(IuJson.unwrap(pkp));
			wellKnownPkpBuilder.add("jwk", CryptJsonAdapters.WEBKEY.toJson(wellKnownJwk));
			pkp = AuthConfig.adaptJson(IuPrivateKeyPrincipal.class).fromJson(wellKnownPkpBuilder.build());
		}

		try {
			IuException.checked(CertPathValidatorException.class, () -> {
				final var validator = CertPathValidator.getInstance("PKIX");
				final var certFactory = CertificateFactory.getInstance("X.509");
				final var certPath = certFactory.generateCertPath(List.of(jwk.getCertificateChain()));
				validator.validate(certPath, trustParams);
			});

			return new PkiPrincipal(pkp);

		} catch (CertPathValidatorException e) {
			LOG.log(Level.FINE, e, () -> "pki:invalid:" + realm);
			return null;
		}
	}

	@Override
	public String toString() {
		return "PkiVerifier [" + realm + "]";
	}

}
