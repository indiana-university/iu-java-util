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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

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
		IuPrincipalIdentity.verify(pki, pki.getName());
		
		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());
		
		final var pub = sub.getPublicCredentials();
		assertEquals(1, pub.size());
		final var wellKnown = (WebKey) pub.iterator().next();
		final var cert = wellKnown.getCertificateChain()[0];
		assertEquals(pki.getName(), X500Utils.getCommonName(cert.getSubjectX500Principal()));

		assertNotNull(sub.getPrivateCredentials(WebKey.class).iterator().next().getPrivateKey());
	}

	@Test
	public void testPublicCA() throws InvalidAlgorithmParameterException {
		final CertPath iuEdu;
		final X509Certificate inCommon;
		final CertStore crl;
		try {
			final var http = HttpClient.newHttpClient();
			final var resp = http.send(HttpRequest.newBuilder(URI.create("https://www.iu.edu/index.html"))
					.method("HEAD", BodyPublishers.noBody()).build(), BodyHandlers.discarding());

			final var x509 = CertificateFactory.getInstance("X.509");
			iuEdu = x509.generateCertPath(List.of(resp.sslSession().get().getPeerCertificates()));
			inCommon = (X509Certificate) x509.generateCertificate(http.send(
					HttpRequest.newBuilder(URI.create("http://crt.usertrust.com/USERTrustRSAAddTrustCA.crt")).build(),
					BodyHandlers.ofInputStream()).body());

			crl = CertStore.getInstance("Collection", new CollectionCertStoreParameters(Set.of(
					x509.generateCRL(http.send(HttpRequest
							.newBuilder(URI.create("http://crl.incommon-rsa.org/InCommonRSAServerCA.crl")).build(),
							BodyHandlers.ofInputStream()).body()),
					x509.generateCRL(http.send(HttpRequest
							.newBuilder(URI.create("http://crl.usertrust.com/USERTrustRSACertificationAuthority.crl"))
							.build(), BodyHandlers.ofInputStream()).body()))));
		} catch (Throwable e) {
			e.printStackTrace();
			Assumptions.abort("unable to read public key data for verifying iu.edu " + e);
			return;
		}

		final var anchor = new TrustAnchor(inCommon, null);
		final var pkix = new PKIXParameters(Set.of(anchor));
		pkix.addCertStore(crl);
		IuPkiPrincipal.trust(pkix);

		final var iuEduPem = new StringBuilder();
		PemEncoded.serialize(iuEdu.getCertificates().toArray(X509Certificate[]::new))
				.forEachRemaining(iuEduPem::append);
		final var iuEduId = IuPkiPrincipal.from(iuEduPem.toString());

		System.out.println(iuEduId.getSubject());
	}

}
