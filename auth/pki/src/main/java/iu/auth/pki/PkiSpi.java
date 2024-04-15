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
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.CertPathParameters;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.spi.IuPkiSpi;
import edu.iu.crypt.DigestUtils;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import iu.auth.util.PrincipalVerifierRegistry;

/**
 * {@link IuPkiSpi} service provider implementation.
 */
public class PkiSpi implements IuPkiSpi {

	private static final Map<String, CertPathParameters> TRUST = new HashMap<>();

	@Override
	public PkiPrincipal readPkiPrincipal(String serialized) {
		final PrivateKey privateKey;
		final CertPath certPath;
		final WebKey partialKey;
		if (serialized.startsWith("{")) {
			partialKey = WebKey.parse(serialized);
			privateKey = Objects.requireNonNull(partialKey.getPrivateKey(), "missing private key");

			final var certChain = WebCertificateReference.verify(partialKey);
			certPath = IuException
					.unchecked(() -> CertificateFactory.getInstance("X.509").generateCertPath(List.of(certChain)));

		} else if (serialized.startsWith("-----BEGIN ")) {
			partialKey = null;
			final List<X509Certificate> certChain = new ArrayList<>();
			PemEncoded encodedPrivateKey = null;
			for (final var pemEncoded : IuIterable.of(() -> PemEncoded.parse(serialized)))
				switch (pemEncoded.getKeyType()) {
				case CERTIFICATE:
					certChain.add(pemEncoded.asCertificate());
					break;
				case PRIVATE_KEY:
					encodedPrivateKey = IuObject.once(encodedPrivateKey, pemEncoded);
					break;

				default:
					throw new IllegalArgumentException("only PRIVATE KEY and CERTIFICATE allowed");
				}

			if (certChain.isEmpty())
				throw new IllegalArgumentException("at least one CERTIFICATE is required");

			privateKey = Objects.requireNonNull(encodedPrivateKey, "missing private key")
					.asPrivate(certChain.get(0).getPublicKey().getAlgorithm());

			certPath = IuException.unchecked(() -> CertificateFactory.getInstance("X.509").generateCertPath(certChain));
		} else
			throw new UnsupportedOperationException("only PEM and JWK encoded identity certs are supported");

		final var certList = certPath.getCertificates();
		final var idCert = (X509Certificate) certList.get(0);

		final var pathLen = idCert.getBasicConstraints();
		if (pathLen != -1)
			throw new IllegalArgumentException("ID certificate must be an end-entity");

		final var publicKey = IuObject.once(IuObject.convert(partialKey, WebKey::getPublicKey), idCert.getPublicKey());

		final WebKey.Builder<?> jwkBuilder;
		final var params = WebKey.algorithmParams(publicKey);
		if (params == null)
			jwkBuilder = WebKey.builder(Type.from(publicKey.getAlgorithm(), null));
		else
			jwkBuilder = WebKey.builder(Objects.requireNonNull(Type.from(params), params.toString()));

		jwkBuilder.key(privateKey).key(publicKey);

		final var commonName = Objects.requireNonNull(X500Utils.getCommonName(idCert.getSubjectX500Principal()),
				"missing common name");
		if (commonName.indexOf(':') != -1) {
			final var uri = URI.create(commonName);
			final var fragment = uri.getFragment();
			if (fragment != null)
				jwkBuilder.keyId(IuObject.once(IuObject.convert(partialKey, WebKey::getKeyId), fragment));
			else
				jwkBuilder.keyId(IuObject.once(IuObject.convert(partialKey, WebKey::getKeyId), commonName));
		} else
			jwkBuilder.keyId(IuObject.once(IuObject.convert(partialKey, WebKey::getKeyId), commonName));

		final var keyUsage = idCert.getKeyUsage();
		if (keyUsage[0]) // digitalSignature
			jwkBuilder.use(Use.SIGN).ops(Operation.SIGN, Operation.VERIFY);
		if (keyUsage[1]) // nonRepudiation
			jwkBuilder.use(Use.SIGN).ops(Operation.VERIFY);
		if (keyUsage[2]) // keyEncipherment
			jwkBuilder.use(Use.ENCRYPT).ops(Operation.WRAP, Operation.UNWRAP);
		if (keyUsage[3]) // dataEncipherment
			jwkBuilder.use(Use.ENCRYPT).ops(Operation.ENCRYPT, Operation.DECRYPT);
		if (keyUsage[4]) // keyAgreement
			jwkBuilder.use(Use.ENCRYPT).ops(Operation.DERIVE_KEY);
		if (keyUsage[5] || keyUsage[6]) // keyCertSign || cRLSign
			jwkBuilder.use(Use.SIGN).ops(Operation.SIGN);

		final CertPathParameters trust;
		synchronized (TRUST) {
			final var selfSigned = idCert.getSubjectX500Principal().equals(idCert.getIssuerX500Principal());
			if (selfSigned)
				IuException.unchecked(() -> {
					final var selfSignedParams = new PKIXParameters(Set.of(new TrustAnchor(idCert, null)));
					selfSignedParams.setRevocationEnabled(false);
					PrincipalVerifierRegistry.registerVerifier(commonName,
							new IdVerifier(true, commonName, selfSignedParams), true);
					TRUST.put(commonName, selfSignedParams);
				});

			final var caIssuedCert = (X509Certificate) certList.get(certList.size() - 1);
			final var caCommonName = Objects.requireNonNull(
					X500Utils.getCommonName(caIssuedCert.getIssuerX500Principal()), "issuer is missing CN or UID");
			trust = Objects.requireNonNull(TRUST.get(caCommonName), "issuer is not registered as trusted");
		}

		IuException.unchecked(() -> CertPathValidator.getInstance("PKIX").validate(certPath, trust));

		if (partialKey == null)
			jwkBuilder.cert(certList.toArray(X509Certificate[]::new));
		else {
			IuObject.convert(partialKey.getCertificateChain(), jwkBuilder::cert);
			IuObject.convert(partialKey.getCertificateUri(), jwkBuilder::cert);
			IuObject.convert(partialKey.getCertificateThumbprint(), jwkBuilder::x5t);
			IuObject.convert(partialKey.getCertificateSha256Thumbprint(), jwkBuilder::x5t256);
			IuObject.convert(partialKey.getUse(), jwkBuilder::use);
			IuObject.convert(partialKey.getOps(), a -> jwkBuilder.ops(a.toArray(Operation[]::new)));
			IuObject.convert(partialKey.getKey(), jwkBuilder::key);
		}

		IuException.unchecked(() -> {
			final var encoded = idCert.getEncoded();
			jwkBuilder.x5t(DigestUtils.sha1(encoded));
			jwkBuilder.x5t256(DigestUtils.sha256(encoded));
		});

		return new PkiPrincipal(jwkBuilder.build(), certPath);
	}

	private class IdVerifier implements Consumer<IuPrincipalIdentity> {

		private final boolean authoritative;
		private final String issuer;
		private final PKIXParameters pkix;

		private IdVerifier(boolean authoritative, String issuer, PKIXParameters pkix) {
			this.authoritative = authoritative;
			this.issuer = issuer;
			this.pkix = pkix;
		}

		@Override
		public void accept(IuPrincipalIdentity principalIdentity) {
			final var pki = (PkiPrincipal) principalIdentity;
			IuException.unchecked(() -> CertPathValidator.getInstance("PKIX").validate(pki.getCertPath(), pkix));

			if (authoritative) {
				IuObject.once(issuer, pki.getName(), "principal name mismatch");
				IuObject.require(pki.getSubject().getPrivateCredentials(WebKey.class).iterator(), Iterator::hasNext,
						() -> "missing private key");
			}
		}

	}

	@Override
	public void trust(CertPathParameters validatorParams) {
		final var pkix = (PKIXParameters) validatorParams;
		final var issuer = Objects.requireNonNull(
				X500Utils.getCommonName(
						pkix.getTrustAnchors().iterator().next().getTrustedCert().getSubjectX500Principal()),
				"first trust anchor's root certificate must name authentication realm");

		synchronized (TRUST) {
			if (TRUST.containsKey(issuer))
				throw new IllegalArgumentException("another issuer is already configured for " + issuer);

			PrincipalVerifierRegistry.registerVerifier(issuer, new IdVerifier(false, issuer, pkix), false);
			TRUST.put(issuer, validatorParams);
		}
	}

}
