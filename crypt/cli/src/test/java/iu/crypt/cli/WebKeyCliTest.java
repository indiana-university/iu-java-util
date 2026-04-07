package iu.crypt.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.interfaces.RSAPrivateKey;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.iu.IdGenerator;
import edu.iu.IuProcess;
import edu.iu.client.IuJson;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.X500Utils;
import edu.iu.test.CliTestSupport;
import edu.iu.test.IuTestLogger;
import iu.crypt.CryptJsonAdapters;

@SuppressWarnings("javadoc")
@ExtendWith(CliTestSupport.class)
public class WebKeyCliTest {

	@Test
	void testUsage() {
		WebKeyCli.main(new String[0]);
		assertEquals(WebKeyCli.USAGE, CliTestSupport.ERR.toString());
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
		CliTestSupport.input("{}");
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKeyCli.main(new String[] { cmd }));
		assertEquals("invalid command " + cmd, error.getMessage());
		assertEquals(WebKeyCli.USAGE, CliTestSupport.ERR.toString());
	}

	@Test
	void testMissingKeyType() {
		final var error = assertThrows(IllegalArgumentException.class, () -> WebKeyCli.main(new String[] { "create" }));
		assertEquals("missing key type", error.getMessage());
		assertEquals(WebKeyCli.USAGE, CliTestSupport.ERR.toString());
	}

	@Test
	void testInvalidKeyType() {
		final var type = IdGenerator.generateId();
		final var error = assertThrows(IllegalArgumentException.class,
				() -> WebKeyCli.main(new String[] { "create", type }));
		assertEquals("No enum constant edu.iu.crypt.WebKey.Type." + type, error.getMessage());
		assertEquals(WebKeyCli.USAGE, CliTestSupport.ERR.toString());
	}

	@Test
	void testED25519() {
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "create", "ED25519" }));
		assertEquals("", CliTestSupport.ERR.toString());
		assertEquals(WebKey.Type.ED25519, WebKey.parse(CliTestSupport.OUT.toString()).getType());
	}

	@Test
	void testECWithAlg() {
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "create", "EC_P521", "ECDH-ES" }));
		final var key = WebKey.parse(CliTestSupport.OUT.toString());
		assertEquals(WebKey.Type.EC_P521, key.getType());
		assertEquals(Algorithm.ECDH_ES, key.getAlgorithm());
	}

	@Test
	void testES256() {
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "create", "ES256" }));
		final var key = WebKey.parse(CliTestSupport.OUT.toString());
		assertEquals(WebKey.Type.EC_P256, key.getType());
		assertEquals(Algorithm.ES256, key.getAlgorithm());
	}

	@Test
	void testCreateCustomKeyId() {
		final var keyId = IdGenerator.generateId();
		WebKeyCli.main(new String[] { "create", "ED25519", keyId });
		final var key = WebKey.parse(CliTestSupport.OUT.toString());
		assertEquals(keyId, key.getKeyId());
	}

	@Test
	void testRSAOAEP3072WithCustomKeyId() {
		final var keyId = IdGenerator.generateId();
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "create", "RSA", "RSA-OAEP", "3072", keyId }));
		final var key = WebKey.parse(CliTestSupport.OUT.toString());
		assertEquals(WebKey.Type.RSA, key.getType());
		assertEquals(Algorithm.RSA_OAEP, key.getAlgorithm());
		assertEquals(3072, ((RSAPrivateKey) key.getPrivateKey()).getModulus().bitLength());
		assertEquals(keyId, key.getKeyId());
	}

	@Test
	void testRSAOAEP2048() {
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "create", "RSA-OAEP", "2048" }));
		final var key = WebKey.parse(CliTestSupport.OUT.toString());
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
	void testPrintError() {
		CliTestSupport.input("{}");
		assertThrows(NullPointerException.class, () -> WebKeyCli.main(new String[] { "print" }));
	}

	@Test
	void testPrintNoAlg() {
		CliTestSupport.input(WebKey.builder(WebKey.Type.ED25519).ephemeral().build().toString());
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "print" }));
		assertEquals(
				"JWK Private Key" + System.lineSeparator() + "-----------------------------" + System.lineSeparator()
						+ "Type:    OKP Ed25519" + System.lineSeparator() + System.lineSeparator(),
				CliTestSupport.OUT.toString());
	}

	@Test
	void testPrintNoAlgRsa() {
		CliTestSupport.input(WebKey.builder(WebKey.Type.RSA).ephemeral(4096).build().toString());
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "print" }));
		assertEquals(
				"JWK Private Key" + System.lineSeparator() + "-----------------------------" + System.lineSeparator()
						+ "Type:    RSA 4096-bit" + System.lineSeparator() + System.lineSeparator(),
				CliTestSupport.OUT.toString(), CliTestSupport.ERR.toString());
	}

	@Test
	void testPrintPS256() {
		final var jwk = WebKey.builder(Algorithm.PS256).ephemeral().build().wellKnown();
		CliTestSupport.input(jwk.toString());
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "print" }));
		assertEquals("JWK Public Key" + System.lineSeparator() + "-----------------------------"
				+ System.lineSeparator() + "Type:    RSASSA-PSS 2048-bit" + System.lineSeparator() + "Algorithm: PS256"
				+ System.lineSeparator() + System.lineSeparator(), CliTestSupport.OUT.toString());
	}

	@Test
	void testPrintSelfCert() {
		final var kid = IdGenerator.generateId();
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var jwk = WebKeyCli.self(WebKey.builder(Algorithm.EDDSA).keyId(kid).ephemeral().build()).wellKnown();
		final var cert = jwk.getCertificateChain()[0];
		CliTestSupport.input(jwk.toString());
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "print" }));
		assertEquals("JWK Public Key" + System.lineSeparator() + "-----------------------------"
				+ System.lineSeparator() + "ID:      " + kid + System.lineSeparator() + "Type:    OKP Ed25519"
				+ System.lineSeparator() + "Algorithm: EdDSA" + System.lineSeparator() + System.lineSeparator()
				+ "X.509 Certificate Chain" + System.lineSeparator() + "======================="
				+ System.lineSeparator() + " 1) " + cert.getNotAfter() + " "
				+ WebKeyCli.formatSerial(cert.getSerialNumber()) + " " + kid + " EE,digitalSignature"
				+ System.lineSeparator() + System.lineSeparator(), CliTestSupport.OUT.toString());
	}

	@Test
	void testPrintHS256() {
		CliTestSupport.input(WebKey.builder(Algorithm.HS256).ephemeral().build().toString());
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "print" }));
		assertEquals("JWK Secret Key" + System.lineSeparator() + "-----------------------------"
				+ System.lineSeparator() + "Type:    oct 256-bit" + System.lineSeparator() + "Algorithm: HS256"
				+ System.lineSeparator() + System.lineSeparator(), CliTestSupport.OUT.toString());
	}

	@Test
	void testSelf() {
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(WebKey.Type.ED25519).keyId(kid).ephemeral().build();
		CliTestSupport.input(jwk.toString());
		WebKeyCli.main(new String[] { "self" });
		final var certJwk = WebKey.parse(CliTestSupport.OUT.toString());
		final var cert = certJwk.getCertificateChain()[0];
		assertEquals("CN=" + kid, cert.getSubjectX500Principal().getName());
		assertEquals(-1, cert.getBasicConstraints());
		assertArrayEquals(new boolean[] { true, false, false, false, false, false, false, false, false },
				cert.getKeyUsage());
	}

	@Test
	void testNewCA() {
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(WebKey.Type.ED448).keyId(kid).ephemeral().build();
		CliTestSupport.input(jwk.toString());
		IuTestLogger.expect(WebKeyCli.class.getName(), Level.FINE, "OpenSSL CA config.*");
		WebKeyCli.main(new String[] { "ca" });
		final var ca = CryptJsonAdapters.CA.fromJson(IuJson.parse(CliTestSupport.OUT.toString()));
		final var certJwk = ca.getJwk();
		final var cert = certJwk.getCertificateChain()[0];
		assertEquals("CN=" + kid, cert.getSubjectX500Principal().getName());
		assertEquals(0, cert.getBasicConstraints());
		assertArrayEquals(new boolean[] { false, false, false, false, false, true, true, false, false },
				cert.getKeyUsage());

		final var database = ca.getDatabase();
		assertEquals(0, database.length);
		assertFalse(ca.getCertificates().iterator().hasNext());
		assertNull(ca.getCrl().iterator().next().getRevokedCertificates());
	}

	@Test
	void testPrintNewCA() {
		IuTestLogger.allow(WebKeyCli.class.getName(), Level.FINE);
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(WebKey.Type.ED448).keyId(kid).ephemeral().build();
		final var ca = WebKeyCli.ca(jwk);
		final var cert = ca.getJwk().getCertificateChain()[0];
		final var crl = ca.getCrl().iterator().next();
		CliTestSupport.input(CryptJsonAdapters.CA.toJson(ca).toString());
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "print" }));
		assertEquals(
				"X.509 Certificate Authority" + System.lineSeparator() + System.lineSeparator() + "JWK Private Key"
						+ System.lineSeparator() + "-----------------------------" + System.lineSeparator()
						+ "ID:      " + kid + System.lineSeparator() + "Type:    OKP Ed448" + System.lineSeparator()
						+ System.lineSeparator() + "X.509 Certificate Chain" + System.lineSeparator()
						+ "=======================" + System.lineSeparator() + " 1) " + cert.getNotAfter() + " "
						+ WebKeyCli.formatSerial(cert.getSerialNumber()) + " " + kid
						+ " CA pathLen=0,keyCertSign,cRLSign" + System.lineSeparator() + System.lineSeparator()
						+ "Database: 0 bytes" + System.lineSeparator() + System.lineSeparator() + "CRL Issuer: CN="
						+ kid + System.lineSeparator() + "Update Due: " + crl.getNextUpdate() + System.lineSeparator()
						+ "** no certificates revoked **" + System.lineSeparator() + System.lineSeparator(),
				CliTestSupport.OUT.toString());
	}

	@Test
	void testCASignAndRevoke() throws IOException {
		IuTestLogger.allow(WebKeyCli.class.getName(), Level.FINE);
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);

		final var eekid = IdGenerator.generateId();
		final var eejwk = WebKey.builder(WebKey.Type.ED25519).keyId(eekid).ephemeral().build();
		CliTestSupport.input(eejwk.toString());
		WebKeyCli.main(new String[] { "req" });
		final var req = CliTestSupport.OUT.toByteArray();
		CliTestSupport.OUT.reset();

		final var eekid2 = IdGenerator.generateId();
		final var eejwk2 = WebKey.builder(WebKey.Type.ED25519).keyId(eekid2).ephemeral().build();
		CliTestSupport.input(eejwk2.toString());
		WebKeyCli.main(new String[] { "req" });
		final var req2 = CliTestSupport.OUT.toByteArray();
		CliTestSupport.OUT.reset();

		final var cakid = IdGenerator.generateId();
		final var cajwk = WebKey.builder(WebKey.Type.ED448).keyId(cakid).ephemeral().build();
		final var ca = WebKeyCli.ca(cajwk);

		final var csr = IuProcess.createTempFile();
		Files.write(csr, req);
		CliTestSupport.input(CryptJsonAdapters.CA.toJson(ca).toString());
		WebKeyCli.main(new String[] { "sign", csr.toString() });
		final var caWith1Cert = CryptJsonAdapters.CA.fromJson(IuJson.parse(CliTestSupport.OUT.toString()));
		CliTestSupport.OUT.reset();

		final var csr2 = IuProcess.createTempFile();
		Files.write(csr2, req2);
		CliTestSupport.input(CryptJsonAdapters.CA.toJson(caWith1Cert).toString());
		WebKeyCli.main(new String[] { "sign", csr2.toString() });
		final var caWithCert = CryptJsonAdapters.CA.fromJson(IuJson.parse(CliTestSupport.OUT.toString()));
		CliTestSupport.OUT.reset();

		final var newCert = caWithCert.getCertificates().iterator().next();
		final var caCert = caWithCert.getJwk().getCertificateChain()[0];
		final var certFile = IuProcess.createTempFile();
		try (final var out = Files.newOutputStream(certFile); final var ps = new PrintStream(out)) {
			PemEncoded.print(ps, newCert);
			PemEncoded.print(ps, caCert);
		}
		CliTestSupport.input(eejwk.toString());
		WebKeyCli.main(new String[] { "cert", certFile.toString() });
		final var eeWithCert = CryptJsonAdapters.WEBKEY.fromJson(IuJson.parse(CliTestSupport.OUT.toString()));
		CliTestSupport.OUT.reset();

		assertEquals(newCert, eeWithCert.getCertificateChain()[0]);
		assertEquals(caCert, eeWithCert.getCertificateChain()[1]);

		final var certIter = caWithCert.getCertificates().iterator();
		certIter.next();
		final var newCert2 = certIter.next();
		CliTestSupport.input(CryptJsonAdapters.CA.toJson(caWithCert).toString());
		WebKeyCli.main(new String[] { "export", WebKeyCli.formatSerialOSSL(newCert2.getSerialNumber()) });
		final var exportPem = PemEncoded.parse(CliTestSupport.OUT.toString());
		CliTestSupport.OUT.reset();

		assertEquals(newCert2, exportPem.next().asCertificate());
		assertEquals(caCert, exportPem.next().asCertificate());

		CliTestSupport.input(CryptJsonAdapters.CA.toJson(caWithCert).toString());
		WebKeyCli.main(new String[] { "revoke", WebKeyCli.formatSerial(newCert.getSerialNumber()) });
		final var caWithRevokedCert = CryptJsonAdapters.CA.fromJson(IuJson.parse(CliTestSupport.OUT.toString()));
		CliTestSupport.OUT.reset();

		assertEquals(eekid2, X500Utils
				.getCommonName(caWithRevokedCert.getCertificates().iterator().next().getSubjectX500Principal()));
		assertEquals(newCert.getSerialNumber(), caWithRevokedCert.getCrl().iterator().next().getRevokedCertificates()
				.iterator().next().getSerialNumber());

		assertThrows(IllegalArgumentException.class,
				() -> WebKeyCli.export(null, caWithRevokedCert, newCert.getSerialNumber()));

		final var print = new ByteArrayOutputStream();
		final var printStream = new PrintStream(print);
		WebKeyCli.print(printStream, caWithRevokedCert);

		assertEquals(
				"X.509 Certificate Authority" + System.lineSeparator() + System.lineSeparator() + "JWK Private Key"
						+ System.lineSeparator() + "-----------------------------" + System.lineSeparator()
						+ "ID:      " + cakid + System.lineSeparator() + "Type:    OKP Ed448" + System.lineSeparator()
						+ System.lineSeparator() + "X.509 Certificate Chain" + System.lineSeparator()
						+ "=======================" + System.lineSeparator() + " 1) " + caCert.getNotAfter() + " "
						+ WebKeyCli.formatSerial(caCert.getSerialNumber()) + " " + cakid
						+ " CA pathLen=0,keyCertSign,cRLSign" + System.lineSeparator() + System.lineSeparator()
						+ "Database: " + caWithRevokedCert.getDatabase().length + " bytes" + System.lineSeparator()
						+ System.lineSeparator() + "Issued Certificates:" + System.lineSeparator() + "  "
						+ newCert2.getNotAfter() + " " + WebKeyCli.formatSerial(newCert2.getSerialNumber()) + " "
						+ eekid2 + " EE,digitalSignature" + System.lineSeparator() + System.lineSeparator()
						+ "CRL Issuer: CN=" + cakid + System.lineSeparator() + "Update Due: "
						+ caWithRevokedCert.getCrl().iterator().next().getNextUpdate() + System.lineSeparator() + "  "
						+ WebKeyCli.formatSerial(newCert.getSerialNumber()) + " "
						+ caWithRevokedCert.getCrl().iterator().next().getRevokedCertificates().iterator().next()
								.getRevocationDate()
						+ System.lineSeparator() + System.lineSeparator(),
				print.toString());
	}

	@Test
	void testPublic() {
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(WebKey.Type.RSA).keyId(kid).ephemeral(2048).build();
		final var certJwk = WebKeyCli.self(jwk);
		CliTestSupport.input(certJwk.toString());
		WebKeyCli.main(new String[] { "public" });
		final var outJwk = WebKey.parse(CliTestSupport.OUT.toString());
		assertEquals(certJwk.wellKnown(), outJwk);
	}

	@Test
	void testPublicCA() {
		IuTestLogger.allow(WebKeyCli.class.getName(), Level.FINE);
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var kid = IdGenerator.generateId();
		final var jwk = WebKey.builder(WebKey.Type.ED448).keyId(kid).ephemeral().build();
		final var ca = WebKeyCli.ca(jwk);
		final var crl = ca.getCrl().iterator().next();
		CliTestSupport.input(CryptJsonAdapters.CA.toJson(ca).toString());
		assertDoesNotThrow(() -> WebKeyCli.main(new String[] { "public" }));
		final var outCa = CryptJsonAdapters.CA.fromJson(IuJson.parse(CliTestSupport.OUT.toString()));
		assertEquals(ca.getJwk().wellKnown(), outCa.getJwk());
		assertNull(outCa.getDatabase());
		assertNull(outCa.getCertificates());
		assertEquals(crl, outCa.getCrl().iterator().next());
	}

}
