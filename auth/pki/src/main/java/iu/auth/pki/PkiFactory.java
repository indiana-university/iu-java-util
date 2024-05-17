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
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Use;
import iu.auth.principal.PrincipalVerifier;

/**
 * Provides {@link PkiPrincipal} and {@link PkiVerifier} instances.
 */
public class PkiFactory {
	static {
		IuObject.assertNotOpen(PkiFactory.class);
	}

	/**
	 * Creates a PKI principal.
	 * 
	 * <p>
	 * <em>Must</em> include:
	 * </p>
	 * <ul>
	 * <li>An {@link X509Certificate} with
	 * <ul>
	 * <li>{@link X509Certificate#getBasicConstraints() V3 basic constraints}</li>
	 * <li>{@link X509Certificate#getKeyUsage() Key usage} describing at least one
	 * compatible scenario</li>
	 * <li>X500 subject with an RDN containing one of
	 * <ul>
	 * <li>CN attribute containing a system principal URI with fragment
	 * naming/matching the JWK "kid" parameter</li>
	 * <li>CN attribute containing a system principal URI, with no fragment,
	 * matching the JWK "kid" parameter</li>
	 * <li>UID attribute with user principal name, <em>optionally</em> qualified
	 * with DC attribute values</li>
	 * </ul>
	 * </ul>
	 * <li>A private key matching the certificate's public key if the cert will be
	 * used to sign or decrypt data; <em>not required</em> to verify or encrypt</li>
	 * <li>Additional certificates as needed to form a chain to a trusted signing
	 * certificate.</li>
	 * </ul>
	 * 
	 * @param partialKey {@link WebKey} with the elements listed above
	 * @return {@link IuPrincipalIdentity}
	 */
	public static IuPrincipalIdentity from(final WebKey partialKey) {
		final var privateKey = partialKey.getPrivateKey();

		final var certChain = Objects.requireNonNull(WebCertificateReference.verify(partialKey),
				"Missing X.509 certificate chain");
		final CertPath certPath = IuException
				.unchecked(() -> CertificateFactory.getInstance("X.509").generateCertPath(List.of(certChain)));

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
		final WebKey.Builder<?> jwkBuilder = WebKey.builder(publicKey);

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

		jwkBuilder.cert(certPath.getCertificates().toArray(X509Certificate[]::new));
		IuObject.convert(partialKey.getCertificateUri(), jwkBuilder::cert);
		IuObject.convert(partialKey.getCertificateThumbprint(), jwkBuilder::x5t);
		IuObject.convert(partialKey.getCertificateSha256Thumbprint(), jwkBuilder::x5t256);
		IuObject.convert(partialKey.getUse(), jwkBuilder::use);
		IuObject.convert(partialKey.getOps(), a -> jwkBuilder.ops(a.toArray(Operation[]::new)));
		IuObject.convert(partialKey.getKey(), jwkBuilder::key);

		IuException.unchecked(() -> {
			final var encoded = idCert.getEncoded();
			jwkBuilder.x5t(MessageDigest.getInstance("SHA-1").digest(encoded));
			jwkBuilder.x5t256(MessageDigest.getInstance("SHA-256").digest(encoded));
		});

		return new PkiPrincipal(jwkBuilder.build());
	}

	/**
	 * Creates a verifier for a single PKI principal identity.
	 * 
	 * @param trustedPrincipal PKI principal to trust
	 * @return {@link PrincipalVerifier}
	 */
	public static PrincipalVerifier<?> trust(IuPrincipalIdentity trustedPrincipal) {
		final var subject = trustedPrincipal.getSubject();
		final var privateKeys = subject.getPrivateCredentials(WebKey.class);
		final WebKey key;
		if (privateKeys.isEmpty())
			key = subject.getPublicCredentials(WebKey.class).iterator().next();
		else
			key = privateKeys.iterator().next();

		final var cert = WebCertificateReference.verify(key)[0];
		final var anchor = new TrustAnchor(cert, null);
		final var pkix = IuException.unchecked(() -> new PKIXParameters(Set.of(anchor)));
		pkix.setRevocationEnabled(false);

		return trust(key.getPrivateKey(), pkix);
	}

	/**
	 * Creates a principal verifier for trusting
	 * 
	 * @param privateKey <em>must</em> match the public key of the trusted cert
	 *                   attached to the first trust anchor if non-null;
	 *                   <em>may</em> be null for non-authoritative signature
	 *                   verification and message encryption use only
	 * @param pkix       {@link PKIXParameters} with at least one trust anchor
	 * @return {@link PrincipalVerifier}
	 */
	public static PrincipalVerifier<?> trust(PrivateKey privateKey, PKIXParameters pkix) {
		final var issuerCertificate = pkix.getTrustAnchors().iterator().next().getTrustedCert();
		final var publicKey = issuerCertificate.getPublicKey();
		final var issuer = Objects.requireNonNull(X500Utils.getCommonName(issuerCertificate.getSubjectX500Principal()),
				"First trust anchor's root certificate must name authentication realm");

		if (privateKey != null) // validates keys match
			WebKey.builder(publicKey).key(privateKey).build();

		return new PkiVerifier(privateKey != null, issuer, pkix);
	}

	private PkiFactory() {
	}

}
