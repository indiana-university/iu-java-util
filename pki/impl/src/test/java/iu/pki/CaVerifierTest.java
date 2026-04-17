/*
 * Copyright © 2026 Indiana University
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertPathValidatorException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.logging.Level;

import javax.security.auth.Subject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuProcess;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.config.IuCertificateAuthority;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.X500Utils;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class CaVerifierTest extends PkiTestCase {

	private String cakid;
	private String caCert;
	private Path databaseFile;
	private Path caConfig;
	private IuCertificateAuthority ca;

	@BeforeEach
	void setup() {
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		cakid = IdGenerator.generateId();
		final var caJwk = WebKey.builder(WebKey.Type.ED448).keyId(cakid).ephemeral().build();
		final var caPrivateKey = Objects.requireNonNull(caJwk.getPrivateKey(), "Missing private key");
		final var caPrivateKeyFile = IuProcess.temp(PemEncoded::print, caPrivateKey);
		caCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", caPrivateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + cakid.replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=critical,CA:true,pathlen:0", //
				"-addext", "keyUsage=keyCertSign,cRLSign" //
		);

		databaseFile = IuProcess.temp(PrintStream::print, "");
		final var newCertsDir = IuProcess.createTempDirectory();
		final var certificateFile = IuProcess.temp(PrintStream::println, caCert);
		var caConfigContents = "[ ca ]" + System.lineSeparator() //
				+ "default_ca = a" + System.lineSeparator() //
				+ System.lineSeparator() //
				+ "[ a ]" + System.lineSeparator() //
				+ "private_key = " + caPrivateKeyFile.toString().replace('\\', '/') + System.lineSeparator() //
				+ "certificate = " + certificateFile.toString().replace('\\', '/') + System.lineSeparator() //
				+ "database = " + databaseFile.toString().replace('\\', '/') + System.lineSeparator() //
				+ "new_certs_dir = " + newCertsDir.toString().replace('\\', '/') + System.lineSeparator() // //
				+ "copy_extensions = copyall" + System.lineSeparator() //
				+ "rand_serial = yes" + System.lineSeparator() //
				+ "policy = b" + System.lineSeparator() //
				+ System.lineSeparator() //
				+ "[ b ]" + System.lineSeparator() //
				+ "countryName = optional" + System.lineSeparator() //
				+ "stateOrProvinceName = optional" + System.lineSeparator() //
				+ "localityName = optional" + System.lineSeparator() //
				+ "organizationName = optional" + System.lineSeparator() //
				+ "organizationalUnitName = optional" + System.lineSeparator() //
				+ "commonName = supplied" + System.lineSeparator() //
				+ "emailAddress = optional" + System.lineSeparator();
		caConfig = IuProcess.temp(PrintStream::print, caConfigContents);

		ca = new IuCertificateAuthority() {
			@Override
			public Iterable<X509CRL> getCrl() {
				final var crl = PemEncoded.parse(IuProcess.exec( //
						"openssl", "ca", "-gencrl", "-config", caConfig.toString(), "-crldays", "1" //
				)).next().asCRL();
				return IuIterable.iter(crl);
			}

			@Override
			public X509Certificate getCertificate() {
				return PemEncoded.parse(caCert).next().asCertificate();
			}
		};
	}

	@AfterEach
	void teardown() {
		IuProcess.deleteTempFiles();
		IuException.unchecked(() -> {
			Files.deleteIfExists(Path.of(databaseFile + ".attr"));
			Files.deleteIfExists(Path.of(databaseFile + ".old"));
		});
	}

	@Test
	public void testSuccess() {
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var csr = IuProcess.exec( //
				"openssl", "req", "-new", "-key", privateKeyFile.toString(), //
				"-subj", "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);

		final var csrFile = IuProcess.temp(PrintStream::println, csr);
		final var caOut = IuProcess.exec( //
				"openssl", "ca", "-batch", "-in", csrFile.toString(), //
				"-config", caConfig.toString(), "-days", "1" //
		);

		final var pemCert = caOut.substring(caOut.indexOf("-----BEGIN"));
		final var signed = WebKey.builder(jwk.getType()) //
				.keyId(jwk.getKeyId()) //
				.key(privateKey) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert + System.lineSeparator() + caCert) //
				.build();

		final var verifier = new CaVerifier(ca);
		assertNull(verifier.getAuthScheme());
		assertNull(verifier.getAuthenticationEndpoint());
		assertSame(PkiPrincipal.class, verifier.getType());
		assertEquals(cakid, verifier.getRealm());
		assertEquals("CaVerifier [" + cakid + "]", verifier.toString());
		assertFalse(verifier.isAuthoritative());

		final var pki = new PkiPrincipal(signed);

		IuTestLogger.expect(CaVerifier.class.getName(), Level.INFO, "ca:verify:" + kid + "; trustAnchor: " + cakid);
		assertDoesNotThrow(() -> verifier.verify(pki));
	}

	@Test
	public void testConstructorInvalidCACert() {
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var cakid = IdGenerator.generateId();
		final var cajwk = WebKey.builder(WebKey.Type.ED448).keyId(cakid).ephemeral().build();
		final var caprivateKey = Objects.requireNonNull(cajwk.getPrivateKey(), "Missing private key");
		final var caprivateKeyFile = IuProcess.temp(PemEncoded::print, caprivateKey);
		final var caCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", caprivateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + cakid.replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=keyAgreement" //
		);

		final var e = assertThrows(IllegalArgumentException.class, () -> new CaVerifier(new IuCertificateAuthority() {
			@Override
			public X509Certificate getCertificate() {
				return PemEncoded.parse(caCert).next().asCertificate();
			}

			@Override
			public Iterable<X509CRL> getCrl() {
				return IuIterable.empty();
			}
		}));

		assertEquals("X.509 certificate is not a valid CA signing cert", e.getMessage(), () -> IuException.trace(e));
	}

	@Test
	public void testRejectsInvalid() {
		final var verifier = new CaVerifier(ca);
		final var ipki = mock(PkiPrincipal.class);
		final var sub = new Subject();
		when(ipki.getSubject()).thenReturn(sub);
		var e = assertThrows(IllegalArgumentException.class, () -> verifier.verify(ipki));
		assertEquals("missing public key", e.getMessage());
	}

	@Test
	public void testRejectSelfSigned() {
		final var verifier = new CaVerifier(ca);

		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-new", "-key", privateKeyFile.toString(), //
				"-subj", "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);
		final var signed = WebKey.builder(jwk.getType()) //
				.keyId(jwk.getKeyId()) //
				.key(privateKey) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();

		final var e = assertThrows(IllegalArgumentException.class, () -> verifier.verify(new PkiPrincipal(signed)));
		assertEquals("issuer not trusted", e.getMessage(), () -> IuException.trace(e));
	}

	@Test
	public void testRejectRevoked() {
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var csr = IuProcess.exec( //
				"openssl", "req", "-new", "-key", privateKeyFile.toString(), //
				"-subj", "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);

		final var csrFile = IuProcess.temp(PrintStream::println, csr);
		final var caOut = IuProcess.exec( //
				"openssl", "ca", "-batch", "-in", csrFile.toString(), //
				"-config", caConfig.toString(), "-days", "1" //
		);

		final var pemCert = caOut.substring(caOut.indexOf("-----BEGIN"));
		final var certFile = IuProcess.temp(PrintStream::println, pemCert);
		IuProcess.exec( //
				"openssl", "ca", "-batch", "-revoke", certFile.toString(), //
				"-config", caConfig.toString() //
		);

		final var signed = WebKey.builder(jwk.getType()) //
				.keyId(jwk.getKeyId()) //
				.key(privateKey) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert + System.lineSeparator() + caCert) //
				.build();

		final var verifier = new CaVerifier(ca);
		IuTestLogger.expect(CaVerifier.class.getName(), Level.INFO, "ca:invalid:" + cakid + " rejected " + kid,
				CertPathValidatorException.class);
		final var e = assertThrows(IuAuthenticationException.class, () -> verifier.verify(new PkiPrincipal(signed)));
		assertInstanceOf(CertPathValidatorException.class, e.getCause(), () -> IuException.trace(e));
	}

}
