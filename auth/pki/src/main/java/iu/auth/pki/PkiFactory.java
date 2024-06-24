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

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CRL;
import java.security.cert.CertPath;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
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

	private static class KeyUsage {
		private final boolean digitalSignature;
		private final boolean nonRepudiation;
		private final boolean keyEncipherment;
		private final boolean keyAgreement;
		private final boolean keyCertSign;
		private final boolean cRLSign;

		private KeyUsage(X509Certificate idCert) {
			final var keyUsage = idCert.getKeyUsage();
			digitalSignature = keyUsage[0];
			nonRepudiation = keyUsage[1];
			keyEncipherment = keyUsage[2];
			keyAgreement = keyUsage[4];
			keyCertSign = keyUsage[5];
			cRLSign = keyUsage[6];
		}

		private boolean matches(Use use) {
			if (use == null)
				return true;
			else
				switch (use) {
				case ENCRYPT:
					return keyAgreement || keyEncipherment;

				case SIGN:
				default:
					return digitalSignature || nonRepudiation || keyCertSign || cRLSign;
				}
		}

		private boolean matches(Set<Operation> ops) {
			if (ops != null)
				for (final var op : ops)
					switch (op) {
					case DERIVE_BITS:
					case DECRYPT:
					case ENCRYPT:
						return false;

					case DERIVE_KEY:
						if (!keyAgreement)
							return false;
						else
							continue;

					case UNWRAP:
					case WRAP:
						if (!keyEncipherment)
							return false;
						else
							continue;

					case SIGN:
						if (!digitalSignature //
								&& !nonRepudiation)
							return false;
						else
							continue;

					case VERIFY:
					default:
						if (!digitalSignature //
								&& !nonRepudiation //
								&& !keyCertSign //
								&& !cRLSign)
							return false;
					}
			return true;
		}

		private Operation[] ops(boolean verify, boolean hasPrivateKey) {
			final Queue<Operation> ops = new ArrayDeque<>();
			if (verify) {
				if (digitalSignature || nonRepudiation) {
					ops.add(Operation.VERIFY);
					if (hasPrivateKey)
						ops.add(Operation.SIGN);
				} else if (keyCertSign || cRLSign)
					ops.add(Operation.VERIFY);
			}

			else { // encrypt
				if (keyEncipherment) {
					ops.add(Operation.WRAP);
					if (hasPrivateKey)
						ops.add(Operation.UNWRAP);
				}
				if (keyAgreement)
					ops.add(Operation.DERIVE_KEY);
			}
			return ops.toArray(Operation[]::new);
		}
	}

	private static class PublicKeyParameters {
		private final boolean ca;
		private final PrivateKey privateKey;
		private final PublicKey publicKey;
		private final X509Certificate idCert;
		private final CertPath certPath;
		private final Operation[] encrypt;
		private final Operation[] verify;

		private PublicKeyParameters(WebKey partialKey) {
			privateKey = partialKey.getPrivateKey();
			certPath = IuException.unchecked(() -> CertificateFactory.getInstance("X.509")
					.generateCertPath(List.of(Objects.requireNonNull(WebCertificateReference.verify(partialKey),
							"Missing X.509 certificate chain"))));

			// extract principal identifying certificate and extended parameters
			final var certList = certPath.getCertificates();
			idCert = (X509Certificate) certList.get(0);
			ca = idCert.getBasicConstraints() >= 0;
			final var keyUsage = new KeyUsage(idCert);

			// extract and verify matching public key
			publicKey = IuObject.once(partialKey.getPublicKey(), idCert.getPublicKey());

			// verify usage restrictions
			if (!keyUsage.matches(partialKey.getUse()) //
					|| !keyUsage.matches(partialKey.getOps()))
				throw new IllegalArgumentException("Invalid key usage for identifying cert");

			encrypt = keyUsage.ops(false, privateKey != null);
			verify = keyUsage.ops(true, privateKey != null);
		}

		private WebKey create(boolean verify) {
			final var ops = verify ? this.verify : encrypt;
			if (ops.length == 0)
				return null;

			final var builder = WebKey.builder(publicKey);
			IuObject.convert(privateKey, builder::key);
			builder.cert(certPath.getCertificates().toArray(X509Certificate[]::new));
			builder.keyId(verify ? "verify" : "encrypt");
			builder.ops(ops);
			return builder.build();
		}
	}

	/**
	 * Creates a PKI principal.
	 * 
	 * <p>
	 * Partial key <em>must</em> include:
	 * </p>
	 * <ul>
	 * <li>An {@link X509Certificate}, direct or by reference, with
	 * <ul>
	 * <li>{@link X509Certificate#getBasicConstraints() V3 basic constraints}</li>
	 * <li>{@link X509Certificate#getKeyUsage() Key usage} describing at least one
	 * compatible scenario</li>
	 * <li>X500 subject with an RDN containing one of
	 * <ul>
	 * <li>CN attribute containing a system principal URI</li>
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
	public static IuPrincipalIdentity from(final IuPrivateKeyPrincipal partialKey) {
		final var pkp = new PublicKeyParameters(partialKey.getJwk());

		// verify end-entity certificate
		if (pkp.ca)
			throw new IllegalArgumentException("ID certificate must be an end-entity");

		return new PkiPrincipal(partialKey, pkp.create(true), pkp.create(false));
	}

	/**
	 * Creates a PKI principal verifier.
	 * 
	 * @param partialKey {@link WebKey} with certificate chain; private key will be
	 *                   ignored for CA certificates; if provided with a self-signed
	 *                   EE certificate, the verifier will be authoritative.
	 * @param crl        Certificate revocation lists; empty if partial key is a
	 *                   self-signed end-entity certificate
	 * @return {@link PrincipalVerifier}
	 */
	public static PrincipalVerifier<?> trust(final IuPrivateKeyPrincipal partialKey, final CRL... crl) {
		final var pkp = new PublicKeyParameters(partialKey.getJwk());
		final PkiPrincipal identity;
		final var anchor = new TrustAnchor(pkp.idCert, null);
		final var pkix = IuException.unchecked(() -> new PKIXParameters(Set.of(anchor)));
		
		if (!pkp.ca && // disable revocation for self-signed end-entity certs
				pkp.idCert.getIssuerX500Principal().equals(pkp.idCert.getSubjectX500Principal())) {
			identity = new PkiPrincipal(partialKey, pkp.create(true), pkp.create(false));
			pkix.setRevocationEnabled(false);
		} else if (crl.length <= 0)
			throw new IllegalArgumentException("At least one revocation list required for CA-signed certificates");
		else {
			final var verify = IuObject.convert(pkp.create(true), WebKey::wellKnown);
			final var encrypt = IuObject.convert(pkp.create(false), WebKey::wellKnown);
			identity = new PkiPrincipal(partialKey, verify, encrypt);
			pkix.addCertStore(IuException.unchecked(
					() -> CertStore.getInstance("Collection", new CollectionCertStoreParameters(Set.of(crl)))));
		}

		return new PkiVerifier(identity, pkix);
	}

	private PkiFactory() {
	}

}
