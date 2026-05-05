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
package iu.pki;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuProcess;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.X500Utils;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class PkiPrincipalTest {

	@BeforeEach
	void setup() {
		IuTestLogger.allow("edu.iu.crypt", Level.CONFIG);
	}
	
	@Test
	public void testSelf() {
		final var kid = IdGenerator.generateId();
		final var cn = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();
		final var keyType = jwk.getType();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + cn.replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);
		IuProcess.deleteTempFiles();

		final var self = WebKey.builder(keyType) //
				.keyId(jwk.getKeyId()) //
				.key(privateKey) //
				.key(jwk.getPublicKey()) // s
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();
		final var pki = new PkiPrincipal(self);
		assertEquals(cn, pki.getName());
		assertEquals(self, pki.getJwk());

		final var pub = new PkiPrincipal(self.wellKnown());
		assertEquals(cn, pub.getName());
		assertEquals(self.wellKnown(), pub.getJwk());
		
		assertEquals(pki, new PkiPrincipal(self));
		assertNotEquals(pki, pub);
		assertNotEquals(pki.hashCode(), pub.hashCode());

		assertNotEquals(pub, this);
		assertDoesNotThrow(pki::toString);
		assertDoesNotThrow(pub::toString);
	}

	@Test
	public void testMissingCert() {
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();
		assertEquals("missing certificate chain",
				assertThrows(NullPointerException.class, () -> new PkiPrincipal(jwk)).getMessage());
	}

	@Test
	public void testCa() {
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var caKid = IdGenerator.generateId();
		final var caJwk = WebKey.builder(WebKey.Type.ED448).keyId(caKid).ephemeral().build();
		final var caPrivateKey = Objects.requireNonNull(caJwk.getPrivateKey(), "Missing private key");
		final var caPrivateKeyFile = IuProcess.temp(PemEncoded::print, caPrivateKey);
		final var caCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", caPrivateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + caKid.replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=critical,CA:true,pathlen:0", //
				"-addext", "keyUsage=keyCertSign,cRLSign" //
		);

		final var databaseFile = IuProcess.temp(PrintStream::print, "");
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
		final var caConfig = IuProcess.temp(PrintStream::print, caConfigContents);

		final var kid = IdGenerator.generateId();
		final var cn = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();
		final var keyType = jwk.getType();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var csr = IuProcess.exec( //
				"openssl", "req", "-new", "-key", privateKeyFile.toString(), //
				"-subj", "/CN=" + cn.replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);

		final var csrFile = IuProcess.temp(PrintStream::println, csr);
		final var caOut = IuProcess.exec( //
				"openssl", "ca", "-batch", "-in", csrFile.toString(), //
				"-config", caConfig.toString(), "-days", "1" //
		);

		final var pemCert = caOut.substring(caOut.indexOf("-----BEGIN"));
		IuProcess.deleteTempFiles();
		IuException.unchecked(() -> {
			Files.deleteIfExists(Path.of(databaseFile + ".attr"));
			Files.deleteIfExists(Path.of(databaseFile + ".old"));
		});

		final var signed = WebKey.builder(keyType) //
				.keyId(jwk.getKeyId()) //
				.key(privateKey) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert + System.lineSeparator() + caCert) //
				.build();
		assertEquals(signed.getCertificateChain()[1], PemEncoded.parse(caCert).next().asCertificate());

		final var pki = new PkiPrincipal(signed);
		assertEquals(cn, pki.getName());
		assertEquals(signed, pki.getJwk());

		final var pub = new PkiPrincipal(signed.wellKnown());
		assertEquals(cn, pub.getName());
		assertEquals(signed.wellKnown(), pub.getJwk());

		assertDoesNotThrow(pki::toString);
		assertDoesNotThrow(pub::toString);
	}

}
