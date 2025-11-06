/*
 * Copyright © 2025 Indiana University
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.auth.config.X500Utils;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;

/**
 * PKI principal identity implementation class.
 */
public final class PkiPrincipal implements IuPrincipalIdentity {
	static {
		IuObject.assertNotOpen(PkiPrincipal.class);
	}

	private final Algorithm alg;
	private final Algorithm encryptAlg;
	private final Encryption enc;
	private final WebKey verify;
	private final WebKey encrypt;
	private final String name;
	private final String issuer;
	private final Instant issuedAt;
	private final Instant authTime;
	private final Instant expires;

	/**
	 * Constructor.
	 * 
	 * @param pkp Authentication realm metadata with original algorithm and
	 *            certificate values
	 */
	public PkiPrincipal(IuPrivateKeyPrincipal pkp) {
		final var jwk = pkp.getJwk();
		final var keyType = jwk.getType();
		final var privateKey = jwk.getPrivateKey();
		final var certificateChain = Objects.requireNonNull( //
				WebCertificateReference.verify(jwk), "missing certificate chain");

		final var cert = certificateChain[0];
		name = X500Utils.getCommonName(cert.getSubjectX500Principal());
		if (!name.equals(jwk.getKeyId()))
			throw new IllegalArgumentException("Key ID doesn't match CN");

		final var keyUsage = new KeyUsage(cert);

		alg = Objects.requireNonNull(pkp.getAlg(), "Missing digital signature algorithm");
		if (!keyUsage.matches(Use.SIGN))
			throw new IllegalArgumentException("X.509 certificate doesn't allow digital signature");
		if (!Set.of(alg.type).contains(keyType))
			throw new IllegalArgumentException("Invalid key type " + keyType + " for algorithm " + alg);

		final var verifyBuilder = WebKey.builder(keyType) //
				.algorithm(alg) //
				.keyId("verify") //
				.key(cert.getPublicKey()) //
				.cert(certificateChain);
		if (privateKey != null)
			verifyBuilder.key(privateKey);
		verifyBuilder.ops(keyUsage.ops(true, privateKey != null));
		verify = verifyBuilder.build();

		encryptAlg = pkp.getEncryptAlg();
		if (encryptAlg != null) {
			if (!keyUsage.matches(Use.ENCRYPT))
				throw new IllegalArgumentException("X.509 certificate doesn't allow encryption");
			if (!Set.of(encryptAlg.type).contains(keyType))
				throw new IllegalArgumentException("Invalid key type " + keyType + " for algorithm " + encryptAlg);
			enc = Objects.requireNonNull(pkp.getEnc(), "Missing content encryption algorithm");

			final var encryptBuilder = WebKey.builder(keyType) //
					.algorithm(encryptAlg) //
					.keyId("encrypt") //
					.key(cert.getPublicKey()) //
					.cert(certificateChain);
			if (privateKey != null)
				encryptBuilder.key(privateKey);
			encryptBuilder.ops(keyUsage.ops(false, privateKey != null));
			encrypt = encryptBuilder.build();
		} else {
			enc = null;
			encrypt = null;
		}

		issuer = X500Utils.getCommonName(cert.getIssuerX500Principal());
		issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		authTime = cert.getNotBefore().toInstant();
		expires = cert.getNotAfter().toInstant();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getIssuer() {
		return issuer;
	}

	@Override
	public Instant getIssuedAt() {
		return issuedAt;
	}

	@Override
	public Instant getAuthTime() {
		return authTime;
	}

	@Override
	public Instant getExpires() {
		return expires;
	}

	@Override
	public Subject getSubject() {
		final var subject = new Subject();
		subject.getPrincipals().add(this);

		final var priv = subject.getPrivateCredentials();
		final var pub = subject.getPublicCredentials();

		if (verify.getPrivateKey() != null)
			priv.add(verify);
		pub.add(verify.wellKnown());

		if (encrypt != null) {
			if (encrypt.getPrivateKey() != null)
				priv.add(encrypt);
			pub.add(encrypt.wellKnown());
		}

		subject.setReadOnly();
		return subject;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(alg, enc, encrypt, encryptAlg, verify);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		PkiPrincipal other = (PkiPrincipal) obj;
		return alg == other.alg //
				&& enc == other.enc //
				&& encryptAlg == other.encryptAlg //
				&& IuObject.equals(encrypt, other.encrypt) //
				&& IuObject.equals(verify, other.verify);
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder();
		if (verify.getPrivateKey() == null)
			sb.append("Well-Known");
		else
			sb.append("Authoritative");
		sb.append(" PKI Principal ").append(getName());

		final var certChain = verify.getCertificateChain();
		final var issuer = certChain[certChain.length - 1].getIssuerX500Principal();
		final var subject = certChain[0].getSubjectX500Principal();
		if (subject.equals(issuer))
			sb.append(", Self-Issued");
		else
			sb.append(", Issued by ").append(X500Utils.getCommonName(issuer));

		return sb.toString();
	}

}
