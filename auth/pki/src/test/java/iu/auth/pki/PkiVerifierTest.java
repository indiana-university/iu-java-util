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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.CertPathValidatorException;
import java.util.Objects;
import java.util.logging.Level;

import javax.security.auth.Subject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuProcess;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.X500Utils;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class PkiVerifierTest extends PkiTestCase {

	private WebKey jwk;
	private String pemCert;

	@BeforeEach
	void setup() {
		final var kid = IdGenerator.generateId();
		jwk = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);
	}

	@AfterEach
	void teardown() {
		IuProcess.deleteTempFiles();
	}

	@Test
	public void testSelfSignedAuthoritativeSuccess() {
		final var self = WebKey.builder(jwk.getType()) //
				.keyId(jwk.getKeyId()) //
				.key(jwk.getPrivateKey()) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();
		final var pkiv = new PkiVerifier(self);
		assertNull(pkiv.getAuthScheme());
		assertNull(pkiv.getAuthenticationEndpoint());
		assertEquals(PkiPrincipal.class, pkiv.getType());
		assertEquals(jwk.getKeyId(), pkiv.getRealm());
		assertEquals("PkiVerifier [" + jwk.getKeyId() + "]", pkiv.toString());
		assertTrue(pkiv.isAuthoritative());

		final var pki = new PkiPrincipal(self);
		IuTestLogger.expect(PkiVerifier.class.getName(), Level.INFO,
				"pki:auth:" + jwk.getKeyId() + "; trustAnchor: " + jwk.getKeyId());
		assertDoesNotThrow(() -> pkiv.verify(pki));
	}

	@Test
	public void testSelfSignedWellKnownSuccess() {
		final var self = WebKey.builder(jwk.getType()) //
				.keyId(jwk.getKeyId()) //
				.key(jwk.getPrivateKey()) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();
		final var pkiv = new PkiVerifier(self.wellKnown());
		assertNull(pkiv.getAuthScheme());
		assertNull(pkiv.getAuthenticationEndpoint());
		assertEquals(PkiPrincipal.class, pkiv.getType());
		assertEquals(jwk.getKeyId(), pkiv.getRealm());
		assertEquals("PkiVerifier [" + jwk.getKeyId() + "]", pkiv.toString());
		assertFalse(pkiv.isAuthoritative());

		final var pki = new PkiPrincipal(self);
		IuTestLogger.expect(PkiVerifier.class.getName(), Level.INFO,
				"pki:verify:" + jwk.getKeyId() + "; trustAnchor: " + jwk.getKeyId());
		assertDoesNotThrow(() -> pkiv.verify(pki));
	}

	@Test
	public void testRequiresSigningCert() {
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(WebKey.Type.EC_P256).keyId(kid).algorithm(Algorithm.ECDH_ES).ephemeral().build();
		final var keyType = jwk.getType();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);

		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);
		IuProcess.deleteTempFiles();

		final var self = WebKey.builder(keyType) //
				.keyId(jwk.getKeyId()) //
				.key(privateKey) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();
		assertEquals("X.509 certificate not valid for digital signature",
				assertThrows(IllegalArgumentException.class, () -> new PkiVerifier(self)).getMessage());
	}

	@Test
	public void testCNMismatch() {
		final var self = WebKey.builder(jwk.getType()) //
				.keyId(IdGenerator.generateId()) //
				.key(jwk.getPrivateKey()) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();
		assertEquals("Key ID doesn't match CN",
				assertThrows(IllegalArgumentException.class, () -> new PkiVerifier(self)).getMessage());
	}

	@Test
	public void testSelfSignedRejectsInvalid() {
		final var self = WebKey.builder(jwk.getType()) //
				.keyId(jwk.getKeyId()) //
				.key(jwk.getPrivateKey()) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();
		final var pkiv = new PkiVerifier(self.wellKnown());

		final var ipki = mock(PkiPrincipal.class);
		final var sub = new Subject();
		when(ipki.getSubject()).thenReturn(sub);
		var e = assertThrows(IllegalArgumentException.class, () -> pkiv.verify(ipki));
		assertEquals("missing public key", e.getMessage());
	}

	@Test
	public void testSelfSignedAuthoritativeRejectsWellKnown() {
		final var self = WebKey.builder(jwk.getType()) //
				.keyId(jwk.getKeyId()) //
				.key(jwk.getPrivateKey()) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();
		final var pkiv = new PkiVerifier(self);

		final var wellKnown = self.wellKnown();

		final var wpki = new PkiPrincipal(wellKnown);
		var e = assertThrows(IllegalArgumentException.class, () -> pkiv.verify(wpki));
		assertEquals("missing private key", e.getMessage());
	}

	@Test
	public void testSelfSignedAuthoritativeRejectsWrongPrivateKey() {
		final var self = WebKey.builder(jwk.getType()) //
				.keyId(jwk.getKeyId()) //
				.key(jwk.getPrivateKey()) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();
		final var pkiv = new PkiVerifier(self);

		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);

		final var wrongSelf = WebKey.builder(jwk.getType()) //
				.keyId(jwk.getKeyId()) //
				.key(jwk.getPrivateKey()) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();

		final var wpki = new PkiPrincipal(wrongSelf);
		var e = assertThrows(IllegalArgumentException.class, () -> pkiv.verify(wpki));
		assertEquals("private key mismatch", e.getMessage());
	}

	@Test
	public void testSelfSignedWellKnwonRejectsInvalidCert() {
		final var self = WebKey.builder(jwk.getType()) //
				.keyId(jwk.getKeyId()) //
				.key(jwk.getPrivateKey()) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();
		final var pkiv = new PkiVerifier(self.wellKnown());

		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);

		final var wrongSelf = WebKey.builder(jwk.getType()) //
				.keyId(jwk.getKeyId()) //
				.key(jwk.getPrivateKey()) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.pem(pemCert) //
				.build();

		final var wpki = new PkiPrincipal(wrongSelf);
		IuTestLogger.expect(PkiVerifier.class.getName(), Level.INFO, "pki:invalid:" + this.jwk.getKeyId() + " rejected " + jwk.getKeyId(),
				CertPathValidatorException.class);
		var e = assertThrows(IuAuthenticationException.class, () -> pkiv.verify(wpki));
		assertInstanceOf(CertPathValidatorException.class, e.getCause(), () -> IuException.trace(e));
	}

}
