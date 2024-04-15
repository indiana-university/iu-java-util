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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.pki.IuPkiPrincipal;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;

@SuppressWarnings("javadoc")
public class PkiSpiTest {

	/**
	 * <p>
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 * 
	 * <pre>
	$ openssl genpkey -algorithm ed448 | tee /tmp/k
	 * </pre>
	 */
	private static final String SELF_SIGNED_PK = "-----BEGIN PRIVATE KEY-----\n" //
			+ "MEcCAQAwBQYDK2VxBDsEOTJhHRjuRVDowCMKWslwironn8lKFWPw5ShatWk8vjgB\n" //
			+ "C4xaM8unbSd02KYIjhisyRIyQX++Ph2QOA==\n" //
			+ "-----END PRIVATE KEY-----\n";

	/**
	 * <p>
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 * 
	 * <pre>
	$ openssl req -x509 -key /tmp/k -addext basicConstraints=pathlen:-1 -days 821
	 * </pre>
	 */
	private static final String SELF_SIGNED_EE = "-----BEGIN CERTIFICATE-----\r\n" //
			+ "MIICmDCCAhigAwIBAgIULhCA4AFQAEpGt5LgLFk8XWjbevgwBQYDK2VxMIGVMQsw\r\n" //
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\r\n" //
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\r\n" //
			+ "MTAwLgYDVQQDDCd1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRl\r\n" //
			+ "c3QwIBcNMjQwNDE0MjMxMjM5WhgPMjEyNDA0MTUyMzEyMzlaMIGVMQswCQYDVQQG\r\n" //
			+ "EwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZ\r\n" //
			+ "BgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTAwLgYD\r\n" //
			+ "VQQDDCd1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRlc3QwQzAF\r\n" //
			+ "BgMrZXEDOgBCIEbu84CEkUNQzWVKhH2TVetBFR+4a7rT4XFXAq6WGigV1LHP199m\r\n" //
			+ "Tt06kwbJsAdaIrpwEoAzQ4CjXTBbMB0GA1UdDgQWBBRqDb4aD1ROQ5uPS/LG7njv\r\n" //
			+ "Eu/f+DAfBgNVHSMEGDAWgBRqDb4aD1ROQ5uPS/LG7njvEu/f+DAMBgNVHRMEBTAD\r\n" //
			+ "AgH/MAsGA1UdDwQEAwIHgDAFBgMrZXEDcwAp8GWXdQB9zGsXyFalPKXdDzxqccGY\r\n" //
			+ "UHigHjvIXAfDzoypRFJXmTBiotbJ719wbBsSijAaiXkMqoCUwY7PUTv9mtfTeXQg\r\n" //
			+ "0gXa+OUW+5/C0tFm4khOPNe50GFwrnlAdV+UCFSU3+ZHluXWSrVVWbHRJwA=\r\n" //
			+ "-----END CERTIFICATE-----\r\n";

	@Test
	public void testInvalidPkiPrincipal() {
		assertEquals("only PEM and JWK encoded identity certs are supported",
				assertThrows(UnsupportedOperationException.class, () -> IuPkiPrincipal.from("foobar")).getMessage());
		assertEquals("only PRIVATE KEY and CERTIFICATE allowed",
				assertThrows(IllegalArgumentException.class,
						() -> IuPkiPrincipal.from("-----BEGIN PUBLIC KEY-----\nF00BA4==\n-----END PUBLIC KEY-----\n"))
						.getMessage());
		assertEquals("at least one CERTIFICATE is required",
				assertThrows(IllegalArgumentException.class, () -> IuPkiPrincipal.from(SELF_SIGNED_PK)).getMessage());
	}

	@Test
	public void testSelfSignedEE() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
		final var pki = IuPkiPrincipal.from(SELF_SIGNED_PK + SELF_SIGNED_EE);
		final var cert = ((X509Certificate) pki.getCertPath().getCertificates().get(0));
		
		final var realm = IdGenerator.generateId();
		IuPkiPrincipal.trust(realm, CertStore.getInstance("Collection", new CollectionCertStoreParameters(Set.of(cert))));
		IuPrincipalIdentity.verify(pki, realm);
		
		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals(IuPkiPrincipal.class));
		assertEquals(Set.of(cert.getSubjectX500Principal()), sub.getPrincipals(X500Principal.class));
		assertEquals(Set.of(cert), sub.getPublicCredentials(X509Certificate.class));
		assertEquals(Set.of(pki.getCertPath()), sub.getPublicCredentials(CertPath.class));
		assertEquals(cert, sub.getPublicCredentials(WebKey.class).iterator().next().getCertificateChain()[0]);
		final var priv = sub.getPrivateCredentials(PrivateKey.class);
		assertEquals(priv, Set.of(sub.getPrivateCredentials(WebKey.class).iterator().next().getPrivateKey()));

		assertDoesNotThrow(() -> {
			final var params = new PKIXParameters(
					Set.of(new TrustAnchor(PemEncoded.parse(SELF_SIGNED_EE).next().asCertificate(), null)));
			params.setRevocationEnabled(false);
			CertPathValidator.getInstance("PKIX").validate(pki.getCertPath(), params);
		});

	}

}
