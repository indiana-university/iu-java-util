package iu.crypt.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.security.interfaces.RSAPrivateKey;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IdGenerator;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
@ExtendWith(CryptCliTestSupport.class)
public class WebKeyCliTest {

	@Test
	void testUsage() {
		WebKeyCli.main(new String[0]);
		assertEquals(WebKeyCli.USAGE, CryptCliTestSupport.ERR.toString());
	}

	@Test
	public void testParseSerial() {
		final var bytes = new byte[32];
		ThreadLocalRandom.current().nextBytes(bytes);
		final var serial = new BigInteger(bytes);
		assertEquals(serial, WebKeyCli.parseSerial(HexFormat.of().withUpperCase().formatHex(serial.toByteArray())));
		assertEquals(serial, WebKeyCli.parseSerial(HexFormat.ofDelimiter(":").formatHex(serial.toByteArray())));
		assertEquals(serial, WebKeyCli.parseSerial(WebKeyCli.formatSerial(serial)));
		assertThrows(IllegalArgumentException.class, () -> WebKeyCli.parseSerial("foobar!"));
	}

	@Test
	public void testKeyUsageECDH() {
		final var jwk = mock(WebKey.class);
		when(jwk.getAlgorithm()).thenReturn(Algorithm.ECDH_ES);
		assertEquals("keyAgreement", WebKeyCli.keyUsage(jwk));
	}

	@Test
	public void testKeyUsageEDDSA() {
		final var jwk = mock(WebKey.class);
		when(jwk.getAlgorithm()).thenReturn(Algorithm.EDDSA);
		assertEquals("digitalSignature", WebKeyCli.keyUsage(jwk));
	}

	@Test
	public void testKeyUsageRSAOAEP() {
		final var jwk = mock(WebKey.class);
		when(jwk.getAlgorithm()).thenReturn(Algorithm.RSA_OAEP);
		assertEquals("keyEncipherment", WebKeyCli.keyUsage(jwk));
	}

	@Test
	public void testKeyUsageDir() {
		final var jwk = mock(WebKey.class);
		when(jwk.getAlgorithm()).thenReturn(Algorithm.DIRECT);
		assertThrows(IllegalArgumentException.class, () -> WebKeyCli.keyUsage(jwk));
	}

	@Test
	public void testKeyUsageECP256() {
		final var jwk = mock(WebKey.class);
		when(jwk.getType()).thenReturn(WebKey.Type.EC_P256);
		assertEquals("digitalSignature,keyAgreement", WebKeyCli.keyUsage(jwk));
		when(jwk.getUse()).thenReturn(Use.SIGN, Use.ENCRYPT);
		assertEquals("digitalSignature", WebKeyCli.keyUsage(jwk));
		assertEquals("keyAgreement", WebKeyCli.keyUsage(jwk));
	}

	@Test
	public void testKeyUsageED25519() {
		final var jwk = mock(WebKey.class);
		when(jwk.getType()).thenReturn(WebKey.Type.ED25519);
		assertEquals("digitalSignature", WebKeyCli.keyUsage(jwk));
		when(jwk.getUse()).thenReturn(Use.SIGN, Use.ENCRYPT);
		assertEquals("digitalSignature", WebKeyCli.keyUsage(jwk));
		assertThrows(IllegalArgumentException.class, () -> WebKeyCli.keyUsage(jwk));
	}

	@Test
	public void testKeyUsageX25519() {
		final var jwk = mock(WebKey.class);
		when(jwk.getType()).thenReturn(WebKey.Type.X25519);
		assertEquals("keyAgreement", WebKeyCli.keyUsage(jwk));
		when(jwk.getUse()).thenReturn(Use.SIGN, Use.ENCRYPT);
		assertThrows(IllegalArgumentException.class, () -> WebKeyCli.keyUsage(jwk));
		assertEquals("keyAgreement", WebKeyCli.keyUsage(jwk));
	}

	@Test
	public void testKeyUsageRSA() {
		final var jwk = mock(WebKey.class);
		when(jwk.getType()).thenReturn(WebKey.Type.RSA);
		assertEquals("digitalSignature,keyEncipherment", WebKeyCli.keyUsage(jwk));
		when(jwk.getUse()).thenReturn(Use.SIGN, Use.ENCRYPT);
		assertEquals("digitalSignature", WebKeyCli.keyUsage(jwk));
		assertEquals("keyEncipherment", WebKeyCli.keyUsage(jwk));
	}

	@Test
	public void testKeyUsageRAW() {
		final var jwk = mock(WebKey.class);
		when(jwk.getType()).thenReturn(WebKey.Type.RAW);
		assertThrows(IllegalArgumentException.class, () -> WebKeyCli.keyUsage(jwk));
	}

	@Test
	public void testSubjectOrg() {
		System.setProperty("iu.crypt.cli.pki.org", "foobar!");
		assertThrows(IllegalArgumentException.class, () -> WebKeyCli.subjectOrg());
		System.setProperty("iu.crypt.cli.pki.org", "/C=US/ST=Indiana/L=Bloomington/O=Indiana University/OU=ESAS");
		assertEquals("/C=US/ST=Indiana/L=Bloomington/O=Indiana University/OU=ESAS", WebKeyCli.subjectOrg());
		System.setProperty("iu.crypt.cli.pki.org", "");
		assertEquals("", WebKeyCli.subjectOrg());
	}

	@Test
	void testInvalidCommand() {
		final var cmd = IdGenerator.generateId();
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKeyCli.main(new String[] { cmd }));
		assertEquals("invalid command " + cmd, error.getMessage());
		assertEquals(WebKeyCli.USAGE, CryptCliTestSupport.ERR.toString());
	}

	@Test
	void testMissingKeyType() {
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKeyCli.main(new String[] { "create" }));
		assertEquals("missing key type", error.getMessage());
		assertEquals(WebKeyCli.USAGE, CryptCliTestSupport.ERR.toString());
	}

	@Test
	void testInvalidKeyType() {
		final var type = IdGenerator.generateId();
		final var error = assertThrows(IllegalArgumentException.class,
				() -> WebKeyCli.main(new String[] { "create", type }));
		assertEquals("No enum constant edu.iu.crypt.WebKey.Type." + type, error.getMessage());
		assertEquals(WebKeyCli.USAGE, CryptCliTestSupport.ERR.toString());
	}

	@Test
	void testED25519() {
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "create", "ED25519" }));
		assertEquals("", CryptCliTestSupport.ERR.toString());
		assertEquals(WebKey.Type.ED25519, WebKey.parse(CryptCliTestSupport.OUT.toString()).getType());
	}

	@Test
	void testECWithAlg() {
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "create", "EC_P521", "ECDH-ES" }));
		final var key = WebKey.parse(CryptCliTestSupport.OUT.toString());
		assertEquals(WebKey.Type.EC_P521, key.getType());
		assertEquals(Algorithm.ECDH_ES, key.getAlgorithm());
	}

	@Test
	void testES256() {
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "create", "ES256" }));
		final var key = WebKey.parse(CryptCliTestSupport.OUT.toString());
		assertEquals(WebKey.Type.EC_P256, key.getType());
		assertEquals(Algorithm.ES256, key.getAlgorithm());
	}

	@Test
	void testCreateCustomKeyId() {
		final var keyId = IdGenerator.generateId();
		WebKeyCli.main(new String[] { "create", "ED25519", keyId });
		final var key = WebKey.parse(CryptCliTestSupport.OUT.toString());
		assertEquals(keyId, key.getKeyId());
	}

	@Test
	void testRSAOAEP3072WithCustomKeyId() {
		final var keyId = IdGenerator.generateId();
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "create", "RSA", "RSA-OAEP", "3072", keyId }));
		final var key = WebKey.parse(CryptCliTestSupport.OUT.toString());
		assertEquals(WebKey.Type.RSA, key.getType());
		assertEquals(Algorithm.RSA_OAEP, key.getAlgorithm());
		assertEquals(3072, ((RSAPrivateKey) key.getPrivateKey()).getModulus().bitLength());
		assertEquals(keyId, key.getKeyId());
	}

	@Test
	void testRSAOAEP2048() {
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "create", "RSA-OAEP", "2048" }));
		final var key = WebKey.parse(CryptCliTestSupport.OUT.toString());
		assertEquals(WebKey.Type.RSA, key.getType());
		assertEquals(Algorithm.RSA_OAEP, key.getAlgorithm());
		assertEquals(2048, ((RSAPrivateKey) key.getPrivateKey()).getModulus().bitLength());
	}

	@Test
	void testFormatPki() {
		assertEquals("EE", WebKeyCli.formatPki(-1,
				new boolean[] { false, false, false, false, false, false, false, false, false }));
		assertEquals(
				"CA pathLen=34,digitalSignature,nonRepudiation,keyEncipherment,dataEncipherment,keyAgreement,keyCertSign,cRLSign,encipherOnly,decipherOnly",
				WebKeyCli.formatPki(34, new boolean[] { true, true, true, true, true, true, true, true, true, true }));
	}

	@Test
	void testPrintNoAlg() {
		CryptCliTestSupport.input(WebKey.builder(WebKey.Type.ED25519).ephemeral().build().toString());
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "print" }));
		assertEquals(
				"JWK Private Key" + System.lineSeparator() + "-----------------------------" + System.lineSeparator()
						+ "Type:    OKP Ed25519" + System.lineSeparator() + System.lineSeparator(),
				CryptCliTestSupport.OUT.toString());
	}

	@Test
	void testPrintNoAlgRsa() {
		CryptCliTestSupport.input(WebKey.builder(WebKey.Type.RSA).ephemeral(4096).build().toString());
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "print" }));
		assertEquals(
				"JWK Private Key" + System.lineSeparator() + "-----------------------------" + System.lineSeparator()
						+ "Type:    RSA 4096-bit" + System.lineSeparator() + System.lineSeparator(),
				CryptCliTestSupport.OUT.toString(), CryptCliTestSupport.ERR.toString());
	}

	@Test
	void testPrintPS256() {
		final var jwk = WebKey.builder(Algorithm.PS256).ephemeral().build().wellKnown();
		CryptCliTestSupport.input(jwk.toString());
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "print" }));
		assertEquals("JWK Public Key" + System.lineSeparator() + "-----------------------------"
				+ System.lineSeparator() + "Type:    RSASSA-PSS 2048-bit" + System.lineSeparator() + "Algorithm: PS256"
				+ System.lineSeparator() + System.lineSeparator(), CryptCliTestSupport.OUT.toString());
	}

	@Test
	void testPrintSelfCert() {
		final var kid = IdGenerator.generateId();
		IuTestLogger.allow("iu.crypt.cli", Level.FINE);
		final var jwk = WebKeyCli.self(WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build()).wellKnown();
		final var cert = jwk.getCertificateChain()[0];
		CryptCliTestSupport.input(jwk.toString());
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "print" }));
		assertEquals("JWK Public Key" + System.lineSeparator() + "-----------------------------"
				+ System.lineSeparator() + "ID:      " + kid + System.lineSeparator() + "Type:    OKP Ed25519"
				+ System.lineSeparator() + "Algorithm: EdDSA" + System.lineSeparator() + System.lineSeparator()
				+ "X.509 Certificate Chain" + System.lineSeparator() + "======================="
				+ System.lineSeparator() + " 1) " + cert.getNotAfter() + " "
				+ WebKeyCli.formatSerial(cert.getSerialNumber()) + " " + kid + " EE,digitalSignature"
				+ System.lineSeparator() + "    ** Self-Signed Certificate **" + System.lineSeparator()
				+ System.lineSeparator(), CryptCliTestSupport.OUT.toString());
	}

	@Test
	void testPrintHS256() {
		CryptCliTestSupport.input(WebKey.builder(Algorithm.HS256).ephemeral().build().toString());
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "print" }));
		assertEquals("JWK Secret Key" + System.lineSeparator() + "-----------------------------"
				+ System.lineSeparator() + "Type:    oct 256-bit" + System.lineSeparator() + "Algorithm: HS256"
				+ System.lineSeparator() + System.lineSeparator(), CryptCliTestSupport.OUT.toString());
	}

	@Test
	void testSelf() {
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(WebKey.Type.ED25519).keyId(kid).ephemeral().build();
		CryptCliTestSupport.input(jwk.toString());
		WebKeyCli.main(new String[] { "self" });
		final var certJwk = WebKey.parse(CryptCliTestSupport.OUT.toString());
		final var cert = certJwk.getCertificateChain()[0];
		assertEquals("CN=" + kid, cert.getSubjectX500Principal().getName());
		assertEquals(-1, cert.getBasicConstraints());
		assertArrayEquals(new boolean[] { true, false, false, false, false, false, false, false, false },
				cert.getKeyUsage());
	}

	@Test
	void testExport() {
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(WebKey.Type.RSA).keyId(kid).ephemeral(2048).build();
		final var certJwk = WebKeyCli.self(jwk);
		CryptCliTestSupport.input(certJwk.toString());
		WebKeyCli.main(new String[] { "export" });
		System.out.flush();
		final var cert = PemEncoded.parse(CryptCliTestSupport.OUT.toString()).next().asCertificate();
		assertEquals("CN=" + kid, cert.getSubjectX500Principal().getName());
		assertEquals(-1, cert.getBasicConstraints());
		assertArrayEquals(new boolean[] { true, false, true, false, false, false, false, false, false },
				cert.getKeyUsage());
	}

	@Test
	void testExportNoCert() {
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(WebKey.Type.RSA).keyId(kid).ephemeral(2048).build();
		CryptCliTestSupport.input(jwk.toString());
		assertThrows(IllegalArgumentException.class, () -> WebKeyCli.main(new String[] { "export" }));
	}

	@Test
	void testExportSerial() {
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(WebKey.Type.RSA).keyId(kid).ephemeral(2048).build();
		final var certJwk = WebKeyCli.self(jwk);
		CryptCliTestSupport.input(certJwk.toString());
		WebKeyCli.main(
				new String[] { "export", WebKeyCli.formatSerial(certJwk.getCertificateChain()[0].getSerialNumber()) });
		final var pem = PemEncoded.parse(CryptCliTestSupport.OUT.toString());
		final var cert = pem.next().asCertificate();
		assertFalse(pem.hasNext());
		assertEquals("CN=" + kid, cert.getSubjectX500Principal().getName());
		assertEquals(-1, cert.getBasicConstraints());
		assertArrayEquals(new boolean[] { true, false, true, false, false, false, false, false, false },
				cert.getKeyUsage());
	}

	@Test
	void testExportBadSerial() {
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(WebKey.Type.RSA).keyId(kid).ephemeral(2048).build();
		final var certJwk = WebKeyCli.self(jwk);
		CryptCliTestSupport.input(certJwk.toString());
		final var bytes = new byte[32];
		ThreadLocalRandom.current().nextBytes(bytes);
		assertThrows(IllegalArgumentException.class,
				() -> WebKeyCli.main(new String[] { "export", WebKeyCli.formatSerial(new BigInteger(bytes)) }));
	}

}
