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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.cert.CertPath;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import edu.iu.IuException;
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
	$ openssl req -x509 -key /tmp/k -addext basicConstraints=pathlen:-1 \
	-addext keyUsage=keyCertSign,cRLSign -days 821
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

	/**
	 * <p>
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 * 
	 * <pre>
	$ openssl req -x509 -key /tmp/k -addext basicConstraints=critical,CA:true,pathlen:0 \
	 -addext keyUsage=keyCertSign,cRLSign -days 821 | tee /tmp/ca
	 * </pre>
	 */
	private static final String CA_ROOT = "-----BEGIN CERTIFICATE-----\n" //
			+ "MIICpDCCAiSgAwIBAgIUTA+Skb7j/4Km5D/xt7kssBM/Kk8wBQYDK2VxMIGYMQsw\n" //
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n" //
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n" //
			+ "MTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRl\n" //
			+ "c3RfQ0EwIBcNMjQwNDE2MDk0ODM2WhgPMjEyNDA0MTcwOTQ4MzZaMIGYMQswCQYD\n" //
			+ "VQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24x\n" //
			+ "GzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMw\n" //
			+ "MQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRlc3Rf\n" //
			+ "Q0EwQzAFBgMrZXEDOgBCIEbu84CEkUNQzWVKhH2TVetBFR+4a7rT4XFXAq6WGigV\n" //
			+ "1LHP199mTt06kwbJsAdaIrpwEoAzQ4CjYzBhMB0GA1UdDgQWBBRqDb4aD1ROQ5uP\n" //
			+ "S/LG7njvEu/f+DAfBgNVHSMEGDAWgBRqDb4aD1ROQ5uPS/LG7njvEu/f+DASBgNV\n" //
			+ "HRMBAf8ECDAGAQH/AgEAMAsGA1UdDwQEAwIBBjAFBgMrZXEDcwAxIP6HDFL5cxNO\n" //
			+ "PqH0L1Vkk6xbqjmK1hGr79W6OCvfjlcaKhvC4ivnQzxJQV6CHCfGVlkix3m084Ce\n" //
			+ "p6NSHLht5UOW+CeNzF4B8I6y3EJjxzUc/PvLy4Q5VRwJ64Aol1lttLgmIyr1Ww2w\n" //
			+ "UvHVFEgfEQA=\n" //
			+ "-----END CERTIFICATE-----\n";

	/**
	 * <p>
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 * 
	 * <pre>
	$ touch /tmp/crl_index
	$ cat /tmp/cnf
	[ ca ]
	default_ca = a
	
	[ a ]
	database = /tmp/crl_index
	
	$ openssl ca -gencrl -keyfile /tmp/k -cert /tmp/ca -config /tmp/cnf -crldays 36525
	 * </pre>
	 */
	private static final String CA_ROOT_CRL = "-----BEGIN X509 CRL-----\n" //
			+ "MIIBQTCBwjAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5h\n" //
			+ "MRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJz\n" //
			+ "aXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWph\n" //
			+ "dmEtYXV0aC1wa2kjUGtpU3BpVGVzdF9DQRcNMjQwNDE2MTA0NTM5WhgPMjEyNDA0\n" //
			+ "MTcxMDQ1MzlaMAUGAytlcQNzAC1rkeM6SUWX0un6apmCNwisvs6Hxsy0e4K6D7ou\n" //
			+ "+AXr0kWbeWzdisGRs7Zy0RUY0WXu5KiZ7kbwABDkGfOn0NFdnbA02hu5/V6xvfOa\n" //
			+ "jeDhXM+cmPQ/VFMuJf2tOy+n4TC+DvRMJg5bd8xqgU8Vm04lAA==\n" //
			+ "-----END X509 CRL-----\n";

	/**
	 * <p>
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 * 
	 * <pre>
	$ openssl req -new -out /tmp/req -key /tmp/k
	$ openssl req -x509 -in /tmp/req -addext basicConstraints=CA:false \
		-addext keyUsage=digitalSignature,keyAgreement -CA /tmp/ca -CAkey /tmp/k -days
	 * </pre>
	 */
	private static final String CA_SIGNED = "-----BEGIN CERTIFICATE-----\n" //
			+ "MIICmzCCAhugAwIBAgIUPYscr3NNwWMvs+DxyNZrN472cvUwBQYDK2VxMIGYMQsw\n" //
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n" //
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n" //
			+ "MTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRl\n" //
			+ "c3RfQ0EwIBcNMjQwNDE2MTAwNDA0WhgPMjEyNDA0MTcxMDA0MDRaMIGYMQswCQYD\n" //
			+ "VQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24x\n" //
			+ "GzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMw\n" //
			+ "MQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRlc3Rf\n" //
			+ "RUUwQzAFBgMrZXEDOgBCIEbu84CEkUNQzWVKhH2TVetBFR+4a7rT4XFXAq6WGigV\n" //
			+ "1LHP199mTt06kwbJsAdaIrpwEoAzQ4CjWjBYMB0GA1UdDgQWBBRqDb4aD1ROQ5uP\n" //
			+ "S/LG7njvEu/f+DAfBgNVHSMEGDAWgBRqDb4aD1ROQ5uPS/LG7njvEu/f+DAJBgNV\n" //
			+ "HRMEAjAAMAsGA1UdDwQEAwIDiDAFBgMrZXEDcwBX2foR72+bPqirhp/XsG8piwC8\n" //
			+ "HR3PGzh+tOXoLnjVuRARtb6OdO9Mz8NdjMKwseA+xB+YYl0DsIDVqq5IdtEIfgz9\n" //
			+ "Y98CSqcpVOI9Wpmp9bpnrX0+fvlXVw4SWCklCl7FOTSuVeMlmIoyP9otyvaMFQA=\n" //
			+ "-----END CERTIFICATE-----\n" + "";

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
	public void testSelfSignedEE() throws Exception {
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
		assertSerializable(pki);
	}

	@Test
	public void testPublicCA() throws Exception {
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
		assertEquals("iu.edu", iuEduId.getName());

		IuPrincipalIdentity.verify(iuEduId, "USERTrust RSA Certification Authority");

		assertSerializable(iuEduId);
	}

	@Test
	public void testPrivateCA() throws Exception {
		assertEquals("ID certificate must be an end-entity",
				assertThrows(IllegalArgumentException.class, () -> IuPkiPrincipal.from(SELF_SIGNED_PK + CA_ROOT))
						.getMessage());
		assertEquals("Issuer is not registered as trusted",
				assertThrows(NullPointerException.class, () -> IuPkiPrincipal.from(SELF_SIGNED_PK + CA_SIGNED))
						.getMessage());

		final var anchor = new TrustAnchor(PemEncoded.parse(CA_ROOT).next().asCertificate(), null);
		final var pkix = new PKIXParameters(Set.of(anchor));
		pkix.addCertStore(CertStore.getInstance("Collection",
				new CollectionCertStoreParameters(Set.of(PemEncoded.parse(CA_ROOT_CRL).next().asCRL()))));
		IuPkiPrincipal.trust(pkix);

		final var pki = IuPkiPrincipal.from(SELF_SIGNED_PK + CA_SIGNED);
		assertSerializable(pki);
		System.out.println(pki);
	}

	private void assertSerializable(IuPkiPrincipal pki) {
		final var auth = !pki.getSubject().getPrivateCredentials().isEmpty();
		final var pkis = pki.toString();
		if (auth)
			assertTrue(pkis.startsWith("Authoritative "), pkis);
		else
			assertTrue(pkis.startsWith("Well-Known "), pkis);

		final var serialCopy = IuException.unchecked(() -> {
			final var out = new ByteArrayOutputStream();
			try (final var o = new ObjectOutputStream(out)) {
				o.writeObject(pki);
			}
			try (final var o = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()))) {
				return (IuPrincipalIdentity) o.readObject();
			}
		});

		final var wks = serialCopy.toString();
		assertTrue(wks.startsWith("Well-Known "), wks);
		if (auth)
			assertEquals(pkis.substring(14), wks.substring(11));
		else
			assertEquals(pkis, wks);

		final var jwk = IuPkiPrincipal
				.from(pki.getSubject().getPublicCredentials(WebKey.class).iterator().next().toString());
		assertEquals(wks, jwk.toString());
	}

}
