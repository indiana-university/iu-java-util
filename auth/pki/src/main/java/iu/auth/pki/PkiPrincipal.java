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

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.WebCertificateReference;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * PKI principal identity implementation class.
 */
final class PkiPrincipal implements IuPrincipalIdentity, IuPrivateKeyPrincipal {

	/**
	 * JSON Type Adapter.
	 */
	static final IuJsonAdapter<PkiPrincipal> JSON = IuJsonAdapter.from(PkiPrincipal::new, PkiPrincipal::toJson);

	private final Algorithm alg;
	private final Algorithm encryptAlg;
	private final Encryption enc;
	private final WebKey verify;
	private final WebKey encrypt;
	private final String name;
	private final Instant issuedAt;
	private final Instant authTime;
	private final Instant expires;

	/**
	 * Constructor.
	 * 
	 * @param metadata Authentication realm metadata with original algorithm and
	 *                 certificate values
	 * @param verify   Fully populated JWK from {@link PkiFactory}
	 * @param encrypt  Fully populated JWK from {@link PkiFactory}
	 */
	PkiPrincipal(IuPrivateKeyPrincipal metadata, WebKey verify, WebKey encrypt) {
		final var originalJwk = metadata.getJwk();
		final var privateKey = originalJwk.getPrivateKey();
		final var certificateChain = Objects.requireNonNull( //
				WebCertificateReference.verify(originalJwk), "original missing certificate chain");
		final Predicate<WebKey> keyMatch = //
				jwk -> IuObject.equals(privateKey, jwk.getPrivateKey())
						&& Arrays.equals(certificateChain, Objects.requireNonNull( //
								jwk.getCertificateChain(), "missing certificate chain"));

		IuObject.require(verify, keyMatch, () -> "verify key mismatch");
		IuObject.require(encrypt, keyMatch, () -> "encrypt key mismatch");

		this.verify = verify;
		this.encrypt = encrypt;
		alg = metadata.getAlg();
		encryptAlg = metadata.getEncryptAlg();
		enc = metadata.getEnc();

		final var cert = validateArguments();

		name = X500Utils.getCommonName(cert.getSubjectX500Principal());
		issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		authTime = cert.getNotBefore().toInstant();
		expires = cert.getNotAfter().toInstant();
	}

	private PkiPrincipal(JsonValue value) {
		final var claims = value.asJsonObject();
		verify = IuJson.get(claims, "verify", WebKey.JSON);
		encrypt = IuJson.get(claims, "encrypt", WebKey.JSON);
		alg = IuJson.get(claims, "alg", Algorithm.JSON);
		encryptAlg = IuJson.get(claims, "encrypt_alg", Algorithm.JSON);
		enc = IuJson.get(claims, "enc", Encryption.JSON);

		final var cert = validateArguments();
		IuObject.once(claims.getString("iss"), X500Utils.getCommonName(cert.getIssuerX500Principal()));
		name = X500Utils.getCommonName(cert.getSubjectX500Principal());
		IuObject.once(claims.getString("sub"), name);
		IuObject.once(claims.getString("aud"), name);

		issuedAt = Instant.ofEpochSecond(claims.getJsonNumber("iat").longValue());
		expires = Instant.ofEpochSecond(claims.getJsonNumber("exp").longValue());
		authTime = Instant.ofEpochSecond(claims.getJsonNumber("auth_time").longValue());
	}

	private X509Certificate validateArguments() {
		X509Certificate cert = null;

		if (verify != null) {
			Objects.requireNonNull(alg, "alg");

			if (!Use.SIGN.equals(alg.use))
				throw new IllegalArgumentException("Invalid verify algorithm " + alg);
			if (!Set.of(alg.type).contains(verify.getType()))
				throw new IllegalArgumentException("Invalid key type " + verify.getType() + " for algorithm " + alg);

			cert = Objects.requireNonNull(
					Objects.requireNonNull(verify.getCertificateChain(), "verify key missing certificate chain")[0],
					"verify key missing certificate");
		} else {
			IuObject.require(alg, Objects::isNull, () -> "alg");
		}

		if (encrypt != null) {
			Objects.requireNonNull(encryptAlg, "encrypt_alg");
			Objects.requireNonNull(enc, "enc");

			if (!Use.ENCRYPT.equals(encryptAlg.use))
				throw new IllegalArgumentException("Invalid encrypt algorithm " + encryptAlg);
			if (!Set.of(encryptAlg.type).contains(encrypt.getType()))
				throw new IllegalArgumentException(
						"Invalid key type " + encrypt.getType() + " for algorithm " + encryptAlg);

			cert = IuObject.once(cert,
					Objects.requireNonNull(Objects.requireNonNull(encrypt.getCertificateChain(),
							"encrypt key missing certificate chain")[0], "encrypt key missing certificate"),
					() -> "certificate mismatch");
		}

		return Objects.requireNonNull(cert, "missing certificate");
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Algorithm getAlg() {
		return alg;
	}

	@Override
	public Algorithm getEncryptAlg() {
		return encryptAlg;
	}

	@Override
	public Encryption getEnc() {
		return enc;
	}

	@Override
	public WebKey getJwk() {
		return verify;
	}

	@Override
	public WebKey getEncryptJwk() {
		return encrypt;
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

		if (verify != null) {
			if (verify.getPrivateKey() != null)
				priv.add(verify);
			pub.add(verify.wellKnown());
		}

		if (encrypt != null) {
			if (encrypt.getPrivateKey() != null)
				priv.add(encrypt);
			pub.add(encrypt.wellKnown());
		}

		subject.setReadOnly();
		return subject;
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder();
		final var key = key();
		if (key == null //
				|| key.getPrivateKey() == null)
			sb.append("Well-Known");
		else
			sb.append("Authoritative");
		sb.append(" PKI Principal ").append(getName());

		final var certChain = wellKnown().getCertificateChain();
		if (certChain.length == 1
				&& certChain[0].getSubjectX500Principal().equals(certChain[0].getIssuerX500Principal()))
			sb.append(", Self-Issued");
		else
			sb.append(", Issued by ")
					.append(X500Utils.getCommonName(certChain[certChain.length - 1].getIssuerX500Principal()));

		return sb.toString();
	}

	/**
	 * Gets {@link #verify} if non-null; else gets {@link #encrypt}
	 * 
	 * @return {@link WebKey}
	 */
	WebKey key() {
		return Objects.requireNonNullElse(verify, encrypt);
	}

	private WebKey wellKnown() {
		return key().wellKnown();
	}

	private JsonObject toJson() {
		final var cert = wellKnown().getCertificateChain()[0];
		final var builder = IuJson.object() //
				.add("iss", X500Utils.getCommonName(cert.getIssuerX500Principal())) //
				.add("sub", name) //
				.add("aud", name) //
				.add("iat", issuedAt.getEpochSecond()) //
				.add("exp", expires.getEpochSecond()) //
				.add("auth_time", authTime.getEpochSecond());
		IuJson.add(builder, "alg", () -> alg, Algorithm.JSON);
		IuJson.add(builder, "encrypt_alg", () -> encryptAlg, Algorithm.JSON);
		IuJson.add(builder, "enc", () -> enc, Encryption.JSON);
		IuJson.add(builder, "verify", () -> verify, WebKey.JSON);
		IuJson.add(builder, "encrypt", () -> encrypt, WebKey.JSON);
		return builder.build();
	}

}
