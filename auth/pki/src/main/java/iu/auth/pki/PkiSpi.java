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
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.auth.spi.IuPkiSpi;
import edu.iu.crypt.DigestUtils;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;
import iu.auth.principal.PrincipalVerifierRegistry;

/**
 * {@link IuPkiSpi} service provider implementation.
 */
public class PkiSpi implements IuPkiSpi {
	static {
		IuObject.assertNotOpen(PkiSpi.class);
	}

	private static final Map<String, PKIXParameters> TRUST = new HashMap<>();

	/**
	 * Gets PKIX certificate path validator parameters for an authentication realm.
	 * 
	 * @param realm authentication realm
	 * @return {@link PKIXParameters}
	 */
	static PKIXParameters getPKIXParameters(String realm) {
		return Objects.requireNonNull(TRUST.get(realm));
	}

	/**
	 * Default constructor.
	 */
	public PkiSpi() {
	}

	@Override
	public PkiPrincipal readPkiPrincipal(String serialized) {
		final PrivateKey privateKey;
		final CertPath certPath;
		final WebKey partialKey;
		if (serialized.startsWith("{")) { // JWK
			partialKey = WebKey.parse(serialized);
			privateKey = partialKey.getPrivateKey();

			final var certChain = Objects.requireNonNull(WebCertificateReference.verify(partialKey),
					"Missing X.509 certificate chain");
			certPath = IuException
					.unchecked(() -> CertificateFactory.getInstance("X.509").generateCertPath(List.of(certChain)));

		} else if (serialized.startsWith("-----BEGIN ")) { // PEM
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
			privateKey = IuObject.convert(encodedPrivateKey,
					a -> a.asPrivate(certChain.get(0).getPublicKey().getAlgorithm()));

			certPath = IuException.unchecked(() -> CertificateFactory.getInstance("X.509").generateCertPath(certChain));
		} else
			throw new UnsupportedOperationException("only PEM and JWK encoded identity certs are supported");

		// extract principal identifying certificate
		final var certList = certPath.getCertificates();
		final var idCert = (X509Certificate) certList.get(0);

		// verify end-entity certificate
		final var pathLen = idCert.getBasicConstraints();
		if (pathLen != -1)
			throw new IllegalArgumentException("ID certificate must be an end-entity");

		// extract and verify matching public key
		final var publicKey = IuObject.once(IuObject.convert(partialKey, WebKey::getPublicKey), idCert.getPublicKey());

		// build principal JWK
		final WebKey.Builder<?> jwkBuilder;
		final var params = WebKey.algorithmParams(publicKey);
		if (params == null)
			jwkBuilder = WebKey.builder(Type.from(publicKey.getAlgorithm(), null));
		else
			jwkBuilder = WebKey.builder(Objects.requireNonNull(Type.from(params), params.toString()));

		// populate private and public keys
		IuObject.convert(privateKey, jwkBuilder::key);
		jwkBuilder.key(publicKey);

		// extract key ID from X.500 subject common name
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

		// extract public key use and key operations from X.509 extensions
		final var keyUsage = idCert.getKeyUsage();
		var use = IuObject.convert(partialKey, WebKey::getUse);
		if (!Use.ENCRYPT.equals(use))
			if (keyUsage[0]) { // digitalSignature
				jwkBuilder.use(use = Use.SIGN);
				if (privateKey == null)
					jwkBuilder.ops(Operation.VERIFY);
				else
					jwkBuilder.ops(Operation.VERIFY, Operation.SIGN);
			} else if (keyUsage[1]) { // nonRepudiation
				jwkBuilder.use(use = Use.SIGN);
				jwkBuilder.ops(Operation.VERIFY);
			}

		if (!Use.SIGN.equals(use)) {
			if (keyUsage[2]) { // keyEncipherment
				jwkBuilder.use(use = Use.ENCRYPT);
				if (privateKey == null)
					jwkBuilder.ops(Operation.WRAP);
				else
					jwkBuilder.ops(Operation.WRAP, Operation.UNWRAP);
			}
			if (keyUsage[3]) { // dataEncipherment
				jwkBuilder.use(use = Use.ENCRYPT);
				if (privateKey == null)
					jwkBuilder.ops(Operation.ENCRYPT);
				else
					jwkBuilder.ops(Operation.ENCRYPT, Operation.DECRYPT);
			}
			if (keyUsage[4]) // keyAgreement
				jwkBuilder.use(use = Use.ENCRYPT).ops(Operation.DERIVE_KEY);
		}

		// establish and verify trust of signing key
		final CertPathParameters trust;
		final List<Certificate> pathToAnchor = new ArrayList<>();
		synchronized (TRUST) {
			PKIXParameters matchedTrust = null;

			final var selfSigned = idCert.getSubjectX500Principal().equals(idCert.getIssuerX500Principal());
			if (selfSigned && privateKey != null) {
				pathToAnchor.add(idCert);
				matchedTrust = IuException.unchecked(() -> new PKIXParameters(Set.of(new TrustAnchor(idCert, null))));
				matchedTrust.setRevocationEnabled(false);
			} else
				for (final var cert : certList) {
					pathToAnchor.add(cert);

					final var caIssuerCert = (X509Certificate) cert;
					final var caTrust = IuObject.convert(X500Utils.getCommonName(caIssuerCert.getIssuerX500Principal()),
							TRUST::get);
					if (caTrust != null) {
						matchedTrust = caTrust;
						break;
					}
				}
			trust = Objects.requireNonNull(matchedTrust, "Issuer is not registered as trusted");

			if (privateKey != null) { // register authoritative trust for private key holder
				PrincipalVerifierRegistry.registerVerifier(new PkiVerifier(true, commonName));
				TRUST.put(commonName, matchedTrust);
			}
		}

		IuException.unchecked(() -> CertPathValidator.getInstance("PKIX")
				.validate(CertificateFactory.getInstance("X.509").generateCertPath(pathToAnchor), trust));

		jwkBuilder.cert(pathToAnchor.toArray(X509Certificate[]::new));
		if (partialKey != null) {
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

		return new PkiPrincipal(jwkBuilder.build());
	}

	@Override
	public void trust(CertPathParameters validatorParams) {
		final var pkix = (PKIXParameters) validatorParams;
		final var issuer = Objects.requireNonNull(
				X500Utils.getCommonName(
						pkix.getTrustAnchors().iterator().next().getTrustedCert().getSubjectX500Principal()),
				"First trust anchor's root certificate must name authentication realm");

		synchronized (TRUST) {
			if (TRUST.containsKey(issuer))
				throw new IllegalArgumentException("Another trust anchor is already configured for " + issuer);

			PrincipalVerifierRegistry.registerVerifier(new PkiVerifier(false, issuer));
			TRUST.put(issuer, pkix);
		}
	}

}
